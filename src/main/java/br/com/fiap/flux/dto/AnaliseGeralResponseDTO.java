package br.com.fiap.flux.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resumo da análise rodada em todas as residências que têm dispositivo (rota administrativa, de
 * demonstração). Uma residência com falha não interrompe as outras: ela aparece com {@code erro}.
 */
public record AnaliseGeralResponseDTO(
        LocalDateTime executadaEm,
        int residenciasAnalisadas,
        int residenciasComFalha,
        int alertasCriados,
        int alertasAtualizados,
        int alertasJaExistentes,
        int alertasResolvidos,
        List<Residencia> residencias
) {

    /** Resultado da análise de uma residência; {@code erro} é {@code null} quando deu certo. */
    public record Residencia(
            int idResidencia,
            int leiturasAnalisadas,
            int anomalias,
            int alertasCriados,
            int alertasAtualizados,
            int alertasJaExistentes,
            int alertasResolvidos,
            String erro
    ) {
    }
}