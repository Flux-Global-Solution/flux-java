package br.com.fiap.flux.controller;

import br.com.fiap.flux.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard do morador. */
@CrossOrigin("*")
@RestController
public class DashboardController {

    private final DashboardService service = new DashboardService();

    /**
     * Indicadores gerais de cada residência: consumo de hoje e do mês, última leitura, alertas
     * ativos e status do dispositivo.
     */
    @GetMapping("/dashboard/morador/{idMorador}")
    public ResponseEntity<?> dashboard(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.dashboard(idMorador));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Consumo agrupado por hora, dia, semana e mês de cada residência, para os gráficos. */
    @GetMapping("/dashboard/historico/morador/{idMorador}")
    public ResponseEntity<?> historico(@PathVariable int idMorador) {
        try {
            return ResponseEntity.ok(service.historico(idMorador));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}