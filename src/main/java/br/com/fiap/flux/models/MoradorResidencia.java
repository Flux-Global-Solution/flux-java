package br.com.fiap.flux.models;

import br.com.fiap.flux.enums.TipoVinculoEnum;

import java.time.LocalDate;

/**
 * Representa o vínculo entre um {@link Morador} e uma {@link Residencia}.
 *
 * <p>O vínculo define o acesso do morador à residência, conforme {@link TipoVinculoEnum}:
 * {@code TITULAR} (um por residência, definido no cadastro) ou {@code MORADOR_COMUM}. Também guarda
 * a data em que o vínculo foi criado.</p>
 */
public class MoradorResidencia {
    private int idMoradorResidencia;
    private TipoVinculoEnum tipoVinculo;
    private LocalDate dataVinculo;
    private Morador morador;
    private Residencia residencia;

    public int getIdMoradorResidencia() {
        return idMoradorResidencia;
    }

    public void setIdMoradorResidencia(int idMoradorResidencia) {
        this.idMoradorResidencia = idMoradorResidencia;
    }

    public TipoVinculoEnum getTipoVinculo() {
        return tipoVinculo;
    }

    public void setTipoVinculo(TipoVinculoEnum tipoVinculo) {
        this.tipoVinculo = tipoVinculo;
    }

    public LocalDate getDataVinculo() {
        return dataVinculo;
    }

    public void setDataVinculo(LocalDate dataVinculo) {
        this.dataVinculo = dataVinculo;
    }

    public Morador getMorador() {
        return morador;
    }

    public void setMorador(Morador morador) {
        this.morador = morador;
    }

    public Residencia getResidencia() {
        return residencia;
    }

    public void setResidencia(Residencia residencia) {
        this.residencia = residencia;
    }

    /**
     * Indica se este é o vínculo do titular da residência.
     *
     * @return {@code true} se o tipo de vínculo for {@code TITULAR}
     */
    public boolean isTitular() {
        return tipoVinculo == TipoVinculoEnum.TITULAR;
    }
}