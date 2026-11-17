package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.ContatoConfiancaDAO;
import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dto.ContatoConfiancaRequestDTO;
import br.com.fiap.flux.dto.ContatoConfiancaResponseDTO;
import br.com.fiap.flux.mapper.ContatoConfiancaMapper;
import br.com.fiap.flux.models.ContatoConfianca;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Cadastro dos contatos de confiança: pessoas ou associações que recebem avisos sem ter conta nem
 * acesso ao aplicativo (não são moradores). Quem cadastra precisa ser um morador existente.
 *
 * <p>A tabela não guarda quem cadastrou o contato, então o "dono" é quem tem autorizações dele: um
 * contato sem autorização ainda não tem dono (a ligação a uma residência é feita em
 * {@link AutorizacaoContatoService}) e, depois disso, só esse titular o enxerga.</p>
 */
@Service
public class ContatoConfiancaService {

    private final ContatoConfiancaDAO contatoDAO;
    private final MoradorDAO moradorDAO;

    public ContatoConfiancaService() {
        this.contatoDAO = new ContatoConfiancaDAO();
        this.moradorDAO = new MoradorDAO();
    }

    /**
     * Cadastra o contato sem associá-lo a nenhuma residência: a autorização é uma operação à parte.
     *
     * @throws IllegalArgumentException se o morador não existe ou os dados são inválidos
     * @throws IllegalStateException se o banco não gravar o contato
     */
    public ContatoConfiancaResponseDTO cadastrar(int idMorador, ContatoConfiancaRequestDTO dto) {
        verificarMorador(idMorador);
        validar(dto);

        ContatoConfianca contato = ContatoConfiancaMapper.dtoToEntity(dto);
        limpar(contato);
        contatoDAO.cadastrar(contato);
        if (contato.getIdContato() == 0) {
            throw new IllegalStateException("Não foi possível gravar o contato.");
        }
        return ContatoConfiancaMapper.toDto(contato);
    }

    /** Lista os contatos que têm autorização em alguma residência do morador. */
    public List<ContatoConfiancaResponseDTO> listar(int idMorador) {
        return contatoDAO.listarPorMorador(idMorador)
                .stream()
                .map(ContatoConfiancaMapper::toDto)
                .toList();
    }

    /**
     * Consulta um contato.
     *
     * @throws IllegalArgumentException se o contato não existe
     */
    public ContatoConfiancaResponseDTO buscarPorId(int idContato) {
        ContatoConfianca contato = contatoDAO.buscarPorId(idContato);
        if (contato == null) {
            throw new IllegalArgumentException("Contato não encontrado");
        }
        return ContatoConfiancaMapper.toDto(contato);
    }

    /**
     * Atualiza nome, telefone, e-mail e tipo do contato; as autorizações dele não mudam.
     *
     * @throws IllegalArgumentException se o contato não existe ou os dados são inválidos
     */
    public ContatoConfiancaResponseDTO atualizar(int idContato, ContatoConfiancaRequestDTO dto) {
        if (contatoDAO.buscarPorId(idContato) == null) {
            throw new IllegalArgumentException("Contato não encontrado");
        }
        validar(dto);

        ContatoConfianca contato = ContatoConfiancaMapper.dtoToEntity(dto);
        limpar(contato);
        contato.setIdContato(idContato);
        contatoDAO.alterar(contato);
        return ContatoConfiancaMapper.toDto(contato);
    }

    /**
     * Exclui o cadastro do contato respeitando o histórico: autorizações ativas ou revogadas e
     * notificações impedem a exclusão (409). Só as autorizações pendentes, que nunca foram
     * concedidas,
     * saem junto com o contato.
     *
     * @throws ResponseStatusException 409 se o contato tem autorização concedida/revogada ou
     *     notificações
     * @throws IllegalArgumentException se o contato não existe
     */
    public void excluir(int idContato) {
        if (contatoDAO.buscarPorId(idContato) == null) {
            throw new IllegalArgumentException("Contato não encontrado");
        }

        if (contatoDAO.contarAutorizacoesNaoPendentes(idContato) > 0 || contatoDAO.contarNotificacoes(idContato) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O contato possui autorizações concedidas ou revogadas, ou notificações, e não pode ser "
                            + "excluído: o histórico é preservado. Para parar os avisos, revogue a autorização.");
        }
        contatoDAO.excluirComAutorizacoesPendentes(idContato);
    }

    private void verificarMorador(int idMorador) {
        if (moradorDAO.buscarPorId(idMorador) == null) {
            throw new IllegalArgumentException("Morador não encontrado");
        }
    }

    /** Tira os espaços das pontas e deixa o e-mail em minúsculas. */
    private void limpar(ContatoConfianca contato) {
        contato.setNome(contato.getNome().trim());
        contato.setTelefone(contato.getTelefone().trim());
        contato.setEmail(contato.getEmail().trim().toLowerCase());
    }

    /**
     * Confere os campos obrigatórios, os tamanhos máximos das colunas e o formato do e-mail.
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validar(ContatoConfiancaRequestDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new IllegalArgumentException("O nome do contato é obrigatório.");
        }
        if (dto.nome().trim().length() > 100) {
            throw new IllegalArgumentException("O nome deve ter no máximo 100 caracteres.");
        }
        if (dto.telefone() == null || dto.telefone().isBlank()) {
            throw new IllegalArgumentException("O telefone do contato é obrigatório.");
        }
        if (dto.telefone().trim().length() > 15) {
            throw new IllegalArgumentException("O telefone deve ter no máximo 15 caracteres.");
        }
        if (dto.email() == null || !dto.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        if (dto.email().trim().length() > 120) {
            throw new IllegalArgumentException("O e-mail deve ter no máximo 120 caracteres.");
        }
        if (dto.tipoContato() == null) {
            throw new IllegalArgumentException("O tipo de contato é obrigatório (PESSOA ou ASSOCIACAO).");
        }
    }
}