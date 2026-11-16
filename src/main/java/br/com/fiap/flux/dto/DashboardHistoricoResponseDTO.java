
package br.com.fiap.flux.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dados históricos de consumo agrupados para os gráficos do dashboard (hora, dia, semana e mês). A semana
 * vai de segunda a domingo: {@code data} é a segunda-feira e o rótulo mostra o intervalo.
 */
public record DashboardHistoricoResponseDTO(
        List<PontoHistorico> consumoPorHora,
        List<PontoHistorico> consumoPorDia,
        List<PontoHistorico> consumoPorSemana,
        List<PontoHistorico> consumoPorMes,
        List<HorarioMaiorConsumo> horariosMaiorConsumo
) {

    /** Representa um agrupamento de consumo em determinado período. */
    public record PontoHistorico(
            String rotulo,
            LocalDate data,
            LocalDateTime dataHora,
            double consumo
    ) {
    }

    /** Representa um horário com maior volume de consumo. */
    public record HorarioMaiorConsumo(
            int hora,
            double consumo
    ) {
    }
}
