package controller;

import domain.dto.HolidayDTO;
import domain.dto.HolidayRequestDTO;
import security.ProfileAuthorization;
import service.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Feriados.
 *
 * <p>A mesma divisão das folgas e das alocações: a leitura é de qualquer
 * perfil autenticado e a escrita é exclusiva da supervisão. O badge de feriado
 * na escala é visto por toda a gente, mas acrescentar um dia ao calendário é
 * decisão de quem supervisiona — é esse dia que passa a render folga extra no
 * fecho da escala.
 */
@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    @GetMapping
    public ResponseEntity<List<HolidayDTO>> listar() {
        return ResponseEntity.ok(holidayService.listar());
    }

    @PostMapping
    public ResponseEntity<HolidayDTO> criar(@Valid @RequestBody HolidayRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.status(HttpStatus.CREATED).body(holidayService.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HolidayDTO> atualizar(@PathVariable Long id,
                                                @Valid @RequestBody HolidayRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(holidayService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        holidayService.apagar(id);

        return ResponseEntity.noContent().build();
    }
}
