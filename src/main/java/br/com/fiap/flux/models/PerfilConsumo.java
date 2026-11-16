package br.com.fiap.flux.models;

import java.time.LocalDate;

/**
 * Representa o perfil de consumo de uma {@link Residencia}, usado como referência para identificar
 * consumos fora do padrão.
 *
 * <p>Os valores são calculados pelo servidor a partir das leituras: consumo médio diário (litros),
 * vazão média (litros por minuto) e desvio padrão do consumo diário. Também guarda a descrição do
 * padrão, a data da última atualização e se há histórico suficiente para o perfil ser
 * confiável.</p>
 */
public class PerfilConsumo {
    public static final double DESVIOS_LIMITE = 2.0;
    public static final double MULTIPLO_LIMITE = 1.5;

    private int idPerfilConsumo;
    private double consumoMedio;
    private double vazaoMedia;
    private double desvioPadrao;
    private LocalDate ultimaAtualizacao;
    private String padraoConsumo;
    private int historicoSuficiente;
    private Residencia residencia;

    public int getIdPerfilConsumo() {
        return idPerfilConsumo;
    }

    public void setIdPerfilConsumo(int idPerfilConsumo) {
        this.idPerfilConsumo = idPerfilConsumo;
    }

    public double getConsumoMedio() {
        return consumoMedio;
    }

    public void setConsumoMedio(double consumoMedio) {
        this.consumoMedio = consumoMedio;
    }

    public double getVazaoMedia() {
        return vazaoMedia;
    }

    public void setVazaoMedia(double vazaoMedia) {
        this.vazaoMedia = vazaoMedia;
    }

    public double getDesvioPadrao() {
        return desvioPadrao;
    }

    public void setDesvioPadrao(double desvioPadrao) {
        this.desvioPadrao = desvioPadrao;
    }

    public LocalDate getUltimaAtualizacao() {
        return ultimaAtualizacao;
    }

    public void setUltimaAtualizacao(LocalDate ultimaAtualizacao) {
        this.ultimaAtualizacao = ultimaAtualizacao;
    }

    public String getPadraoConsumo() {
        return padraoConsumo;
    }

    public void setPadraoConsumo(String padraoConsumo) {
        this.padraoConsumo = padraoConsumo;
    }

    public int getHistoricoSuficiente() {
        return historicoSuficiente;
    }

    public void setHistoricoSuficiente(int historicoSuficiente) {
        this.historicoSuficiente = historicoSuficiente;
    }

    public Residencia getResidencia() {
        return residencia;
    }

    public void setResidencia(Residencia residencia) {
        this.residencia = residencia;
    }

    /**
     * Indica se há histórico suficiente para o perfil servir de referência confiável.
     *
     * @return {@code true} se o perfil foi calculado com dias de leitura suficientes
     */
    public boolean possuiHistoricoSuficiente() {
        return historicoSuficiente == 1;
    }

    /**
     * Indica se o consumo diário está dentro do padrão da residência. É o critério usado por
     * {@code AnaliseConsumoService} na variação diária.
     *
     * <p>O consumo só fica fora do padrão quando atinge a média mais {@value #DESVIOS_LIMITE}
     * desvios padrão e também {@value #MULTIPLO_LIMITE} vez a média. As duas condições valem juntas
     * para que um desvio pequeno não transforme variação comum em anomalia. Consumo abaixo da média
     * nunca é fora do padrão, e sem histórico suficiente nada é tido como fora do padrão.</p>
     *
     * @param consumo consumo do dia, em litros (mesma unidade de {@code consumoMedio})
     * @return {@code true} se o consumo está dentro do padrão
     */
    public boolean estaNoPadrao(double consumo) {
        if (!possuiHistoricoSuficiente()) {
            return true;
        }
        boolean acimaDosDesvios = consumo >= consumoMedio + DESVIOS_LIMITE * desvioPadrao;
        boolean acimaDoMultiplo = consumo >= consumoMedio * MULTIPLO_LIMITE;
        return !(acimaDosDesvios && acimaDoMultiplo);
    }
}