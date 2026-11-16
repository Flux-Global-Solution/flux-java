package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.LeituraRequestDTO;
import br.com.fiap.flux.dto.LeituraResponseDTO;
import br.com.fiap.flux.dto.SimulacaoLeiturasRequestDTO;
import br.com.fiap.flux.mapper.LeituraMapper;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;
import br.com.fiap.flux.models.MoradorResidencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cadastro e consulta das leituras de consumo, todas simuladas: vêm do banco (script) ou dos
 * endpoints de teste, nunca de hardware, e não há sincronização real. Toda consulta sai em ordem
 * cronológica. Consultar exige vínculo com a residência; cadastrar leituras de teste exige ser
 * titular. Leituras já gravadas não são alteradas nem apagadas.
 *
 * <p>Quando entram leituras novas, o perfil de consumo da residência é atualizado e a análise de
 * consumo roda em seguida (RN01 e RN02), sem que ninguém precise chamá-la.</p>
 *
 * <p>As variantes só com o id do recurso agem em nome do titular, que é resolvido internamente.</p>
 */
@Service
public class LeituraService {

    private static final int LIMITE_CONJUNTO = 500;

    private final LeituraDAO leituraDAO;
    private final DispositivoDAO dispositivoDAO;
    private final ResidenciaDAO residenciaDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final PerfilConsumoService perfilConsumoService;
    private final AnaliseConsumoService analiseConsumoService;

    public LeituraService() {
        this.leituraDAO = new LeituraDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.perfilConsumoService = new PerfilConsumoService();
        this.analiseConsumoService = new AnaliseConsumoService();
    }

    /**
     * Cadastra uma leitura de teste. O dispositivo precisa existir e a sequência não pode se
     * repetir
     * nele. A sincronização é definida aqui (a leitura vale como recebida na própria data dela),
     * nunca
     * pelo cliente.
     *
     * @throws ResponseStatusException 403 se não for o titular da residência do dispositivo
     * @throws IllegalArgumentException se os dados são inválidos, o dispositivo não existe ou a
     *     sequência já existe
     * @throws IllegalStateException se o banco não gravar a leitura
     */
    public LeituraResponseDTO cadastrar(int idMorador, LeituraRequestDTO dto) {
        validar(dto);
        Dispositivo dispositivo = exigirDispositivoValido(dto.idDispositivo());
        exigirTitular(idMorador, dispositivo.getResidencia().getIdResidencia());
        exigirSequenciaLivre(dto.idDispositivo(), dto.sequencia());

        Leitura leitura = montar(dto);
        leituraDAO.cadastrar(leitura);
        if (leitura.getIdLeitura() == 0) {
            throw new IllegalStateException("Não foi possível gravar a leitura.");
        }
        atualizarPerfis(Set.of(dispositivo.getResidencia().getIdResidencia()));
        return LeituraMapper.toDto(buscarExistente(leitura.getIdLeitura()));
    }

    /**
     * Cadastra de uma vez um conjunto de leituras fictícias (até 500), em uma
     * transação: se qualquer leitura for inválida ou repetir sequência, nada é gravado. Vale para
     * vários
     * dispositivos, desde que o morador seja titular da residência de cada um.
     *
     * @throws ResponseStatusException 403 se não for titular de alguma das residências
     * @throws IllegalArgumentException se o conjunto é vazio, grande demais ou tem leitura inválida
     *     (a mensagem diz qual)
     */
    public List<LeituraResponseDTO> cadastrarConjunto(int idMorador, SimulacaoLeiturasRequestDTO dto) {
        if (dto == null || dto.leituras() == null || dto.leituras().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma leitura.");
        }
        if (dto.leituras().size() > LIMITE_CONJUNTO) {
            throw new IllegalArgumentException("O conjunto deve ter no máximo " + LIMITE_CONJUNTO + " leituras.");
        }

        Map<Integer, Dispositivo> dispositivos = new HashMap<>();
        Set<String> sequenciasDoConjunto = new HashSet<>();
        List<Leitura> leituras = new ArrayList<>();

        int posicao = 0;
        for (LeituraRequestDTO item : dto.leituras()) {
            posicao++;
            String prefixo = "Leitura " + posicao + ": ";
            if (item == null) {
                throw new IllegalArgumentException(prefixo + "dados não informados.");
            }
            try {
                validar(item);
                if (!dispositivos.containsKey(item.idDispositivo())) {
                    Dispositivo dispositivo = exigirDispositivoValido(item.idDispositivo());
                    exigirTitular(idMorador, dispositivo.getResidencia().getIdResidencia());
                    dispositivos.put(item.idDispositivo(), dispositivo);
                }
                if (!sequenciasDoConjunto.add(item.idDispositivo() + ":" + item.sequencia())) {
                    throw new IllegalArgumentException(
                            "Já existe outra leitura do conjunto com esta sequência para o dispositivo.");
                }
                exigirSequenciaLivre(item.idDispositivo(), item.sequencia());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(prefixo + e.getMessage());
            }
            leituras.add(montar(item));
        }

        leituraDAO.cadastrarLote(leituras);
        Set<Integer> residencias = new HashSet<>();
        for (Dispositivo dispositivo : dispositivos.values()) {
            residencias.add(dispositivo.getResidencia().getIdResidencia());
        }
        atualizarPerfis(residencias);
        return leituras.stream().map(LeituraMapper::toDto).toList();
    }

    /**
     * Consulta uma leitura. Qualquer morador vinculado à residência do dispositivo pode.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a leitura não existe
     */
    public LeituraResponseDTO buscarPorId(int idMorador, int idLeitura) {
        Leitura leitura = buscarExistente(idLeitura);
        Dispositivo dispositivo = exigirDispositivoValido(leitura.getDispositivo().getIdDispositivo());
        exigirVinculo(idMorador, dispositivo.getResidencia().getIdResidencia());
        return LeituraMapper.toDto(leitura);
    }

    /**
     * Lista as leituras em ordem cronológica, só das residências às quais o morador tem acesso.
     * Todos os
     * filtros são opcionais e se combinam.
     *
     * @param idResidencia  só leituras desta residência
     * @param idDispositivo só leituras deste dispositivo (se a residência também vier, ele precisa
     *     ser dela)
     * @param inicio        início do período: "2026-11-01" ou "2026-11-01T08:00"
     * @param fim           fim do período, no mesmo formato (só a data vale até o fim do dia)
     * @throws ResponseStatusException 403 se não houver vínculo com a residência
     * @throws IllegalArgumentException se o período é inválido ou residência/dispositivo não
     *     existem
     */
    public List<LeituraResponseDTO> listar(int idMorador, Integer idResidencia, Integer idDispositivo,
                                           String inicio, String fim) {
        LocalDateTime de = lerPeriodo(inicio, "inicio", false);
        LocalDateTime ate = lerPeriodo(fim, "fim", true);
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("O início do período não pode ser depois do fim.");
        }

        if (idResidencia != null) {
            if (idResidencia <= 0) {
                throw new IllegalArgumentException("Residência inválida.");
            }
            if (residenciaDAO.buscarPorId(idResidencia) == null) {
                throw new IllegalArgumentException("Residência não encontrada");
            }
            exigirVinculo(idMorador, idResidencia);
        }
        if (idDispositivo != null) {
            if (idDispositivo <= 0) {
                throw new IllegalArgumentException("Dispositivo inválido.");
            }
            Dispositivo dispositivo = dispositivoDAO.buscarPorId(idDispositivo);
            if (dispositivo == null) {
                throw new IllegalArgumentException("Dispositivo não encontrado");
            }
            exigirVinculo(idMorador, dispositivo.getResidencia().getIdResidencia());
            if (idResidencia != null && dispositivo.getResidencia().getIdResidencia() != idResidencia) {
                throw new IllegalArgumentException("O dispositivo não pertence à residência informada.");
            }
        }

        return leituraDAO.listarFiltrado(idResidencia, idDispositivo, idMorador, de, ate)
                .stream()
                .map(LeituraMapper::toDto)
                .toList();
    }

    /** Leituras de uma residência; é {@link #listar} com a residência fixa. */
    public List<LeituraResponseDTO> listarPorResidencia(int idMorador, int idResidencia, String inicio, String fim) {
        return listar(idMorador, idResidencia, null, inicio, fim);
    }

    /** Leituras de um dispositivo; é {@link #listar} com o dispositivo fixo. */
    public List<LeituraResponseDTO> listarPorDispositivo(int idMorador, int idDispositivo, String inicio, String fim) {
        return listar(idMorador, null, idDispositivo, inicio, fim);
    }

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public LeituraResponseDTO buscarPorId(int idLeitura) {
        Leitura leitura = buscarExistente(idLeitura);
        Dispositivo dispositivo = exigirDispositivoValido(leitura.getDispositivo().getIdDispositivo());
        int idMorador = moradorResidenciaDAO.buscarIdTitular(dispositivo.getResidencia().getIdResidencia());
        return buscarPorId(idMorador, idLeitura);
    }

    /**
     * Variante de {@link #listarPorResidencia(int, int, String, String)} sem {@code idMorador}: age
     * em
     * nome do titular.
     */
    public List<LeituraResponseDTO> listarPorResidencia(int idResidencia, String inicio, String fim) {
        return listarPorResidencia(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia, inicio, fim);
    }

    /**
     * Variante de {@link #listarPorDispositivo(int, int, String, String)} sem {@code idMorador}:
     * age em
     * nome do titular.
     */
    public List<LeituraResponseDTO> listarPorDispositivo(int idDispositivo, String inicio, String fim) {
        Dispositivo dispositivo = exigirDispositivoValido(idDispositivo);
        int idMorador = moradorResidenciaDAO.buscarIdTitular(dispositivo.getResidencia().getIdResidencia());
        return listarPorDispositivo(idMorador, idDispositivo, inicio, fim);
    }



    /**
     * Para a análise e o dashboard: leituras da residência em ordem cronológica, no período
     * (extremos
     * opcionais e inclusivos). Não confere acesso; quem chama já deve ter conferido o vínculo.
     */
    public List<Leitura> leiturasDaResidencia(int idResidencia, LocalDateTime inicio, LocalDateTime fim) {
        return leituraDAO.listarFiltrado(idResidencia, null, null, inicio, fim);
    }

    /** Como {@link #leiturasDaResidencia}, mas de um único dispositivo. */
    public List<Leitura> leiturasDoDispositivo(int idDispositivo, LocalDateTime inicio, LocalDateTime fim) {
        return leituraDAO.listarFiltrado(null, idDispositivo, null, inicio, fim);
    }

    /**
     * Atualiza o perfil de consumo de cada residência que recebeu leituras e roda a análise em
     * seguida
     * (RN01 e RN02). A leitura já foi gravada: se o perfil ou a análise falharem, o cadastro
     * continua
     * valendo, o perfil pode ser recalculado e a análise é refeita pelo agendamento.
     */
    private void atualizarPerfis(Set<Integer> idsResidencia) {
        for (int idResidencia : idsResidencia) {
            try {
                perfilConsumoService.atualizarAposNovasLeituras(idResidencia);
            } catch (RuntimeException e) {
                System.out.println("AVISO: não foi possível atualizar o perfil de consumo da residência "
                        + idResidencia + ": " + e.getMessage());
            }
            try {
                analiseConsumoService.analisarAposNovasLeituras(idResidencia);
            } catch (RuntimeException e) {
                System.out.println("AVISO: não foi possível analisar o consumo da residência "
                        + idResidencia + ": " + e.getMessage());
            }
        }
    }

    /** Monta a leitura a partir do pedido; a sincronização é definida pelo servidor. */
    private Leitura montar(LeituraRequestDTO dto) {
        Leitura leitura = LeituraMapper.dtoToEntity(dto);
        leitura.marcarComoSincronizada(dto.dataLeitura());
        return leitura;
    }

    /**
     * Lê "yyyy-MM-dd" (início ou fim do dia, conforme {@code fimDoDia}) ou "yyyy-MM-ddTHH:mm[:ss]";
     * vazio vira {@code null}.
     *
     * @throws IllegalArgumentException se o texto não está num desses formatos
     */
    private LocalDateTime lerPeriodo(String texto, String campo, boolean fimDoDia) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            String valor = texto.trim();
            if (valor.length() == 10) {
                LocalDate dia = LocalDate.parse(valor);
                return fimDoDia ? dia.atTime(23, 59, 59) : dia.atStartOfDay();
            }
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em '" + campo
                    + "' (use 2026-11-14 ou 2026-11-14T08:00).");
        }
    }

    /** Impede sequência repetida no mesmo dispositivo. */
    private void exigirSequenciaLivre(int idDispositivo, int sequencia) {
        if (leituraDAO.buscarPorDispositivoESequencia(idDispositivo, sequencia) != null) {
            throw new IllegalArgumentException("Já existe uma leitura com esta sequência para o dispositivo.");
        }
    }

    /**
     * O dispositivo da leitura precisa existir; senão é erro de validação (400), e não recurso
     * inexistente (404).
     */
    private Dispositivo exigirDispositivoValido(int idDispositivo) {
        Dispositivo dispositivo = dispositivoDAO.buscarPorId(idDispositivo);
        if (dispositivo == null) {
            throw new IllegalArgumentException("Dispositivo inválido: nenhum dispositivo com o id informado.");
        }
        return dispositivo;
    }

    /** Devolve o vínculo do morador com a residência; senão 403. */
    private MoradorResidencia exigirVinculo(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso às leituras desta residência.");
        }
        return vinculo;
    }

    /** Como {@link #exigirVinculo}, mas o vínculo precisa ser de titular. */
    private void exigirTitular(int idMorador, int idResidencia) {
        if (!exigirVinculo(idMorador, idResidencia).isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o titular da residência pode cadastrar leituras de teste.");
        }
    }

    /** Busca a leitura ou lança "não encontrada". */
    private Leitura buscarExistente(int idLeitura) {
        Leitura leitura = leituraDAO.buscarPorId(idLeitura);
        if (leitura == null) {
            throw new IllegalArgumentException("Leitura não encontrada");
        }
        return leitura;
    }

    /**
     * Confere data, sequência (mínimo 1), dispositivo e que vazão, volume e duração não sejam
     * negativos.
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validar(LeituraRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Os dados da leitura são obrigatórios.");
        }
        if (dto.dataLeitura() == null) {
            System.out.println("ERRO: A data da leitura é obrigatória");
            throw new IllegalArgumentException("A data da leitura é obrigatória.");
        }
        if (dto.sequencia() < 1) {
            System.out.println("ERRO: A sequência deve ser de pelo menos 1");
            throw new IllegalArgumentException("A sequência deve ser de pelo menos 1.");
        }
        if (dto.vazaoLitroMinuto() < 0) {
            throw new IllegalArgumentException("A vazão não pode ser negativa.");
        }
        if (dto.volumeLitros() < 0) {
            throw new IllegalArgumentException("O volume não pode ser negativo.");
        }
        if (dto.duracaoFluxoSeg() < 0) {
            throw new IllegalArgumentException("A duração do fluxo não pode ser negativa.");
        }
        if (dto.idDispositivo() < 1) {
            throw new IllegalArgumentException("O dispositivo é obrigatório.");
        }
    }
}