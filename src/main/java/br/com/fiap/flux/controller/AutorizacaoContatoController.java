package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.AutorizacaoContatoRequestDTO;
import br.com.fiap.flux.dto.AutorizacaoContatoResponseDTO;
import br.com.fiap.flux.enums.StatusAutorizacaoEnum;
import br.com.fiap.flux.service.AutorizacaoContatoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Autorizações para contatos de confiança receberem avisos de uma residência. Os endpoints não
 * exigem
 * login. Só a autorização ATIVA gera notificação, e revogar não apaga nada.
 *
 * <p>Respostas de erro: 404 para recurso inexistente, 409 para repetição ou status incompatível,
 * 400
 * para validação e 500 para falha interna.</p>
 */
@CrossOrigin("*")
@RestController
public class AutorizacaoContatoController {

    private final AutorizacaoContatoService service = new AutorizacaoContatoService();

    /**
     * Solicita a autorização de um contato numa residência do morador titular; ela nasce PENDENTE.
     */
    @PostMapping("/autorizacoes/morador/{idMorador}")
    public ResponseEntity<?> solicitar(@PathVariable int idMorador, @RequestBody AutorizacaoContatoRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(idMorador, dto));
        } catch (IllegalArgumentException e) { return erro(e, "Erro ao solicitar a autorização"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Lista as autorizações da residência, com filtro opcional de status. */
    @GetMapping("/residencias/{id}/autorizacoes")
    public ResponseEntity<List<AutorizacaoContatoResponseDTO>> listarPorResidencia(
            @PathVariable int id, @RequestParam(required = false) StatusAutorizacaoEnum status) {
        return ResponseEntity.ok(service.listarPorResidencia(id, status));
    }

    /** Consulta uma autorização. */
    @GetMapping("/autorizacoes/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try { return ResponseEntity.ok(service.buscarPorId(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao buscar a autorização"); }
    }

    /** Ativa a autorização (pendente ou revogada). */
    @PutMapping("/autorizacoes/{id}/ativar")
    public ResponseEntity<?> ativar(@PathVariable int id) {
        try { return ResponseEntity.ok(service.ativar(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao ativar a autorização"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Revoga a autorização; ela continua gravada como histórico. */
    @PutMapping("/autorizacoes/{id}/revogar")
    public ResponseEntity<?> revogar(@PathVariable int id) {
        try { return ResponseEntity.ok(service.revogar(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao revogar a autorização"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /**
     * Recurso inexistente vira 404; repetição vira 409; qualquer outra falha de validação vira 400.
     */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        if (mensagem != null && mensagem.contains("já cadastrad")) return ResponseEntity.status(HttpStatus.CONFLICT).body(mensagem);
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}
