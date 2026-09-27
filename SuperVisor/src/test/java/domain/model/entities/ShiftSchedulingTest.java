package domain.model.entities;

import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade ShiftScheduling")
class ShiftSchedulingTest {

    @Test
    @DisplayName("nova alocação nasce com o estado de aceitação PENDING")
    void estadoDeAceitacaoPadrao() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.PENDING);
    }

    @Test
    @DisplayName("nova alocação nasce sem atribuições, para não partilhar a coleção")
    void atribuicoesPadrao() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getAssignments()).isEmpty();
    }

    @Test
    @DisplayName("construtor completo preserva todos os campos")
    void construtorCompleto() {
        EditionScale scale = new EditionScale();
        User user = new User();
        LocalDate date = LocalDate.of(2025, 10, 15);

        ShiftScheduling allocation = new ShiftScheduling(
                5L, scale, ShiftType.T3_SAB, user, date, AllocationStatus.ACCEPTED);

        assertThat(allocation.getId()).isEqualTo(5L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getShift()).isEqualTo(ShiftType.T3_SAB);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getSpecificDate()).isEqualTo(date);
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        ShiftScheduling allocation = new ShiftScheduling();
        EditionScale scale = new EditionScale();
        User user = new User();

        allocation.setId(1L);
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        allocation.setShift(ShiftType.T4_SAB);
        allocation.setSpecificDate(LocalDate.of(2025, 10, 1));
        allocation.setAnalystAcceptanceStatus(AllocationStatus.REJECTED);

        assertThat(allocation.getId()).isEqualTo(1L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getShift()).isEqualTo(ShiftType.T4_SAB);
        assertThat(allocation.getSpecificDate()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.REJECTED);
    }

    @Test
    @DisplayName("as atribuições especiais são guardadas tal como foram informadas")
    void atribuicoesGuardadas() {
        ShiftScheduling allocation = new ShiftScheduling();

        allocation.setAssignments(Set.of(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS));

        assertThat(allocation.getAssignments())
                .containsExactlyInAnyOrder(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
    }

    @Test
    @DisplayName("definir atribuições a null deixa a coleção vazia, não nula")
    void atribuicoesNulas() {
        ShiftScheduling allocation = new ShiftScheduling();

        allocation.setAssignments(null);

        assertThat(allocation.getAssignments()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("horário especial só é considerado quando início e fim existem")
    void horarioCustomizadoCompleto() {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setShift(ShiftType.T6_DOM);

        assertThat(allocation.temHorarioCustomizado()).isFalse();

        allocation.setCustomStartTime(LocalTime.of(10, 30));
        assertThat(allocation.temHorarioCustomizado()).isFalse();

        allocation.setCustomEndTime(LocalTime.of(14, 30));
        assertThat(allocation.temHorarioCustomizado()).isTrue();
    }

    @Test
    @DisplayName("o horário efetivo usa o customizado quando existe e o do turno caso contrário")
    void horarioEfetivo() {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setShift(ShiftType.T1_SAB);

        assertThat(allocation.getInicioEfetivo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(allocation.getFimEfetivo()).isEqualTo(LocalTime.of(12, 0));

        allocation.setCustomStartTime(LocalTime.of(10, 30));
        allocation.setCustomEndTime(LocalTime.of(14, 30));

        assertThat(allocation.getInicioEfetivo()).isEqualTo(LocalTime.of(10, 30));
        assertThat(allocation.getFimEfetivo()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("sem turno nem horário customizado não há horário efetivo")
    void horarioEfetivoSemTurno() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getInicioEfetivo()).isNull();
        assertThat(allocation.getFimEfetivo()).isNull();
    }

    @Test
    @DisplayName("o utilizador da alocação pode ser trocado, base da troca de turno")
    void utilizadorPodeSerTrocado() {
        User original = new User(1L, "A", "a@t.com", "x", null);
        User novo = new User(2L, "B", "b@t.com", "x", null);

        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setUser(original);
        allocation.setUser(novo);

        assertThat(allocation.getUser()).isSameAs(novo);
    }

    @Test
    @DisplayName("a alocação cobre todas as atribuições especiais do domínio")
    void todasAsAtribuicoesSaoConhecidas() {
        assertThat(List.of(AssignmentType.values()))
                .containsExactly(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
    }
}
