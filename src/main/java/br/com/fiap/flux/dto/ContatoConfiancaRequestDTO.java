package br.com.fiap.flux.dto;

import br.com.fiap.flux.enums.TipoContatoEnum;

public record ContatoConfiancaRequestDTO(
        String nome,
        String telefone,
        String email,
        TipoContatoEnum tipoContato
) {
}