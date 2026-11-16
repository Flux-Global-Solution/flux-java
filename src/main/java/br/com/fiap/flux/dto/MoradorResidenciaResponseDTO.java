package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoVinculoEnum;

import java.time.LocalDate;

public record MoradorResidenciaResponseDTO(
        int idMoradorResidencia,
        TipoVinculoEnum tipoVinculo,
        LocalDate dataVinculo,
        MoradorResponseDTO morador,
        int idResidencia
) {
}