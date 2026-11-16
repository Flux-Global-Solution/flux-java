package br.com.fiap.flux.dto;

import java.time.LocalTime;

/**
 * Pedido para cadastrar ou atualizar uma residência. Só traz o que serve ao perfil de consumo.
 * A data de cadastro e o titular não vêm no corpo: o servidor preenche a data e vincula como titular
 * o morador identificado na rota de cadastro. Os limites são opcionais (sem valor, valem 20 e 30 minutos);
 * os horários são no formato "HH:mm" (ex.: "23:00").
 */
public record ResidenciaRequestDTO(
        String endereco,
        Integer qtdMoradores,
        String periodoAusencia,
        Integer limitePontoAberto,
        Integer limiteVazamentoMin,
        LocalTime inicioMenorConsumo,
        LocalTime fimMenorConsumo
) {
}