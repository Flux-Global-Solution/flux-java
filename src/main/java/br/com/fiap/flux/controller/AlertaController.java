package br.com.fiap.flux.controller;

import br.com.fiap.flux.enums.StatusAlertaEnum;
import br.com.fiap.flux.enums.TipoAlertaEnum;
import br.com.fiap.flux.enums.TipoGravidadeEnum;
import br.com.fiap.flux.service.AlertaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Ocorrências (alertas de consumo) de uma residência: consulta, histórico e acompanhamento do andamento. */
@CrossOrigin("*")
@RestController
public class AlertaController {

    private final AlertaService service = new AlertaService();

    /** Lista as ocorrências de uma residência. */
    @GetMapping("/residencias/{id}/ocorrencias")
    public ResponseEntity<?> listarPorResidencia(
            @PathVariable int id,
            @RequestParam(required = false) TipoAlertaEnum tipo,
            @RequestParam(required = false) TipoGravidadeEnum gravidade,
            @RequestParam(required = false) StatusAlertaEnum status,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        try {
            return ResponseEntity.ok(service.listarPorResidencia(id, tipo, gravidade, status, inicio, fim));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as ocorrências");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as ocorrências: " + e.getMessage());
        }
    }

    /** Detalhes de uma ocorrência. */
    @GetMapping("/ocorrencias/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar a ocorrência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar a ocorrência: " + e.getMessage());
        }
    }

    /** Histórico da residência: só as ocorrências resolvidas, da mais recente para a mais antiga. */
    @GetMapping("/residencias/{id}/ocorrencias/historico")
    public ResponseEntity<?> listarHistorico(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.listarHistoricoPorResidencia(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao consultar o histórico");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao consultar o histórico: " + e.getMessage());
        }
    }

    /** Reconhece a ocorrência ({@code ABERTO -> RECONHECIDO}). */
    @PutMapping("/ocorrencias/{id}/reconhecer")
    public ResponseEntity<?> reconhecer(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.reconhecer(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao reconhecer a ocorrência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao reconhecer a ocorrência: " + e.getMessage());
        }
    }

    /** Inicia o reparo da ocorrência ({@code RECONHECIDO -> EM_REPARO}). */
    @PutMapping("/ocorrencias/{id}/em-reparo")
    public ResponseEntity<?> iniciarReparo(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.iniciarReparo(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao iniciar o reparo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao iniciar o reparo: " + e.getMessage());
        }
    }

    /** Resolve a ocorrência ({@code EM_REPARO -> RESOLVIDO}). */
    @PutMapping("/ocorrencias/{id}/resolver")
    public ResponseEntity<?> resolver(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.resolver(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao resolver a ocorrência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao resolver a ocorrência: " + e.getMessage());
        }
    }

    /** Recurso inexistente vira 404; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(
            IllegalArgumentException e,
            String contexto) {

        String mensagem = e.getMessage();

        if (mensagem != null && mensagem.contains("não encontrad")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mensagem);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(contexto + ": " + mensagem);
    }
}