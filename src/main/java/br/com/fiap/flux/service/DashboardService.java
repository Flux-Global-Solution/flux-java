
package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AlertaDAO;
import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.PerfilConsumoDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.*;
import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.mapper.DashboardMapper;
import br.com.fiap.flux.models.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Indicadores da tela inicial, calculados na hora a partir das leituras e dos alertas da
 * residência.
 * Somente leitura: consultar nunca grava nada. Cada operação exige que o morador tenha vínculo com
 * a
 * residência (qualquer tipo de vínculo serve).
 *
 * <p>Há três formas de chamar: com {@code idMorador} e {@code idResidencia}; só com a residência
 * (age em nome do titular); e as variantes {@code ...PorMorador}, que repetem a consulta para cada
 * residência vinculada ao morador.</p>
 */
@Service
public class DashboardService {

    private final LeituraDAO leituraDAO;
    private final AlertaDAO alertaDAO;
    private final DispositivoDAO dispositivoDAO;
    private final ResidenciaDAO residenciaDAO;
    private final PerfilConsumoDAO perfilDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;

    public DashboardService() {
        this.leituraDAO = new LeituraDAO();
        this.alertaDAO = new AlertaDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.perfilDAO = new PerfilConsumoDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
    }

    /**
     * Indicadores gerais da residência: consumo de hoje e do mês, última leitura, consumo médio
     * diário
     * (do perfil, quando existe; senão calculado das leituras), alertas ativos com o volume
     * desperdiçado
     * e o status do dispositivo ({@code SEM_DISPOSITIVO} se não houver).
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não existe
     */
    public DashboardResponseDTO dashboard(int idMorador, int idResidencia) {

        Residencia residencia = exigirResidencia(idResidencia);
        MoradorResidencia vinculo = exigirVinculo(idMorador, idResidencia);

        List<Leitura> leituras = buscarLeituras(idMorador, idResidencia);
        List<Alerta> alertas = buscarAlertasAtivos(idResidencia);

        Dispositivo dispositivo = dispositivoDAO.buscarPorResidencia(idResidencia);
        PerfilConsumo perfil = perfilDAO.buscarPorResidencia(idResidencia);

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        double consumoHoje = somarVolume(
                leituras, hoje.atStartOfDay(), agora);

        double consumoMes = somarVolume(
                leituras, hoje.withDayOfMonth(1).atStartOfDay(), agora);

        Leitura ultima = buscarUltimaLeitura(leituras);

        double desperdicio = alertas.stream()
                .map(Alerta::getVolumeDesperdicado)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        double media = perfil != null
                ? perfil.getConsumoMedio()
                : calcularMediaDiaria(leituras);

        return new DashboardResponseDTO(
                DashboardMapper.residenciaToDto(residencia, vinculo),
                arredondar(consumoHoje),
                arredondar(consumoMes),
                ultima != null ? ultima.getVazaoLitroMinuto() : 0,
                ultima != null ? ultima.getDataLeitura() : null,
                arredondar(media),
                alertas.size(),
                arredondar(desperdicio),
                dispositivo != null
                        ? dispositivo.getStatusDispositivo().name()
                        : "SEM_DISPOSITIVO"
        );
    }

    /**
     * Indicadores de consumo, comparando o mês até hoje com o período anterior de mesma duração
     * (a variação é 0 quando o período anterior não teve consumo).
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não existe
     */
    public DashboardConsumoResponseDTO consumo(
            int idMorador, int idResidencia) {

        exigirResidencia(idResidencia);
        exigirVinculo(idMorador, idResidencia);

        List<Leitura> leituras = buscarLeituras(idMorador, idResidencia);

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();

        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();

        long diasPeriodo = hoje.getDayOfMonth();

        LocalDateTime inicioAnterior = inicioMes.minusDays(diasPeriodo);
        LocalDateTime fimAnterior = inicioMes.minusNanos(1);

        double consumoHoje = somarVolume(
                leituras, hoje.atStartOfDay(), agora);

        double consumoMes = somarVolume(
                leituras, inicioMes, agora);

        double consumoAnterior = somarVolume(
                leituras, inicioAnterior, fimAnterior);

        Leitura ultima = buscarUltimaLeitura(leituras);

        PerfilConsumo perfil = perfilDAO.buscarPorResidencia(idResidencia);

        double media = perfil != null
                ? perfil.getConsumoMedio()
                : calcularMediaDiaria(leituras);

        double variacao = consumoAnterior > 0
                ? ((consumoMes - consumoAnterior) / consumoAnterior) * 100
                : 0;

        return new DashboardConsumoResponseDTO(
                arredondar(consumoHoje),
                arredondar(consumoMes),
                arredondar(media),
                ultima != null ? ultima.getVazaoLitroMinuto() : 0,
                ultima != null ? ultima.getDataLeitura() : null,
                arredondar(consumoAnterior),
                arredondar(variacao)
        );
    }

    /**
     * Agrupa o consumo por hora, dia, semana (de segunda a domingo) e mês para os gráficos, e
     * indica os
     * três horários do dia de maior consumo.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não existe
     */
    public DashboardHistoricoResponseDTO historico(
            int idMorador, int idResidencia) {

        exigirResidencia(idResidencia);
        exigirVinculo(idMorador, idResidencia);

        List<Leitura> leituras = buscarLeituras(idMorador, idResidencia);

        Map<LocalDateTime, Double> porHora = new TreeMap<>();
        Map<LocalDate, Double> porDia = new TreeMap<>();
        Map<LocalDate, Double> porSemana = new TreeMap<>();
        Map<YearMonth, Double> porMes = new TreeMap<>();
        Map<Integer, Double> horarios = new TreeMap<>();

        for (Leitura leitura : leituras) {

            LocalDateTime dataHora = leitura.getDataLeitura();
            LocalDate data = dataHora.toLocalDate();
            double volume = leitura.getVolumeLitros();

            LocalDateTime hora = dataHora.withMinute(0).withSecond(0)
                    .withNano(0);

            porHora.merge(hora, volume, Double::sum);
            porDia.merge(data, volume, Double::sum);
            porSemana.merge(data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), volume, Double::sum);
            porMes.merge(YearMonth.from(data), volume, Double::sum);
            horarios.merge(dataHora.getHour(), volume, Double::sum);
        }

        DateTimeFormatter formatoData =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        DateTimeFormatter formatoMes =
                DateTimeFormatter.ofPattern("MM/yyyy");

        List<DashboardHistoricoResponseDTO.PontoHistorico> horas =
                porHora.entrySet().stream()
                        .map(e -> new DashboardHistoricoResponseDTO.PontoHistorico(
                                e.getKey().format(
                                        DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                                e.getKey().toLocalDate(),
                                e.getKey(),
                                arredondar(e.getValue())
                        ))
                        .toList();

        List<DashboardHistoricoResponseDTO.PontoHistorico> dias =
                porDia.entrySet().stream()
                        .map(e -> new DashboardHistoricoResponseDTO.PontoHistorico(
                                e.getKey().format(formatoData),
                                e.getKey(),
                                null,
                                arredondar(e.getValue())
                        ))
                        .toList();

        DateTimeFormatter formatoDiaMes = DateTimeFormatter.ofPattern("dd/MM");

        List<DashboardHistoricoResponseDTO.PontoHistorico> semanas =
                porSemana.entrySet().stream()
                        .map(e -> new DashboardHistoricoResponseDTO.PontoHistorico(
                                e.getKey().format(formatoDiaMes) + " a "
                                        + e.getKey().plusDays(6).format(formatoDiaMes),
                                e.getKey(),
                                null,
                                arredondar(e.getValue())
                        ))
                        .toList();

        List<DashboardHistoricoResponseDTO.PontoHistorico> meses =
                porMes.entrySet().stream()
                        .map(e -> new DashboardHistoricoResponseDTO.PontoHistorico(
                                e.getKey().format(formatoMes),
                                e.getKey().atDay(1),
                                null,
                                arredondar(e.getValue())
                        ))
                        .toList();

        List<DashboardHistoricoResponseDTO.HorarioMaiorConsumo> picos =
                horarios.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Double>comparingByValue()
                                .reversed())
                        .limit(3)
                        .map(e -> new DashboardHistoricoResponseDTO.HorarioMaiorConsumo(
                                e.getKey(),
                                arredondar(e.getValue())
                        ))
                        .toList();

        return new DashboardHistoricoResponseDTO(
                horas, dias, semanas, meses, picos);
    }

    /**
     * Alertas da residência que ainda não foram resolvidos.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não existe
     */
    public List<AlertaResponseDTO> alertas(
            int idMorador, int idResidencia) {

        exigirResidencia(idResidencia);
        exigirVinculo(idMorador, idResidencia);

        List<Alerta> alertas = buscarAlertasAtivos(idResidencia);

        return DashboardMapper.alertasToDto(alertas);
    }

    /**
     * Dispositivo instalado na residência, ou {@code null} se ainda não há nenhum.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não existe
     */
    public DispositivoResponseDTO dispositivo(
            int idMorador, int idResidencia) {

        exigirResidencia(idResidencia);
        exigirVinculo(idMorador, idResidencia);

        Dispositivo dispositivo =
                dispositivoDAO.buscarPorResidencia(idResidencia);

        if (dispositivo == null) {
            return null;
        }

        return DashboardMapper.dispositivoToDto(dispositivo);
    }

    /** Variante de {@link #dashboard(int, int)} sem {@code idMorador}: age em nome do titular. */
    public DashboardResponseDTO dashboard(int idResidencia) {
        return dashboard(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /** Variante de {@link #consumo(int, int)} sem {@code idMorador}: age em nome do titular. */
    public DashboardConsumoResponseDTO consumo(int idResidencia) {
        return consumo(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /** Variante de {@link #historico(int, int)} sem {@code idMorador}: age em nome do titular. */
    public DashboardHistoricoResponseDTO historico(int idResidencia) {
        return historico(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /** Variante de {@link #alertas(int, int)} sem {@code idMorador}: age em nome do titular. */
    public List<AlertaResponseDTO> alertas(int idResidencia) {
        return alertas(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /** Variante de {@link #dispositivo(int, int)} sem {@code idMorador}: age em nome do titular. */
    public DispositivoResponseDTO dispositivo(int idResidencia) {
        return dispositivo(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }



    /**
     * O dashboard de cada residência vinculada ao morador.
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<DashboardResponseDTO> dashboardsPorMorador(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado.");
        }
        return residencias.stream()
                .map(v -> dashboard(idMorador, v.residencia().getIdResidencia()))
                .toList();
    }

    /**
     * Os indicadores de consumo de cada residência vinculada ao morador.
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<DashboardConsumoResponseDTO> consumoPorMorador(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado.");
        }
        return residencias.stream()
                .map(v -> consumo(idMorador, v.residencia().getIdResidencia()))
                .toList();
    }

    /**
     * O histórico de consumo de cada residência vinculada ao morador.
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<DashboardHistoricoResponseDTO> historicoPorMorador(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado.");
        }
        return residencias.stream()
                .map(v -> historico(idMorador, v.residencia().getIdResidencia()))
                .toList();
    }

    /**
     * Os alertas não resolvidos de todas as residências vinculadas ao morador, juntos numa só
     * lista.
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<AlertaResponseDTO> alertasPorMorador(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado.");
        }
        return residencias.stream()
                .flatMap(v -> alertas(idMorador, v.residencia().getIdResidencia()).stream())
                .toList();
    }

    /**
     * Os dispositivos das residências vinculadas ao morador (residência sem dispositivo é
     * ignorada).
     *
     * @throws IllegalArgumentException se o morador não tem nenhuma residência
     */
    public List<DispositivoResponseDTO> dispositivosPorMorador(int idMorador) {
        List<ResidenciaDAO.ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado.");
        }
        return residencias.stream()
                .map(v -> dispositivo(idMorador, v.residencia().getIdResidencia()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Leituras da residência consultada, vistas pelo morador (só as residências a que ele tem
     * acesso).
     */
    private List<Leitura> buscarLeituras(
            int idMorador, int idResidencia) {

        return leituraDAO.listarFiltrado(
                idResidencia, null, idMorador, null, null);
    }

    /** Alertas da residência que ainda não estão RESOLVIDOS. */
    private List<Alerta> buscarAlertasAtivos(int idResidencia) {

        return alertaDAO.listarPorResidencia(
                        idResidencia, null, null)
                .stream()
                .filter(a -> a.getStatusAlerta() != StatusAlertaEnum.RESOLVIDO)
                .toList();
    }

    /**
     * Leitura mais recente registrada, ou {@code null} se não há nenhuma. Os dados são simulados:
     * não
     * indica uma leitura em tempo real.
     */
    private Leitura buscarUltimaLeitura(List<Leitura> leituras) {

        return leituras.stream()
                .max(Comparator.comparing(Leitura::getDataLeitura))
                .orElse(null);
    }

    /** Soma os litros das leituras dentro do intervalo (extremos inclusivos). */
    private double somarVolume(
            List<Leitura> leituras,
            LocalDateTime inicio,
            LocalDateTime fim) {

        return leituras.stream()
                .filter(l -> !l.getDataLeitura().isBefore(inicio)
                        && !l.getDataLeitura().isAfter(fim))
                .mapToDouble(Leitura::getVolumeLitros)
                .sum();
    }

    /**
     * Média de litros por dia, considerando só os dias que têm leitura (usada quando não há
     * perfil).
     */
    private double calcularMediaDiaria(List<Leitura> leituras) {

        if (leituras.isEmpty()) {
            return 0;
        }

        Map<LocalDate, Double> dias = new HashMap<>();

        for (Leitura leitura : leituras) {
            dias.merge(
                    leitura.getDataLeitura().toLocalDate(),
                    leitura.getVolumeLitros(),
                    Double::sum
            );
        }

        return dias.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }

    /** Busca a residência ou lança "não encontrada". */
    private Residencia exigirResidencia(int idResidencia) {

        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);

        if (residencia == null) {
            throw new IllegalArgumentException("Residência não encontrada.");
        }

        return residencia;
    }

    /** Devolve o vínculo do morador com a residência; senão 403. */
    private MoradorResidencia exigirVinculo(
            int idMorador, int idResidencia) {

        MoradorResidencia vinculo =
                moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);

        if (vinculo == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Você não tem acesso a esta residência."
            );
        }

        return vinculo;
    }

    /** Arredonda para duas casas decimais. */
    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
