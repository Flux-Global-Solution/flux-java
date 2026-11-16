
package br.com.fiap.flux.dto;

import java.time.LocalDateTime;

/** Dados gerais apresentados no dashboard da residência. */
public record DashboardResponseDTO(
        ResidenciaResponseDTO residencia,
        double consumoHoje,
        double consumoMes,
        double vazaoUltimaLeitura,
        LocalDateTime ultimaLeitura,
        double consumoMedioDiario,
        int alertasAtivos,
        double volumeDesperdicado,
        String statusDispositivo
) {
}
