package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.DispositivoRequestDTO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.DispositivoStatusRequestDTO;
import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;
import br.com.fiap.flux.mapper.DispositivoMapper;
import br.com.fiap.flux.models.Dispositivo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Cadastro, consulta e simulação de status dos dispositivos FLUX. Não há comunicação com hardware:
 * os status são simulados pela própria API, para demonstrar cenários.
 *
 * <p>Cadastrar um dispositivo exige um morador e uma residência existentes.</p>
 */
@Service
public class DispositivoService {

    private final DispositivoDAO dispositivoDAO;
    private final MoradorDAO moradorDAO;
    private final ResidenciaDAO residenciaDAO;

    public DispositivoService() {
        this.dispositivoDAO = new DispositivoDAO();
        this.moradorDAO = new MoradorDAO();
        this.residenciaDAO = new ResidenciaDAO();
    }

    /**
     * Cadastra um dispositivo e o associa à residência informada. Nasce ATIVO, com sensor OK e energia
     * NORMAL (os status nunca vêm do cliente). Série ou MAC já cadastrados não são aceitos de novo.
     */
    public DispositivoResponseDTO cadastrar(int idMorador, DispositivoRequestDTO dto) {
        validar(dto);
        verificarMorador(idMorador);
        verificarResidencia(dto.idResidencia());

        Dispositivo dispositivo = DispositivoMapper.dtoToEntity(dto);
        normalizar(dispositivo);
        exigirSerieEMacLivres(dispositivo, 0);

        dispositivo.setStatusDispositivo(StatusDispositivoEnum.ATIVO);   // os status nunca vêm do cliente
        dispositivo.setSensorOk(true);
        dispositivo.setStatusEnergia(StatusEnergiaEnum.NORMAL);
        dispositivoDAO.cadastrar(dispositivo);
        if (dispositivo.getIdDispositivo() == 0) {
            throw new IllegalStateException("Não foi possível gravar o dispositivo.");
        }
        return DispositivoMapper.toDto(buscarExistente(dispositivo.getIdDispositivo()));
    }

    /** Lista os dispositivos das residências às quais o morador tem acesso. */
    public List<DispositivoResponseDTO> listar(int idMorador) {
        return dispositivoDAO.listarPorMorador(idMorador)
                .stream()
                .map(DispositivoMapper::toDto)
                .toList();
    }

    /** Consulta o dispositivo, com os status de dispositivo, sensor e energia. */
    public DispositivoResponseDTO buscarPorId(int idDispositivo) {
        return DispositivoMapper.toDto(buscarExistente(idDispositivo));
    }

    /** Atualiza série, MAC, modelo e data de instalação. A residência do dispositivo não muda. */
    public DispositivoResponseDTO atualizar(int idDispositivo, DispositivoRequestDTO dto) {
        buscarExistente(idDispositivo);
        validar(dto);

        Dispositivo dispositivo = DispositivoMapper.dtoToEntity(dto);
        normalizar(dispositivo);
        exigirSerieEMacLivres(dispositivo, idDispositivo);

        dispositivo.setIdDispositivo(idDispositivo);
        dispositivoDAO.alterar(dispositivo);
        return DispositivoMapper.toDto(buscarExistente(idDispositivo));
    }

    /**
     * Altera o status simulado (dispositivo, sensor e/ou energia). Os campos ausentes ficam como estão,
     * mas ao menos um precisa vir.
     */
    public DispositivoResponseDTO alterarStatus(int idDispositivo, DispositivoStatusRequestDTO dto) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);

        if (dto.statusDispositivo() == null && dto.sensorOk() == null && dto.statusEnergia() == null) {
            throw new IllegalArgumentException("Informe ao menos um status a alterar.");
        }
        if (dto.statusDispositivo() != null) {
            dispositivo.setStatusDispositivo(dto.statusDispositivo());
        }
        if (dto.sensorOk() != null) {
            dispositivo.setSensorOk(dto.sensorOk());
        }
        if (dto.statusEnergia() != null) {
            dispositivo.setStatusEnergia(dto.statusEnergia());
        }
        dispositivoDAO.alterarStatus(dispositivo);
        return DispositivoMapper.toDto(buscarExistente(idDispositivo));
    }

    /**
     * Impede que série ou MAC já cadastrados sejam usados de novo. Se o dispositivo existente está
     * em
     * outra residência, a mensagem diz isso.
     *
     * @param idIgnorado id do próprio dispositivo na atualização (0 no cadastro)
     */
    private void exigirSerieEMacLivres(Dispositivo novo, int idIgnorado) {
        for (Dispositivo outro : dispositivoDAO.buscarPorSerieOuMac(novo.getSerie(), novo.getMacAddress())) {
            if (outro.getIdDispositivo() == idIgnorado) {
                continue;
            }
            boolean outraResidencia = novo.getResidencia() == null
                    || outro.getResidencia().getIdResidencia() != novo.getResidencia().getIdResidencia();
            throw new IllegalArgumentException(outraResidencia && idIgnorado == 0
                    ? "Dispositivo já cadastrado e vinculado a outra residência."
                    : "Dispositivo já cadastrado com esta série ou MAC.");
        }
    }

    private void verificarMorador(int idMorador) {
        if (moradorDAO.buscarPorId(idMorador) == null) {
            throw new IllegalArgumentException("Morador não encontrado");
        }
    }

    private void verificarResidencia(int idResidencia) {
        if (residenciaDAO.buscarPorId(idResidencia) == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
    }

    /** Busca o dispositivo ou lança "não encontrado". */
    private Dispositivo buscarExistente(int idDispositivo) {
        Dispositivo dispositivo = dispositivoDAO.buscarPorId(idDispositivo);
        if (dispositivo == null) {
            throw new IllegalArgumentException("Dispositivo não encontrado");
        }
        return dispositivo;
    }

    /** Tira os espaços e deixa série e MAC em maiúsculas, com o MAC separado por ":". */
    private void normalizar(Dispositivo dispositivo) {
        dispositivo.setSerie(dispositivo.getSerie().trim().toUpperCase(Locale.ROOT));
        dispositivo.setMacAddress(dispositivo.getMacAddress().trim().toUpperCase(Locale.ROOT).replace('-', ':'));
        dispositivo.setModelo(dispositivo.getModelo().trim());
    }

    /**
     * Confere os campos obrigatórios, os tamanhos máximos, o formato do MAC (AA:BB:CC:DD:EE:FF) e
     * que a
     * data de instalação não seja futura.
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validar(DispositivoRequestDTO dto) {
        if (dto.serie() == null || dto.serie().isBlank()) {
            System.out.println("ERRO: O número de série é obrigatório");
            throw new IllegalArgumentException("O número de série é obrigatório.");
        }
        if (dto.serie().trim().length() > 50) {
            throw new IllegalArgumentException("O número de série deve ter no máximo 50 caracteres.");
        }
        if (dto.macAddress() == null
                || !dto.macAddress().trim().matches("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")) {
            System.out.println("ERRO: Endereço MAC inválido");
            throw new IllegalArgumentException("Endereço MAC inválido (use o formato AA:BB:CC:DD:EE:FF).");
        }
        if (dto.modelo() == null || dto.modelo().isBlank()) {
            System.out.println("ERRO: O modelo é obrigatório");
            throw new IllegalArgumentException("O modelo é obrigatório.");
        }
        if (dto.modelo().trim().length() > 50) {
            throw new IllegalArgumentException("O modelo deve ter no máximo 50 caracteres.");
        }
        if (dto.dataInstalacao() == null) {
            System.out.println("ERRO: A data de instalação é obrigatória");
            throw new IllegalArgumentException("A data de instalação é obrigatória.");
        }
        if (dto.dataInstalacao().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("A data de instalação não pode ser futura.");
        }
    }
}