package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;

import java.time.LocalDateTime;

/**
 * Pedido para registrar um alerta. Status, data de reconhecimento e data de resolução
 * não vêm do cliente: são definidos pelo servidor.
 */
public record AlertaRequestDTO(
        TipoAlertaEnum tipoAlerta,
        TipoGravidadeEnum tipoGravidade,
        LocalDateTime dataIdentificacao,
        Double volumeDesperdicado,
        int idResidencia,
        int idDispositivo,
        Integer idMorador
) {
}