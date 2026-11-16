package br.com.fiap.flux.dto;

/**
 * Pedido de cadastro de morador. Status da conta e data de cadastro não vêm do cliente:
 * são definidos pelo servidor.
 */
public record MoradorRequestDTO(
        String nome,
        String email,
        String senha,
        String telefone
) {
}