package br.com.fiap.flux.controller;

import br.com.fiap.flux.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard do morador. Os endpoints não exigem login e são só de leitura. Cada um devolve uma
 * lista
 * com os dados das residências vinculadas ao morador (os alertas, juntos numa só lista); 400 se o
 * morador não tem nenhuma residência.
 */
@CrossOrigin("*")
@RestController
public class DashboardController {

    private final DashboardService service = new DashboardService();

    /**
     * Indicadores gerais de cada residência: consumo de hoje e do mês, última leitura, alertas
     * ativos e
     * status do dispositivo.
     */
    @GetMapping("/dashboard/morador/{idMorador}")
    public ResponseEntity<?> dashboard(@PathVariable int idMorador) {
        try { return ResponseEntity.ok(service.dashboardsPorMorador(idMorador)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    /** Indicadores de consumo de cada residência, comparados com o período anterior. */
    @GetMapping("/dashboard/consumo/morador/{idMorador}")
    public ResponseEntity<?> consumo(@PathVariable int idMorador) {
        try { return ResponseEntity.ok(service.consumoPorMorador(idMorador)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    /** Consumo agrupado por hora, dia, semana e mês de cada residência, para os gráficos. */
    @GetMapping("/dashboard/historico/morador/{idMorador}")
    public ResponseEntity<?> historico(@PathVariable int idMorador) {
        try { return ResponseEntity.ok(service.historicoPorMorador(idMorador)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    /** Alertas ainda não resolvidos de todas as residências do morador. */
    @GetMapping("/dashboard/alertas/morador/{idMorador}")
    public ResponseEntity<?> alertas(@PathVariable int idMorador) {
        try { return ResponseEntity.ok(service.alertasPorMorador(idMorador)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    /** Dispositivos das residências do morador. */
    @GetMapping("/dashboard/dispositivos/morador/{idMorador}")
    public ResponseEntity<?> dispositivos(@PathVariable int idMorador) {
        try { return ResponseEntity.ok(service.dispositivosPorMorador(idMorador)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
