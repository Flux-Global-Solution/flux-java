package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.DispositivoRequestDTO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Residencia;

/** Conversão entre {@link Dispositivo} e seus DTOs. */
public class DispositivoMapper {
    /**
     * Monta o dispositivo com os dados do pedido; a residência é só uma referência (apenas o id).
     * Os
     * status e a sincronização não vêm do cliente: o service os define.
     */
    public static Dispositivo dtoToEntity (DispositivoRequestDTO dto){
        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setSerie(dto.serie());
        dispositivo.setMacAddress(dto.macAddress());
        dispositivo.setModelo(dto.modelo());
        dispositivo.setDataInstalacao(dto.dataInstalacao());

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(dto.idResidencia());
        dispositivo.setResidencia(residencia);

        return dispositivo;
    }

    public static DispositivoResponseDTO toDto (Dispositivo dispositivo){
        return new DispositivoResponseDTO(
                dispositivo.getIdDispositivo(),
                dispositivo.getSerie(),
                dispositivo.getMacAddress(),
                dispositivo.getModelo(),
                dispositivo.getDataInstalacao(),
                dispositivo.getStatusDispositivo(),
                dispositivo.isSensorOk(),
                dispositivo.getStatusEnergia(),
                dispositivo.getUltimaSincronizacao(),
                dispositivo.getResidencia() != null ? dispositivo.getResidencia().getIdResidencia() : 0
        );
    }
}