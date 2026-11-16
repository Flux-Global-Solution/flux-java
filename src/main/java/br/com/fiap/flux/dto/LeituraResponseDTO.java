package br.com.fiap.flux.dto;

import java.time.LocalDateTime;

public record LeituraResponseDTO(
        int idLeitura,
        LocalDateTime dataLeitura,
        int sequencia,
        double vazaoLitroMinuto,
        double volumeLitros,
        int duracaoFluxoSeg,
        int sincronizada,
        LocalDateTime dataSincronizacao,
        int idDispositivo
) {
}