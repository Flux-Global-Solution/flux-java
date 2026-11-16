package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.ContatoConfiancaRequestDTO;
import br.com.fiap.flux.dto.ContatoConfiancaResponseDTO;
import br.com.fiap.flux.service.ContatoConfiancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contatos de confiança: pessoas ou associações que recebem avisos sem ter conta no aplicativo. Os
 * endpoints não exigem login. O contato só é ligado a uma residência pela autorização
 * ({@link AutorizacaoContatoController}).
 *
 * <p>Respostas de erro: 404 para contato inexistente, 409 se o contato tem histórico e não pode ser
 * excluído, 400 para validação e 500 para falha interna.</p>
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/contatos-confianca")
public class ContatoConfiancaController {

    private final ContatoConfiancaService service = new ContatoConfiancaService();

    /** Cadastra um contato. Só quem é titular de alguma residência cadastra. */
    @PostMapping("/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador, @RequestBody ContatoConfiancaRequestDTO dto) {
        try { return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(idMorador, dto)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao cadastrar o contato"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Lista os contatos que têm autorização em alguma residência do morador. */
    @GetMapping("/morador/{idMorador}")
    public ResponseEntity<List<ContatoConfiancaResponseDTO>> listar(@PathVariable int idMorador) {
        return ResponseEntity.ok(service.listar(idMorador));
    }

    /** Consulta um contato. */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try { return ResponseEntity.ok(service.buscarPorId(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao buscar o contato"); }
    }

    /** Atualiza os dados do contato. */
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id, @RequestBody ContatoConfiancaRequestDTO dto) {
        try { return ResponseEntity.ok(service.atualizar(id, dto)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao atualizar o contato"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /**
     * Exclui o contato, se ele não tiver autorização concedida ou revogada nem notificações (409 se
     * tiver).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable int id) {
        try { service.excluir(id); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao excluir o contato"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Recurso inexistente vira 404; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}
