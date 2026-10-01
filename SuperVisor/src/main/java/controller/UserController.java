package controller;

import domain.dto.ActiveUserDTO;
import domain.dto.ChangeActiveRequestDTO;
import domain.dto.ChangePasswordRequestDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import security.ProfileAuthorization;
import service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public ResponseEntity<CurrentUserDTO> currentUser() {

        return ResponseEntity.ok(userService.currentUser(utilizadorAutenticado().getEmail()));
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

    /**
     * Todos os utilizadores, ativos e inativos. Reservado à supervisão porque é
     * a vista de administração: mostra quem está fora, o que o selector de
     * pessoas — só com contas ativas — por definição não mostra.
     */
    @GetMapping("/todos")
    public ResponseEntity<List<ActiveUserDTO>> listarTodos() {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(userService.listarTodos());
    }

    /**
     * Altera o cadastro. O e-mail não entra no pedido, e o motivo está em
     * {@link UpdateUserRequestDTO}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ActiveUserDTO> atualizar(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(userService.atualizar(id, dto));
    }

    /**
     * Ativa ou desativa a conta.
     *
     * <p>O corpo é um booleano simples e não um recurso aninhado porque a
     * operação é uma comutação de estado e não uma edição parcial: não há campo
     * que o pedido possa deixar por omisso e ser reinterpretado.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ActiveUserDTO> alterarEstado(@PathVariable Long id,
                                                       @RequestBody ChangeActiveRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        // O id de quem pede vem do contexto de segurança e não do caminho, para
        // que a regra de autodesativação não possa ser contornada por um
        // parâmetro enviado pelo cliente.
        return ResponseEntity.ok(userService.alterarEstado(
                id, dto.active(), utilizadorAutenticado().getId()));
    }

    /**
     * Redefine a senha de um utilizador. Restrito à supervisão porque define a
     * senha de outra pessoa, que é o mesmo motivo de {@link #criar}.
     */
    @PostMapping("/{id}/senha")
    public ResponseEntity<Void> redefinirSenha(@PathVariable Long id,
                                               @Valid @RequestBody ChangePasswordRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        userService.redefinirSenha(id, dto.password());

        return ResponseEntity.noContent().build();
    }

    /**
     * O utilizador autenticado, lido do contexto de segurança.
     *
     * <p>Usa o mesmo caminho que {@code ExchangeController} e
     * {@code ProfileAuthorization} em vez de {@code @AuthenticationPrincipal},
     * por duas razões. A primeira é a segurança: a regra de autodesativação
     * depende de saber quem está a pedir, e esse dado não deve passar por um
     * mecanismo que o cliente possa influenciar. A segunda é a testabilidade:
     * {@code @AuthenticationPrincipal} é resolvido por um {@code
     * HandlerMethodArgumentResolver} que o MockMvc isolado não regista, e o
     * parâmetro acabava por ser preenchido por encadernação de parâmetros de
     * pedido — o que faria o teste passar sem nunca exercitar o principal real.
     *
     * <p>O contexto já foi guaranteeing não estar vazio a este ponto: sem token
     * a cadeia de filtros responde 401 antes de chegar aqui, e
     * {@code exigirSupervisor} já devolveu 403 quando não há principal.
     */
    private User utilizadorAutenticado() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (User) authentication.getPrincipal();
    }
}
