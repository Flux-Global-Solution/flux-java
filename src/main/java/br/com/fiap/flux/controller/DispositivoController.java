package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.DispositivoRequestDTO;
import br.com.fiap.flux.dto.DispositivoStatusRequestDTO;
import br.com.fiap.flux.service.DispositivoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/** Dispositivos FLUX: cadastro, consulta e simulação de status. */
@CrossOrigin("*")
@RestController
@RequestMapping("/dispositivos")
public class DispositivoController {

    private final DispositivoService service = new DispositivoService();

    /** Cadastra um dispositivo numa residência. */
    @PostMapping("/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador, @RequestBody DispositivoRequestDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(idMorador, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar o dispositivo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o dispositivo: " + e.getMessage());
        }
    }

    /** Lista os dispositivos das residências às quais o morador tem acesso. */
    @GetMapping("/morador/{idMorador}")
    public ResponseEntity<?> listar(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.listar(idMorador));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar os dispositivos");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar os dispositivos: " + e.getMessage());
        }
    }

    /** Consulta um dispositivo, com os status de dispositivo, sensor e energia. */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar o dispositivo");
        }
    }

    /** Atualiza série, MAC, modelo e data de instalação. */
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id, @RequestBody DispositivoRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao atualizar o dispositivo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao atualizar o dispositivo: " + e.getMessage());
        }
    }

    /** Altera o status simulado do dispositivo (dispositivo, sensor e/ou energia) para demonstrar cenários. */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> alterarStatus(@PathVariable int id, @RequestBody DispositivoStatusRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.alterarStatus(id, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao alterar o status do dispositivo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao alterar o status do dispositivo: " + e.getMessage());
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