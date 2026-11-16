package br.com.fiap.flux.controller;

import br.com.fiap.flux.service.PerfilConsumoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Perfil de consumo das residências, calculado só com as leituras (os valores nunca vêm do
 * cliente).
 * Os endpoints não exigem login. Consultar não recalcula: o perfil é refeito quando chegam leituras
 * novas ou quando o titular pede.
 *
 * <p>Respostas de erro: 404 para recurso inexistente (inclusive perfil ainda não calculado), 400
 * para
 * validação e 500 para falha interna.</p>
 */
@CrossOrigin("*")
@RestController
public class PerfilConsumoController {

    private final PerfilConsumoService service = new PerfilConsumoService();

    /** Consulta o perfil já calculado da residência. */
    @GetMapping("/residencias/{id}/perfil-consumo")
    public ResponseEntity<?> buscarPorResidencia(@PathVariable int id) {
        try { return ResponseEntity.ok(service.buscarPorResidencia(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao buscar o perfil de consumo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Calcula o perfil da residência ou, se já existe, recalcula o mesmo. */
    @PostMapping("/residencias/{id}/perfil-consumo/calcular")
    public ResponseEntity<?> calcular(@PathVariable int id) {
        try { return ResponseEntity.ok(service.calcular(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao calcular o perfil de consumo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Recalcula o perfil indicado com as leituras de agora. */
    @PutMapping("/perfis-consumo/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id) {
        try { return ResponseEntity.ok(service.atualizar(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao atualizar o perfil de consumo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Consulta o perfil pelo seu identificador. */
    @GetMapping("/perfis-consumo/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try { return ResponseEntity.ok(service.buscarPorId(id)); }
        catch (IllegalArgumentException e) { return erro(e, "Erro ao buscar o perfil de consumo"); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage()); }
    }

    /** Recurso inexistente vira 404; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}
