package controller;

import domain.dto.ActiveUserDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.model.entities.User;
import security.ProfileAuthorization;
import service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Utilizadores.
 *
 * <p>A leitura fica aberta a qualquer utilizador autenticado, porque o nome e
 * o e-mail dos colegas são informação partilhada: são eles que alimentam o
 * selector de pessoas do editor de alocações. Já criar um utilizador — e portanto
 * definir a senha de alguém — é restrito ao perfil {@code SUPERVISOR}, tal como
 * a escrita de alocações em {@link AllocationController}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    /**
     * Utilizadores ativos, por ordem de nome. Alimenta o selector de pessoas
     * do editor de alocações. Fica acima de {@code /me} para não ser
     * interceptada por essa rota.
     */
    @GetMapping
    public ResponseEntity<List<ActiveUserDTO>> listarAtivos() {

        return ResponseEntity.ok(userService.listarAtivos());
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> currentUser(@AuthenticationPrincipal User loggedUser) {

        return ResponseEntity.ok(userService.currentUser(loggedUser.getEmail()));
    }

    /**
     * Cadastra um utilizador e responde 201 com o que se pode mostrar sobre
     * ele. A resposta é um {@link ActiveUserDTO} e não a entidade: como
     * {@code User} implementa {@code UserDetails}, serializá-la poria o hash da
     * senha na resposta.
     *
     * <p>A regra de perfil é a mesma das alocações e vale para a mesma razão:
     * o perfil vive no principal e não na rota, pelo que é verificado aqui em
     * vez de no {@code SecurityConfig}. Sem token não se chega ao método — a
     * cadeia de filtros responde 401 antes —, e com token de um perfil errado
     * a negação é um {@code AccessDeniedException} traduzido em 403.
     */
    @PostMapping
    public ResponseEntity<ActiveUserDTO> criar(@Valid @RequestBody CreateUserRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        ActiveUserDTO criado = userService.criar(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }
}
