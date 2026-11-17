package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.LeituraRequestDTO;
import br.com.fiap.flux.dto.LeituraResponseDTO;
import br.com.fiap.flux.dto.SimulacaoLeiturasRequestDTO;
import br.com.fiap.flux.mapper.LeituraMapper;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;
import br.com.fiap.flux.util.ConsumoUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cadastro e consulta das leituras de consumo, todas simuladas: vêm do banco (script) ou dos
 * endpoints de teste, nunca de hardware, e não há sincronização real. Toda consulta sai em ordem
 * cronológica. Cadastrar leituras de teste exige um morador existente. Leituras já gravadas não são
 * alteradas nem apagadas.
 *
 * <p>Quando entram leituras novas, o perfil de consumo da residência é atualizado e a análise de
 * consumo roda em seguida (RN01 e RN02), sem que ninguém precise chamá-la.</p>
 */
@Service
public class LeituraService {

    private static final int LIMITE_CONJUNTO = 500;

    private final LeituraDAO leituraDAO;
    private final DispositivoDAO dispositivoDAO;
    private final ResidenciaDAO residenciaDAO;
    private final MoradorDAO moradorDAO;
    private final PerfilConsumoService perfilConsumoService;
    private final AnaliseConsumoService analiseConsumoService;

    public LeituraService() {
        this.leituraDAO = new LeituraDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.moradorDAO = new MoradorDAO();
        this.perfilConsumoService = new PerfilConsumoService();
        this.analiseConsumoService = new AnaliseConsumoService();
    }

    /**
     * Cadastra uma leitura de teste. O dispositivo precisa existir e a sequência não pode se repetir
     * nele. A sincronização é definida aqui, nunca pelo cliente.
     */
    public LeituraResponseDTO cadastrar(int idMorador, LeituraRequestDTO dto) {
        validar(dto);
        verificarMorador(idMorador);
        Dispositivo dispositivo = exigirDispositivoValido(dto.idDispositivo());
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
     * Cadastra de uma vez um conjunto de leituras fictícias (até 500), em uma transação: se qualquer
     * leitura for inválida ou repetir sequência, nada é gravado.
     */
    public List<LeituraResponseDTO> cadastrarConjunto(int idMorador, SimulacaoLeiturasRequestDTO dto) {
        if (dto == null || dto.leituras() == null || dto.leituras().isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma leitura.");
        }
        if (dto.leituras().size() > LIMITE_CONJUNTO) {
            throw new IllegalArgumentException("O conjunto deve ter no máximo " + LIMITE_CONJUNTO + " leituras.");
        }

        verificarMorador(idMorador);
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
                    dispositivos.put(item.idDispositivo(), exigirDispositivoValido(item.idDispositivo()));
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

    /** Consulta uma leitura. */
    public LeituraResponseDTO buscarPorId(int idLeitura) {
        return LeituraMapper.toDto(buscarExistente(idLeitura));
    }

    /** Lista as leituras de todas as residências do morador, em ordem cronológica. */
    public List<LeituraResponseDTO> listar(int idMorador, String inicio, String fim) {
        return filtrar(null, null, idMorador, inicio, fim);
    }

    /** Lista as leituras de uma residência, em ordem cronológica. */
    public List<LeituraResponseDTO> listarPorResidencia(int idResidencia, String inicio, String fim) {
        if (residenciaDAO.buscarPorId(idResidencia) == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        return filtrar(idResidencia, null, null, inicio, fim);
    }

    /** Lista as leituras de um dispositivo, em ordem cronológica. */
    public List<LeituraResponseDTO> listarPorDispositivo(int idDispositivo, String inicio, String fim) {
        exigirDispositivoValido(idDispositivo);
        return filtrar(null, idDispositivo, null, inicio, fim);
    }

    /** Aplica o período (opcional) e os filtros, e devolve as leituras já convertidas. */
    private List<LeituraResponseDTO> filtrar(Integer idResidencia, Integer idDispositivo, Integer idMorador,
                                             String inicio, String fim) {
        LocalDateTime de = ConsumoUtil.lerPeriodo(inicio, "inicio", false);
        LocalDateTime ate = ConsumoUtil.lerPeriodo(fim, "fim", true);
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("O início do período não pode ser depois do fim.");
        }
        return leituraDAO.listarFiltrado(idResidencia, idDispositivo, idMorador, de, ate)
                .stream()
                .map(LeituraMapper::toDto)
                .toList();
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

    private void verificarMorador(int idMorador) {
        if (moradorDAO.buscarPorId(idMorador) == null) {
            throw new IllegalArgumentException("Morador não encontrado");
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