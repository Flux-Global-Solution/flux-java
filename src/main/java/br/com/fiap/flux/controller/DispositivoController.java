package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.DispositivoRequestDTO;
import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.DispositivoStatusRequestDTO;
import br.com.fiap.flux.service.DispositivoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dispositivos FLUX: cadastro, consulta e simulação de status. Os endpoints não exigem login e não
 * há
 * comunicação com hardware.
 *
 * <p>Respostas de erro: 404 para dispositivo inexistente, 400 para validação e 500 para falha
 * interna.
 * O cadastro e as alterações são do titular da residência.</p>
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/dispositivos")
public class DispositivoController {

    private final DispositivoService service = new DispositivoService();

    /**
     * Cadastra um dispositivo numa residência do morador titular. Nasce ativo, com sensor OK e
     * energia normal.
     */
    @PostMapping("/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador, @RequestBody DispositivoRequestDTO dto) {
        try { return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(idMorador, dto)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao cadastrar o dispositivo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Lista os dispositivos das residências às quais o morador tem acesso. */
    @GetMapping("/morador/{idMorador}")
    public ResponseEntity<List<DispositivoResponseDTO>> listar(@PathVariable int idMorador) {
        return ResponseEntity.ok(service.listar(idMorador));
    }

    /** Consulta um dispositivo, com os status de dispositivo, sensor e energia. */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try { return ResponseEntity.ok(service.buscarPorId(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao buscar o dispositivo"); }
    }

    /** Atualiza série, MAC, modelo e data de instalação. */
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id, @RequestBody DispositivoRequestDTO dto) {
        try { return ResponseEntity.ok(service.atualizar(id, dto)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao atualizar o dispositivo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /**
     * Altera o status simulado do dispositivo (dispositivo, sensor e/ou energia) para demonstrar
     * cenários.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> alterarStatus(@PathVariable int id, @RequestBody DispositivoStatusRequestDTO dto) {
        try { return ResponseEntity.ok(service.alterarStatus(id, dto)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao alterar o status do dispositivo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Recurso inexistente vira 404; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}
