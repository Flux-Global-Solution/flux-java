package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;

import java.time.LocalDateTime;

/**
 * Item do histórico de análises: um alerta gerado para a residência, com o andamento dele
 * (aberto, reconhecido, em reparo ou resolvido).
 */
public record AnaliseHistoricoResponseDTO(
        int idAlerta,
        TipoAlertaEnum tipoAlerta,
        TipoGravidadeEnum tipoGravidade,
        StatusAlertaEnum statusAlerta,
        LocalDateTime dataIdentificacao,
        LocalDateTime dataReconhecimento,
        LocalDateTime dataResolucao,
        Double volumeDesperdicado,
        int idResidencia,
        int idDispositivo
) {
}