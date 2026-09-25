package controller;

import domain.dto.CreateScaleDTO;
import domain.model.entities.EditionScale;
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

    @PostMapping
    public ResponseEntity<EditionScale> createScale(@RequestBody CreateScaleDTO dto) {
        EditionScale newScale = scaleService.createScale(dto);

        return ResponseEntity.ok(newScale);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Void> publishScale(@PathVariable Long id) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<EditionScale>> listAll() {

        return ResponseEntity.ok(scaleService.listAll());
    }
}