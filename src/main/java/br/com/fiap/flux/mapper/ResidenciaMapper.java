package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.ResidenciaRequestDTO;
import br.com.fiap.flux.dto.ResidenciaResponseDTO;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.models.Residencia;

/** Conversão entre {@link Residencia} e seus DTOs. */
public class ResidenciaMapper {

    // limite padrão (min) de ponto aberto, igual ao DEFAULT da tabela
    public static final int LIMITE_PONTO_ABERTO_PADRAO = 20;
    // limite padrão (min) de vazamento, igual ao DEFAULT da tabela
    public static final int LIMITE_VAZAMENTO_PADRAO = 30;

    /**
     * Copia os dados do pedido. Limites ausentes assumem o padrão; a validação fica no service. O
     * id e a
     * data de cadastro não vêm do cliente.
     */
    public static Residencia dtoToEntity (ResidenciaRequestDTO dto){
        Residencia residencia = new Residencia();
        residencia.setEndereco(dto.endereco());
        residencia.setQtdMoradores(dto.qtdMoradores() != null ? dto.qtdMoradores() : 0);
        residencia.setPeriodoAusencia(dto.periodoAusencia());
        residencia.setLimitePontoAberto(
                dto.limitePontoAberto() != null ? dto.limitePontoAberto() : LIMITE_PONTO_ABERTO_PADRAO);
        residencia.setLimiteVazamentoMin(
                dto.limiteVazamentoMin() != null ? dto.limiteVazamentoMin() : LIMITE_VAZAMENTO_PADRAO);
        residencia.setInicioMenorConsumo(dto.inicioMenorConsumo());
        residencia.setFimMenorConsumo(dto.fimMenorConsumo());
        return residencia;
    }

    /**
     * Converte a residência.
     *
     * @param tipoVinculo vínculo do morador que consulta com a residência
     */
    public static ResidenciaResponseDTO toDto (Residencia residencia, TipoVinculoEnum tipoVinculo){
        return new ResidenciaResponseDTO(
                residencia.getIdResidencia(),
                residencia.getEndereco(),
                residencia.getQtdMoradores(),
                residencia.getDtCadastro(),
                residencia.getPeriodoAusencia(),
                residencia.getLimitePontoAberto(),
                residencia.getLimiteVazamentoMin(),
                residencia.getInicioMenorConsumo(),
                residencia.getFimMenorConsumo(),
                tipoVinculo
        );
    }
}