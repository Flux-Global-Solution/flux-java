package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.NotificacaoRequestDTO;
import br.com.fiap.flux.dto.NotificacaoStatusRequestDTO;
import br.com.fiap.flux.enums.StatusEnvioEnum;
import br.com.fiap.flux.service.NotificacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Notificações dos alertas, sempre simuladas: nada é enviado, só o status e a data de envio são registrados. */
@CrossOrigin("*")
@RestController
public class NotificacaoController {

    private final NotificacaoService service = new NotificacaoService();

    /** Lista as notificações visíveis ao morador, com filtro opcional de status de envio. */
    @GetMapping("/notificacoes/morador/{idMorador}")
    public ResponseEntity<?> listar(@PathVariable int idMorador,
                                    @RequestParam(required = false) StatusEnvioEnum status) {
        try {
            return ResponseEntity.ok(service.listar(idMorador, status));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as notificações");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as notificações: " + e.getMessage());
        }
    }

    /** Lista as notificações pendentes visíveis ao morador. */
    @GetMapping("/notificacoes/pendentes/morador/{idMorador}")
    public ResponseEntity<?> listarPendentes(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.listarPendentes(idMorador));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as notificações pendentes");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as notificações pendentes: " + e.getMessage());
        }
    }

    /** Consulta uma notificação. */
    @GetMapping("/notificacoes/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar a notificação");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar a notificação: " + e.getMessage());
        }
    }

    /** Lista as notificações de um alerta. */
    @GetMapping("/alertas/{id}/notificacoes")
    public ResponseEntity<?> listarPorAlerta(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.listarPorAlerta(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as notificações do alerta");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as notificações do alerta: " + e.getMessage());
        }
    }

    /** Registra uma notificação pendente de um alerta para um morador ou um contato. */
    @PostMapping("/notificacoes/morador/{idMorador}")
    public ResponseEntity<?> registrar(@PathVariable int idMorador, @RequestBody NotificacaoRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(idMorador, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao registrar a notificação");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao registrar a notificação: " + e.getMessage());
        }
    }

    /** Atualiza o status de envio (simulado), se a passagem for permitida. */
    @PutMapping("/notificacoes/{id}/status")
    public ResponseEntity<?> atualizarStatus(@PathVariable int id, @RequestBody NotificacaoStatusRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizarStatus(id, dto != null ? dto.status() : null));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao atualizar o status da notificação");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao atualizar o status da notificação: " + e.getMessage());
        }
    }

    /** Recurso inexistente vira 404; notificação repetida vira 409; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        }
        if (mensagem != null && mensagem.contains("Já existe")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensagem);
        }
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}