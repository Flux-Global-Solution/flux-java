package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.AlertaDAO;
import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.LeituraDAO;
import br.com.fiap.flux.dao.PerfilConsumoDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dao.ResidenciaDAO.ResidenciaComVinculo;
import br.com.fiap.flux.dto.DashboardHistoricoResponseDTO;
import br.com.fiap.flux.dto.DashboardHistoricoResponseDTO.HorarioMaiorConsumo;
import br.com.fiap.flux.dto.DashboardHistoricoResponseDTO.PontoHistorico;
import br.com.fiap.flux.dto.DashboardResponseDTO;
import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.mapper.ResidenciaMapper;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;
import br.com.fiap.flux.models.PerfilConsumo;
import br.com.fiap.flux.util.ConsumoUtil;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Indicadores da tela inicial, calculados na hora a partir das leituras e dos alertas das
 * residências do morador. Somente leitura: consultar nunca grava nada.
 */
@Service
public class DashboardService {
    private final LeituraDAO leituraDAO;
    private final AlertaDAO alertaDAO;
    private final DispositivoDAO dispositivoDAO;
    private final ResidenciaDAO residenciaDAO;
    private final PerfilConsumoDAO perfilDAO;

    public DashboardService() {
        this.leituraDAO = new LeituraDAO();
        this.alertaDAO = new AlertaDAO();
        this.dispositivoDAO = new DispositivoDAO();
        this.residenciaDAO = new ResidenciaDAO();
        this.perfilDAO = new PerfilConsumoDAO();
    }

    // ---------- Dashboard ----------

    /**
     * Indicadores de cada residência do morador: consumo de hoje e do mês, última leitura,
     * consumo médio diário, alertas ativos com o volume desperdiçado e o status do dispositivo
     * ({@code SEM_DISPOSITIVO} se não houver).
     */
    public List<DashboardResponseDTO> dashboard(int idMorador) {
        List<DashboardResponseDTO> dashboards = new ArrayList<>();
        for (ResidenciaComVinculo item : listarResidencias(idMorador)) {
            dashboards.add(montarDashboard(item));
        }
        return dashboards;
    }

    private DashboardResponseDTO montarDashboard(ResidenciaComVinculo item) {
        int idResidencia = item.residencia().getIdResidencia();
        List<Leitura> leituras = leituraDAO.listarFiltrado(idResidencia, null, null, null, null);
        List<Alerta> alertas = buscarAlertasAtivos(idResidencia);
        Leitura ultima = buscarUltimaLeitura(leituras);

        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();
        double consumoHoje = somarVolume(leituras, hoje.atStartOfDay(), agora);
        double consumoMes = somarVolume(leituras, hoje.withDayOfMonth(1).atStartOfDay(), agora);

        return new DashboardResponseDTO(
                ResidenciaMapper.toDto(item.residencia(), item.tipoVinculo()),
                ConsumoUtil.arredondar(consumoHoje),
                ConsumoUtil.arredondar(consumoMes),
                ultima != null ? ultima.getVazaoLitroMinuto() : 0,
                ultima != null ? ultima.getDataLeitura() : null,
                ConsumoUtil.arredondar(consumoMedioDiario(idResidencia, leituras)),
                alertas.size(),
                ConsumoUtil.arredondar(somarDesperdicio(alertas)),
                statusDispositivo(idResidencia));
    }

    // ---------- Histórico ----------

    /**
     * Consumo de cada residência do morador agrupado por hora, dia, semana (de segunda a
     * domingo) e mês, para os gráficos, e os três horários do dia de maior consumo.
     */
    public List<DashboardHistoricoResponseDTO> historico(int idMorador) {
        List<DashboardHistoricoResponseDTO> historicos = new ArrayList<>();
        for (ResidenciaComVinculo item : listarResidencias(idMorador)) {
            historicos.add(montarHistorico(item.residencia().getIdResidencia()));
        }
        return historicos;
    }

    private DashboardHistoricoResponseDTO montarHistorico(int idResidencia) {
        List<Leitura> leituras = leituraDAO.listarFiltrado(idResidencia, null, null, null, null);

        Map<LocalDateTime, Double> porHora = new TreeMap<>();
        Map<LocalDate, Double> porDia = new TreeMap<>();
        Map<LocalDate, Double> porSemana = new TreeMap<>();
        Map<YearMonth, Double> porMes = new TreeMap<>();
        Map<Integer, Double> porHorario = new HashMap<>();

        for (Leitura leitura : leituras) {
            LocalDateTime dataHora = leitura.getDataLeitura();
            LocalDate data = dataHora.toLocalDate();
            double volume = leitura.getVolumeLitros();

            porHora.merge(dataHora.withMinute(0).withSecond(0).withNano(0), volume, Double::sum);
            porDia.merge(data, volume, Double::sum);
            porSemana.merge(data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), volume, Double::sum);
            porMes.merge(YearMonth.from(data), volume, Double::sum);
            porHorario.merge(dataHora.getHour(), volume, Double::sum);
        }

        return new DashboardHistoricoResponseDTO(
                pontosPorHora(porHora),
                pontosPorDia(porDia),
                pontosPorSemana(porSemana),
                pontosPorMes(porMes),
                horariosMaiorConsumo(porHorario));
    }

    private List<PontoHistorico> pontosPorHora(Map<LocalDateTime, Double> porHora) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        List<PontoHistorico> pontos = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Double> e : porHora.entrySet()) {
            pontos.add(new PontoHistorico(e.getKey().format(formato), e.getKey().toLocalDate(),
                    e.getKey(), ConsumoUtil.arredondar(e.getValue())));
        }
        return pontos;
    }

    private List<PontoHistorico> pontosPorDia(Map<LocalDate, Double> porDia) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<PontoHistorico> pontos = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : porDia.entrySet()) {
            pontos.add(new PontoHistorico(e.getKey().format(formato), e.getKey(),
                    null, ConsumoUtil.arredondar(e.getValue())));
        }
        return pontos;
    }

    private List<PontoHistorico> pontosPorSemana(Map<LocalDate, Double> porSemana) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM");
        List<PontoHistorico> pontos = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : porSemana.entrySet()) {
            String rotulo = e.getKey().format(formato) + " a " + e.getKey().plusDays(6).format(formato);
            pontos.add(new PontoHistorico(rotulo, e.getKey(), null, ConsumoUtil.arredondar(e.getValue())));
        }
        return pontos;
    }

    private List<PontoHistorico> pontosPorMes(Map<YearMonth, Double> porMes) {
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("MM/yyyy");
        List<PontoHistorico> pontos = new ArrayList<>();
        for (Map.Entry<YearMonth, Double> e : porMes.entrySet()) {
            pontos.add(new PontoHistorico(e.getKey().format(formato), e.getKey().atDay(1),
                    null, ConsumoUtil.arredondar(e.getValue())));
        }
        return pontos;
    }

    private List<HorarioMaiorConsumo> horariosMaiorConsumo(Map<Integer, Double> porHorario) {
        List<Map.Entry<Integer, Double>> ordenados = new ArrayList<>(porHorario.entrySet());
        ordenados.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<HorarioMaiorConsumo> picos = new ArrayList<>();
        for (int i = 0; i < ordenados.size() && i < 3; i++) {
            picos.add(new HorarioMaiorConsumo(ordenados.get(i).getKey(),
                    ConsumoUtil.arredondar(ordenados.get(i).getValue())));
        }
        return picos;
    }

    // ---------- auxiliares ----------

    private List<ResidenciaComVinculo> listarResidencias(int idMorador) {
        List<ResidenciaComVinculo> residencias = residenciaDAO.listarPorMorador(idMorador);
        if (residencias.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma residência encontrada para o morador informado");
        }
        return residencias;
    }

    private List<Alerta> buscarAlertasAtivos(int idResidencia) {
        List<Alerta> ativos = new ArrayList<>();
        for (Alerta a : alertaDAO.listarPorResidencia(idResidencia, null, null)) {
            if (a.getStatusAlerta() != StatusAlertaEnum.RESOLVIDO) {
                ativos.add(a);
            }
        }
        return ativos;
    }

    private Leitura buscarUltimaLeitura(List<Leitura> leituras) {
        Leitura ultima = null;
        for (Leitura l : leituras) {
            if (ultima == null || l.getDataLeitura().isAfter(ultima.getDataLeitura())) {
                ultima = l;
            }
        }
        return ultima;
    }

    private double somarVolume(List<Leitura> leituras, LocalDateTime inicio, LocalDateTime fim) {
        double total = 0;
        for (Leitura l : leituras) {
            if (!l.getDataLeitura().isBefore(inicio) && !l.getDataLeitura().isAfter(fim)) {
                total += l.getVolumeLitros();
            }
        }
        return total;
    }

    private double somarDesperdicio(List<Alerta> alertas) {
        double total = 0;
        for (Alerta a : alertas) {
            if (a.getVolumeDesperdicado() != null) {
                total += a.getVolumeDesperdicado();
            }
        }
        return total;
    }

    // usa o consumo médio do perfil; sem perfil, calcula pelas leituras
    private double consumoMedioDiario(int idResidencia, List<Leitura> leituras) {
        PerfilConsumo perfil = perfilDAO.buscarPorResidencia(idResidencia);
        if (perfil != null) {
            return perfil.getConsumoMedio();
        }
        return calcularMediaDiaria(leituras);
    }

    // média de litros por dia, só dos dias que têm leitura
    private double calcularMediaDiaria(List<Leitura> leituras) {
        Map<LocalDate, Double> dias = new HashMap<>();
        for (Leitura l : leituras) {
            dias.merge(l.getDataLeitura().toLocalDate(), l.getVolumeLitros(), Double::sum);
        }
        if (dias.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (double volume : dias.values()) {
            total += volume;
        }
        return total / dias.size();
    }

    private String statusDispositivo(int idResidencia) {
        Dispositivo dispositivo = dispositivoDAO.buscarPorResidencia(idResidencia);
        return dispositivo != null ? dispositivo.getStatusDispositivo().name() : "SEM_DISPOSITIVO";
    }
}