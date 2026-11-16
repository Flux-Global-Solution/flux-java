package br.com.fiap.flux.dto;

import java.time.LocalDate;

/**
 * Perfil de consumo de uma residência. Os valores são calculados pelo servidor a partir das
 * leituras: consumo médio em litros por dia, vazão média em litros por minuto e desvio padrão do
 * consumo diário. {@code historicoSuficiente} é 1 quando há dias de leitura suficientes para
 * servir de referência confiável e 0 quando o perfil ainda é pouco confiável.
 */
public record PerfilConsumoResponseDTO(
        int idPerfilConsumo,
        double consumoMedio,
        double vazaoMedia,
        double desvioPadrao,
        String padraoConsumo,
        int historicoSuficiente,
        LocalDate ultimaAtualizacao,
        int idResidencia
) {
}