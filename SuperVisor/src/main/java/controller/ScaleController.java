package controller;

import domain.dto.CreateScaleDTO;
import domain.dto.ScaleCoverageDTO;
import domain.model.entities.EditionScale;
import service.ScaleCoverageService;
import service.ScaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scales")
public class ScaleController {

    @Autowired
    private ScaleService scaleService;

    @Autowired
    private ScaleCoverageService scaleCoverageService;

    @PostMapping
    public ResponseEntity<EditionScale> createScale(@RequestBody CreateScaleDTO dto) {
        EditionScale newScale = scaleService.createScale(dto);

        return ResponseEntity.ok(newScale);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publishScale(@PathVariable Long id) {
        scaleService.publishSchedule(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<EditionScale>> listAll() {

        return ResponseEntity.ok(scaleService.listAll());
    }

    /**
     * Detalhes de uma escala. O {@code id} só aceita dígitos para que um
     * caminho como {@code /api/v1/scales/1/alterar} continue a devolver 404 em
     * vez de cair nesta rota e responder 400 por falha de conversão.
     */
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<EditionScale> getById(@PathVariable Long id) {

        return ResponseEntity.ok(scaleService.findById(id));
    }

    /**
     * Cobertura e conflitos da escala.
     *
     * <p>Fica na mesma rota de detalhe e com a mesma restrição a dígitos, para
     * que {@code /api/v1/scales/1/coverage} não colida com a rota de detalhe.
     *
     * <p>É uma leitura, como o resto dos recursos de escala, e não fica restrita
     * à supervisão: quem está a ver a escala precisa de ver também o que está
     * errado nela. O relatório não expõe o motivo das folgas pelo mesmo motivo.
     */
    @GetMapping("/{id:[0-9]+}/coverage")
    public ResponseEntity<ScaleCoverageDTO> coverage(@PathVariable Long id) {

        return ResponseEntity.ok(scaleCoverageService.relatorio(id));
    }
}