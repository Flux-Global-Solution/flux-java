package br.com.fiap.flux.dto;

import java.time.LocalDateTime;

/**
 * Pedido para registrar uma leitura do dispositivo. O controle de sincronização
 * (sincronizada e dataSincronizacao) não vem do cliente: é definido pelo servidor.
 */
public record LeituraRequestDTO(
        LocalDateTime dataLeitura,
        int sequencia,
        double vazaoLitroMinuto,
        double volumeLitros,
        int duracaoFluxoSeg,
        int idDispositivo
) {
}