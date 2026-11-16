package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoVinculoEnum;

import java.time.LocalDate;

public record MoradorResidenciaRequestDTO(
        TipoVinculoEnum tipoVinculo,
        LocalDate dataVinculo,
        int idMorador,
        int idResidencia
) {
}