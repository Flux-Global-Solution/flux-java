package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dao.ResidenciaDAO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.dto.ResidenciaRequestDTO;
import br.com.fiap.flux.dto.ResidenciaResponseDTO;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.mapper.DispositivoMapper;
import br.com.fiap.flux.mapper.MoradorResidenciaMapper;
import br.com.fiap.flux.mapper.ResidenciaMapper;
import br.com.fiap.flux.models.Dispositivo;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Cadastro e gerenciamento de residências. No cadastro, o {@code idMorador} informado pela API vira o
 * titular da residência.
 */
@Service
public class ResidenciaService {

    private final ResidenciaDAO residenciaDAO;
    private final MoradorDAO moradorDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final DispositivoDAO dispositivoDAO;

    public ResidenciaService() {
        this.residenciaDAO = new ResidenciaDAO();
        this.moradorDAO = new MoradorDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.dispositivoDAO = new DispositivoDAO();
    }

    /**
     * Cadastra a residência e já vincula o morador informado como titular, em uma única transação.
     *
     * @return a residência criada, com a data de cadastro definida pelo banco
     * @throws IllegalArgumentException se o morador não existe ou os dados são inválidos
     * @throws IllegalStateException se o banco não gravar a residência
     */
    public ResidenciaResponseDTO cadastrar(int idMorador, ResidenciaRequestDTO dto) {
        validar(dto);
        verificarMorador(idMorador);

        Residencia residencia = ResidenciaMapper.dtoToEntity(dto);
        limpar(residencia);
        residenciaDAO.cadastrarComTitular(residencia, idMorador);
        if (residencia.getIdResidencia() == 0) {
            throw new IllegalStateException("Não foi possível gravar a residência.");
        }
        return ResidenciaMapper.toDto(
                residenciaDAO.buscarPorId(residencia.getIdResidencia()), TipoVinculoEnum.TITULAR);
    }

    /** Lista as residências do morador, cada uma com o tipo de vínculo dele. */
    public List<ResidenciaResponseDTO> listar(int idMorador) {
        return residenciaDAO.listarPorMorador(idMorador)
                .stream()
                .map(r -> ResidenciaMapper.toDto(r.residencia(), r.tipoVinculo()))
                .toList();
    }

    /** Consulta a residência. */
    public ResidenciaResponseDTO buscarPorId(int idResidencia) {
        return ResidenciaMapper.toDto(buscarExistente(idResidencia), TipoVinculoEnum.TITULAR);
    }

    /**
     * Atualiza as configurações da residência (endereço, moradores, ausência, limites e período de menor
     * consumo). A data de cadastro nunca muda.
     */
    public ResidenciaResponseDTO atualizar(int idResidencia, ResidenciaRequestDTO dto) {
        Residencia existente = buscarExistente(idResidencia);
        validar(dto);

        // O DTO não carrega o id nem a data de cadastro: o id vem da URL e a data nunca muda.
        Residencia residencia = ResidenciaMapper.dtoToEntity(dto);
        limpar(residencia);
        residencia.setIdResidencia(idResidencia);
        residencia.setDtCadastro(existente.getDtCadastro());
        residenciaDAO.alterar(residencia);

        return ResidenciaMapper.toDto(buscarExistente(idResidencia), TipoVinculoEnum.TITULAR);
    }

    /**
     * Exclui a residência, só enquanto ela não tem dispositivo, leituras, alertas (com as
     * notificações), perfil de consumo nem autorizações de contatos. Para quem já tem histórico a
     * política de exclusão ainda não foi definida, então a exclusão é recusada (409).
     */
    public void excluir(int idResidencia) {
        buscarExistente(idResidencia);

        if (residenciaDAO.possuiHistorico(idResidencia)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta residência possui dispositivo, leituras, alertas, perfil de consumo ou autorizações de contatos e não pode "
                            + "ser excluída: a política de exclusão desse histórico ainda não foi definida.");
        }
        residenciaDAO.excluir(idResidencia);
    }

    /** Lista os moradores vinculados à residência, o titular primeiro. */
    public List<MoradorResidenciaResponseDTO> listarMoradores(int idResidencia) {
        buscarExistente(idResidencia);
        return moradorResidenciaDAO.listarPorResidencia(idResidencia)
                .stream()
                .map(MoradorResidenciaMapper::toDto)
                .toList();
    }

    /**
     * Consulta o dispositivo instalado na residência.
     *
     * @throws IllegalArgumentException se a residência não tem dispositivo
     */
    public DispositivoResponseDTO buscarDispositivo(int idResidencia) {
        buscarExistente(idResidencia);
        Dispositivo dispositivo = dispositivoDAO.buscarPorResidencia(idResidencia);
        if (dispositivo == null) {
            throw new IllegalArgumentException("Dispositivo não encontrado para esta residência");
        }
        return DispositivoMapper.toDto(dispositivo);
    }

    private void verificarMorador(int idMorador) {
        if (moradorDAO.buscarPorId(idMorador) == null) {
            throw new IllegalArgumentException("Morador não encontrado");
        }
    }

    /** Busca a residência ou lança "não encontrada". */
    private Residencia buscarExistente(int idResidencia) {
        Residencia residencia = residenciaDAO.buscarPorId(idResidencia);
        if (residencia == null) {
            throw new IllegalArgumentException("Residência não encontrada");
        }
        return residencia;
    }

    /** Tira os espaços das pontas; período de ausência em branco vira nulo (é opcional). */
    private void limpar(Residencia residencia) {
        residencia.setEndereco(residencia.getEndereco().trim());
        String periodo = residencia.getPeriodoAusencia();
        residencia.setPeriodoAusencia(periodo == null || periodo.isBlank() ? null : periodo.trim());
    }

    /**
     * Confere endereço, quantidade de moradores (mínimo 1), tamanhos máximos, limites positivos e o
     * período de menor consumo (os dois horários são obrigatórios e diferentes).
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validar(ResidenciaRequestDTO dto) {
        if (dto.endereco() == null || dto.endereco().isBlank()) {
            throw new IllegalArgumentException("O endereço é obrigatório.");
        }
        if (dto.endereco().trim().length() > 200) {
            throw new IllegalArgumentException("O endereço deve ter no máximo 200 caracteres.");
        }
        if (dto.qtdMoradores() == null || dto.qtdMoradores() < 1) {
            throw new IllegalArgumentException("A quantidade de moradores deve ser de pelo menos 1.");
        }
        if (dto.periodoAusencia() != null && dto.periodoAusencia().trim().length() > 200) {
            throw new IllegalArgumentException("O período de ausência deve ter no máximo 200 caracteres.");
        }
        if (dto.limitePontoAberto() != null && dto.limitePontoAberto() <= 0) {
            throw new IllegalArgumentException("O limite de ponto aberto deve ser maior que zero.");
        }
        if (dto.limiteVazamentoMin() != null && dto.limiteVazamentoMin() <= 0) {
            throw new IllegalArgumentException("O limite de vazamento deve ser maior que zero.");
        }
        if (dto.inicioMenorConsumo() == null || dto.fimMenorConsumo() == null) {
            throw new IllegalArgumentException("Os horários de início e fim do menor consumo são obrigatórios.");
        }
        if (dto.inicioMenorConsumo().equals(dto.fimMenorConsumo())) {
            throw new IllegalArgumentException("O início e o fim do menor consumo não podem ser iguais.");
        }
    }
}