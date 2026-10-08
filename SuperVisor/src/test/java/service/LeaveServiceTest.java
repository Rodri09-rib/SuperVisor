package service;

import domain.dto.UserLeaveDTO;
import domain.dto.UserLeaveRequestDTO;
import domain.model.entities.User;
import domain.model.entities.UserLeave;
import domain.model.enums.LeaveDuration;
import domain.model.enums.UserProfile;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Folgas — LeaveService")
class LeaveServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIM = LocalDate.of(2026, 3, 6);

    @Mock
    private UserLeaveRepository userLeaveRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeaveService leaveService;

    private User joao;
    private User inativo;

    @BeforeEach
    void setUp() {
        joao = tests.support.TestFixtures.analyst();
        joao.setId(2L);

        inativo = tests.support.TestFixtures.inactiveAnalyst();
        inativo.setId(9L);
    }

    private UserLeaveRequestDTO pedido(Long userId, LocalDate inicio, LocalDate fim, String motivo) {
        return new UserLeaveRequestDTO(userId, inicio, fim, motivo);
    }

    private UserLeave folgaDe(User dono, LocalDate inicio, LocalDate fim, String motivo) {
        UserLeave folga = new UserLeave();
        folga.setId(100L);
        folga.setUser(dono);
        folga.setStartDate(inicio);
        folga.setEndDate(fim);
        folga.setReason(motivo);
        return folga;
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("grava a folga com as datas, o motivo e o dono")
        void gravaFolga() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            leaveService.criar(pedido(2L, INICIO, FIM, "Consulta médica"), UserProfile.SUPERVISOR);

            ArgumentCaptor<UserLeave> captor = ArgumentCaptor.forClass(UserLeave.class);
            verify(userLeaveRepository).save(captor.capture());

            assertThat(captor.getValue().getUser()).isSameAs(joao);
            assertThat(captor.getValue().getStartDate()).isEqualTo(INICIO);
            assertThat(captor.getValue().getEndDate()).isEqualTo(FIM);
            assertThat(captor.getValue().getReason()).isEqualTo("Consulta médica");
        }

        @Test
        @DisplayName("uma folga de segunda a sexta dura cinco dias, com as extremidades incluídas")
        void duracaoComExtremidades() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UserLeaveDTO dto = leaveService.criar(pedido(2L, INICIO, FIM, null),
                    UserProfile.SUPERVISOR);

            // A diferença entre as datas dá 4; quem está de folga está de
            // folga durante 5 dias, e é o 5 que o cartão tem de dizer.
            assertThat(dto.durationDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("uma folga de um só dia dura um dia, e não zero")
        void folgaDeUmDia() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UserLeaveDTO dto = leaveService.criar(pedido(2L, INICIO, INICIO, null),
                    UserProfile.SUPERVISOR);

            assertThat(dto.durationDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("uma folga que começa e acaba no mesmo dia é aceite")
        void mesmoDiaInicioEFim() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(leaveService.criar(pedido(2L, INICIO, INICIO, null), UserProfile.SUPERVISOR)
                    .durationDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("recusa uma folga que termine antes de começar")
        void recusaIntervaloInvertido() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> leaveService.criar(pedido(2L, FIM, INICIO, null),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A data de fim da folga não pode ser anterior à data de início.");

            verify(userLeaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusa registrar folga a um usuário inativo")
        void recusaUtilizadorInativo() {
            when(userRepository.findById(9L)).thenReturn(Optional.of(inativo));

            assertThatThrownBy(() -> leaveService.criar(pedido(9L, INICIO, FIM, null),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Não é possível registrar folga a um usuário inativo.");

            verify(userLeaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("um usuário inexistente dá erro, e nada é gravado")
        void utilizadorInexistente() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveService.criar(pedido(404L, INICIO, FIM, null),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Usuário não encontrado.");

            verify(userLeaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("um motivo só com espaços é guardado como nulo")
        void motivoEmBrancoViraNulo() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            leaveService.criar(pedido(2L, INICIO, FIM, "   "), UserProfile.SUPERVISOR);

            ArgumentCaptor<UserLeave> captor = ArgumentCaptor.forClass(UserLeave.class);
            verify(userLeaveRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isNull();
        }

        @Test
        @DisplayName("um motivo com espaços nas pontas é aparado")
        void motivoAparado() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            leaveService.criar(pedido(2L, INICIO, FIM, "  (baixo)  "), UserProfile.SUPERVISOR);

            ArgumentCaptor<UserLeave> captor = ArgumentCaptor.forClass(UserLeave.class);
            verify(userLeaveRepository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isEqualTo("(baixo)");
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("altera as datas e o motivo, mantendo o dono")
        void atualizaMantendoDono() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, "Consulta médica");
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            leaveService.atualizar(100L, pedido(2L, INICIO.plusDays(1), FIM.plusDays(1), "Ferro"),
                    UserProfile.SUPERVISOR);

            assertThat(folga.getUser()).isSameAs(joao);
            assertThat(folga.getStartDate()).isEqualTo(INICIO.plusDays(1));
            assertThat(folga.getEndDate()).isEqualTo(FIM.plusDays(1));
            assertThat(folga.getReason()).isEqualTo("Ferro");
        }

        @Test
        @DisplayName("o userId do corpo é ignorado: uma correção não muda o dono da ausência")
        void ignoraUserIdDoCorpo() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, "Ferro");
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // O corpo aponta para o id 9, de outro usuário.
            leaveService.atualizar(100L, pedido(9L, INICIO, FIM, null), UserProfile.SUPERVISOR);

            // Se o serviço obedecesse, a ausência passaria a dizer que o
            // usuário que faltou era outro, e o calendário da equipe
            // passaria a errar em nome de alguém que não faltou.
            assertThat(folga.getUser()).isSameAs(joao);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("recusa inverter o intervalo, e a folga fica como estava")
        void recusaInversao() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, "Ferro");
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));

            assertThatThrownBy(() -> leaveService.atualizar(100L, pedido(2L, FIM, INICIO, null),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(IllegalArgumentException.class);

            // A validação tem de acontecer antes de tocar na entidade: uma
            // atualização recusada que deixasse as datas a meia escrever era
            // pior do que não fazer nada.
            assertThat(folga.getStartDate()).isEqualTo(INICIO);
            assertThat(folga.getEndDate()).isEqualTo(FIM);
            verify(userLeaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("uma folga inexistente dá erro")
        void folgaInexistente() {
            when(userLeaveRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveService.atualizar(404L, pedido(2L, INICIO, FIM, null),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Folga não encontrada.");
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("apaga a folga existente")
        void apaga() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, null);
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));

            leaveService.apagar(100L);

            verify(userLeaveRepository).delete(folga);
        }

        @Test
        @DisplayName("uma folga inexistente dá erro, e nada é apagado")
        void folgaInexistente() {
            when(userLeaveRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveService.apagar(404L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Folga não encontrada.");

            verify(userLeaveRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("sem filtro, devolve as folgas de toda a equipe")
        void todas() {
            when(userLeaveRepository.listar(null))
                    .thenReturn(List.of(folgaDe(joao, INICIO, FIM, "Ferro")));

            var resultado = leaveService.listar(null, UserProfile.ANALIST);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).userName()).isEqualTo("João");
            assertThat(resultado.get(0).durationDays()).isEqualTo(5);
        }

        @Test
        @DisplayName("com filtro, devolve só as folgas dessa pessoa")
        void porPessoa() {
            when(userLeaveRepository.listar(2L))
                    .thenReturn(List.of(folgaDe(joao, INICIO, FIM, null)));

            var resultado = leaveService.listar(2L, UserProfile.SUPERVISOR);

            assertThat(resultado).hasSize(1);
            verify(userLeaveRepository).listar(2L);
        }

        @Test
        @DisplayName("um analista também vê as folgas dos outros, porque a falta é informação de equipe")
        void analistaVeTodas() {
            when(userLeaveRepository.listar(null))
                    .thenReturn(List.of(folgaDe(joao, INICIO, FIM, "Ferro")));

            var resultado = leaveService.listar(null, UserProfile.ANALIST);

            // Ao contrário do histórico de trocas, aqui não há filtro por
            // perfil: quem está de folga já é visível no calendário da equipe, e
            // esconder isso só faria com que alguém soubesse da falta no dia em
            // que ela acontece.
            assertThat(resultado).hasSize(1);
            verify(userLeaveRepository).listar(null);
        }
    }

    @Nested
    @DisplayName("O que cada perfil pode fazer")
    class Permissoes {

        @Test
        @DisplayName("só o supervisor recebe canEdit a true")
        void canEditSegueOPerfil() {
            when(userLeaveRepository.listar(null))
                    .thenReturn(List.of(folgaDe(joao, INICIO, FIM, null)));

            assertThat(leaveService.listar(null, UserProfile.SUPERVISOR).get(0).canEdit()).isTrue();
            assertThat(leaveService.listar(null, UserProfile.ANALIST).get(0).canEdit()).isFalse();
        }

        @Test
        @DisplayName("a regra de escrita está no serviço, não só no perfil do DTO")
        void regraDeEscritaNoServico() {
            // canEdit é uma conveniência para a interface esconder o botão. O
            // que impede o pedido é o perfil verificado no serviço, e por isso
            // o serviço tem de continuar a funcionar para quem não é
            // supervisor — quem rejeita é o controller, antes de chegar aqui.
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            var dto = leaveService.criar(pedido(2L, INICIO, FIM, null), UserProfile.ANALIST);

            assertThat(dto.canEdit()).isFalse();
            assertThat(dto.userId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("Anotações transacionais")
    class Transacoes {

        @Test
        @DisplayName("as escritas são transacionais, e a leitura é só de leitura")
        void anotacoes() throws NoSuchMethodException {
            // Sem @Transactional, uma escrita que falhasse a meio — a validação
            // do intervalo, por exemplo — deixaria a sessão e a base em
            // estados que ninguém pediu. E sem readOnly na leitura, a
            // persistência abre uma transação de escrita para um GET.
            assertThat(LeaveService.class.getMethod("criar", UserLeaveRequestDTO.class,
                    UserProfile.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();
            assertThat(LeaveService.class.getMethod("atualizar", Long.class,
                    UserLeaveRequestDTO.class, UserProfile.class)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();
            assertThat(LeaveService.class.getMethod("apagar", Long.class)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();

            var listar = LeaveService.class.getMethod("listar", Long.class, UserProfile.class)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(listar).isNotNull();
            assertThat(listar.readOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("O saldo de folgas que as operações movem")
    class Saldos {

        @Test
        @DisplayName("criar uma folga debita os dias ao saldo de quem a tem")
        void criarDebitaOsaldo() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UserLeaveDTO dto = leaveService.criar(pedido(2L, INICIO, FIM, null),
                    UserProfile.SUPERVISOR);

            // A folga de segunda a sexta consome cinco dias, e o saldo nasce
            // a zero: fica a -5, que é a página a dizer «ainda não compensou
            // nada do que usou».
            assertThat(dto.costDays()).isEqualByComparingTo("5");
            assertThat(dto.leaveDuration()).isEqualTo(LeaveDuration.FULL_DAY);
            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo("-5");
        }

        @Test
        @DisplayName("uma folga de meio dia consome meio dia, e não um dia inteiro")
        void folgaDeMeioDiaConsomeMeio() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UserLeaveDTO dto = leaveService.criar(new UserLeaveRequestDTO(
                    2L, INICIO, INICIO, null, LeaveDuration.MORNING_SHIFT),
                    UserProfile.SUPERVISOR);

            assertThat(dto.costDays()).isEqualByComparingTo("0.5");
            assertThat(dto.leaveDurationLabel()).isEqualTo("Apenas manhã (08h-12h)");
            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo("-0.5");
        }

        @Test
        @DisplayName("uma folga de meio dia em vários dias é recusada, e o saldo não se mexe")
        void folgaParcialTemDeSerNumDia() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> leaveService.criar(new UserLeaveRequestDTO(
                            2L, INICIO, FIM, null, LeaveDuration.AFTERNOON_SHIFT),
                    UserProfile.SUPERVISOR))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Uma folga de meia jornada tem de ser num único dia: "
                            + "escolha o mesmo dia de início e de fim.");

            verify(userLeaveRepository, never()).save(any());
            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("encurtar uma folga devolve os dias que deixaram de ser usados")
        void atualizarDevolveOsDiasSobrados() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, null);
            joao.setAccumulatedLeaves(new BigDecimal("-5"));
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));
            when(userLeaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            leaveService.atualizar(100L, pedido(2L, INICIO, INICIO.plusDays(2), null),
                    UserProfile.SUPERVISOR);

            // Devolve os 5 e cobra os 3 da folga nova: é uma substituição de
            // cobrança, não uma cobrança em cima da outra.
            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo("-3");
        }

        @Test
        @DisplayName("apagar uma folga devolve os dias ao saldo de quem a tinha")
        void apagarDevolveOsDias() {
            UserLeave folga = folgaDe(joao, INICIO, FIM, null);
            joao.setAccumulatedLeaves(new BigDecimal("-5"));
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));

            leaveService.apagar(100L);

            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(userLeaveRepository).delete(folga);
        }

        @Test
        @DisplayName("uma folga de meio dia apagada devolve meio dia, e não um")
        void apagarFolgaParcialDevolveMeio() {
            UserLeave folga = folgaDe(joao, INICIO, INICIO, null);
            folga.setLeaveDuration(LeaveDuration.AFTERNOON_SHIFT);
            joao.setAccumulatedLeaves(new BigDecimal("-0.5"));
            when(userLeaveRepository.findById(100L)).thenReturn(Optional.of(folga));

            leaveService.apagar(100L);

            assertThat(joao.getAccumulatedLeaves()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Folga sem dono, e o que a API devolve")
    class FolgaSemDono {

        @Test
        @DisplayName("uma folga sem usuário não rebenta a serialização")
        void folgaSemUtilizador() {
            // Uma leitura que trouxesse uma folga órfã — o que a base impede
            // hoje, mas que uma migração podia deixar — não pode rebentar a
            // página inteira com um NullPointerException.
            var orfa = new UserLeave();
            orfa.setStartDate(INICIO);
            orfa.setEndDate(FIM);

            UserLeaveDTO dto = UserLeaveDTO.from(orfa, UserProfile.SUPERVISOR);

            assertThat(dto.userId()).isNull();
            assertThat(dto.userName()).isNull();
            assertThat(dto.durationDays()).isEqualTo(5);
        }
    }
}
