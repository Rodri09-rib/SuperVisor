package controller;

import domain.dto.ActiveUserDTO;
import domain.dto.ChangeActiveRequestDTO;
import domain.dto.ChangePasswordRequestDTO;
import domain.dto.CompensationAdjustRequestDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.dto.ExtratoEventoDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import security.ProfileAuthorization;
import service.CompensationService;
import service.ExtratoService;
import service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;

/**
 * Usuários.
 *
 * <p>A leitura fica aberta a qualquer usuário autenticado, porque o nome e
 * o e-mail dos colegas são informação partilhada: são eles que alimentam o
 * seletor de pessoas do editor de alocações. Já criar um usuário — e portanto
 * definir a senha de alguém — é restrito ao perfil {@code SUPERVISOR}, tal como
 * a escrita de alocações em {@link AllocationController}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CompensationService compensationService;

    @Autowired
    private ExtratoService extratoService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    /**
     * Usuários ativos, por ordem de nome. Alimenta o seletor de pessoas
     * do editor de alocações. Fica acima de {@code /me} para não ser
     * interceptada por essa rota.
     *
     * <p>Os saldos vêm apenas para quem supervisiona. A rota está aberta a
     * qualquer perfil autenticado — nomes e e-mails são informação partilhada
     * — mas o saldo de folgas e a dívida de compensação são gestão, e um
     * analista que lesse a lista não ia usá-los para nada legítimo. A omissão
     * é da resposta e não do frontend: esconder a coluna no browser deixava o
     * valor no JSON de qualquer pessoa que abrisse a consola.
     */
    @GetMapping
    public ResponseEntity<List<ActiveUserDTO>> listarAtivos() {
        List<ActiveUserDTO> utilizadores = userService.listarAtivos();

        if (perfilDeQuemPede() != UserProfile.SUPERVISOR) {
            utilizadores = utilizadores.stream()
                    .map(ActiveUserDTO::semSaldos)
                    .toList();
        }

        return ResponseEntity.ok(utilizadores);
    }

    /**
     * Extrato de um colaborador: faltas, folgas e recompensas, do mais
     * recente para o mais antigo.
     *
     * <p>É a resposta ao «de onde vem este saldo?» do botão de histórico da
     * página de folgas. Restrito à supervisão pela mesma razão de
     * {@link #ajustarCompensacao}: o extrato reconstrói o saldo de qualquer
     * pessoa a partir do histórico dela, que é informação de gestão.
     *
     * <p>Uma conta inexistente é 400 com a mensagem de serviço e não 404: é
     * a mesma resposta de «usuário não encontrado» do resto do recurso, e
     * distinguir os dois códigos só ensinaria o chamador a mapear cada
     * serviço para o seu favorito.
     */
    @GetMapping("/{id}/extrato")
    public ResponseEntity<List<ExtratoEventoDTO>> extrato(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(extratoService.listar(id));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserDTO> currentUser() {

        return ResponseEntity.ok(userService.currentUser(utilizadorAutenticado().getEmail()));
    }

    /**
     * Cadastra um usuário e responde 201 com o que se pode mostrar sobre
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
     * Todos os usuários, ativos e inativos. Reservado à supervisão porque é
     * a vista de administração: mostra quem está fora, o que o seletor de
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
     * Redefine a senha de um usuário. Restrito à supervisão porque define a
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
     * Ajusta o saldo de compensação de um colaborador.
     *
     * <p>Restrito à supervisão porque mexe no saldo de outra pessoa, e o saldo
     * é o que a grelha de presencialidade usa para avisar que alguém deve
     * compensar. O corpo traz um delta assinado — negativo para abater, quando
     * o colaborador compensou; positivo para acrescentar, numa troca não
     * autorizada — e o serviço corta o resultado em zero.
     */
    @PatchMapping("/{id}/compensation")
    public ResponseEntity<ActiveUserDTO> ajustarCompensacao(@PathVariable Long id,
                                                            @Valid @RequestBody CompensationAdjustRequestDTO dto) {
        profileAuthorization.exigirSupervisor();

        return ResponseEntity.ok(compensationService.ajustar(id, dto.deltaDays()));
    }

    /**
     * Exclui definitivamente uma conta.
     *
     * <p>Difere de {@link #alterarEstado} em duas coisas, e ambas são o motivo de
     * a operação ser recitada por {@link UserService#apagar} e não de ser um
     * {@code delete} a seco. A primeira é que a exclusão é definitiva e por isso
     * recusa contas que ainda tenham turnos marcados: apagar a escala inteira
     * para libertar a conta levaria com ela o trabalho de todas as outras
     * pessoas. A segunda é que a conta alheia não pode ser a de quem pede, e o
     * id de quem pede vem do contexto de segurança pelo mesmo motivo de
     * {@link #alterarEstado} — um parâmetro enviado pelo cliente não pode
     * decidir a própria regra que o impede.
     *
     * <p>Um {@code 204} e não um corpo com o usuário removido: a resposta seria
     * uma cópia de um registo que já não existe.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> apagar(@PathVariable Long id) {
        profileAuthorization.exigirSupervisor();

        userService.apagar(id, utilizadorAutenticado().getId());

        return ResponseEntity.noContent().build();
    }

    /**
     * O usuário autenticado, lido do contexto de segurança.
     *
     * <p>Usa o mesmo caminho que {@code ExchangeController} e
     * {@code ProfileAuthorization} em vez de {@code @AuthenticationPrincipal},
     * por duas razões. A primeira é a segurança: a regra de autodesativação
     * depende de saber quem está pedindo, e esse dado não deve passar por um
     * mecanismo que o cliente possa influenciar. A segunda é a testabilidade:
     * {@code @AuthenticationPrincipal} é resolvido por um {@code
     * HandlerMethodArgumentResolver} que o MockMvc isolado não registra, e o
     * parâmetro acabava por ser preenchido por encadernação de parâmetros de
     * pedido — o que faria o teste passar sem nunca exercitar o principal real.
     *
     * <p>O contexto já está garantido que não está vazio neste ponto: sem token
     * a cadeia de filtros responde 401 antes de chegar aqui, e
     * {@code exigirSupervisor} já devolveu 403 quando não há principal.
     */
    private User utilizadorAutenticado() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (User) authentication.getPrincipal();
    }

    /**
     * Perfil de quem fez o pedido, para a leitura decidir o que omitir.
     *
     * <p>Devolve {@code null} quando não há principal ou quando não é um
     * {@link User} — e um perfil desconhecido não é supervisor, o que fecha a
     * lista de saldos em vez de a abrir por omissão. É a mesma defesa do
     * método equivalente de {@code LeaveController}, pela mesma razão: o
     * caminho seguro é o de mostrar menos.
     */
    private UserProfile perfilDeQuemPede() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }

        return user.getProfile();
    }
}
