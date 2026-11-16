package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.MoradorRequestDTO;
import br.com.fiap.flux.dto.MoradorResponseDTO;
import br.com.fiap.flux.service.MoradorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Conta do morador: cadastro, login, dados, senha e desativação. Não há sessão nem autenticação
 * obrigatória: as operações identificam o morador pelo {@code id} da URL.
 *
 * <p>Respostas de erro: 404 para morador inexistente, 409 para e-mail já cadastrado, 401 para
 * credenciais inválidas no login, 400 para validação e 500 para falha interna.</p>
 */
@CrossOrigin("*")
@RestController
public class MoradorController {
    private final MoradorService service;

    /** Corpo do login. */
    public record LoginRequest(String email, String senha) {}
    /** Corpo da troca de senha: a senha atual e a nova. */
    public record AlterarSenhaRequest(String senhaAtual, String novaSenha) {}
    /** Corpo da desativação: a senha, como confirmação. */
    public record DesativarContaRequest(String senha) {}
    /** Resposta do cadastro: o id do morador criado. */
    public record CadastroResponse(int idMorador) {}

    public MoradorController() {
        this.service = new MoradorService();
    }

    /** Cadastra um morador (conta ativa) e devolve o id gerado. */
    @PostMapping("/moradores")
    public ResponseEntity<?> cadastrar(@RequestBody MoradorRequestDTO morador) {
        try {
            int id = service.cadastrar(morador);
            return ResponseEntity.status(HttpStatus.CREATED).body(new CadastroResponse(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar o morador");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o morador: " + e.getMessage());
        }
    }

    /**
     * Valida e-mail e senha e devolve os dados do morador. Não cria sessão, cookie nem token para
     * as
     * demais rotas.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest login) {
        try {
            MoradorResponseDTO morador = service.login(login.email(), login.senha());
            return ResponseEntity.ok(morador);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    /**
     * Encerra a sessão. Como o login não cria nada no servidor, não há o que invalidar: o cliente
     * descarta
     * os dados do morador e a resposta é só a confirmação.
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    /** Consulta os dados do morador, sem senha. */
    @GetMapping("/moradores/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar o morador");
        }
    }

    /** Atualiza nome, e-mail e telefone; a senha enviada aqui é ignorada. */
    @PutMapping("/moradores/{id}")
    public ResponseEntity<String> alterar(
            @PathVariable int id,
            @RequestBody MoradorRequestDTO objeto) {
        try {
            service.alterar(objeto, id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao alterar o morador");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao alterar o morador: " + e.getMessage());
        }
    }

    /** Troca a senha, exigindo a senha atual correta. */
    @PutMapping("/moradores/{id}/senha")
    public ResponseEntity<String> alterarSenha(
            @PathVariable int id,
            @RequestBody AlterarSenhaRequest dto) {
        try {
            service.alterarSenha(id, dto.senhaAtual(), dto.novaSenha());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao alterar a senha");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao alterar a senha: " + e.getMessage());
        }
    }

    /**
     * Desativa a conta ({@code INATIVA}) depois de confirmar a senha; a conta e o histórico não são
     * apagados.
     */
    @PutMapping("/moradores/{id}/desativar")
    public ResponseEntity<String> desativarConta(
            @PathVariable int id,
            @RequestBody DesativarContaRequest dto) {
        try {
            service.desativarConta(id, dto.senha());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao desativar a conta");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao desativar a conta: " + e.getMessage());
        }
    }

    /**
     * Recurso inexistente vira 404; repetição vira 409; qualquer outra falha de validação vira 400.
     */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        }
        if (mensagem != null && mensagem.contains("já cadastrado")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensagem);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(contexto + ": " + mensagem);
    }
}