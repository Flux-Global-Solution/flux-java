package br.com.fiap.flux.models;

import java.time.LocalDateTime;

/**
 * Representa uma leitura de consumo registrada por um {@link Dispositivo}.
 *
 * <p>Cada leitura traz a data e a hora, o número de sequência dentro do dispositivo, a vazão
 * (litros por minuto), o volume (litros) e a duração do fluxo (segundos). A sincronização é
 * controlada por {@link #marcarComoSincronizada(LocalDateTime)} e {@link #estaSincronizada()}.</p>
 */
public class Leitura {
    private int idLeitura;
    private LocalDateTime dataLeitura;
    private int sequencia;
    private double vazaoLitroMinuto;
    private double volumeLitros;
    private int duracaoFluxoSeg;
    private int sincronizada;
    private LocalDateTime dataSincronizacao;
    private Dispositivo dispositivo;

    public int getIdLeitura() {
        return idLeitura;
    }

    public void setIdLeitura(int idLeitura) {
        this.idLeitura = idLeitura;
    }

    public LocalDateTime getDataLeitura() {
        return dataLeitura;
    }

    public void setDataLeitura(LocalDateTime dataLeitura) {
        this.dataLeitura = dataLeitura;
    }

    public int getSequencia() {
        return sequencia;
    }

    public void setSequencia(int sequencia) {
        this.sequencia = sequencia;
    }

    public double getVazaoLitroMinuto() {
        return vazaoLitroMinuto;
    }

    public void setVazaoLitroMinuto(double vazaoLitroMinuto) {
        this.vazaoLitroMinuto = vazaoLitroMinuto;
    }

    public double getVolumeLitros() {
        return volumeLitros;
    }

    public void setVolumeLitros(double volumeLitros) {
        this.volumeLitros = volumeLitros;
    }

    public int getDuracaoFluxoSeg() {
        return duracaoFluxoSeg;
    }

    public void setDuracaoFluxoSeg(int duracaoFluxoSeg) {
        this.duracaoFluxoSeg = duracaoFluxoSeg;
    }

    public int getSincronizada() {
        return sincronizada;
    }

    public void setSincronizada(int sincronizada) {
        this.sincronizada = sincronizada;
    }

    public LocalDateTime getDataSincronizacao() {
        return dataSincronizacao;
    }

    public void setDataSincronizacao(LocalDateTime dataSincronizacao) {
        this.dataSincronizacao = dataSincronizacao;
    }

    public Dispositivo getDispositivo() {
        return dispositivo;
    }

    public void setDispositivo(Dispositivo dispositivo) {
        this.dispositivo = dispositivo;
    }

    /**
     * Marca a leitura como sincronizada na data informada. A data é obrigatória porque o banco
     * exige que a marca e a data sejam gravadas juntas.
     *
     * @param data data e hora da sincronização
     * @throws IllegalArgumentException se a data for {@code null}
     */
    public void marcarComoSincronizada(LocalDateTime data) {
        if (data == null) {
            throw new IllegalArgumentException("A data de sincronização é obrigatória.");
        }
        this.sincronizada = 1;
        this.dataSincronizacao = data;
    }

    /**
     * Indica se a leitura já foi sincronizada.
     *
     * @return {@code true} se a leitura está marcada como sincronizada
     */
    public boolean estaSincronizada() {
        return sincronizada == 1;
    }
}