package br.com.fiap.flux.dto;

import java.util.List;

/**
 * Pedido para cadastrar de uma vez um conjunto de leituras fictícias (simulação). As leituras vêm
 * prontas do cliente: o servidor não gera valores, o que mantém os cenários estáveis e repetíveis.
 * O conjunto é gravado inteiro ou não é gravado.
 */
public record SimulacaoLeiturasRequestDTO(
        List<LeituraRequestDTO> leituras
) {
}