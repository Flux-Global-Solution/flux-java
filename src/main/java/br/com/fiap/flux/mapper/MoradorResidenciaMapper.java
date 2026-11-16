package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.MoradorResidenciaRequestDTO;
import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;

/** Conversão entre {@link MoradorResidencia} (vínculo) e seus DTOs. */
public class MoradorResidenciaMapper {
    public static MoradorResidencia dtoToEntity (MoradorResidenciaRequestDTO dto){
        MoradorResidencia moradorResidencia = new MoradorResidencia();
        moradorResidencia.setTipoVinculo(dto.tipoVinculo());
        moradorResidencia.setDataVinculo(dto.dataVinculo());

        Morador morador = new Morador();
        morador.setIdMorador(dto.idMorador());
        moradorResidencia.setMorador(morador);

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(dto.idResidencia());
        moradorResidencia.setResidencia(residencia);

        return moradorResidencia;
    }

    /** Converte o vínculo; o morador sai sem senha. */
    public static MoradorResidenciaResponseDTO toDto (MoradorResidencia moradorResidencia){
        return new MoradorResidenciaResponseDTO(
                moradorResidencia.getIdMoradorResidencia(),
                moradorResidencia.getTipoVinculo(),
                moradorResidencia.getDataVinculo(),
                moradorResidencia.getMorador() != null ? MoradorMapper.toDto(moradorResidencia.getMorador()) : null,
                moradorResidencia.getResidencia() != null ? moradorResidencia.getResidencia().getIdResidencia() : 0
        );
    }
}