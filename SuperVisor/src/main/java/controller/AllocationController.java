package controller;

import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import security.ProfileAuthorization;
import service.AllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Alocações de turno.
 *
 * <p>A leitura fica aberta a qualquer utilizador autenticado, porque o ficheiro
 * de escalas é informação partilhada. Já criar, alterar e remover são
 * operações de escrita restritas ao perfil {@code SUPERVISOR}: o perfil vive no
 * principal e não na rota, pelo que a regra é verificada aqui, no controlador,
 * em vez de no {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/allocations")
public class AllocationController {

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    @GetMapping
    public ResponseEntity<List<AllocationDTO>> list(@RequestParam(required = false) Long editionScaleId) {

        return ResponseEntity.ok(allocationService.list(editionScaleId));
    }

    /**
     * Cria uma alocação. O utilizador e a escala chegam por id e o turno vem
     * como constante de {@code ShiftType}, pelo que nunca há texto livre para
     * nomes de pessoas nem para turnos.
     */
    @PostMapping
    public ResponseEntity<AllocationDTO> create(@RequestBody AllocationRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(allocationService.create(dto));
    }

    /** Substitui os campos editáveis de uma alocação existente. */
    @PutMapping("/{id}")
    public ResponseEntity<AllocationDTO> update(@PathVariable Long id,
                                                @RequestBody AllocationRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(allocationService.update(id, dto));
    }

    /** Remove uma alocação. A regra de negócio continua a ser a do serviço. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        allocationService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
