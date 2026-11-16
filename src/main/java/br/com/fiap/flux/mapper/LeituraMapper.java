package br.com.fiap.flux.mapper;

import br.com.fiap.flux.dto.LeituraRequestDTO;
import br.com.fiap.flux.dto.LeituraResponseDTO;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Leitura;

/** Conversão entre {@link Leitura} e seus DTOs. */
public class LeituraMapper {
    /**
     * Monta a leitura com os dados do pedido; o dispositivo é só uma referência (apenas o id). A
     * sincronização não vem do cliente: o service a define.
     */
    public static Leitura dtoToEntity (LeituraRequestDTO dto){
        Leitura leitura = new Leitura();
        leitura.setDataLeitura(dto.dataLeitura());
        leitura.setSequencia(dto.sequencia());
        leitura.setVazaoLitroMinuto(dto.vazaoLitroMinuto());
        leitura.setVolumeLitros(dto.volumeLitros());
        leitura.setDuracaoFluxoSeg(dto.duracaoFluxoSeg());

        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setIdDispositivo(dto.idDispositivo());
        leitura.setDispositivo(dispositivo);

        return leitura;
    }

    public static LeituraResponseDTO toDto (Leitura leitura){
        return new LeituraResponseDTO(
                leitura.getIdLeitura(),
                leitura.getDataLeitura(),
                leitura.getSequencia(),
                leitura.getVazaoLitroMinuto(),
                leitura.getVolumeLitros(),
                leitura.getDuracaoFluxoSeg(),
                leitura.getSincronizada(),
                leitura.getDataSincronizacao(),
                leitura.getDispositivo() != null ? leitura.getDispositivo().getIdDispositivo() : 0
        );
    }
}