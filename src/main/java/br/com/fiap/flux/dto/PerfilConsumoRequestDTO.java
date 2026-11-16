package br.com.fiap.flux.dto;

/**
 * Pedido para cadastrar um perfil de consumo. A data da última atualização
 * não vem do cliente: é definida pelo servidor.
 */
public record PerfilConsumoRequestDTO(
        int consumoMedio,
        int vazaoMedia,
        int desvioPadrao,
        String padraoConsumo,
        int historicoSuficiente,
        int idResidencia
) {
}