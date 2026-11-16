package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado da análise simulada de uma residência. A janela analisada termina na última leitura da
 * residência (os dados são simulados, então ela acompanha as leituras e não o relógio). Cada
 * anomalia traz o alerta correspondente quando ele já existe; {@code situacao} diz o que aconteceu
 * com ele: {@code CRIADO}, {@code ATUALIZADO} (a mesma ocorrência piorou) ou {@code JA_EXISTENTE}
 * na execução; na consulta do resultado (que não grava nada), {@code NOVO} indica uma anomalia
 * que ainda não virou alerta. {@code alertasResolvidos} conta os alertas fechados automaticamente nesta
 * execução (fluxo normal por 48 h seguidas, RN09).
 */
public record AnaliseResultadoResponseDTO(
        int idResidencia,
        LocalDateTime analisadoEm,
        LocalDateTime inicioJanela,
        LocalDateTime fimJanela,
        int leiturasAnalisadas,
        boolean perfilConfiavel,
        int alertasCriados,
        int alertasAtualizados,
        int alertasJaExistentes,
        int alertasResolvidos,
        List<Anomalia> anomalias,
        List<String> observacoes
) {

    /** Uma ocorrência anômala encontrada. {@code volumeDesperdicado} é {@code null} quando não se aplica. */
    public record Anomalia(
            TipoAlertaEnum tipoAlerta,
            TipoGravidadeEnum tipoGravidade,
            LocalDateTime dataIdentificacao,
            LocalDateTime dataFim,
            Double volumeDesperdicado,
            int idDispositivo,
            String motivo,
            String situacao,
            Integer idAlerta
    ) {
    }
}