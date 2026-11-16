package br.com.fiap.flux.dto;

import java.time.LocalDate;

/**
 * Pedido para cadastrar um dispositivo. Os status (dispositivo, sensor e energia) e a última
 * sincronização não vêm do cliente: são definidos pelo servidor.
 */
public record DispositivoRequestDTO(
        String serie,
        String macAddress,
        String modelo,
        LocalDate dataInstalacao,
        int idResidencia
) {
}