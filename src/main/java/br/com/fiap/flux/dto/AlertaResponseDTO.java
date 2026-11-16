package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;

import java.time.LocalDateTime;

public record AlertaResponseDTO(
        int idAlerta,
        TipoAlertaEnum tipoAlerta,
        TipoGravidadeEnum tipoGravidade,
        StatusAlertaEnum statusAlerta,
        LocalDateTime dataIdentificacao,
        LocalDateTime dataReconhecimento,
        LocalDateTime dataResolucao,
        Double volumeDesperdicado,
        int idResidencia,
        int idDispositivo,
        MoradorResponseDTO morador
) {
}