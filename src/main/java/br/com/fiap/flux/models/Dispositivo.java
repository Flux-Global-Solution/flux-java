package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;

import java.time.LocalDate;

/**
 * Representa o dispositivo FLUX instalado em uma {@link Residencia} para medir o consumo de água.
 *
 * <p>Guarda os dados de cadastro (série, MAC, modelo e data de instalação) e três status simulados:
 * o do dispositivo ({@link StatusDispositivoEnum}), o do sensor ({@code sensorOk}, em que
 * {@code false} indica possível falha) e o de energia ({@link StatusEnergiaEnum}). A data da última
 * sincronização é opcional.</p>
 */
public class Dispositivo {
    private int idDispositivo;
    private String serie;
    private String macAddress;
    private String modelo;
    private LocalDate dataInstalacao;
    private StatusDispositivoEnum statusDispositivo;
    private boolean sensorOk;
    private StatusEnergiaEnum statusEnergia;
    private LocalDate ultimaSincronizacao;
    private Residencia residencia;

    public int getIdDispositivo() {
        return idDispositivo;
    }

    public void setIdDispositivo(int idDispositivo) {
        this.idDispositivo = idDispositivo;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public LocalDate getDataInstalacao() {
        return dataInstalacao;
    }

    public void setDataInstalacao(LocalDate dataInstalacao) {
        this.dataInstalacao = dataInstalacao;
    }

    public StatusDispositivoEnum getStatusDispositivo() {
        return statusDispositivo;
    }

    public void setStatusDispositivo(StatusDispositivoEnum statusDispositivo) {
        this.statusDispositivo = statusDispositivo;
    }

    public boolean isSensorOk() {
        return sensorOk;
    }

    public void setSensorOk(boolean sensorOk) {
        this.sensorOk = sensorOk;
    }

    public StatusEnergiaEnum getStatusEnergia() {
        return statusEnergia;
    }

    public void setStatusEnergia(StatusEnergiaEnum statusEnergia) {
        this.statusEnergia = statusEnergia;
    }

    public LocalDate getUltimaSincronizacao() {
        return ultimaSincronizacao;
    }

    public void setUltimaSincronizacao(LocalDate ultimaSincronizacao) {
        this.ultimaSincronizacao = ultimaSincronizacao;
    }

    public Residencia getResidencia() {
        return residencia;
    }

    public void setResidencia(Residencia residencia) {
        this.residencia = residencia;
    }

    /**
     * Indica se o dispositivo tem condições de monitorar o consumo: precisa estar ativo, com o
     * sensor sem suspeita de falha e com energia. Bateria baixa ainda permite o monitoramento; sem
     * energia, não.
     *
     * @return {@code true} se o dispositivo está apto a monitorar
     */
    public boolean aptoParaMonitoramento() {
        return statusDispositivo == StatusDispositivoEnum.ATIVO
                && sensorOk
                && statusEnergia != null
                && statusEnergia != StatusEnergiaEnum.SEM_ENERGIA;
    }
}