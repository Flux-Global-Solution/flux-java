package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.ContatoConfiancaRequestDTO;
import br.com.fiap.flux.service.ContatoConfiancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/** Contatos de confiança: pessoas ou associações que recebem avisos sem ter conta no aplicativo. */
@CrossOrigin("*")
@RestController
@RequestMapping("/contatos-confianca")
public class ContatoConfiancaController {

    private final ContatoConfiancaService service = new ContatoConfiancaService();

    /** Cadastra um contato de confiança. */
    @PostMapping("/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador, @RequestBody ContatoConfiancaRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(idMorador, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar o contato");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o contato: " + e.getMessage());
        }
    }

    /** Lista os contatos que têm autorização em alguma residência do morador. */
    @GetMapping("/morador/{idMorador}")
    public ResponseEntity<?> listar(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.listar(idMorador));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar os contatos");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar os contatos: " + e.getMessage());
        }
    }

    /** Consulta um contato. */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar o contato");
        }
    }

    /** Atualiza os dados do contato. */
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id, @RequestBody ContatoConfiancaRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao atualizar o contato");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao atualizar o contato: " + e.getMessage());
        }
    }

    /** Exclui o contato, se ele não tiver autorização concedida ou revogada nem notificações (409 se tiver). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable int id) {
        try {
            service.excluir(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao excluir o contato");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao excluir o contato: " + e.getMessage());
        }
    }

    /** Recurso inexistente vira 404; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        }
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}