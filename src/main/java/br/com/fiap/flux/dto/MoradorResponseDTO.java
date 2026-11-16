package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusContaEnum;

import java.time.LocalDate;

public record MoradorResponseDTO(
        int idMorador,
        String nome,
        String email,
        String telefone,
        StatusContaEnum statusConta,
        LocalDate dataCadastro
) {
}