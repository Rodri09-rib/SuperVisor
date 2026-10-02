package service;

import domain.dto.ActiveUserDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import domain.repository.WorkModalityScheduleRepository;
import exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tests.support.TestFixtures;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final String EMAIL_LIVRE = "nova.pessoa@teste.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private UserLeaveRepository userLeaveRepository;

    @Mock
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Mock
    private EditionScaleRepository editionScaleRepository;

    @Mock
    private ExchangeRequestRepository exchangeRequestRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "shiftSchedulingRepository", shiftSchedulingRepository);
        ReflectionTestUtils.setField(service, "userLeaveRepository", userLeaveRepository);
        ReflectionTestUtils.setField(service, "workModalityScheduleRepository",
                workModalityScheduleRepository);
        ReflectionTestUtils.setField(service, "editionScaleRepository", editionScaleRepository);
        ReflectionTestUtils.setField(service, "exchangeRequestRepository", exchangeRequestRepository);
        // O BCrypt real, e não um mock: o que se verifica aqui é a criptografia
        // propriamente dita, e um encoder simulado confirmaria sempre o que
        // lhe fosse pedido.
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder());
    }

    private CreateUserRequestDTO pedido() {
        return new CreateUserRequestDTO(
                "João Silva", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, null, null);
    }

    /** Faz o e-mail parecer livre e o save devolver a própria entidade. */
    private void emailLivre() {
        when(userRepository.findByEmail(EMAIL_LIVRE)).thenReturn(null);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    /** As entidades que chegaram ao repositório, por ordem de chamada. */
    private List<User> gravados() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("Usuário novo")
    class Sucesso {

        @Test
        @DisplayName("grava a senha criptografada, nunca em claro")
        void encriptaAPassword() {
            emailLivre();

            service.criar(pedido());

            String hash = gravados().get(0).getPassword();
            assertThat(hash).isNotEqualTo("segredo123").startsWith("$2");
        }

        @Test
        @DisplayName("a senha gravada deixa o BCrypt reconhecer a senha original")
        void passwordGravadaAutentica() {
            emailLivre();

            service.criar(pedido());

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            assertThat(encoder.matches("segredo123", gravados().get(0).getPassword())).isTrue();
        }

        @Test
        @DisplayName("duas senhas iguais dão hashes diferentes, por causa do sal do BCrypt")
        void hashNaoEhDeterministico() {
            emailLivre();

            service.criar(pedido());
            service.criar(pedido());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getPassword())
                    .isNotEqualTo(captor.getAllValues().get(1).getPassword());
        }

        @Test
        @DisplayName("salva o nome sem espaços nas pontas e o e-mail tal como foi enviado")
        void limpaONome() {
            emailLivre();

            service.criar(new CreateUserRequestDTO(
                    "  João Silva  ", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, null, null));

            User gravado = gravados().get(0);
            assertThat(gravado.getName()).isEqualTo("João Silva");
            assertThat(gravado.getEmail()).isEqualTo(EMAIL_LIVRE);
        }

        @Test
        @DisplayName("um pedido sem o campo active grava a conta ativa")
        void activePorOmissao() {
            emailLivre();

            service.criar(pedido());

            assertThat(gravados().get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("active=false no pedido é respeitado, e não substituído pelo predefinido")
        void activeFalseERespeitado() {
            emailLivre();

            service.criar(new CreateUserRequestDTO(
                    "João Silva", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, null, false));

            assertThat(gravados().get(0).isActive()).isFalse();
        }

        @Test
        @DisplayName("devolve o usuário criado, sem a senha no resultado")
        void respostaNaoIncluiASenha() {
            when(userRepository.findByEmail(EMAIL_LIVRE)).thenReturn(null);
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocacao -> {
                        User user = invocacao.getArgument(0);
                        user.setId(7L);
                        return user;
                    });

            ActiveUserDTO resposta = service.criar(pedido());

            assertThat(resposta.id()).isEqualTo(7L);
            assertThat(resposta.name()).isEqualTo("João Silva");
            assertThat(resposta.email()).isEqualTo(EMAIL_LIVRE);
            assertThat(resposta.profile()).isEqualTo(UserProfile.ANALIST);
        }
    }

    @Nested
    @DisplayName("E-mail já cadastrado")
    class EmailEmUso {

        @Test
        @DisplayName("lança RegraDeNegocioException com mensagem para o usuário")
        void lancaRegraDeNegocio() {
            when(userRepository.findByEmail(EMAIL_LIVRE))
                    .thenReturn(TestFixtures.supervisor());

            assertThatThrownBy(() -> service.criar(pedido()))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("E-mail já cadastrado.");
        }

        @Test
        @DisplayName("não chega a gravar, para não bater na restrição unique da coluna")
        void naoGrava() {
            when(userRepository.findByEmail(EMAIL_LIVRE))
                    .thenReturn(TestFixtures.supervisor());

            assertThatThrownBy(() -> service.criar(pedido()))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    /** Faz o `save` devolver a própria entidade, como faz o JPA. */
    private void saveDevolveOProprio() {
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private User existente(Long id) {
        User user = TestFixtures.analyst();
        user.setId(id);
        return user;
    }

    private UpdateUserRequestDTO alteracao(String nome, UserProfile perfil, TeamGroup equipa) {
        return new UpdateUserRequestDTO(nome, perfil, equipa);
    }

    @Nested
    @DisplayName("Criar com equipe atribuída")
    class EquipaNaCriacao {

        @Test
        @DisplayName("grava a equipe que veio no pedido, para a pessoa poder ser escalada")
        void gravaEquipa() {
            emailLivre();

            service.criar(new CreateUserRequestDTO(
                    "João", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST,
                    TeamGroup.EQUIPE_A, null));

            assertThat(gravados().get(0).getTeamGroup()).isEqualTo(TeamGroup.EQUIPE_A);
        }

        @Test
        @DisplayName("uma conta sem equipe continua válida: as contas antigas não têm equipe")
        void equipaAusenteGravaNula() {
            emailLivre();

            service.criar(pedido());

            assertThat(gravados().get(0).getTeamGroup()).isNull();
        }
    }

    @Nested
    @DisplayName("Alteração de cadastro")
    class Alteracao {

        @Test
        @DisplayName("grava nome, perfil e equipe, e devolve o usuário alterado")
        void alteraOsTresCampos() {
            User user = existente(3L);
            user.setTeamGroup(null);
            when(userRepository.findById(3L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            ActiveUserDTO resposta = service.atualizar(3L,
                    alteracao("  Maria Costa  ", UserProfile.ANALIST, TeamGroup.EQUIPE_B));

            assertThat(resposta.name()).isEqualTo("Maria Costa");
            assertThat(resposta.teamGroup()).isEqualTo(TeamGroup.EQUIPE_B);
            assertThat(resposta.teamGroupLabel()).isEqualTo("Equipe B");
            assertThat(gravados().get(0).getProfile()).isEqualTo(UserProfile.ANALIST);
        }

        @Test
        @DisplayName("teamGroup nulo retira alguém da equipe, e não é ignorado como omissão")
        void equipaNulaRetiraDaEquipa() {
            User user = TestFixtures.userInTeam(
                    "Maria", "maria@teste.com", UserProfile.ANALIST, TeamGroup.EQUIPE_A);
            user.setId(4L);
            when(userRepository.findById(4L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            ActiveUserDTO resposta = service.atualizar(
                    4L, alteracao("Maria", UserProfile.ANALIST, null));

            assertThat(resposta.teamGroup()).isNull();
            assertThat(resposta.teamGroupLabel()).isEqualTo("Sem equipe");
        }

        @Test
        @DisplayName("o e-mail fica intacto: é a identidade da conta e a chave do token")
        void emailNaoETocavel() {
            User user = existente(5L);
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            service.atualizar(5L, alteracao("Outro Nome", UserProfile.SUPERVISOR, null));

            assertThat(gravados().get(0).getEmail()).isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("alterar o perfil de um usuário existente é permitido")
        void promoveAAnalista() {
            User user = existente(6L);
            when(userRepository.findById(6L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            service.atualizar(6L, alteracao("João", UserProfile.SUPERVISOR, null));

            assertThat(gravados().get(0).getProfile()).isEqualTo(UserProfile.SUPERVISOR);
        }

        @Test
        @DisplayName("usuário inexistente dá mensagem clara")
        void inexistenteDaErro() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizar(
                    99L, alteracao("Maria", UserProfile.ANALIST, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Usuário não encontrado.");
        }

        @Test
        @DisplayName("id nulo também é usuário inexistente, e não um NPE")
        void idNuloDaErro() {
            assertThatThrownBy(() -> service.atualizar(
                    null, alteracao("Maria", UserProfile.ANALIST, null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Usuário não encontrado.");
        }

        @Test
        @DisplayName("quando o usuário não existe nada é gravado")
        void inexistenteNaoGrava() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizar(
                    99L, alteracao("Maria", UserProfile.ANALIST, null)))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Ativação e desativação")
    class EstadoDaConta {

        private final Long ID = 10L;

        @Test
        @DisplayName("desativa um analista sem perguntar por supervisores")
        void desativaAnalista() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            ActiveUserDTO resposta = service.alterarEstado(ID, false, 99L);

            assertThat(resposta.active()).isFalse();
            assertThat(gravados().get(0).isActive()).isFalse();
            verify(userRepository, never())
                    .countByProfileAndActiveTrue(any(UserProfile.class));
        }

        @Test
        @DisplayName("reativa uma conta desativada, que de outra forma não tinha caminho de volta")
        void reativaContaDesativada() {
            User user = TestFixtures.inactiveAnalyst();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            ActiveUserDTO resposta = service.alterarEstado(ID, true, 99L);

            assertThat(resposta.active()).isTrue();
            assertThat(gravados().get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("reativar nunca é bloqueado pela regra do último supervisor")
        void reativaSemprePassa() {
            User supervisorDesativado = TestFixtures.supervisor();
            supervisorDesativado.setId(ID);
            supervisorDesativado.setActive(false);
            when(userRepository.findById(ID)).thenReturn(Optional.of(supervisorDesativado));
            saveDevolveOProprio();

            assertThat(service.alterarEstado(ID, true, 99L).active()).isTrue();

            verify(userRepository, never())
                    .countByProfileAndActiveTrue(any(UserProfile.class));
        }

        @Test
        @DisplayName("o supervisor não se desativa a si próprio")
        void naoSeDesativa() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.alterarEstado(ID, false, ID))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Não pode desativar a sua própria conta.");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("a autodesativação é o primeiro motivo recusado, mesmo sendo o único supervisor")
        void autodesativacaoTemPrecedencia() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.alterarEstado(ID, false, ID))
                    .hasMessage("Não pode desativar a sua própria conta.");

            verify(userRepository, never())
                    .countByProfileAndActiveTrue(any(UserProfile.class));
        }

        @Test
        @DisplayName("o último supervisor ativo não pode ser desativado, ou ninguém geriria contas")
        void ultimoSupervisorNaoPodeSerDesativado() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(userRepository.countByProfileAndActiveTrue(UserProfile.SUPERVISOR))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.alterarEstado(ID, false, 99L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("único supervisor ativo");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("com outro supervisor ativo, desativar um deles é uma decisão normal e passa")
        void desativaSupervisorQuandoHaOutroAtivo() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(userRepository.countByProfileAndActiveTrue(UserProfile.SUPERVISOR))
                    .thenReturn(2L);
            saveDevolveOProprio();

            assertThat(service.alterarEstado(ID, false, 99L).active()).isFalse();
        }

        @Test
        @DisplayName("pedir o estado que a conta já tem não grava nemhetade de nada")
        void estadoJaIgualNaoGrava() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));

            ActiveUserDTO resposta = service.alterarEstado(ID, true, 99L);

            assertThat(resposta.active()).isTrue();
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("usuário inexistente dá erro em vez de criar uma conta nova")
        void inexistenteDaErro() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.alterarEstado(99L, false, 1L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Usuário não encontrado.");
        }
    }

    @Nested
    @DisplayName("Redefinição de senha")
    class RedefinicaoDeSenha {

        @Test
        @DisplayName("grava a nova senha criptografada, e nunca em claro")
        void encriptaANovaSenha() {
            User user = existente(20L);
            when(userRepository.findById(20L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            service.redefinirSenha(20L, "novasenha123");

            String hash = gravados().get(0).getPassword();
            assertThat(hash).isNotEqualTo("novasenha123").startsWith("$2");
        }

        @Test
        @DisplayName("a nova senha é a que passa a autenticar")
        void novaSenhaAutentica() {
            User user = existente(21L);
            when(userRepository.findById(21L)).thenReturn(Optional.of(user));
            saveDevolveOProprio();

            service.redefinirSenha(21L, "novasenha123");

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            assertThat(encoder.matches("novasenha123", gravados().get(0).getPassword())).isTrue();
        }

        @Test
        @DisplayName("usuário inexistente dá erro e não grava nada")
        void inexistenteDaErro() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.redefinirSenha(99L, "novasenha123"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Usuário não encontrado.");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Vista de administração")
    class VistaDeAdministracao {

        @Test
        @DisplayName("inclui as contas desativadas, que o seletor de pessoas não mostra")
        void incluiInativos() {
            User ativo = TestFixtures.analyst();
            User inativo = TestFixtures.inactiveAnalyst();
            when(userRepository.findAllByOrderByNameAsc())
                    .thenReturn(List.of(ativo, inativo));

            List<ActiveUserDTO> resposta = service.listarTodos();

            assertThat(resposta).hasSize(2);
            assertThat(resposta.get(0).active()).isTrue();
            assertThat(resposta.get(1).active()).isFalse();
        }

        @Test
        @DisplayName("a conta sem equipe é rotulada em vez de vir com a equipe a null")
        void rotulaQuemNaoTemEquipa() {
            User semEquipa = TestFixtures.analyst();
            when(userRepository.findAllByOrderByNameAsc()).thenReturn(List.of(semEquipa));

            assertThat(service.listarTodos().get(0).teamGroupLabel()).isEqualTo("Sem equipe");
        }
    }

    @Nested
    @DisplayName("Exclusão definitiva")
    class Exclusao {

        private final Long ID = 30L;

        /** Alvo sem nenhum vínculo, que é o caso normal de quem sai da empresa. */
        private User semVinculos() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(shiftSchedulingRepository.countByUserId(ID)).thenReturn(0L);
            when(exchangeRequestRepository.countByRequestingUserId(ID)).thenReturn(0L);
            return user;
        }

        @Test
        @DisplayName("apaga a conta de quem não tem turnos nem folgas nem presença")
        void apagaContaLimpa() {
            User user = semVinculos();

            service.apagar(ID, 99L);

            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("leva as folgas e a escala de presencialidade da pessoa, que não fazem sentido sem ela")
        void apagaOQueDescreveAPessoa() {
            semVinculos();

            service.apagar(ID, 99L);

            verify(userLeaveRepository).apagarDoUtilizador(ID);
            verify(workModalityScheduleRepository).apagarDoUtilizador(ID);
        }

        @Test
        @DisplayName("solta as escalas que a pessoa criou em vez de as apagar: a escala sobrevive à conta")
        void apagaMasPreservaEscalas() {
            semVinculos();

            service.apagar(ID, 99L);

            verify(editionScaleRepository).soltarCriador(ID);
            verify(editionScaleRepository, never()).delete(any());
        }

        @Test
        @DisplayName("a ordem respeita o grafo: o que aponta para a conta cai antes de a conta")
        void apagaPorOrdemDeGrafo() {
            semVinculos();

            service.apagar(ID, 99L);

            InOrder ordem = inOrder(workModalityScheduleRepository, userLeaveRepository,
                    editionScaleRepository, userRepository);
            ordem.verify(workModalityScheduleRepository).apagarDoUtilizador(ID);
            ordem.verify(userLeaveRepository).apagarDoUtilizador(ID);
            ordem.verify(editionScaleRepository).soltarCriador(ID);
            ordem.verify(userRepository).delete(any(User.class));
        }

        @Test
        @DisplayName("recusa quem tem turnos marcados, e diz quantos")
        void recusaComTurnosMarcados() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(shiftSchedulingRepository.countByUserId(ID)).thenReturn(3L);

            assertThatThrownBy(() -> service.apagar(ID, 99L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("3 turnos marcados")
                    .hasMessageContaining("Retire-os antes");

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("um turno só é dito no singular, porque é uma pessoa a ler")
        void singularQuandoSoHaUmTurno() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(shiftSchedulingRepository.countByUserId(ID)).thenReturn(1L);

            assertThatThrownBy(() -> service.apagar(ID, 99L))
                    .hasMessageContaining("1 turno marcado");
        }

        @Test
        @DisplayName("recusa quem iniciou pedidos de troca, mesmo sem turnos, porque o pedido aponta para a conta")
        void recusaComPedidosDeTroca() {
            User user = existente(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(shiftSchedulingRepository.countByUserId(ID)).thenReturn(0L);
            when(exchangeRequestRepository.countByRequestingUserId(ID)).thenReturn(2L);

            assertThatThrownBy(() -> service.apagar(ID, 99L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("2 pedidos de troca iniciados");

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("não se exclui a si próprio")
        void naoSeExclui() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.apagar(ID, ID))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Não pode excluir a sua própria conta.");

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("a autoexclusão tem precedência sobre o resto, mesmo sendo o único supervisor")
        void autoexclusaoTemPrecedencia() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.apagar(ID, ID))
                    .hasMessage("Não pode excluir a sua própria conta.");

            verify(userRepository, never()).countByProfile(any(UserProfile.class));
        }

        @Test
        @DisplayName("o único supervisor não pode ser excluído, nem desativado, porque a aplicação ficava sem gestão de contas")
        void ultimoSupervisorNaoPodeSerExcluido() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(userRepository.countByProfile(UserProfile.SUPERVISOR)).thenReturn(1L);

            assertThatThrownBy(() -> service.apagar(ID, 99L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("único supervisor");

            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("conta todos os supervisores, ativos ou não: um supervisor desativado não é caminho de volta")
        void contaSupervisoresInativos() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            user.setActive(false);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(userRepository.countByProfile(UserProfile.SUPERVISOR)).thenReturn(1L);

            assertThatThrownBy(() -> service.apagar(ID, 99L))
                    .hasMessageContaining("único supervisor");

            verify(userRepository, never()).countByProfileAndActiveTrue(any(UserProfile.class));
        }

        @Test
        @DisplayName("com dois supervisores, excluir um deles é uma decisão normal e passa")
        void excluiSupervisorQuandoHaOutro() {
            User user = TestFixtures.supervisor();
            user.setId(ID);
            when(userRepository.findById(ID)).thenReturn(Optional.of(user));
            when(userRepository.countByProfile(UserProfile.SUPERVISOR)).thenReturn(2L);
            when(shiftSchedulingRepository.countByUserId(ID)).thenReturn(0L);
            when(exchangeRequestRepository.countByRequestingUserId(ID)).thenReturn(0L);

            service.apagar(ID, 99L);

            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("excluir um analista nunca pergunta por supervisores")
        void excluirAnalistaNaoContaSupervisores() {
            semVinculos();

            service.apagar(ID, 99L);

            verify(userRepository, never()).countByProfile(any(UserProfile.class));
        }

        @Test
        @DisplayName("usuário inexistente dá erro e não apaga nada")
        void inexistenteDaErro() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.apagar(99L, 1L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Usuário não encontrado.");

            verify(userRepository, never()).delete(any(User.class));
        }
    }
}
