package br.com.fiap.flux.service;

import br.com.fiap.flux.dao.MoradorDAO;
import br.com.fiap.flux.dto.MoradorRequestDTO;
import br.com.fiap.flux.dto.MoradorResponseDTO;
import br.com.fiap.flux.enums.StatusContaEnum;
import br.com.fiap.flux.mapper.MoradorMapper;
import br.com.fiap.flux.models.Morador;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cadastro e conta do morador: cadastro, login, alteração de dados e senha e desativação. A senha é
 * sempre guardada como hash e o status da conta nunca vem do cliente.
 *
 * <p>Não há sessão, cookie nem token: o login só confere as credenciais e devolve os dados do
 * morador, e as demais operações identificam o morador pelo id informado.</p>
 */
@Service
public class MoradorService {

    private final MoradorDAO moradorDAO;

    public MoradorService() {
        this.moradorDAO = new MoradorDAO();
    }

    /**
     * Cadastra o morador (conta sempre ATIVA, senha gravada como hash) e devolve o id gerado.
     *
     * @throws IllegalArgumentException se os dados são inválidos ou o e-mail já está cadastrado
     * @throws IllegalStateException se o banco não gravar o morador
     */
    public int cadastrar(MoradorRequestDTO dto) {
        validarDadosPessoais(dto);
        validarSenha(dto.senha());

        String email = dto.email().trim().toLowerCase();
        if (moradorDAO.buscarPorEmail(email) != null) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        Morador morador = MoradorMapper.dtoToEntity(dto);
        morador.setNome(dto.nome().trim());
        morador.setEmail(email);
        morador.setTelefone(dto.telefone().trim());
        morador.definirSenha(dto.senha());                 // o mapper copia a senha pura; aqui vira hash
        morador.setStatusConta(StatusContaEnum.ATIVA);     // o status nunca vem do cliente

        moradorDAO.cadastrar(morador);
        if (morador.getIdMorador() == 0) {
            throw new IllegalStateException("Não foi possível gravar o morador.");
        }
        return morador.getIdMorador();
    }

    /**
     * Confere e-mail e senha; só entra quem tem a conta ATIVA.
     *
     * @throws IllegalArgumentException se faltam credenciais ou elas não conferem
     * @throws ResponseStatusException 403 se a conta não está ativa
     */
    public MoradorResponseDTO login(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }
        Morador morador = moradorDAO.buscarPorEmail(email.trim());
        if (morador == null || !morador.senhaConfere(senha)) {
            throw new IllegalArgumentException("Credenciais inválidas.");
        }
        if (!morador.estaAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta não está ativa.");
        }
        return MoradorMapper.toDto(morador);
    }

    /**
     * Consulta o morador, sem senha nem hash.
     *
     * @throws IllegalArgumentException se o morador não existe
     */
    public MoradorResponseDTO buscarPorId(int id) {
        return MoradorMapper.toDto(buscarExistente(id));
    }

    /**
     * Atualiza nome, e-mail e telefone. A senha e o status da conta não mudam por aqui.
     *
     * @throws IllegalArgumentException se o morador não existe, os dados são inválidos ou o e-mail
     *     é de outra conta
     */
    public void alterar(MoradorRequestDTO dto, int id) {
        Morador existente = buscarExistente(id);
        validarDadosPessoais(dto);

        String email = dto.email().trim().toLowerCase();
        Morador outro = moradorDAO.buscarPorEmail(email);
        if (outro != null && outro.getIdMorador() != id) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        Morador morador = MoradorMapper.dtoToEntity(dto);
        morador.setIdMorador(id);
        morador.setNome(dto.nome().trim());
        morador.setEmail(email);
        morador.setTelefone(dto.telefone().trim());
        morador.setHashSenha(existente.getHashSenha());
        morador.setStatusConta(existente.getStatusConta());
        moradorDAO.alterar(morador);
    }

    /**
     * Troca a senha, exigindo a senha atual correta.
     *
     * @throws IllegalArgumentException se a senha atual está ausente ou errada, ou a nova é
     *     inválida
     */
    public void alterarSenha(int id, String senhaAtual, String novaSenha) {
        Morador existente = buscarExistente(id);
        if (senhaAtual == null || senhaAtual.isBlank()) {
            throw new IllegalArgumentException("A senha atual é obrigatória.");
        }
        validarSenha(novaSenha);
        if (!existente.senhaConfere(senhaAtual)) {
            throw new IllegalArgumentException("Senha atual incorreta.");
        }
        existente.definirSenha(novaSenha);
        moradorDAO.alterarSenha(id, existente.getHashSenha());
    }

    /**
     * Desativa a conta ({@code ATIVA -> INATIVA}) depois de confirmar a senha. Nada é apagado: a
     * conta só
     * deixa de conseguir entrar. Reativar não é tratado aqui.
     *
     * @throws IllegalArgumentException se a senha está ausente ou errada
     * @throws ResponseStatusException 409 se a conta já não está ativa
     */
    public void desativarConta(int id, String senha) {
        Morador existente = buscarExistente(id);
        if (senha == null || senha.isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória para desativar a conta.");
        }
        if (!existente.senhaConfere(senha)) {
            throw new IllegalArgumentException("Senha incorreta.");
        }
        if (!existente.estaAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A conta não está ativa.");
        }
        moradorDAO.alterarStatus(id, StatusContaEnum.INATIVA);
    }

    /**
     * Confirma que o morador informado pela API existe e tem a conta ativa. Não autentica quem fez
     * a
     * requisição: só valida o id recebido.
     *
     * @return o próprio {@code idMorador}
     * @throws ResponseStatusException 400 se o id é inválido, 404 se não existe, 403 se a conta não
     *     está ativa
     */
    public int validarMoradorAtivo(int idMorador) {
        if (idMorador <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Informe um idMorador válido.");
        }

        Morador morador = moradorDAO.buscarPorId(idMorador);
        if (morador == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Morador não encontrado.");
        }
        if (!morador.estaAtivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta não está ativa.");
        }
        return idMorador;
    }

    /** Busca o morador ou lança "não encontrado". */
    private Morador buscarExistente(int id) {
        Morador morador = moradorDAO.buscarPorId(id);
        if (morador == null) {
            throw new IllegalArgumentException("Morador não encontrado");
        }
        return morador;
    }

    /**
     * Confere nome, e-mail (formato) e telefone: obrigatórios e dentro dos tamanhos das colunas.
     *
     * @throws IllegalArgumentException na primeira regra violada
     */
    private void validarDadosPessoais(MoradorRequestDTO dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            System.out.println("ERRO: Nome é obrigatório");
            throw new IllegalArgumentException("Nome é obrigatório.");
        }
        if (dto.nome().trim().length() > 100) {
            throw new IllegalArgumentException("O nome deve ter no máximo 100 caracteres.");
        }
        if (dto.email() == null || !dto.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            System.out.println("ERRO: E-mail inválido");
            throw new IllegalArgumentException("E-mail inválido.");
        }
        if (dto.email().trim().length() > 120) {
            throw new IllegalArgumentException("O e-mail deve ter no máximo 120 caracteres.");
        }
        if (dto.telefone() == null || dto.telefone().isBlank()) {
            System.out.println("ERRO: Telefone é obrigatório");
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        if (dto.telefone().trim().length() > 15) {
            throw new IllegalArgumentException("O telefone deve ter no máximo 15 caracteres.");
        }
    }

    /** A senha é obrigatória. */
    private void validarSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            System.out.println("ERRO: Senha é obrigatória");
            throw new IllegalArgumentException("Senha é obrigatória.");
        }
    }
}