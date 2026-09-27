package controller;

import domain.dto.UserLeaveDTO;
import domain.dto.UserLeaveRequestDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import security.ProfileAuthorization;
import service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Folgas.
 *
 * <p>A leitura é aberta a qualquer perfil autenticado e a escrita é exclusiva da
 * supervisão. É a mesma divisão do registo de utilizadores e das alocações, e
 * pelo mesmo motivo: registar uma falta é escrever no estado de uma pessoa, e
 * isso é decisão de quem supervisiona, não de quem a serve.
 */
@RestController
@RequestMapping("/api/v1/leaves")
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    /**
     * Lista de folgas, opcionalmente filtrada por pessoa.
     *
     * <p>Não é filtrada por perfil: ver quem está de folga é informação de toda
     * a equipa, e o que a aplicação restringe é a escrita.
     */
    @GetMapping
    public ResponseEntity<List<UserLeaveDTO>> listar(@RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(leaveService.listar(userId, perfilDeQuemPede()));
    }

    @PostMapping
    public ResponseEntity<UserLeaveDTO> criar(@Valid @RequestBody UserLeaveRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        UserLeaveDTO criada = leaveService.criar(dto, UserProfile.SUPERVISOR);

        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserLeaveDTO> atualizar(@PathVariable Long id,
                                                  @Valid @RequestBody UserLeaveRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(leaveService.atualizar(id, dto, UserProfile.SUPERVISOR));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        leaveService.apagar(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Perfil de quem fez o pedido, para marcar a resposta com {@code canEdit}.
     *
     * <p>Um principal ausente ou que não seja um {@link User} é tratado como
     * perfil desconhecido, o que devolve {@code canEdit} a falso em vez de
     * rebentar com um {@code NullPointerException}. A cadeia de filtros já
     * rejeita quem não está autenticado; o que este método cobre é o token
     * válido que não resolve para um utilizador.
     */
    private UserProfile perfilDeQuemPede() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }
        return user.getProfile();
    }
}
