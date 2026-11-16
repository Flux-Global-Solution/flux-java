package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.CanalEnum;
import br.com.fiap.flux.enums.StatusEnvioEnum;

import java.time.LocalDate;

public record NotificacaoResponseDTO(
        int idNotificacao,
        String notificacao,
        LocalDate dataEnvio,
        CanalEnum canal,
        StatusEnvioEnum statusEnvio,
        AlertaResponseDTO alerta,
        MoradorResponseDTO morador,
        ContatoConfiancaResponseDTO contato
) {
}