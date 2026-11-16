package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoVinculoEnum;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Residência devolvida ao front-end. {@code tipoVinculo} é o vínculo do usuário autenticado com ela
 * (TITULAR ou MORADOR_COMUM), para a tela saber se pode editar.
 */
public record ResidenciaResponseDTO(
        int idResidencia,
        String endereco,
        int qtdMoradores,
        LocalDate dtCadastro,
        String periodoAusencia,
        int limitePontoAberto,
        int limiteVazamentoMin,
        LocalTime inicioMenorConsumo,
        LocalTime fimMenorConsumo,
        TipoVinculoEnum tipoVinculo
) {
}