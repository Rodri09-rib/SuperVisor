package service;

import domain.dto.ActiveUserDTO;
import domain.dto.ChangePasswordRequestDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.UserRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Perfil do usuário autenticado. O e-mail vem do token, já validado
     * pelo {@code SecurityFilter}, pelo que a autenticação está garantida.
     */
    @Transactional(readOnly = true)
    public CurrentUserDTO currentUser(String email) {
        UserDetails encontrado = userRepository.findByEmail(email);
        if (encontrado == null) {
            throw new UsernameNotFoundException("Usuário não encontrado");
        }
        return CurrentUserDTO.from((User) encontrado);
    }

    /**
     * Usuários ativos, por ordem de nome. É a origem de dados do seletor
     * de pessoas do editor de alocações: só contas ativas podem receber turnos.
     */
    @Transactional(readOnly = true)
    public List<ActiveUserDTO> listarAtivos() {
        return ActiveUserDTO.from(userRepository.findByActiveTrueOrderByNameAsc());
    }

    /**
     * Cadastra um novo usuário.
     *
     * <p>O e-mail é a identidade da conta e tem de ser único. A verificação é
     * feita aqui e não deixada para a restrição {@code unique} da base de dados
     * porque essa chegaria como 500: o frontend precisa de um 400 com mensagem
     * para mostrar no formulário, e o usuário precisa de saber que o e-mail
     * é que está repetido e não o nome ou a senha.
     *
     * <p>O e-mail é guardado tal como foi enviado, sem normalização. A
     * autenticação compara o valor por igualdade exata, pelo que passar a
     * gravar em minúsculas faria o valor gravado divergir da chave de leitura
     * e o novo usuário não conseguiria entrar com o mesmo endereço com que
     * se cadastrou. A unicidade é, por isso, a da coluna {@code email}.
     *
     * <p>A senha é criptografada com o {@code BCryptPasswordEncoder} declarado
     * no {@code SecurityConfig}, e nunca chega ao banco em claro.
     */
    @Transactional
    public ActiveUserDTO criar(CreateUserRequestDTO dto) {
        if (userRepository.findByEmail(dto.email()) != null) {
            throw new RegraDeNegocioException("E-mail já cadastrado.");
        }

        User novo = new User();
        novo.setName(dto.name().trim());
        novo.setEmail(dto.email());
        novo.setPassword(passwordEncoder.encode(dto.password()));
        novo.setProfile(dto.profile());
        novo.setTeamGroup(dto.teamGroup());
        novo.setActive(dto.active());

        return ActiveUserDTO.from(userRepository.save(novo));
    }

    /**
     * Alterar o cadastro de um usuário.
     *
     * <p>O e-mail não é tocável, e a razão está em
     * {@link UpdateUserRequestDTO}: o e-mail é a identidade da conta e o que vai
     * dentro do token, e mudá-lo deixaria no ar os tokens da conta antiga sem
     * forma de os invalidar.
     */
    @Transactional
    public ActiveUserDTO atualizar(Long id, UpdateUserRequestDTO dto) {
        User user = procurar(id);

        user.setName(dto.name().trim());
        user.setProfile(dto.profile());
        // O `null` é um valor pedido, não uma ausência: é assim que alguém sai
        // de uma equipe.
        user.setTeamGroup(dto.teamGroup());

        return ActiveUserDTO.from(userRepository.save(user));
    }

    /**
     * Todos os usuários, ativos e inativos.
     *
     * <p>É a vista de administração: sem ela, uma conta desativada desaparece
     * do seletor de pessoas e deixa de haver caminho para a reativar a partir da
     * aplicação.
     */
    @Transactional(readOnly = true)
    public List<ActiveUserDTO> listarTodos() {
        return ActiveUserDTO.from(userRepository.findAllByOrderByNameAsc());
    }

    /**
     * Ativa ou desativa uma conta.
     *
     * <p>O estado da conta já era respeitado em todo o lado — autenticação,
     * alocações, escala de presencialidade — mas não havia forma de o mudar, e
     * por isso um Collaborator que saía da empresa tinha de continuar a constar
     * como ativo para sempre.
     *
     * <p>Duas regras impedem estados em que ninguém consegue recuperar a
     * aplicação: ninguém se desativa a si próprio, e o último supervisor ativo
     * não pode ser desativado. Sem elas, um clique podia deixar a instalação sem
     * nenhuma conta capaz de criar usuários ou de reativar contas, e a
     * saída passaria a ser a base de dados à mão.
     *
     * <p>As duas regras protegem o mesmo estado por caminhos diferentes, e é
     * por isso que as duas ficam. Quem desativa um supervisor tem de ser ele
     * próprio — o único perfil que chega a este método — ou outro supervisor
     * ativo. A segunda opção é a única que a regra do último supervisor pega,
     * porque nesse caso o alvo está ativo e a contagem dá pelo menos dois; quando
     * a contagem dá um, só pode ser a pessoa a desativar-se a si própria, e aí a
     * outra regra trata. A do último supervisor é a rede debaixo: não é
     * alcançável hoje, e mesmo assim não deve desaparecer, porque o dia em que
     * a autodesativação seja revista passa a ser ela a única barreira.
     */
    @Transactional
    public ActiveUserDTO alterarEstado(Long id, boolean ativo, Long loggedUserId) {
        User user = procurar(id);

        if (ativo == user.isActive()) {
            return ActiveUserDTO.from(user);
        }

        if (!ativo) {
            exigirQueNaoSeDesative(id, loggedUserId);
            impedirDesativarUltimoSupervisor(user);
        }

        user.setActive(ativo);

        return ActiveUserDTO.from(userRepository.save(user));
    }

    private void exigirQueNaoSeDesative(Long id, Long loggedUserId) {
        if (id.equals(loggedUserId)) {
            throw new RegraDeNegocioException(
                    "Não pode desativar a sua própria conta.");
        }
    }

    /**
     * Recusa desativar o último supervisor ativo.
     *
     * <p>Só interessa quando o usuário é supervisor: desativar um analista
     * não tira a ninguém a capacidade de gerenciar contas. Com mais de um
     * supervisor ativo, desativar um deles é uma decisão normal e passa.
     */
    private void impedirDesativarUltimoSupervisor(User user) {
        if (user.getProfile() != UserProfile.SUPERVISOR) {
            return;
        }

        long outrosSupervisoresAtivos =
                userRepository.countByProfileAndActiveTrue(UserProfile.SUPERVISOR);

        if (outrosSupervisoresAtivos <= 1) {
            throw new RegraDeNegocioException(
                    "Não pode desativar o único supervisor ativo: a aplicação ficaria "
                            + "sem ninguém capaz de gerenciar os usuários.");
        }
    }

    /**
     * Redefine a senha de um usuário.
     *
     * <p>É a operação de recuperação de acesso: sem ela, um colaborador que se
     * esqueça da senha fica fora da aplicação sem caminho para lá voltar. A
     * autorização é da camada web, pelo mesmo motivo de sempre — o serviço
     * mantém-se responsável apenas pela regra de negócio.
     */
    @Transactional
    public void redefinirSenha(Long id, String novaSenha) {
        User user = procurar(id);

        user.setPassword(passwordEncoder.encode(novaSenha));
        userRepository.save(user);
    }

    private User procurar(Long id) {
        if (id == null) {
            throw new RegraDeNegocioException("Usuário não encontrado.");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Usuário não encontrado."));
    }
}
