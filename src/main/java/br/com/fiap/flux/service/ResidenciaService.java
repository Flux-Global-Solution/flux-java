package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.DispositivoDAO;
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
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Cadastro e gerenciamento de residências. O {@code idMorador} é informado pela API: no cadastro
 * ele
 * vira o titular e, nas demais operações, é o vínculo existente que decide quem pode consultar
 * (qualquer vinculado) ou alterar (só o titular). Residência de terceiros e residência inexistente
 * respondem igual (403), para não revelar quais ids existem.
 *
 * <p>As variantes só com o id da residência agem em nome do titular, que é resolvido
 * internamente.</p>
 */
@Service
public class ResidenciaService {

    private final ResidenciaDAO residenciaDAO;
    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final DispositivoDAO dispositivoDAO;

    public ResidenciaService() {
        this.residenciaDAO = new ResidenciaDAO();
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.dispositivoDAO = new DispositivoDAO();
    }

    /**
     * Cadastra a residência e já vincula o morador informado como titular, em uma única transação.
     *
     * @return a residência criada, com a data de cadastro definida pelo banco
     * @throws IllegalArgumentException se os dados são inválidos
     * @throws IllegalStateException se o banco não gravar a residência
     */
    public ResidenciaResponseDTO cadastrar(int idMorador, ResidenciaRequestDTO dto) {
        validar(dto);

        Residencia residencia = ResidenciaMapper.dtoToEntity(dto);
        limpar(residencia);
        residenciaDAO.cadastrarComTitular(residencia, idMorador);
        if (residencia.getIdResidencia() == 0) {
            throw new IllegalStateException("Não foi possível gravar a residência.");
        }
        return ResidenciaMapper.toDto(
                residenciaDAO.buscarPorId(residencia.getIdResidencia()), TipoVinculoEnum.TITULAR);
    }

    /**
     * Lista as residências às quais o morador tem acesso (titular ou morador comum), cada uma com o
     * tipo
     * de vínculo dele.
     */
    public List<ResidenciaResponseDTO> listar(int idMorador) {
        return residenciaDAO.listarPorMorador(idMorador)
                .stream()
                .map(r -> ResidenciaMapper.toDto(r.residencia(), r.tipoVinculo()))
                .toList();
    }

    /**
     * Consulta a residência. Qualquer morador vinculado pode.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     */
    public ResidenciaResponseDTO buscarPorId(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = exigirAcesso(idMorador, idResidencia);
        return ResidenciaMapper.toDto(buscarExistente(idResidencia), vinculo.getTipoVinculo());
    }

    /**
     * Atualiza as configurações da residência (endereço, moradores, ausência, limites e período de
     * menor
     * consumo). A data de cadastro nunca muda. Só o titular.
     *
     * @throws ResponseStatusException 403 se não for o titular
     * @throws IllegalArgumentException se os dados são inválidos
     */
    public ResidenciaResponseDTO atualizar(int idMorador, int idResidencia, ResidenciaRequestDTO dto) {
        MoradorResidencia vinculo = exigirTitular(idMorador, idResidencia);
        Residencia existente = buscarExistente(idResidencia);
        validar(dto);

        // O DTO não carrega o id nem a data de cadastro: o id vem da URL e a data nunca muda.
        Residencia residencia = ResidenciaMapper.dtoToEntity(dto);
        limpar(residencia);
        residencia.setIdResidencia(idResidencia);
        residencia.setDtCadastro(existente.getDtCadastro());
        residenciaDAO.alterar(residencia);

        return ResidenciaMapper.toDto(buscarExistente(idResidencia), vinculo.getTipoVinculo());
    }

    /**
     * Exclui a residência. Só o titular pode, e só enquanto ela não tem dispositivo, leituras,
     * alertas
     * (com as notificações), perfil de consumo nem autorizações de contatos. Para quem já tem
     * histórico
     * a política de exclusão ainda não foi definida, então a exclusão é recusada.
     *
     * @throws ResponseStatusException 403 se não for o titular; 409 se a residência já tem
     *     histórico
     */
    public void excluir(int idMorador, int idResidencia) {
        exigirTitular(idMorador, idResidencia);
        buscarExistente(idResidencia);

        if (residenciaDAO.possuiHistorico(idResidencia)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta residência possui dispositivo, leituras, alertas, perfil de consumo ou autorizações de contatos e não pode "
                            + "ser excluída: a política de exclusão desse histórico ainda não foi definida.");
        }
        residenciaDAO.excluir(idResidencia);
    }

    /**
     * Lista os moradores vinculados à residência, o titular primeiro. Qualquer vinculado pode ver.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     */
    public List<MoradorResidenciaResponseDTO> listarMoradores(int idMorador, int idResidencia) {
        exigirAcesso(idMorador, idResidencia);
        return moradorResidenciaDAO.listarPorResidencia(idResidencia)
                .stream()
                .map(MoradorResidenciaMapper::toDto)
                .toList();
    }

    /**
     * Consulta o dispositivo instalado na residência. Qualquer morador vinculado pode.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     * @throws IllegalArgumentException se a residência não tem dispositivo
     */
    public DispositivoResponseDTO buscarDispositivo(int idMorador, int idResidencia) {
        exigirAcesso(idMorador, idResidencia);
        Dispositivo dispositivo = dispositivoDAO.buscarPorResidencia(idResidencia);
        if (dispositivo == null) {
            throw new IllegalArgumentException("Dispositivo não encontrado para esta residência");
        }
        return DispositivoMapper.toDto(dispositivo);
    }

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public ResidenciaResponseDTO buscarPorId(int idResidencia) {
        return buscarPorId(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /**
     * Variante de {@link #atualizar(int, int, ResidenciaRequestDTO)} sem {@code idMorador}: age em
     * nome
     * do titular.
     */
    public ResidenciaResponseDTO atualizar(int idResidencia, ResidenciaRequestDTO dto) {
        return atualizar(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia, dto);
    }

    /** Variante de {@link #excluir(int, int)} sem {@code idMorador}: age em nome do titular. */
    public void excluir(int idResidencia) {
        excluir(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /**
     * Variante de {@link #listarMoradores(int, int)} sem {@code idMorador}: age em nome do titular.
     */
    public List<MoradorResidenciaResponseDTO> listarMoradores(int idResidencia) {
        return listarMoradores(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }

    /**
     * Variante de {@link #buscarDispositivo(int, int)} sem {@code idMorador}: age em nome do
     * titular.
     */
    public DispositivoResponseDTO buscarDispositivo(int idResidencia) {
        return buscarDispositivo(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia);
    }


    /**
     * Confere se o morador tem vínculo com a residência. Residência de terceiros e inexistente
     * respondem
     * igual (403), para não revelar quais ids existem.
     *
     * @throws ResponseStatusException 403 se não houver vínculo
     */
    private MoradorResidencia exigirAcesso(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a esta residência.");
        }
        return vinculo;
    }

    /** Como {@link #exigirAcesso}, mas o vínculo precisa ser de titular. */
    private MoradorResidencia exigirTitular(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = exigirAcesso(idMorador, idResidencia);
        if (!vinculo.isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o titular pode alterar ou excluir a residência.");
        }
        return vinculo;
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
            System.out.println("ERRO: O endereço é obrigatório");
            throw new IllegalArgumentException("O endereço é obrigatório.");
        }
        if (dto.endereco().trim().length() > 200) {
            throw new IllegalArgumentException("O endereço deve ter no máximo 200 caracteres.");
        }
        if (dto.qtdMoradores() == null || dto.qtdMoradores() < 1) {
            System.out.println("ERRO: A quantidade de moradores deve ser de pelo menos 1");
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
            System.out.println("ERRO: Os horários de início e fim do menor consumo são obrigatórios");
            throw new IllegalArgumentException("Os horários de início e fim do menor consumo são obrigatórios.");
        }
        if (dto.inicioMenorConsumo().equals(dto.fimMenorConsumo())) {
            throw new IllegalArgumentException("O início e o fim do menor consumo não podem ser iguais.");
        }
    }
}