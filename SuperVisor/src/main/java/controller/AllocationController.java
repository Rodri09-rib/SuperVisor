package controller;

import domain.dto.AllocationAcceptanceRequestDTO;
import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.User;
import security.ProfileAuthorization;
import service.AllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;

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

    /**
     * O analista aceita ou recusa o turno que lhe foi escalado.
     *
     * <p>Não é uma escrita restrita à supervisão, ao contrário das outras desta
     * rota: é justamente a pessoa escalada que tem de responder, e a
     * supervisão responde por cima para desbloquear alguém que não está
     * disponível. Quem pode é o dono do turno ou um supervisor.
     *
     * <p>Quem responde é lido do contexto de segurança e não do corpo do pedido
     * pelo mesmo motivo de {@code UserController}: o id de quem responde não pode
     * ser escolhido pelo cliente.
     */
    @PatchMapping("/{id}/acceptance")
    public ResponseEntity<AllocationDTO> responder(@PathVariable Long id,
                                                   @Valid @RequestBody AllocationAcceptanceRequestDTO dto) {
        return ResponseEntity.ok(allocationService.responder(id, dto.status(), utilizadorAutenticadoId()));
    }

    /**
     * O utilizador autenticado, ou {@code null} quando não há principal.
     *
     * <p>Devolve {@code null} em vez de rebentar para que a negação venha do
     * serviço, como {@code AccessDeniedException} e 403, e não como uma
     * {@code NullPointerException} transformada em 400.
     */
    private Long utilizadorAutenticadoId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }

        return user.getId();
    }
}
