package controller;

import domain.dto.AllocationDTO;
import service.AllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/allocations")
public class AllocationController {

    @Autowired
    private AllocationService allocationService;

    @GetMapping
    public ResponseEntity<List<AllocationDTO>> list(@RequestParam(required = false) Long editionScaleId) {

        return ResponseEntity.ok(allocationService.list(editionScaleId));
    }
}
