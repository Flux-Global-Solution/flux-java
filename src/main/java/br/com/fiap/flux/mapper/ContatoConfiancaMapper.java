package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.ContatoConfiancaRequestDTO;
import br.com.fiap.flux.dto.ContatoConfiancaResponseDTO;
import br.com.fiap.flux.models.ContatoConfianca;

/** Conversão entre {@link ContatoConfianca} e seus DTOs. */
public class ContatoConfiancaMapper {
    public static ContatoConfianca dtoToEntity (ContatoConfiancaRequestDTO dto){
        ContatoConfianca contato = new ContatoConfianca();
        contato.setNome(dto.nome());
        contato.setTelefone(dto.telefone());
        contato.setEmail(dto.email());
        contato.setTipoContato(dto.tipoContato());
        return contato;
    }

    public static ContatoConfiancaResponseDTO toDto (ContatoConfianca contato){
        return new ContatoConfiancaResponseDTO(
                contato.getIdContato(),
                contato.getNome(),
                contato.getTelefone(),
                contato.getEmail(),
                contato.getTipoContato()
        );
    }
}