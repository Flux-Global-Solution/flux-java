package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.AlertaRequestDTO;
import br.com.fiap.flux.dto.AlertaResponseDTO;
import br.com.fiap.flux.models.Alerta;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.Residencia;

/** Conversão entre {@link Alerta} e seus DTOs. */
public class AlertaMapper {
    /**
     * Monta o alerta com os dados do pedido. Status e datas de reconhecimento e resolução são do
     * servidor,
     * nunca do cliente. O morador é opcional: sem ele, o alerta fica sem responsável.
     */
    public static Alerta dtoToEntity (AlertaRequestDTO dto){
        Alerta alerta = new Alerta();
        // status e datas de reconhecimento/resolução são definidos pelo servidor, nunca pelo cliente.
        alerta.setTipoAlerta(dto.tipoAlerta());
        alerta.setTipoGravidade(dto.tipoGravidade());
        alerta.setDataIdentificacao(dto.dataIdentificacao());
        alerta.setVolumeDesperdicado(dto.volumeDesperdicado());

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(dto.idResidencia());
        alerta.setResidencia(residencia);

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(dto.idDispositivo());
        alerta.setDispositivo(dispositivo);

        // O morador é opcional: sem ele, o alerta fica sem responsável (id_morador NULL).
        if (dto.idMorador() != null) {
            Morador morador = new Morador();
            morador.setIdMorador(dto.idMorador());
            alerta.setMorador(morador);
        }

        return alerta;
    }

    /** Converte o alerta; o morador responsável sai sem senha e é {@code null} quando não há. */
    public static AlertaResponseDTO toDto (Alerta alerta){
        return new AlertaResponseDTO(
                alerta.getIdAlerta(),
                alerta.getTipoAlerta(),
                alerta.getTipoGravidade(),
                alerta.getStatusAlerta(),
                alerta.getDataIdentificacao(),
                alerta.getDataReconhecimento(),
                alerta.getDataResolucao(),
                alerta.getVolumeDesperdicado(),
                alerta.getResidencia() != null ? alerta.getResidencia().getIdResidencia() : 0,
                alerta.getDispositivo() != null ? alerta.getDispositivo().getIdDispositivo() : 0,
                alerta.getMorador() != null ? MoradorMapper.toDto(alerta.getMorador()) : null
        );
    }
}