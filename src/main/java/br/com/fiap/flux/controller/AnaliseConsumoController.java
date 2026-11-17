package br.com.fiap.flux.controller;

import br.com.fiap.flux.service.AnaliseConsumoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Análise simulada de consumo, por morador. */
@CrossOrigin("*")
@RestController
public class AnaliseConsumoController {

    private final AnaliseConsumoService service = new AnaliseConsumoService();

    /** Executa a análise das residências em que o morador é titular e grava os alertas novos. */
    @PostMapping("/residencias/analise/executar/morador/{idMorador}")
    public ResponseEntity<?> executar(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.executarPorMorador(idMorador));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao executar a análise");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao executar a análise: " + e.getMessage());
        }
    }

    /** Resultado atual da análise das residências vinculadas ao morador. */
    @GetMapping("/residencias/analise/resultado/morador/{idMorador}")
    public ResponseEntity<?> resultado(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.resultadoPorMorador(idMorador));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao consultar o resultado da análise");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao consultar o resultado da análise: " + e.getMessage());
        }
    }

    /** Histórico de alertas de todas as residências vinculadas ao morador, com filtro opcional de período. */
    @GetMapping("/residencias/analise/historico/morador/{idMorador}")
    public ResponseEntity<?> historico(
            @PathVariable int idMorador,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        try {
            return ResponseEntity.ok(service.historicoPorMorador(idMorador, inicio, fim));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao consultar o histórico de análises");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao consultar o histórico de análises: " + e.getMessage());
        }
    }

    /** Executa a análise em todas as residências com dispositivo. */
    @PostMapping("/analises/executar-todas")
    public ResponseEntity<?> executarTodas() {
        try {
            return ResponseEntity.ok(service.executarTodas());
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao executar a análise geral");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao executar a análise geral: " + e.getMessage());
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