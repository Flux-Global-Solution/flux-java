package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.StatusAutorizacaoEnum;

import java.time.LocalDateTime;

/**
 * Representa a autorização para que um {@link ContatoConfianca} receba avisos de uma residência.
 *
 * <p>A autorização fica ligada a um vínculo ({@link MoradorResidencia}) e passa pelos status
 * {@code PENDENTE}, {@code ATIVA} e {@code REVOGADA}. Só uma autorização ativa permite que o
 * contato seja notificado. Revogar não apaga o registro: ele permanece como histórico.</p>
 */
public class AutorizacaoContato {
    private int idAutorizacao;
    private LocalDateTime dataAutorizacao;
    private LocalDateTime dataRevogacao;
    private StatusAutorizacaoEnum statusAutorizacao;
    private ContatoConfianca contato;
    private MoradorResidencia moradorResidencia;

    public int getIdAutorizacao() {
        return idAutorizacao;
    }

    public void setIdAutorizacao(int idAutorizacao) {
        this.idAutorizacao = idAutorizacao;
    }

    public LocalDateTime getDataAutorizacao() {
        return dataAutorizacao;
    }

    public void setDataAutorizacao(LocalDateTime dataAutorizacao) {
        this.dataAutorizacao = dataAutorizacao;
    }

    public LocalDateTime getDataRevogacao() {
        return dataRevogacao;
    }

    public void setDataRevogacao(LocalDateTime dataRevogacao) {
        this.dataRevogacao = dataRevogacao;
    }

    public StatusAutorizacaoEnum getStatusAutorizacao() {
        return statusAutorizacao;
    }

    public void setStatusAutorizacao(StatusAutorizacaoEnum statusAutorizacao) {
        this.statusAutorizacao = statusAutorizacao;
    }

    public ContatoConfianca getContato() {
        return contato;
    }

    public void setContato(ContatoConfianca contato) {
        this.contato = contato;
    }

    public MoradorResidencia getMoradorResidencia() {
        return moradorResidencia;
    }

    public void setMoradorResidencia(MoradorResidencia moradorResidencia) {
        this.moradorResidencia = moradorResidencia;
    }

    /**
     * Ativa a autorização, registrando a data de autorização e limpando a data de revogação, caso o
     * contato tenha sido revogado antes.
     *
     * @throws IllegalStateException se a autorização já estiver ativa
     */
    public void ativar() {
        if (estaAtiva()) {
            throw new IllegalStateException("A autorização já está ativa.");
        }
        statusAutorizacao = StatusAutorizacaoEnum.ATIVA;
        dataAutorizacao = LocalDateTime.now();
        dataRevogacao = null;
    }

    /**
     * Revoga a autorização, registrando a data de revogação. O registro continua no histórico.
     *
     * @throws IllegalStateException se a autorização já tiver sido revogada
     */
    public void revogar() {
        if (statusAutorizacao == StatusAutorizacaoEnum.REVOGADA) {
            throw new IllegalStateException("A autorização já foi revogada.");
        }
        statusAutorizacao = StatusAutorizacaoEnum.REVOGADA;
        dataRevogacao = LocalDateTime.now();
    }

    /**
     * Indica se a autorização está ativa.
     *
     * @return {@code true} se o status for {@code ATIVA}
     */
    public boolean estaAtiva() {
        return statusAutorizacao == StatusAutorizacaoEnum.ATIVA;
    }
}