package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.dto.VinculoRequestDTO;
import br.com.fiap.flux.service.ResidenciaService;
import br.com.fiap.flux.service.VinculoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * Vínculos entre moradores e residências: compartilhar o acesso de uma residência com outro morador (que
 * já precisa ter conta) e removê-lo.
 */
@CrossOrigin("*")
@RestController
public class VinculoController {

    private final VinculoService service = new VinculoService();
    private final ResidenciaService residenciaService = new ResidenciaService();

    /** Vincula à residência o morador dono do e-mail enviado no corpo. A residência vem da URL. */
    @PostMapping("/residencias/{id}/moradores")
    public ResponseEntity<?> vincular(@PathVariable int id, @RequestBody VinculoRequestDTO dto) {
        try {
            MoradorResidenciaResponseDTO vinculo = service.vincular(id, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(vinculo);
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao vincular o morador");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao vincular o morador: " + e.getMessage());
        }
    }

    /** Lista as residências do morador, com o tipo de vínculo dele em cada uma. */
    @GetMapping("/moradores/{id}/residencias")
    public ResponseEntity<?> listarResidencias(@PathVariable int id) {
        try {
            return ResponseEntity.ok(residenciaService.listar(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao listar as residências do morador");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao listar as residências do morador: " + e.getMessage());
        }
    }

    /** Consulta um vínculo. */
    @GetMapping("/vinculos/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar o vínculo");
        }
    }

    /** Remove o vínculo de um morador comum; o vínculo do titular não pode ser removido. */
    @DeleteMapping("/vinculos/{id}")
    public ResponseEntity<?> remover(@PathVariable int id) {
        try {
            service.remover(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao remover o vínculo");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao remover o vínculo: " + e.getMessage());
        }
    }

    /** Recurso inexistente vira 404; vínculo repetido vira 409; qualquer outra falha de validação vira 400. */
    private ResponseEntity<String> erro(IllegalArgumentException e, String contexto) {
        String mensagem = e.getMessage();
        if (mensagem != null && mensagem.contains("não encontrad")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mensagem);
        }
        if (mensagem != null && mensagem.contains("já cadastrado")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mensagem);
        }
        return ResponseEntity.badRequest().body(contexto + ": " + mensagem);
    }
}