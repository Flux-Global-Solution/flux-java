package br.com.fiap.flux.models;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Representa uma residência monitorada pelo sistema FLUX.
 *
 * <p>Guarda o endereço, a quantidade de moradores, a data de cadastro, o período de ausência (texto
 * livre com datas) e os limites, em minutos, usados para classificar fluxo contínuo como ponto
 * aberto e como vazamento. Também define a janela de menor consumo, o período do dia em que se
 * espera pouco uso de água. Enquanto a residência não definir a sua, vale o padrão das 01h às 05h
 * (RN06).</p>
 */
public class Residencia {
    public static final LocalTime INICIO_MENOR_CONSUMO_PADRAO = LocalTime.of(1, 0);
    public static final LocalTime FIM_MENOR_CONSUMO_PADRAO = LocalTime.of(5, 0);

    private int idResidencia;
    private String endereco;
    private int qtdMoradores;
    private LocalDate dtCadastro;
    private String periodoAusencia;
    private int limitePontoAberto;
    private int limiteVazamentoMin;
    private LocalTime inicioMenorConsumo;
    private LocalTime fimMenorConsumo;

    public int getIdResidencia() {
        return idResidencia;
    }

    public void setIdResidencia(int idResidencia) {
        this.idResidencia = idResidencia;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public int getQtdMoradores() {
        return qtdMoradores;
    }

    public void setQtdMoradores(int qtdMoradores) {
        this.qtdMoradores = qtdMoradores;
    }

    public LocalDate getDtCadastro() {
        return dtCadastro;
    }

    public void setDtCadastro(LocalDate dtCadastro) {
        this.dtCadastro = dtCadastro;
    }

    public String getPeriodoAusencia() {
        return periodoAusencia;
    }

    public void setPeriodoAusencia(String periodoAusencia) {
        this.periodoAusencia = periodoAusencia;
    }

    public int getLimitePontoAberto() {
        return limitePontoAberto;
    }

    public void setLimitePontoAberto(int limitePontoAberto) {
        this.limitePontoAberto = limitePontoAberto;
    }

    public int getLimiteVazamentoMin() {
        return limiteVazamentoMin;
    }

    public void setLimiteVazamentoMin(int limiteVazamentoMin) {
        this.limiteVazamentoMin = limiteVazamentoMin;
    }

    public LocalTime getInicioMenorConsumo() {
        return inicioMenorConsumo;
    }

    public void setInicioMenorConsumo(LocalTime inicioMenorConsumo) {
        this.inicioMenorConsumo = inicioMenorConsumo;
    }

    public LocalTime getFimMenorConsumo() {
        return fimMenorConsumo;
    }

    public void setFimMenorConsumo(LocalTime fimMenorConsumo) {
        this.fimMenorConsumo = fimMenorConsumo;
    }

    /**
     * Obtém o horário em que começa a janela de menor consumo, usando o padrão das 01h (RN06) se a
     * residência não definiu a sua.
     *
     * @return o início efetivo da janela de menor consumo
     */
    public LocalTime inicioMenorConsumoEfetivo() {
        return inicioMenorConsumo != null && fimMenorConsumo != null ? inicioMenorConsumo : INICIO_MENOR_CONSUMO_PADRAO;
    }

    /**
     * Obtém o horário em que termina a janela de menor consumo, usando o padrão das 05h (RN06) se a
     * residência não definiu a sua.
     *
     * @return o fim efetivo da janela de menor consumo
     */
    public LocalTime fimMenorConsumoEfetivo() {
        return inicioMenorConsumo != null && fimMenorConsumo != null ? fimMenorConsumo : FIM_MENOR_CONSUMO_PADRAO;
    }

    /**
     * Indica se um horário cai na janela de menor consumo, com início inclusivo e fim exclusivo. A
     * janela pode virar a meia-noite (por exemplo, das 22h às 6h). Sem janela configurada, vale o
     * padrão das 01h às 05h (RN06).
     *
     * @param horario horário do dia a ser verificado
     * @return {@code true} se o horário está dentro da janela; {@code false} também quando for
     *     {@code null}
     */
    public boolean estaNoPeriodoMenorConsumo(LocalTime horario) {
        if (horario == null) {
            return false;
        }
        LocalTime inicio = inicioMenorConsumoEfetivo();
        LocalTime fim = fimMenorConsumoEfetivo();
        if (inicio.isBefore(fim)) {
            return !horario.isBefore(inicio) && horario.isBefore(fim);
        }
        return !horario.isBefore(inicio) || horario.isBefore(fim);
    }

    /**
     * Calcula quantos segundos do intervalo de {@code de} (inclusivo) até {@code ate} (exclusivo)
     * caem dentro da janela de menor consumo, somando todas as ocorrências diárias dela, inclusive
     * as que viram a meia-noite.
     *
     * @param de  início do intervalo
     * @param ate fim do intervalo
     * @return o total de segundos dentro da janela; zero se o intervalo for nulo ou vazio
     */
    public long segundosNoPeriodoMenorConsumo(LocalDateTime de, LocalDateTime ate) {
        if (de == null || ate == null || !ate.isAfter(de)) {
            return 0;
        }
        LocalTime inicio = inicioMenorConsumoEfetivo();
        LocalTime fim = fimMenorConsumoEfetivo();
        boolean viraMeiaNoite = !inicio.isBefore(fim);
        long total = 0;
        // o período que começou na véspera pode invadir o intervalo
        for (LocalDate dia = de.toLocalDate().minusDays(1); !dia.isAfter(ate.toLocalDate()); dia = dia.plusDays(1)) {
            LocalDateTime comeco = dia.atTime(inicio);
            LocalDateTime termino = (viraMeiaNoite ? dia.plusDays(1) : dia).atTime(fim);
            LocalDateTime a = comeco.isAfter(de) ? comeco : de;
            LocalDateTime b = termino.isBefore(ate) ? termino : ate;
            if (b.isAfter(a)) {
                total += Duration.between(a, b).getSeconds();
            }
        }
        return total;
    }
}