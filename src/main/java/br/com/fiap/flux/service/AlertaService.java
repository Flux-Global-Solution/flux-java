package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AlertaDAO;
import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.AlertaResponseDTO;
import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;
import br.com.fiap.flux.mapper.AlertaMapper;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Consulta e acompanhamento das ocorrências (alertas) de uma residência. O alerta nasce na análise
 * de
 * consumo ({@link AnaliseConsumoService}); aqui ele só é listado e tem o andamento atualizado,
 * nunca
 * é criado ou apagado.
 *
 * <p>O andamento segue um único caminho: {@code ABERTO -> RECONHECIDO -> EM_REPARO -> RESOLVIDO}.
 * A regra de cada passo fica no model {@link Alerta}; um passo fora de ordem vira 409.</p>
 */
@Service
public class AlertaService {

    private final AlertaDAO alertaDAO;
    private final ResidenciaDAO residenciaDAO;
    private final DispositivoDAO dispositivoDAO;
    private final MoradorDAO moradorDAO;

    public AlertaService() {
        this.alertaDAO = new AlertaDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.moradorDAO = new MoradorDAO();
    }

    /**
     * Lista as ocorrências combinando os filtros informados; todos são opcionais. Não exige login
     * nem
     * vínculo com a residência.
     *
     * @param idResidencia só ocorrências desta residência (precisa existir)
     * @param inicio       início do período, pela data de identificação: "2026-11-01" ou
     *     "2026-11-01T08:00"
     * @param fim          fim do período, no mesmo formato (só a data vale até o fim do dia)
     * @throws IllegalArgumentException se o período é inválido ou invertido, ou a residência não
     *     existe
     */
    public List<AlertaResponseDTO> listar(
            Integer idResidencia,
            TipoAlertaEnum tipo,
            TipoGravidadeEnum gravidade,
            StatusAlertaEnum status,
            String inicio,
            String fim) {

        LocalDateTime de = lerPeriodo(inicio, "inicio", false);
        LocalDateTime ate = lerPeriodo(fim, "fim", true);

        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException(
                    "O início do período não pode ser depois do fim."
            );
        }

        if (idResidencia != null) {
            if (idResidencia <= 0) {
                throw new IllegalArgumentException("Residência inválida.");
            }

            exigirResidencia(idResidencia);
        }

        Map<Integer, Residencia> residencias = new HashMap<>();
        Map<Integer, Dispositivo> dispositivos = new HashMap<>();

        return alertaDAO.listarFiltrado(
                        idResidencia,
                        null,
                        tipo,
                        gravidade,
                        status,
                        de,
                        ate
                )
                .stream()
                .map(alerta -> AlertaMapper.toDto(
                        completar(alerta, residencias, dispositivos)
                ))
                .toList();
    }

    /** Lista as ocorrências de uma residência; é {@link #listar} com a residência obrigatória. */
    public List<AlertaResponseDTO> listarPorResidencia(
            int idResidencia,
            TipoAlertaEnum tipo,
            TipoGravidadeEnum gravidade,
            StatusAlertaEnum status,
            String inicio,
            String fim) {

        return listar(
                idResidencia,
                tipo,
                gravidade,
                status,
                inicio,
                fim
        );
    }

    /**
     * Consulta os detalhes de uma ocorrência, com residência, dispositivo e morador responsável.
     *
     * @throws IllegalArgumentException se o id é inválido ou a ocorrência não existe
     */
    public AlertaResponseDTO buscarPorId(int idAlerta) {

        Alerta alerta = buscarExistente(idAlerta);

        return AlertaMapper.toDto(completar(alerta));
    }

    /**
     * Histórico da residência: só as ocorrências já RESOLVIDAS, da resolvida mais recentemente para
     * a
     * mais antiga.
     */
    public List<AlertaResponseDTO> listarHistoricoPorResidencia(
            int idResidencia) {

        // Mais recentemente resolvidas primeiro.
        return listar(
                idResidencia,
                null,
                null,
                StatusAlertaEnum.RESOLVIDO,
                null,
                null
        )
                .stream()
                .sorted(Comparator.comparing(
                        AlertaResponseDTO::dataResolucao,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    /**
     * Reconhece a ocorrência ({@code ABERTO -> RECONHECIDO}) e registra a data do reconhecimento.
     *
     * @throws ResponseStatusException 409 se o status atual não permite a passagem
     */
    public AlertaResponseDTO reconhecer(int idAlerta) {

        return atualizarStatus(idAlerta, Alerta::reconhecer);
    }

    /**
     * Informa o início do reparo ({@code RECONHECIDO -> EM_REPARO}).
     *
     * @throws ResponseStatusException 409 se o status atual não permite a passagem
     */
    public AlertaResponseDTO iniciarReparo(int idAlerta) {

        return atualizarStatus(idAlerta, Alerta::iniciarReparo);
    }

    /**
     * Resolve a ocorrência ({@code EM_REPARO -> RESOLVIDO}) e registra a data da resolução.
     *
     * @throws ResponseStatusException 409 se o status atual não permite a passagem
     */
    public AlertaResponseDTO resolver(int idAlerta) {

        return atualizarStatus(idAlerta, Alerta::resolver);
    }

    /**
     * Busca a ocorrência já como model, para uso de outros services.
     *
     * @throws IllegalArgumentException se o id é inválido ou a ocorrência não existe
     */
    public Alerta buscarExistente(int idAlerta) {

        if (idAlerta <= 0) {
            throw new IllegalArgumentException(
                    "Identificador da ocorrência inválido."
            );
        }

        Alerta alerta = alertaDAO.buscarPorId(idAlerta);

        if (alerta == null) {
            throw new IllegalArgumentException(
                    "Ocorrência não encontrada."
            );
        }

        return alerta;
    }

    /** Completa residência, dispositivo e morador do alerta, que o DAO devolve só com o id. */
    public Alerta completar(Alerta alerta) {

        return completar(
                alerta,
                new HashMap<>(),
                new HashMap<>()
        );
    }

    /**
     * Versão em lote de {@link #completar(Alerta)}: reaproveita nos mapas as residências e os
     * dispositivos
     * já carregados, para não consultar o mesmo registro a cada alerta da lista.
     */
    private Alerta completar(
            Alerta alerta,
            Map<Integer, Residencia> residencias,
            Map<Integer, Dispositivo> dispositivos) {

        if (alerta.getResidencia() != null) {

            int idResidencia =
                    alerta.getResidencia().getIdResidencia();

            Residencia residencia = residencias.get(idResidencia);

            if (residencia == null) {
                residencia = residenciaDAO.buscarPorId(idResidencia);

                if (residencia != null) {
                    residencias.put(idResidencia, residencia);
                }
            }

            if (residencia != null) {
                alerta.setResidencia(residencia);
            }
        }

        if (alerta.getDispositivo() != null) {

            int idDispositivo =
                    alerta.getDispositivo().getIdDispositivo();

            Dispositivo dispositivo = dispositivos.get(idDispositivo);

            if (dispositivo == null) {
                dispositivo = dispositivoDAO.buscarPorId(idDispositivo);

                if (dispositivo != null) {
                    dispositivos.put(idDispositivo, dispositivo);
                }
            }

            if (dispositivo != null) {
                alerta.setDispositivo(dispositivo);
            }
        }

        if (alerta.getMorador() != null) {

            Morador responsavel = moradorDAO.buscarPorId(
                    alerta.getMorador().getIdMorador()
            );

            if (responsavel != null) {
                alerta.setMorador(responsavel);
            }
        }

        return alerta;
    }

    /**
     * Aplica a passagem de status e grava só o status e as datas de reconhecimento e resolução:
     * tipo,
     * gravidade e data de identificação nunca mudam por aqui. Passagem inválida (o model lança
     * {@link IllegalStateException}) vira 409.
     */
    private AlertaResponseDTO atualizarStatus(
            int idAlerta,
            Consumer<Alerta> acao) {

        Alerta alerta = buscarExistente(idAlerta);

        try {
            acao.accept(alerta);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }

        // Grava só status e datas de reconhecimento/resolução: tipo, gravidade e
        // data de identificação nunca são alterados por aqui.
        alertaDAO.atualizarStatus(alerta);

        return AlertaMapper.toDto(
                completar(buscarExistente(idAlerta))
        );
    }

    /**
     * Lê "yyyy-MM-dd" (início ou fim do dia, conforme {@code fimDoDia}) ou "yyyy-MM-ddTHH:mm[:ss]";
     * vazio vira {@code null}.
     *
     * @throws IllegalArgumentException se o texto não está num desses formatos
     */
    private LocalDateTime lerPeriodo(
            String texto,
            String campo,
            boolean fimDoDia) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        try {
            String valor = texto.trim();

            if (valor.length() == 10) {
                LocalDate dia = LocalDate.parse(valor);

                return fimDoDia
                        ? dia.atTime(23, 59, 59)
                        : dia.atStartOfDay();
            }

            return LocalDateTime.parse(valor);

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Data inválida em '" + campo
                            + "'. Use yyyy-MM-dd ou yyyy-MM-ddTHH:mm."
            );
        }
    }

    /** Confirma que a residência existe. */
    private void exigirResidencia(int idResidencia) {

        if (residenciaDAO.buscarPorId(idResidencia) == null) {
            throw new IllegalArgumentException(
                    "Residência não encontrada."
            );
        }
    }
}