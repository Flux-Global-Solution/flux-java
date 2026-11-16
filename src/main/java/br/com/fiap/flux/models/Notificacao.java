package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.CanalEnum;
import br.com.fiap.flux.enums.StatusEnvioEnum;

import java.time.LocalDate;

/**
 * Representa uma notificação gerada para um {@link Alerta}, enviada a um {@link Morador} ou a um
 * {@link ContatoConfianca}.
 *
 * <p>O destinatário é um morador ou um contato, e o outro fica {@code null}. A notificação é
 * simulada: nada é enviado de fato, apenas o canal ({@link CanalEnum}), o status de envio
 * ({@link StatusEnvioEnum}) e a data de envio são registrados. O texto da mensagem fica no campo
 * {@code notificacao}.</p>
 */
public class Notificacao {
    private int idNotificacao;
    private String notificacao;
    private LocalDate dataEnvio;
    private CanalEnum canal;
    private StatusEnvioEnum statusEnvio;
    private Alerta alerta;
    private Morador morador;
    private ContatoConfianca contato;

    public int getIdNotificacao() {
        return idNotificacao;
    }

    public void setIdNotificacao(int idNotificacao) {
        this.idNotificacao = idNotificacao;
    }

    public String getNotificacao() {
        return notificacao;
    }

    public void setNotificacao(String notificacao) {
        this.notificacao = notificacao;
    }

    public LocalDate getDataEnvio() {
        return dataEnvio;
    }

    public void setDataEnvio(LocalDate dataEnvio) {
        this.dataEnvio = dataEnvio;
    }

    public CanalEnum getCanal() {
        return canal;
    }

    public void setCanal(CanalEnum canal) {
        this.canal = canal;
    }

    public StatusEnvioEnum getStatusEnvio() {
        return statusEnvio;
    }

    public void setStatusEnvio(StatusEnvioEnum statusEnvio) {
        this.statusEnvio = statusEnvio;
    }

    public Alerta getAlerta() {
        return alerta;
    }

    public void setAlerta(Alerta alerta) {
        this.alerta = alerta;
    }

    public Morador getMorador() {
        return morador;
    }

    public void setMorador(Morador morador) {
        this.morador = morador;
    }

    public ContatoConfianca getContato() {
        return contato;
    }

    public void setContato(ContatoConfianca contato) {
        this.contato = contato;
    }
}