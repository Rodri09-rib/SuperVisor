package service;

import domain.dto.ActiveUserDTO;
import domain.dto.ChangePasswordRequestDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.CurrentUserDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import domain.repository.WorkModalityScheduleRepository;
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
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private UserLeaveRepository userLeaveRepository;

    @Autowired
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;

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
     * Exclui definitivamente uma conta.
     *
     * <p>Diferente de {@link #alterarEstado}, que é reversível: aqui a conta
     * deixa de existir, e por isso a operação é recusada quando a pessoa tem
     * turnos marcados. Desativar resolve o mesmo problema do dia a dia e deixa o
     * histórico intacto; excluir é para quando nem isso chega.
     *
     * <p>O que é apagado em cascata é o que descreve a pessoa e não sobrevive
     * sem ela: as folgas e as células de presencialidade. O que não é apagado é o
     * que sobrevive: as escalas que criou ficam na base sem autoria, e os turnos
     * não são tocados porque não podem ser — apagam-se antes, escala a escala, e
     * é por isso que haver algum é motivo de recusa em vez de motivo para
     * apagar o resto.
     *
     * <p>A ordem de quem decide é a mesma de {@link #alterarEstado}, e pela mesma
     * razão: quem está a pedir não pode ser o alvo, e o motivo tem de ser o que
     * a pessoa consegue resolver. Falar primeiro do histórico apagar-se-ia
     * deixaria-a a Credo que apagar a conta resolve, e não resolve.
     *
     * <p>Quem pede vem do contexto de segurança e não do caminho, como em
     * {@link #alterarEstado}: a regra da autoexclusão não pode ser contornada por
     * um parâmetro enviado pelo cliente.
     */
    @Transactional
    public void apagar(Long id, Long loggedUserId) {
        User user = procurar(id);

        if (id.equals(loggedUserId)) {
            throw new RegraDeNegocioException(
                    "Não pode excluir a sua própria conta.");
        }

        impedirExcluirUltimoSupervisor(user);
        impedirExcluirComTurnos(id);

        // A ordem é a do grafo de chaves estrangeiras: primeiro o que aponta para
        // a conta e é descartável, depois o que aponta para a conta e é
        // preservado, e só no fim a conta. Inverter a primeira com a segunda
        // rebentaria a exclusão, porque as escalas ainda estariam a apontar para
        // alguém que já não existe.
        workModalityScheduleRepository.apagarDoUtilizador(id);
        userLeaveRepository.apagarDoUtilizador(id);
        editionScaleRepository.soltarCriador(id);

        userRepository.delete(user);
    }

    /**
     * Recusa excluir a única conta de supervisão que existe.
     *
     * <p>Conta todos os supervisores, ativos ou não, e não só os ativos como faz
     * a regra equivalente da desativação. A diferença é que uma conta desativada
     * ainda pode ser reativada e portanto ainda é caminho de volta; uma conta
     * excluída não. Deixar o último supervisor desativado para trás seria dar
     * por resolvido um problema que fica exatamente igual: ninguém consegue
     * gerir contas, e a saída passa a ser a base de dados à mão.
     *
     * <p>Só interessa quando o alvo é supervisor: excluir um analista não tira a
     * ninguém a capacidade de gerir contas.
     */
    private void impedirExcluirUltimoSupervisor(User user) {
        if (user.getProfile() != UserProfile.SUPERVISOR) {
            return;
        }

        if (userRepository.countByProfile(UserProfile.SUPERVISOR) <= 1) {
            throw new RegraDeNegocioException(
                    "Não pode excluir o único supervisor: a aplicação ficaria sem ninguém "
                            + "capaz de gerenciar os usuários.");
        }
    }

    /**
     * Recusa excluir alguém que ainda tem turnos marcados.
     *
     * <p>Há duas coisas a bloquear, e as duas porque a base de dados as
     * recusaria com um erro de integridade referencial — que o frontend mostraria
     * como uma falha qualquer em vez de uma instrução. Os turnos, porque a coluna
     * é obrigatória e um turno sem dono é um turno que ninguém vem cobrir. Os
     * pedidos de troca iniciados pela pessoa, porque o pedido guarda quem o pediu
     * e esse vínculo não pode ficar órfão.
     *
     * <p>A mensagem diz o que fazer, e não apenas o que impediu: quem está a
     * ler já sabe que há turnos marcados, e a parte que precisa de ouvir é a que
     * resolve.
     */
    private void impedirExcluirComTurnos(Long id) {
        long turnos = shiftSchedulingRepository.countByUserId(id);
        long pedidos = exchangeRequestRepository.countByRequestingUserId(id);

        if (turnos == 0 && pedidos == 0) {
            return;
        }

        String razao = turnos > 0
                ? turnos + (turnos == 1 ? " turno marcado" : " turnos marcados")
                : pedidos + (pedidos == 1 ? " pedido de troca iniciado" : " pedidos de troca iniciados");

        throw new RegraDeNegocioException(
                "Não é possível excluir a conta: a pessoa tem " + razao
                        + ". Retire-os antes de excluir a conta.");
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
