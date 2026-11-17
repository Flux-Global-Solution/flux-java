package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.LeituraRequestDTO;
import br.com.fiap.flux.dto.SimulacaoLeiturasRequestDTO;
import br.com.fiap.flux.service.LeituraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Leituras de consumo, todas simuladas (sem hardware). */
@CrossOrigin("*")
@RestController
public class LeituraController {

    private final LeituraService service = new LeituraService();

    /** Lista as leituras de todas as residências às quais o morador tem acesso. */
    @GetMapping("/leituras/morador/{idMorador}")
    public ResponseEntity<?> listar(@PathVariable int idMorador,
                                    @RequestParam(required = false) String inicio,
                                    @RequestParam(required = false) String fim) {
        try {
            return ResponseEntity.ok(service.listar(idMorador, inicio, fim));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as leituras");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as leituras: " + e.getMessage());
        }
    }

    /** Consulta uma leitura. */
    @GetMapping("/leituras/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar a leitura");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar a leitura: " + e.getMessage());
        }
    }

    /** Lista as leituras de uma residência. */
    @GetMapping("/residencias/{id}/leituras")
    public ResponseEntity<?> listarPorResidencia(@PathVariable int id,
                                                 @RequestParam(required = false) String inicio,
                                                 @RequestParam(required = false) String fim) {
        try {
            return ResponseEntity.ok(service.listarPorResidencia(id, inicio, fim));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as leituras da residência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as leituras da residência: " + e.getMessage());
        }
    }

    /** Lista as leituras de um dispositivo. */
    @GetMapping("/dispositivos/{id}/leituras")
    public ResponseEntity<?> listarPorDispositivo(@PathVariable int id,
                                                  @RequestParam(required = false) String inicio,
                                                  @RequestParam(required = false) String fim) {
        try {
            return ResponseEntity.ok(service.listarPorDispositivo(id, inicio, fim));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as leituras do dispositivo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as leituras do dispositivo: " + e.getMessage());
        }
    }

    /** Cadastra uma leitura de teste. */
    @PostMapping("/leituras/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador, @RequestBody LeituraRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(idMorador, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar a leitura");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar a leitura: " + e.getMessage());
        }
    }

    /** Cadastra de uma vez um conjunto de leituras fictícias, gravado inteiro ou não gravado. */
    @PostMapping("/simulacoes/leituras/morador/{idMorador}")
    public ResponseEntity<?> cadastrarConjunto(@PathVariable int idMorador, @RequestBody SimulacaoLeiturasRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrarConjunto(idMorador, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar as leituras simuladas");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar as leituras simuladas: " + e.getMessage());
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