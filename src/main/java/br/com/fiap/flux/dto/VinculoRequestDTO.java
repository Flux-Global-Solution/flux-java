package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoVinculoEnum;

/**
 * Pedido para vincular a uma residência um morador que já tem conta no FLUX, localizado pelo e-mail.
 * Sem {@code tipoVinculo}, vale MORADOR_COMUM. Não existe titular novo por aqui: a residência
 * tem um único titular, definido no cadastro.
 */
public record VinculoRequestDTO(
        String emailMorador,
        TipoVinculoEnum tipoVinculo
) {
}