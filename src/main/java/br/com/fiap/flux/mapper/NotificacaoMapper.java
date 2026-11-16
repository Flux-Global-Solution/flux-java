package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.NotificacaoRequestDTO;
import br.com.fiap.flux.dto.NotificacaoResponseDTO;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.ContatoConfianca;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.Notificacao;

/** Conversão entre {@link Notificacao} e seus DTOs. */
public class NotificacaoMapper {
    /**
     * Monta a notificação com o texto e o canal do pedido; alerta, morador e contato são só
     * referências
     * (apenas o id). O status e a data de envio são do servidor.
     */
    public static Notificacao dtoToEntity (NotificacaoRequestDTO dto){
        Notificacao notificacao = new Notificacao();
        notificacao.setNotificacao(dto.notificacao());
        notificacao.setCanal(dto.canal());

        Alerta alerta = new Alerta();
        alerta.setIdAlerta(dto.idAlerta());
        notificacao.setAlerta(alerta);

        if (dto.idMorador() != null) {
            Morador morador = new Morador();
            morador.setIdMorador(dto.idMorador());
            notificacao.setMorador(morador);
        }

        if (dto.idContato() != null) {
            ContatoConfianca contato = new ContatoConfianca();
            contato.setIdContato(dto.idContato());
            notificacao.setContato(contato);
        }

        return notificacao;
    }

    /** Converte a notificação com o alerta, o morador e o contato já completos, quando houver. */
    public static NotificacaoResponseDTO toDto (Notificacao notificacao){
        return new NotificacaoResponseDTO(
                notificacao.getIdNotificacao(),
                notificacao.getNotificacao(),
                notificacao.getDataEnvio(),
                notificacao.getCanal(),
                notificacao.getStatusEnvio(),
                notificacao.getAlerta() != null ? AlertaMapper.toDto(notificacao.getAlerta()) : null,
                notificacao.getMorador() != null ? MoradorMapper.toDto(notificacao.getMorador()) : null,
                notificacao.getContato() != null ? ContatoConfiancaMapper.toDto(notificacao.getContato()) : null
        );
    }
}