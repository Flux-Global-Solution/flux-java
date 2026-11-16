package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;

import java.time.LocalDate;

public record DispositivoResponseDTO(
        int idDispositivo,
        String serie,
        String macAddress,
        String modelo,
        LocalDate dataInstalacao,
        StatusDispositivoEnum statusDispositivo,
        boolean sensorOk,
        StatusEnergiaEnum statusEnergia,
        LocalDate ultimaSincronizacao,
        int idResidencia
) {
}