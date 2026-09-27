package service;

import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import domain.repository.EditionScaleRepository;
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

    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService();
        ReflectionTestUtils.setField(service, "shiftSchedulingRepository", shiftSchedulingRepository);
        ReflectionTestUtils.setField(service, "editionScaleRepository", editionScaleRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
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

    /** Simula o repositorio a devolver a entidade gravada, com id atribuido. */
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
        @DisplayName("resolve utilizador e escala, e grava todos os campos")
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
        @DisplayName("sem atribuicoes nem horario especial, a alocacao fica sem horario customizado")
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
        @DisplayName("aceita o horario 21h00-00h00 em T5")
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
        @DisplayName("rejeita um pedido sem utilizador")
        void exigeUtilizador() {
            AllocationRequestDTO dto = new AllocationRequestDTO(10L, null, ShiftType.T1_SAB,
                    null, null, null, null);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Escolha o utilizador que cobre o turno.");
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
        @DisplayName("rejeita um utilizador inexistente")
        void rejeitaUtilizadorInexistente() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Utilizador n\u00e3o encontrado.");
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
        @DisplayName("rejeita um utilizador inativo")
        void rejeitaUtilizadorInativo() {
            when(editionScaleRepository.findById(10L)).thenReturn(Optional.of(escala(10L)));
            when(userRepository.findById(5L)).thenReturn(Optional.of(analistaInativo(5L)));

            assertThatThrownBy(() -> service.create(pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("est\u00e1 inativo e n\u00e3o pode receber turnos.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("exige o inicio e o fim do horario especial em conjunto")
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
        @DisplayName("rejeita um horario especial fora do turno")
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
        @DisplayName("substitui os campos editaveis da alocacao existente")
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
        @DisplayName("rejeita uma alocacao inexistente")
        void rejeitaAlocacaoInexistente() {
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(404L, pedido(ShiftType.T1_SAB)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Aloca\u00e7\u00e3o n\u00e3o encontrada.");
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("aplica as mesmas regras de validacao da criacao")
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
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("remove a alocacao existente")
        void removeAlocacaoExistente() {
            ShiftScheduling existente = TestFixtures.allocation(escala(10L), analistaAtivo(5L));
            existente.setId(42L);
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.of(existente));

            service.delete(42L);

            verify(shiftSchedulingRepository).delete(existente);
            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita uma alocacao inexistente sem chegar ao delete")
        void rejeitaAlocacaoInexistente() {
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(404L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Aloca\u00e7\u00e3o n\u00e3o encontrada.");
            verify(shiftSchedulingRepository, never()).delete(any());
        }

        @Test
        @DisplayName("nao apaga a entidade quando o pedido e recusado")
        void naoApagaQuandoRecusado() {
            when(shiftSchedulingRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(42L))
                    .isInstanceOf(RuntimeException.class);
            verify(shiftSchedulingRepository, never()).delete(any(ShiftScheduling.class));
        }
    }
}
