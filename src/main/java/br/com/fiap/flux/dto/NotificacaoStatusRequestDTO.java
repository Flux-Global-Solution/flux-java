package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusEnvioEnum;

/**
 * Pedido para atualizar o status de envio (simulado) de uma notificação. Valores: PENDENTE, ENVIADO,
 * ENTREGUE e FALHOU. O servidor valida a transição e define a data de envio; nada é enviado de verdade.
 */
public record NotificacaoStatusRequestDTO(
        StatusEnvioEnum status
) {
}