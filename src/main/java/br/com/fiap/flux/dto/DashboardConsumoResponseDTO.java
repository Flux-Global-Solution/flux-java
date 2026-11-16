
package br.com.fiap.flux.dto;

import java.time.LocalDateTime;

/** Indicadores de consumo da residência. */
public record DashboardConsumoResponseDTO(
        double consumoHoje,
        double consumoMes,
        double consumoMedioDiario,
        double vazaoUltimaLeitura,
        LocalDateTime ultimaLeitura,
        double consumoPeriodoAnterior,
        double variacaoPercentual
) {
}
