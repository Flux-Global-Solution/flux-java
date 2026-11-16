package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dto.DispositivoRequestDTO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.DispositivoStatusRequestDTO;
import br.com.fiap.flux.enums.StatusDispositivoEnum;
import br.com.fiap.flux.enums.StatusEnergiaEnum;
import br.com.fiap.flux.mapper.DispositivoMapper;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.MoradorResidencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Cadastro, consulta e simulação de status dos dispositivos FLUX. Não há comunicação com hardware:
 * os status são simulados pela própria API, para demonstrar cenários.
 *
 * <p>As operações com {@code idMorador} conferem o vínculo dele com a residência do dispositivo
 * (consultar: qualquer vinculado; cadastrar, alterar e simular status: só o titular). As variantes
 * só
 * com o id do dispositivo agem em nome do titular, que é resolvido internamente.</p>
 */
@Service
public class DispositivoService {

    private final DispositivoDAO dispositivoDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;

    public DispositivoService() {
        this.dispositivoDAO = new DispositivoDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
    }

    /**
     * Cadastra um dispositivo e o associa à residência informada. Nasce ATIVO, com sensor OK e
     * energia
     * NORMAL (os status nunca vêm do cliente). Série ou MAC já cadastrados não são aceitos de novo,
     * nem para outra residência.
     *
     * @throws ResponseStatusException 403 se não for o titular da residência
     * @throws IllegalArgumentException se os dados são inválidos ou série/MAC já existem
     * @throws IllegalStateException se o banco não gravar o dispositivo
     */
    public DispositivoResponseDTO cadastrar(int idMorador, DispositivoRequestDTO dto) {
        validar(dto);
        exigirTitular(idMorador, dto.idResidencia());

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

    /**
     * Consulta o dispositivo, com os status de dispositivo, sensor e energia. Qualquer morador
     * vinculado
     * à residência pode.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se o dispositivo não existe
     */
    public DispositivoResponseDTO buscarPorId(int idMorador, int idDispositivo) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);
        exigirAcesso(idMorador, dispositivo);
        return DispositivoMapper.toDto(dispositivo);
    }

    /**
     * Atualiza série, MAC, modelo e data de instalação. A residência do dispositivo não muda. Só o
     * titular.
     *
     * @throws ResponseStatusException 403 se não for o titular
     * @throws IllegalArgumentException se o dispositivo não existe, os dados são inválidos ou
     *     série/MAC já existem
     */
    public DispositivoResponseDTO atualizar(int idMorador, int idDispositivo, DispositivoRequestDTO dto) {
        Dispositivo existente = buscarExistente(idDispositivo);
        exigirTitular(idMorador, existente.getResidencia().getIdResidencia());
        validar(dto);

        Dispositivo dispositivo = DispositivoMapper.dtoToEntity(dto);
        normalizar(dispositivo);
        exigirSerieEMacLivres(dispositivo, idDispositivo);

        dispositivo.setIdDispositivo(idDispositivo);
        dispositivoDAO.alterar(dispositivo);
        return DispositivoMapper.toDto(buscarExistente(idDispositivo));
    }

    /**
     * Altera o status simulado (dispositivo, sensor e/ou energia). Só o titular. Os campos ausentes
     * ficam como estão, mas ao menos um precisa vir.
     *
     * @throws ResponseStatusException 403 se não for o titular
     * @throws IllegalArgumentException se o dispositivo não existe ou nenhum status foi informado
     */
    public DispositivoResponseDTO alterarStatus(int idMorador, int idDispositivo, DispositivoStatusRequestDTO dto) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);
        exigirTitular(idMorador, dispositivo.getResidencia().getIdResidencia());

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

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public DispositivoResponseDTO buscarPorId(int idDispositivo) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);
        int idMorador = moradorResidenciaDAO.buscarIdTitular(dispositivo.getResidencia().getIdResidencia());
        return buscarPorId(idMorador, idDispositivo);
    }

    /**
     * Variante de {@link #atualizar(int, int, DispositivoRequestDTO)} sem {@code idMorador}: age em
     * nome
     * do titular.
     */
    public DispositivoResponseDTO atualizar(int idDispositivo, DispositivoRequestDTO dto) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);
        int idMorador = moradorResidenciaDAO.buscarIdTitular(dispositivo.getResidencia().getIdResidencia());
        return atualizar(idMorador, idDispositivo, dto);
    }

    /**
     * Variante de {@link #alterarStatus(int, int, DispositivoStatusRequestDTO)} sem {@code
     * idMorador}: age
     * em nome do titular.
     */
    public DispositivoResponseDTO alterarStatus(int idDispositivo, DispositivoStatusRequestDTO dto) {
        Dispositivo dispositivo = buscarExistente(idDispositivo);
        int idMorador = moradorResidenciaDAO.buscarIdTitular(dispositivo.getResidencia().getIdResidencia());
        return alterarStatus(idMorador, idDispositivo, dto);
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

    /** Confere se o morador tem vínculo com a residência do dispositivo; senão 403. */
    private MoradorResidencia exigirAcesso(int idMorador, Dispositivo dispositivo) {
        return exigirVinculo(idMorador, dispositivo.getResidencia().getIdResidencia());
    }

    /** Devolve o vínculo do morador com a residência; senão 403. */
    private MoradorResidencia exigirVinculo(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a este dispositivo.");
        }
        return vinculo;
    }

    /** Como {@link #exigirVinculo}, mas o vínculo precisa ser de titular. */
    private void exigirTitular(int idMorador, int idResidencia) {
        if (!exigirVinculo(idMorador, idResidencia).isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o titular da residência pode cadastrar, alterar ou simular o status do dispositivo.");
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