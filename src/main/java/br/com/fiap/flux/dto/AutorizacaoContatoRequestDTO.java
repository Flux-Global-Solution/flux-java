package br.com.fiap.flux.dto;

/**
 * Pedido para solicitar a autorização de um contato de confiança numa residência. O vínculo
 * (Morador_Residencia) é o do titular autenticado com a residência informada, definido pelo servidor.
 * Status, datas de autorização e de revogação também não vêm do cliente: nasce PENDENTE.
 */
public record AutorizacaoContatoRequestDTO(
        Integer idContato,
        Integer idResidencia
) {
}