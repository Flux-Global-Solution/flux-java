package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.AutorizacaoContatoResponseDTO;
import br.com.fiap.flux.models.AutorizacaoContato;

/** Conversão de {@link AutorizacaoContato} para o DTO de resposta. */
public class AutorizacaoContatoMapper {

    /**
     * Converte a autorização com o contato e os ids do vínculo e da residência. Não há conversão de
     * pedido: a autorização é montada pelo service, porque vínculo, status e datas são do servidor.
     */
    public static AutorizacaoContatoResponseDTO toDto (AutorizacaoContato autorizacao){
        return new AutorizacaoContatoResponseDTO(
                autorizacao.getIdAutorizacao(),
                autorizacao.getDataAutorizacao(),
                autorizacao.getDataRevogacao(),
                autorizacao.getStatusAutorizacao(),
                autorizacao.getContato() != null ? ContatoConfiancaMapper.toDto(autorizacao.getContato()) : null,
                autorizacao.getMoradorResidencia() != null ? autorizacao.getMoradorResidencia().getIdMoradorResidencia() : 0,
                autorizacao.getMoradorResidencia() != null && autorizacao.getMoradorResidencia().getResidencia() != null
                        ? autorizacao.getMoradorResidencia().getResidencia().getIdResidencia() : 0
        );
    }
}