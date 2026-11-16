package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dao.MoradorResidenciaDAO;
import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.dto.VinculoRequestDTO;
import br.com.fiap.flux.enums.TipoVinculoEnum;
import br.com.fiap.flux.mapper.MoradorResidenciaMapper;
import br.com.fiap.flux.models.Morador;
import br.com.fiap.flux.models.MoradorResidencia;
import br.com.fiap.flux.models.Residencia;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Compartilhamento de acesso a uma residência: vincular e remover moradores. Não se confunde com os
 * contatos de confiança (pessoas sem conta, que só recebem avisos). Contas novas nunca são criadas
 * aqui: o morador precisa já existir.
 *
 * <p>As variantes só com o id da residência ou do vínculo agem em nome do titular, que é resolvido
 * internamente.</p>
 */
@Service
public class VinculoService {

    private final MoradorResidenciaDAO moradorResidenciaDAO;
    private final MoradorDAO moradorDAO;

    public VinculoService() {
        this.moradorResidenciaDAO = new MoradorResidenciaDAO();
        this.moradorDAO = new MoradorDAO();
    }

    /**
     * Vincula à residência o morador dono do e-mail informado, como morador comum (é o tipo
     * assumido
     * quando {@code tipoVinculo} não vem). Só o titular pode. Recusa conta inexistente ou inativa,
     * vínculo
     * repetido e um segundo titular (a residência tem um só, definido no cadastro).
     *
     * @return o vínculo criado, com os dados do morador
     * @throws ResponseStatusException 403 se não for o titular; 409 se pediu um segundo titular
     * @throws IllegalArgumentException se o e-mail é inválido ou sem conta ativa, ou o vínculo já
     *     existe
     * @throws IllegalStateException se o banco não gravar o vínculo
     */
    public MoradorResidenciaResponseDTO vincular(int idMorador, int idResidencia, VinculoRequestDTO dto) {
        exigirTitular(idMorador, idResidencia);
        validar(dto);

        TipoVinculoEnum tipo = dto.tipoVinculo() != null ? dto.tipoVinculo() : TipoVinculoEnum.MORADOR_COMUM;
        if (tipo == TipoVinculoEnum.TITULAR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A residência já possui um titular.");
        }

        Morador morador = moradorDAO.buscarPorEmail(dto.emailMorador().trim().toLowerCase());
        if (morador == null || !morador.estaAtivo()) {
            throw new IllegalArgumentException("Morador não encontrado com este e-mail");
        }
        if (moradorResidenciaDAO.buscarVinculo(morador.getIdMorador(), idResidencia) != null) {
            throw new IllegalArgumentException("Vínculo já cadastrado para este morador nesta residência.");
        }

        Residencia residencia = new Residencia();
        residencia.setIdResidencia(idResidencia);
        MoradorResidencia vinculo = new MoradorResidencia();
        vinculo.setTipoVinculo(tipo);
        vinculo.setMorador(morador);
        vinculo.setResidencia(residencia);
        moradorResidenciaDAO.cadastrar(vinculo);
        if (vinculo.getIdMoradorResidencia() == 0) {
            throw new IllegalStateException("Não foi possível gravar o vínculo.");
        }
        return consultar(vinculo.getIdMoradorResidencia());
    }

    /**
     * Consulta um vínculo. Qualquer morador vinculado à mesma residência pode ver.
     *
     * @throws ResponseStatusException 403 se não houver vínculo com a residência
     * @throws IllegalArgumentException se o vínculo não existe
     */
    public MoradorResidenciaResponseDTO buscarPorId(int idMorador, int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        exigirAcesso(idMorador, vinculo.getResidencia().getIdResidencia());
        return consultar(idVinculo);
    }

    /**
     * Remove um vínculo. Só o titular pode, e só de moradores comuns: o vínculo do titular não sai
     * por
     * aqui, nem pelo próprio titular.
     *
     * @throws ResponseStatusException 403 se não for o titular; 409 se o vínculo é do titular
     * @throws IllegalArgumentException se o vínculo não existe
     */
    public void remover(int idMorador, int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        exigirTitular(idMorador, vinculo.getResidencia().getIdResidencia());

        if (vinculo.isTitular()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O titular não pode ser removido da residência.");
        }
        moradorResidenciaDAO.excluir(idVinculo);
    }

    /**
     * Variante de {@link #vincular(int, int, VinculoRequestDTO)} sem {@code idMorador}: age em nome
     * do
     * titular.
     */
    public MoradorResidenciaResponseDTO vincular(int idResidencia, VinculoRequestDTO dto) {
        return vincular(moradorResidenciaDAO.buscarIdTitular(idResidencia), idResidencia, dto);
    }

    /** Variante de {@link #buscarPorId(int, int)} sem {@code idMorador}: age em nome do titular. */
    public MoradorResidenciaResponseDTO buscarPorId(int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        int idResidencia = vinculo.getResidencia().getIdResidencia();
        return buscarPorId(moradorResidenciaDAO.buscarIdTitular(idResidencia), idVinculo);
    }

    /**
     * Variante de {@link #remover(int, int)} sem {@code idMorador}: age em nome do titular e mantém
     * a
     * regra que impede remover o titular.
     */
    public void remover(int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        int idResidencia = vinculo.getResidencia().getIdResidencia();
        remover(moradorResidenciaDAO.buscarIdTitular(idResidencia), idVinculo);
    }


    /** Monta a resposta do vínculo com os dados do morador (sem senha). */
    private MoradorResidenciaResponseDTO consultar(int idVinculo) {
        MoradorResidencia vinculo = buscarExistente(idVinculo);
        vinculo.setMorador(moradorDAO.buscarPorId(vinculo.getMorador().getIdMorador()));
        return MoradorResidenciaMapper.toDto(vinculo);
    }

    /** Busca o vínculo ou lança "não encontrado". */
    private MoradorResidencia buscarExistente(int idVinculo) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarPorId(idVinculo);
        if (vinculo == null) {
            throw new IllegalArgumentException("Vínculo não encontrado");
        }
        return vinculo;
    }

    /**
     * Confere se o morador tem vínculo com a residência; residência de terceiros ou inexistente:
     * 403.
     */
    private MoradorResidencia exigirAcesso(int idMorador, int idResidencia) {
        MoradorResidencia vinculo = moradorResidenciaDAO.buscarVinculo(idMorador, idResidencia);
        if (vinculo == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem acesso a esta residência.");
        }
        return vinculo;
    }

    /** Como {@link #exigirAcesso}, mas o vínculo precisa ser de titular. */
    private void exigirTitular(int idMorador, int idResidencia) {
        if (!exigirAcesso(idMorador, idResidencia).isTitular()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o titular pode gerenciar os moradores da residência.");
        }
    }

    /**
     * O e-mail do morador a vincular precisa estar num formato válido.
     *
     * @throws IllegalArgumentException se o e-mail é inválido
     */
    private void validar(VinculoRequestDTO dto) {
        if (dto.emailMorador() == null || !dto.emailMorador().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            System.out.println("ERRO: E-mail inválido");
            throw new IllegalArgumentException("E-mail inválido.");
        }
    }
}