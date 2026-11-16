package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.PerfilConsumoResponseDTO;
import br.com.fiap.flux.models.PerfilConsumo;

/**
 * Conversão de {@link PerfilConsumo} para o DTO de resposta. Não há conversão de pedido: o perfil é
 * sempre calculado pelo servidor.
 */
public class PerfilConsumoMapper {
    public static PerfilConsumoResponseDTO toDto (PerfilConsumo perfil){
        return new PerfilConsumoResponseDTO(
                perfil.getIdPerfilConsumo(),
                perfil.getConsumoMedio(),
                perfil.getVazaoMedia(),
                perfil.getDesvioPadrao(),
                perfil.getPadraoConsumo(),
                perfil.getHistoricoSuficiente(),
                perfil.getUltimaAtualizacao(),
                perfil.getResidencia() != null ? perfil.getResidencia().getIdResidencia() : 0
        );
    }
}