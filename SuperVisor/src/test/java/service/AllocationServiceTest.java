package service;

import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationService")
class AllocationServiceTest {

    private static final String SABADO = "S\u00e1bado";

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private EditionScaleRepository editionScaleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExchangeRequestRepository exchangeRequestRepository;

    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService();
        ReflectionTestUtils.setField(service, "shiftSchedulingRepository", shiftSchedulingRepository);
        ReflectionTestUtils.setField(service, "editionScaleRepository", editionScaleRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "exchangeRequestRepository", exchangeRequestRepository);
    }

    private EditionScale escala(Long id) {
        EditionScale escala = TestFixtures.draftScale(TestFixtures.supervisor());
        escala.setId(id);
        return escala;
    }

    private User analistaAtivo(Long id) {
        User user = TestFixtures.analyst();
        user.setId(id);
        return user;
    }

    private User analistaInativo(Long id) {
        User user = TestFixtures.inactiveAnalyst();
        user.setId(id);
        return user;
    }

    private AllocationRequestDTO pedido(ShiftType turno) {
        return new AllocationRequestDTO(10L, 5L, turno, null, null, null, null);
    }

    /** Simula o repositorio a devolver a entidade gravada, com id atribuído. */
    private void gravacaoComId(Long id) {
        when(shiftSchedulingRepository.save(any(ShiftScheduling.class))).thenAnswer(invocacao -> {
            ShiftScheduling alocacao = invocacao.getArgument(0);
            alocacao.setId(id);
            return alocacao;
        });
    }

    private ShiftScheduling capturada() {
        ArgumentCaptor<ShiftScheduling> captor = ArgumentCaptor.forClass(ShiftScheduling.class);
        verify(shiftSchedulingRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("resolve usuário e escala, e grava todos os campos")
        void gravaTodosOsCampos() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            gravacaoComId(99L);

            AllocationDTO dto = service.create(new AllocationRequestDTO(10L, 5L, ShiftType.T2_SAB,
                    List.of(AssignmentType.REDES_SOCIAIS), LocalTime.of(11, 30), LocalTime.of(14, 30),
                    LocalDate.of(2025, 10, 4)));

            assertThat(dto.id()).isEqualTo(99L);
            assertThat(dto.editionScaleId()).isEqualTo(10L);
            assertThat(dto.userId()).isEqualTo(5L);
            assertThat(dto.shift()).isEqualTo(ShiftType.T2_SAB);
            assertThat(dto.shiftAcronym()).isEqualTo("T2");
            assertThat(dto.shiftDayOfTheWeek()).isEqualTo(SABADO);
            assertThat(dto.assignments()).containsExactly(AssignmentType.REDES_SOCIAIS);
            assertThat(dto.assignmentLabels()).containsExactly("Redes Sociais");
            assertThat(dto.customSchedule()).isTrue();
            assertThat(dto.customStartTime()).isEqualTo(LocalTime.of(11, 30));
            assertThat(dto.specificDate()).isEqualTo(LocalDate.of(2025, 10, 4));

            ShiftScheduling alocacao = capturada();
            assertThat(alocacao.getShift()).isEqualTo(ShiftType.T2_SAB);
            assertThat(alocacao.getAssignments()).containsExactly(AssignmentType.REDES_SOCIAIS);
        }

        @Test
        @DisplayName("sem atribuições nem horário especial, a alocação fica sem horário customizado")
        void alocacaoMinima() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            gravacaoComId(1L);

            AllocationDTO dto = service.create(pedido(ShiftType.T1_SAB));

            assertThat(dto.assignments()).isEmpty();
            assertThat(dto.assignmentLabels()).isEmpty();
            assertThat(dto.customSchedule()).isFalse();
            assertThat(dto.specificDate()).isNull();
            assertThat(capturada().getAssignments()).isEmpty();
        }

        @Test
        @DisplayName("aceita o horário 21h00-00h00 em T5")
        void aceitaHorarioDoT5() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            gravacaoComId(1L);

            AllocationDTO dto = service.create(new AllocationRequestDTO(10L, 5L, ShiftType.T5_SAB,
                    null, LocalTime.of(21, 0), LocalTime.MIDNIGHT, null));

            assertThat(dto.customStartTime()).isEqualTo(LocalTime.of(21, 0));
            assertThat(dto.customEndTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("rejeita um pedido sem turno")
        void exigeTurno() {
            assertThatThrownBy(() -> service.create(pedido(null)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Escolha o turno do fim de semana.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita um pedido sem usuário")
        void exigeUtilizador() {
            AllocationRequestDTO dto = new AllocationRequestDTO(10L, null, ShiftType.T1_SAB,
                    null, null, null, null);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Escolha o usuário que cobre o turno.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita um pedido sem escala")
        void exigeEscala() {
            AllocationRequestDTO dto = new AllocationRequestDTO(null, 5L, ShiftType.T1_SAB,
                    null, null, null, null);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Escolha a escala a que a aloca\u00e7\u00e3o pertence.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita um usuário inexistente")
        void rejeitaUtilizadorInexistente() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Usuário n\u00e3o encontrado.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita uma escala inexistente")
        void rejeitaEscalaInexistente() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Edi\u00e7\u00e3o de Escala n\u00e3o encontrada.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita um usuário inativo")
        void rejeitaUtilizadorInativo() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaInativo(5L)));

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("est\u00e1 inativo e n\u00e3o pode receber turnos.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("exige o inicio e o fim do horário especial em conjunto")
        void exigeInicioEFimEmConjunto() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));

            AllocationRequestDTO soInicio = new AllocationRequestDTO(10L, 5L, ShiftType.T1_SAB,
                    null, LocalTime.of(9, 0), null, null);

            assertThatThrownBy(() -> service.create(soInicio))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("precisa do in\u00edcio e do fim");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita um horário especial fora do turno")
        void rejeitaHorarioForaDoTurno() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));

            AllocationRequestDTO fora = new AllocationRequestDTO(10L, 5L, ShiftType.T1_SAB,
                    null, LocalTime.of(7, 0), LocalTime.of(11, 0), null);

            assertThatThrownBy(() -> service.create(fora))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("tem de estar dentro do turno");
            verify(shiftSchedulingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("substitui os campos editáveis da alocação existente")
        void substituiCamposEditaveis() {
            ShiftScheduling existente = TestFixtures.allocation(escala(10L), analistaAtivo(5L));
            existente.setId(42L);
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.of(existente));
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            gravacaoComId(42L);

            AllocationDTO dto = service.update(42L, new AllocationRequestDTO(10L, 5L,
                    ShiftType.T4_SAB, List.of(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS),
                    LocalTime.of(17, 0), LocalTime.of(19, 0), LocalDate.of(2025, 10, 11)));

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.shift()).isEqualTo(ShiftType.T4_SAB);
            assertThat(dto.assignments())
                    .containsExactly(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
            assertThat(dto.assignmentLabels())
                    .containsExactly("Redes Sociais", "Celular da Marinas");
            assertThat(dto.specificDate()).isEqualTo(LocalDate.of(2025, 10, 11));
            assertThat(existente.getShift()).isEqualTo(ShiftType.T4_SAB);
        }

        @Test
        @DisplayName("rejeita uma alocação inexistente")
        void rejeitaAlocacaoInexistente() {
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(404L, pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Aloca\u00e7\u00e3o n\u00e3o encontrada.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("aplica as mesmas regras de validação da criação")
        void reaplicaAsValidacoesDaCriacao() {
            ShiftScheduling existente = TestFixtures.allocation(escala(10L), analistaAtivo(5L));
            existente.setId(42L);
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.of(existente));
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaInativo(5L)));

            assertThatThrownBy(() -> service.update(42L, pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("est\u00e1 inativo e n\u00e3o pode receber turnos.");
            verify(shiftSchedulingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Sobreposição de turnos da mesma pessoa")
    class Sobreposicao {

        /** O repositório devolve as alocações que a pessoa já tem na escala. */
        private void jaTem(ShiftScheduling... existentes) {
            when(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(10L, 5L))
                    .thenReturn(List.of(existentes));
        }

        private ShiftScheduling existente(ShiftType turno, LocalDate data) {
            ShiftScheduling alocacao = TestFixtures.allocation(escala(10L), analistaAtivo(5L), turno, data);
            alocacao.setId(100L);
            return alocacao;
        }

        @Test
        @DisplayName("recusa T2 quando a pessoa já tem T1, porque os horários se cruzam")
        void recusaSobreposicaoReal() {
            // A regra que faltava: nada impedia que a mesma pessoa ficasse com
            // T1 (08h00-12h00) e T2 (11h00-15h00) no mesmo sábado, o que produz
            // uma escala impossível de cumprir.
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, null));

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T2_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("já tem o turno")
                    .hasMessageContaining("sobrepõem-se");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a mensagem diz qual é o turno que já lá está")
        void mensagemNomeiaOTurnoQueJaLaEsta() {
            // "Conflito" sozinho não ajuda ninguém a decidir: a correção é mudar
            // um dos turnos, e para isso é preciso saber qual.
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, null));

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T2_SAB)))
                    .hasMessageContaining("T1 - Sábado")
                    .hasMessageContaining("08h00-12h00");
        }

        @Test
        @DisplayName("aceita T1 e T3 para a mesma pessoa: turnos seguidos não se cruzam")
        void aceitaTurnosSeguidos() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, null));
            gravacaoComId(99L);

            AllocationDTO dto = service.create(pedido(ShiftType.T3_SAB));

            assertThat(dto.shift()).isEqualTo(ShiftType.T3_SAB);
        }

        @Test
        @DisplayName("aceita o mesmo turno em dias específicos diferentes")
        void aceitaMesmoTurnoEmDiasDiferentes() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, LocalDate.of(2025, 10, 4)));
            gravacaoComId(99L);

            AllocationDTO dto = service.create(new AllocationRequestDTO(10L, 5L, ShiftType.T1_SAB,
                    null, null, null, LocalDate.of(2025, 10, 11)));

            assertThat(dto.specificDate()).isEqualTo(LocalDate.of(2025, 10, 11));
        }

        @Test
        @DisplayName("recusa o mesmo turno duas vezes no mesmo dia")
        void recusaDuplicadoNoMesmoDia() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, LocalDate.of(2025, 10, 4)));

            assertThatThrownBy(() -> service.create(new AllocationRequestDTO(10L, 5L, ShiftType.T1_SAB,
                    null, null, null, LocalDate.of(2025, 10, 4))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("já tem o turno");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("aceita T2 para quem tem T1 se o T1 for mais cedo e acabar antes das 11h00")
        void aceitaQuandoONaoCruzamPorHorarioEspecial() {
            ShiftScheduling cedo = existente(ShiftType.T1_SAB, null);
            cedo.setCustomStartTime(LocalTime.of(8, 0));
            cedo.setCustomEndTime(LocalTime.of(11, 0));

            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(cedo);
            gravacaoComId(99L);

            AllocationDTO dto = service.create(pedido(ShiftType.T2_SAB));

            assertThat(dto.shift()).isEqualTo(ShiftType.T2_SAB);
        }

        @Test
        @DisplayName("aceita T1 e T6: têm as mesmas horas, mas dias diferentes")
        void aceitaSabadoEDomingo() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            jaTem(existente(ShiftType.T1_SAB, null));
            gravacaoComId(99L);

            AllocationDTO dto = service.create(pedido(ShiftType.T6_DOM));

            assertThat(dto.shift()).isEqualTo(ShiftType.T6_DOM);
        }

        @Test
        @DisplayName("na alteração, a alocação não entra em conflito consigo própria")
        void alteracaoNaoConflitaConsigoPropria() {
            // Sem excluir a própria alocação, editar uma alocação sem mexer no
            // turno nem na data entraria em conflito consigo e nenhuma edição
            // seria possível.
            ShiftScheduling existente = existente(ShiftType.T1_SAB, LocalDate.of(2025, 10, 4));

            when(shiftSchedulingRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(10L, 5L))
                    .thenReturn(List.of(existente));
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));
            gravacaoComId(100L);

            AllocationDTO dto = service.update(100L, new AllocationRequestDTO(10L, 5L,
                    ShiftType.T1_SAB, List.of(AssignmentType.REDES_SOCIAIS),
                    null, null, LocalDate.of(2025, 10, 4)));

            assertThat(dto.assignments()).containsExactly(AssignmentType.REDES_SOCIAIS);
        }

        @Test
        @DisplayName("na alteração, o conflito com outra alocação da mesma pessoa continua a ser recusado")
        void alteracaoRecusaConflitoComOutra() {
            ShiftScheduling existente = existente(ShiftType.T1_SAB, LocalDate.of(2025, 10, 4));
            ShiftScheduling outra = TestFixtures.allocation(
                    escala(10L), analistaAtivo(5L), ShiftType.T2_SAB, LocalDate.of(2025, 10, 4));
            outra.setId(200L);

            when(shiftSchedulingRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(10L, 5L))
                    .thenReturn(List.of(existente, outra));
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));

            assertThatThrownBy(() -> service.update(100L, new AllocationRequestDTO(10L, 5L,
                    ShiftType.T2_SAB, null, null, null, LocalDate.of(2025, 10, 4))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("já tem o turno");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a sobreposição é avaliada depois das validações do horário especial")
        void sobreposicaoVemDepoisDasOutrasRegras() {
            // Um horário especial fora do turno é um erro do formulário, e a
            // mensagem tem de dizer isso — não "conflito". Não se simula aqui
            // nenhuma alocação existente: a sobreposição chega depois, e um stub
            // que nunca é lido seria um falso pressuposto sobre a ordem.
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));

            AllocationRequestDTO fora = new AllocationRequestDTO(10L, 5L, ShiftType.T2_SAB,
                    null, LocalTime.of(7, 0), LocalTime.of(11, 0), null);

            assertThatThrownBy(() -> service.create(fora))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("tem de estar dentro do turno");
        }

        @Test
        @DisplayName("uma alteração recusada não deixa a alocação com os valores novos")
        void alteracaoRecusadaNaoDeixaRastro() {
            // Na alteração a entidade é gerida pelo Hibernate. Se os valores
            // novos fossem escritos antes da regra de sobreposição correr, um
            // pedido recusado deixaria a linha alterada na mesma transação: o
            // usuário receberia um erro e, ainda assim, a escala mudaria.
            ShiftScheduling existente = TestFixtures.allocation(
                    escala(10L), analistaAtivo(5L), ShiftType.T3_SAB, null);
            existente.setId(100L);
            ShiftScheduling outra = TestFixtures.allocation(
                    escala(10L), analistaAtivo(5L), ShiftType.T1_SAB, null);
            outra.setId(200L);

            when(shiftSchedulingRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(10L, 5L))
                    .thenReturn(List.of(existente, outra));
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaAtivo(5L)));

            // T3 (12h00-16h00) seria empurrado para T2 (11h00-15h00), que cruza
            // o T1 que a pessoa já tem.
            assertThatThrownBy(() -> service.update(100L, pedido(ShiftType.T2_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("já tem o turno");

            assertThat(existente.getShift()).isEqualTo(ShiftType.T3_SAB);
            verify(shiftSchedulingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("remove a alocação existente")
        void removeAlocacaoExistente() {
            ShiftScheduling existente = TestFixtures.allocation(escala(10L), analistaAtivo(5L));
            existente.setId(42L);
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.of(existente));

            service.delete(42L);

            verify(shiftSchedulingRepository).delete(existente);
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita uma alocação inexistente sem chegar ao delete")
        void rejeitaAlocacaoInexistente() {
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(404L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Aloca\u00e7\u00e3o n\u00e3o encontrada.");
            verify(shiftSchedulingRepository, never()).delete(any());
        }

        @Test
        @DisplayName("não apaga a entidade quando o pedido é recusado")
        void naoApagaQuandoRecusado() {
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(42L))
                    .isInstanceOf(RuntimeException.class);
            verify(shiftSchedulingRepository, never()).delete(any(ShiftScheduling.class));
        }
    }

    @Nested
    @DisplayName("Resposta do analista ao turno escalado")
    class Resposta {

        private final Long ID = 7L;

        private ShiftScheduling alocacaoDe(User dono) {
            ShiftScheduling alocacao = TestFixtures.allocation(escala(1L), dono);
            alocacao.setId(ID);
            return alocacao;
        }

        private void mockAlocacao(ShiftScheduling alocacao) {
            when(shiftSchedulingRepository.findById(ID)).thenReturn(Optional.of(alocacao));
        }

        private void mockSave() {
            when(shiftSchedulingRepository.save(any(ShiftScheduling.class)))
                    .thenAnswer(invocacao -> invocacao.getArgument(0));
        }

        @Test
        @DisplayName("o dono aceita e o estado passa a ACCEPTED")
        void donoAceita() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            mockSave();
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));

            AllocationDTO resposta = service.responder(ID, AllocationStatus.ACCEPTED, 1L);

            assertThat(resposta.analystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("o dono recusa")
        void donoRecusa() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            mockSave();
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));

            AllocationDTO resposta = service.responder(ID, AllocationStatus.REJECTED, 1L);

            assertThat(resposta.analystAcceptanceStatus()).isEqualTo(AllocationStatus.REJECTED);
        }

        @Test
        @DisplayName("a supervisão responde por cima, para desbloquear alguém indisponível")
        void supervisorResponde() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            mockSave();
            User supervisor = TestFixtures.supervisor();
            supervisor.setId(9L);
            when(userRepository.findById(9L)).thenReturn(Optional.of(supervisor));

            AllocationDTO resposta = service.responder(ID, AllocationStatus.ACCEPTED, 9L);

            assertThat(resposta.analystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("outro analista não responde pelo turno de um colega")
        void outroAnalistaNaoResponde() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            User bruno = TestFixtures.user("Bruno", "bruno@teste.com", UserProfile.ANALIST);
            bruno.setId(2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(bruno));

            assertThatThrownBy(() -> service.responder(ID, AllocationStatus.ACCEPTED, 2L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Apenas a pessoa a quem o turno foi escalado");

            verify(shiftSchedulingRepository, never()).save(any(ShiftScheduling.class));
        }

        @Test
        @DisplayName("sem principal autenticado é negado, e não rebenta com null")
        void semPrincipalDa403() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));

            assertThatThrownBy(() -> service.responder(ID, AllocationStatus.ACCEPTED, null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("aceitar o que já está aceite não volta a gravar")
        void idempotente() {
            User ana = analistaAtivo(1L);
            ShiftScheduling alocacao = alocacaoDe(ana);
            alocacao.setAnalystAcceptanceStatus(AllocationStatus.ACCEPTED);
            mockAlocacao(alocacao);
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));

            AllocationDTO resposta = service.responder(ID, AllocationStatus.ACCEPTED, 1L);

            assertThat(resposta.analystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
            verify(shiftSchedulingRepository, never()).save(any(ShiftScheduling.class));
        }

        @Test
        @DisplayName("recusar um turno com troca pendente é recusado, para não ficar uma troca órfã")
        void recusaBloqueadaPorTrocaPendente() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));
            when(exchangeRequestRepository.existePendenteParaA(ExchangeStatus.PENDING, ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.responder(ID, AllocationStatus.REJECTED, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("troca pendente");

            verify(shiftSchedulingRepository, never()).save(any(ShiftScheduling.class));
        }

        @Test
        @DisplayName("aceitar não é bloqueado por uma troca pendente: aceitar e trocar não se contradizem")
        void aceiteNaoEBloqueadoPorTroca() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            mockSave();
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));

            assertThat(service.responder(ID, AllocationStatus.ACCEPTED, 1L)
                    .analystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);

            // A prova é não ter perguntado: aceitar nunca precisa de saber se há
            // troca, porque aceitar um turno que se vai trocar não é contraditório.
            verify(exchangeRequestRepository, never())
                    .existePendenteParaA(any(ExchangeStatus.class), any());
        }

        @Test
        @DisplayName("recusar sem troca pendente passa")
        void recusaPassaSemTroca() {
            User ana = analistaAtivo(1L);
            mockAlocacao(alocacaoDe(ana));
            mockSave();
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));
            when(exchangeRequestRepository.existePendenteParaA(ExchangeStatus.PENDING, ID))
                    .thenReturn(false);

            assertThat(service.responder(ID, AllocationStatus.REJECTED, 1L)
                    .analystAcceptanceStatus()).isEqualTo(AllocationStatus.REJECTED);
        }

        @Test
        @DisplayName("uma alocação inexistente dá erro antes de perguntar quem é quem")
        void inexistenteDaErro() {
            when(shiftSchedulingRepository.findById(ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.responder(ID, AllocationStatus.ACCEPTED, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Aloca\u00e7\u00e3o n\u00e3o encontrada.");

            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("um estado nulo é recusado, em vez de gravar um estado inventado")
        void estadoNuloDaErro() {
            assertThatThrownBy(() -> service.responder(ID, null, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("aceita ou recusa");

            verify(shiftSchedulingRepository, never()).save(any(ShiftScheduling.class));
        }

        @Test
        @DisplayName("uma edição volta o aceite a pendente: quem tinha aceite tinha aceite outra coisa")
        void edicaoVoltaAPendente() {
            User ana = analistaAtivo(1L);
            ShiftScheduling alocacao = alocacaoDe(ana);
            alocacao.setAnalystAcceptanceStatus(AllocationStatus.ACCEPTED);
            mockAlocacao(alocacao);
            mockSave();

            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala(1L)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(ana));
            when(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(1L, 1L)).thenReturn(List.of(alocacao));

            service.update(ID, new AllocationRequestDTO(
                    1L, 1L, ShiftType.T1_SAB, List.of(), null, null, null));

            assertThat(alocacao.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.PENDING);
        }
    }
}
