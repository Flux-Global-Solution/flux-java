package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.CanalEnum;

/**
 * Pedido para registrar uma notificação. Data de envio e status do envio não vêm do cliente:
 * são definidos pelo servidor. O destinatário é um morador ou um contato de confiança
 * (o outro id pode ser nulo).
 */
public record NotificacaoRequestDTO(
        String notificacao,
        CanalEnum canal,
        int idAlerta,
        Integer idMorador,
        Integer idContato
) {
}