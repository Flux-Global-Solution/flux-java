package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.StatusAutorizacaoEnum;

import java.time.LocalDateTime;

public record AutorizacaoContatoResponseDTO(
        int idAutorizacao,
        LocalDateTime dataAutorizacao,
        LocalDateTime dataRevogacao,
        StatusAutorizacaoEnum statusAutorizacao,
        ContatoConfiancaResponseDTO contato,
        int idMoradorResidencia,
        int idResidencia
) {
}