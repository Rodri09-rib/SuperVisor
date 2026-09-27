package controller;

import domain.dto.AssignmentTypeDTO;
import domain.dto.ShiftTypeDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vocabulário do editor de alocações: os turnos de fim de semana e as
 * atribuições especiais. São dados estáticos do domínio, servidos a partir dos
 * enums para que o frontend não tenha de os duplicar.
 */
@RestController
@RequestMapping("/api/v1")
public class ReferenceController {

    @GetMapping("/shifts")
    public ResponseEntity<List<ShiftTypeDTO>> turnos() {

        return ResponseEntity.ok(ShiftTypeDTO.todos());
    }

    @GetMapping("/assignments")
    public ResponseEntity<List<AssignmentTypeDTO>> atribuicoes() {

        return ResponseEntity.ok(AssignmentTypeDTO.todas());
    }
}
