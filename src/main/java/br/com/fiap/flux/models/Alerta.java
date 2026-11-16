package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;

import java.time.LocalDateTime;

/**
 * Representa uma ocorrência de consumo anômalo (alerta) identificada em uma {@link Residencia}, a
 * partir das leituras de um {@link Dispositivo}.
 *
 * <p>Guarda o tipo ({@link TipoAlertaEnum}), a gravidade ({@link TipoGravidadeEnum}), o status e as
 * datas de identificação, reconhecimento e resolução. O volume desperdiçado e o {@link Morador}
 * associado são opcionais.</p>
 *
 * <p>O andamento segue {@code ABERTO}, {@code RECONHECIDO}, {@code EM_REPARO} e {@code RESOLVIDO}.
 * Os métodos desta classe aplicam cada transição e recusam as que não são permitidas.</p>
 */
public class Alerta {

    private int idAlerta;
    private TipoAlertaEnum tipoAlerta;
    private TipoGravidadeEnum tipoGravidade;
    private StatusAlertaEnum statusAlerta;

    private LocalDateTime dataIdentificacao;
    private LocalDateTime dataReconhecimento;
    private LocalDateTime dataResolucao;

    private Double volumeDesperdicado;

    private Residencia residencia;
    private Dispositivo dispositivo;
    private Morador morador;

    public int getIdAlerta() {
        return idAlerta;
    }

    public void setIdAlerta(int idAlerta) {
        this.idAlerta = idAlerta;
    }

    public TipoAlertaEnum getTipoAlerta() {
        return tipoAlerta;
    }

    public void setTipoAlerta(TipoAlertaEnum tipoAlerta) {
        this.tipoAlerta = tipoAlerta;
    }

    public TipoGravidadeEnum getTipoGravidade() {
        return tipoGravidade;
    }

    public void setTipoGravidade(TipoGravidadeEnum tipoGravidade) {
        this.tipoGravidade = tipoGravidade;
    }

    public StatusAlertaEnum getStatusAlerta() {
        return statusAlerta;
    }

    public void setStatusAlerta(StatusAlertaEnum statusAlerta) {
        this.statusAlerta = statusAlerta;
    }

    public LocalDateTime getDataIdentificacao() {
        return dataIdentificacao;
    }

    public void setDataIdentificacao(LocalDateTime dataIdentificacao) {
        this.dataIdentificacao = dataIdentificacao;
    }

    public LocalDateTime getDataReconhecimento() {
        return dataReconhecimento;
    }

    public void setDataReconhecimento(LocalDateTime dataReconhecimento) {
        this.dataReconhecimento = dataReconhecimento;
    }

    public LocalDateTime getDataResolucao() {
        return dataResolucao;
    }

    public void setDataResolucao(LocalDateTime dataResolucao) {
        this.dataResolucao = dataResolucao;
    }

    public Double getVolumeDesperdicado() {
        return volumeDesperdicado;
    }

    public void setVolumeDesperdicado(Double volumeDesperdicado) {
        this.volumeDesperdicado = volumeDesperdicado;
    }

    public Residencia getResidencia() {
        return residencia;
    }

    public void setResidencia(Residencia residencia) {
        this.residencia = residencia;
    }

    public Dispositivo getDispositivo() {
        return dispositivo;
    }

    public void setDispositivo(Dispositivo dispositivo) {
        this.dispositivo = dispositivo;
    }

    public Morador getMorador() {
        return morador;
    }

    public void setMorador(Morador morador) {
        this.morador = morador;
    }

    /**
     * Reconhece o alerta, passando o status de {@code ABERTO} para {@code RECONHECIDO} e
     * registrando a data de reconhecimento.
     *
     * @throws IllegalStateException se o alerta não estiver {@code ABERTO}
     */
    public void reconhecer() {

        if (statusAlerta != StatusAlertaEnum.ABERTO) {
            throw new IllegalStateException(
                    "Somente ocorrências abertas podem ser reconhecidas."
            );
        }

        statusAlerta = StatusAlertaEnum.RECONHECIDO;
        dataReconhecimento = momentoDoEvento();
    }

    /**
     * Inicia o reparo do alerta, passando o status de {@code RECONHECIDO} para {@code EM_REPARO}.
     *
     * @throws IllegalStateException se o alerta não estiver {@code RECONHECIDO}
     */
    public void iniciarReparo() {

        if (statusAlerta != StatusAlertaEnum.RECONHECIDO) {
            throw new IllegalStateException(
                    "Somente ocorrências reconhecidas podem entrar em reparo."
            );
        }

        statusAlerta = StatusAlertaEnum.EM_REPARO;
    }

    /**
     * Resolve o alerta, passando o status de {@code EM_REPARO} para {@code RESOLVIDO} e registrando
     * a data de resolução.
     *
     * @throws IllegalStateException se o alerta não estiver {@code EM_REPARO}
     */
    public void resolver() {

        if (statusAlerta != StatusAlertaEnum.EM_REPARO) {
            throw new IllegalStateException(
                    "Somente ocorrências em reparo podem ser resolvidas."
            );
        }

        statusAlerta = StatusAlertaEnum.RESOLVIDO;
        dataResolucao = momentoDoEvento();
    }

    /**
     * Resolve o alerta automaticamente quando o fluxo volta ao padrão por 48 horas seguidas (RN09).
     * Vale para qualquer status em aberto ({@code ABERTO}, {@code RECONHECIDO} ou
     * {@code EM_REPARO}), sem passar pelas etapas manuais. A data de resolução nunca fica anterior
     * à data de identificação.
     *
     * @param quando momento em que as 48 horas de fluxo normal se completaram
     * @throws IllegalStateException se o alerta já estiver {@code RESOLVIDO}
     */
    public void resolverAutomaticamente(LocalDateTime quando) {

        if (statusAlerta == StatusAlertaEnum.RESOLVIDO) {
            throw new IllegalStateException("A ocorrência já está resolvida.");
        }

        statusAlerta = StatusAlertaEnum.RESOLVIDO;
        dataResolucao = dataIdentificacao != null && quando.isBefore(dataIdentificacao)
                ? dataIdentificacao : quando;
    }

    /**
     * Obtém o momento atual para registrar um evento do alerta, sem permitir que ele fique anterior
     * à data de identificação.
     *
     * @return a data e hora atuais, ou a data de identificação se esta for posterior
     */
    private LocalDateTime momentoDoEvento() {

        LocalDateTime agora = LocalDateTime.now();

        if (dataIdentificacao != null && agora.isBefore(dataIdentificacao)) {
            return dataIdentificacao;
        }

        return agora;
    }
}