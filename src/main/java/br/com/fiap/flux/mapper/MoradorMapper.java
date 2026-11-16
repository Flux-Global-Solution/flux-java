package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.MoradorRequestDTO;
import br.com.fiap.flux.dto.MoradorResponseDTO;
import br.com.fiap.flux.models.Morador;

/** Conversão entre {@link Morador} e seus DTOs. */
public class MoradorMapper {
    /**
     * Monta o morador com os dados do pedido. A senha é copiada como veio, em texto puro: quem a
     * transforma em hash é o service. O status da conta também é definido por ele.
     */
    public static Morador dtoToEntity (MoradorRequestDTO dto){
        Morador morador = new Morador();
        morador.setNome(dto.nome());
        morador.setEmail(dto.email());
        morador.setHashSenha(dto.senha());
        morador.setTelefone(dto.telefone());
        return morador;
    }

    /** Nunca inclui a senha, nem o hash, na resposta. */
    public static MoradorResponseDTO toDto (Morador morador){
        return new MoradorResponseDTO(
                morador.getIdMorador(),
                morador.getNome(),
                morador.getEmail(),
                morador.getTelefone(),
                morador.getStatusConta(),
                morador.getDataCadastro()
        );
    }
}