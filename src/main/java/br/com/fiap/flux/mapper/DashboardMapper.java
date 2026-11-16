
package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.AlertaResponseDTO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.ResidenciaResponseDTO;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;

import java.util.List;

/** Conversões usadas pelo dashboard; reaproveitam os mapeadores de cada recurso. */
public class DashboardMapper {

    /** Converte a residência com o tipo de vínculo do morador que consulta. */
    public static ResidenciaResponseDTO residenciaToDto(
            Residencia residencia, MoradorResidencia vinculo) {

        return ResidenciaMapper.toDto(
                residencia, vinculo.getTipoVinculo());
    }

    public static List<AlertaResponseDTO> alertasToDto(List<Alerta> alertas) {
        return alertas.stream()
                .map(AlertaMapper::toDto)
                .toList();
    }

    public static DispositivoResponseDTO dispositivoToDto(
            Dispositivo dispositivo) {

        return DispositivoMapper.toDto(dispositivo);
    }
}
