package br.com.fiap.flux.controller;

import br.com.fiap.flux.dto.DispositivoResponseDTO;
import br.com.fiap.flux.dto.MoradorResidenciaResponseDTO;
import br.com.fiap.flux.dto.ResidenciaRequestDTO;
import br.com.fiap.flux.dto.ResidenciaResponseDTO;
import br.com.fiap.flux.service.ResidenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Residências: cadastro e gerenciamento. Os endpoints não exigem login. O morador só é informado no
 * cadastro, para criar o vínculo de titularidade; consultar, alterar e excluir usam o id da
 * residência.
 *
 * <p>Respostas de erro: 404 para residência inexistente, 409 se a exclusão é recusada por haver
 * histórico, 400 para validação e 500 para falha interna.</p>
 */
@CrossOrigin("*")
@RestController
@RequestMapping("/residencias")
public class ResidenciaController {

    private final ResidenciaService service = new ResidenciaService();

    /** Cadastra a residência e vincula o morador informado como titular. */
    @PostMapping("/morador/{idMorador}")
    public ResponseEntity<?> cadastrar(@PathVariable int idMorador,
                                       @RequestBody ResidenciaRequestDTO dto) {
        try {
            ResidenciaResponseDTO criada = service.cadastrar(idMorador, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(criada);
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao cadastrar a residência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar a residência: " + e.getMessage());
        }
    }

    /**
     * Lista as residências às quais o morador tem acesso, com o tipo de vínculo dele em cada uma.
     */
    @GetMapping("/morador/{idMorador}")
    public ResponseEntity<List<ResidenciaResponseDTO>> listar(@PathVariable int idMorador) {
        return ResponseEntity.ok(service.listar(idMorador));
    }

    /** Consulta uma residência. */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        try {
            return ResponseEntity.ok(service.buscarPorId(id));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar a residência");
        }
    }

    /** Atualiza as configurações da residência. */
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(@PathVariable int id,
                                       @RequestBody ResidenciaRequestDTO dto) {
        try {
            return ResponseEntity.ok(service.atualizar(id, dto));
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao atualizar a residência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao atualizar a residência: " + e.getMessage());
        }
    }

    /**
     * Exclui a residência, se ela ainda não tem dispositivo, leituras, alertas, perfil nem
     * autorizações.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> excluir(@PathVariable int id) {
        try {
            service.excluir(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao excluir a residência");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao excluir a residência: " + e.getMessage());
        }
    }

    /** Lista os moradores vinculados à residência, o titular primeiro. */
    @GetMapping("/{id}/moradores")
    public ResponseEntity<List<MoradorResidenciaResponseDTO>> listarMoradores(@PathVariable int id) {
        return ResponseEntity.ok(service.listarMoradores(id));
    }

    /** Consulta o dispositivo instalado na residência. */
    @GetMapping("/{id}/dispositivo")
    public ResponseEntity<?> buscarDispositivo(@PathVariable int id) {
        try {
            DispositivoResponseDTO dispositivo = service.buscarDispositivo(id);
            return ResponseEntity.ok(dispositivo);
        } catch (IllegalArgumentException e) {
            return erro(e, "Erro ao buscar o dispositivo");
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
