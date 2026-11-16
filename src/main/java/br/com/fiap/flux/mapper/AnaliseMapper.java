package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.AnaliseHistoricoResponseDTO;
import br.com.fiap.flux.models.Alerta;

/** Conversão de alertas para o formato do histórico de análises. */
public class AnaliseMapper {

    /**
     * Alerta gerado pela análise, no formato enxuto do histórico (só os ids de residência e
     * dispositivo).
     */
    public static AnaliseHistoricoResponseDTO toHistoricoDto(Alerta alerta) {
        return new AnaliseHistoricoResponseDTO(
                alerta.getIdAlerta(),
                alerta.getTipoAlerta(),
                alerta.getTipoGravidade(),
                alerta.getStatusAlerta(),
                alerta.getDataIdentificacao(),
                alerta.getDataReconhecimento(),
                alerta.getDataResolucao(),
                alerta.getVolumeDesperdicado(),
                alerta.getResidencia().getIdResidencia(),
                alerta.getDispositivo().getIdDispositivo()
        );
    }
}