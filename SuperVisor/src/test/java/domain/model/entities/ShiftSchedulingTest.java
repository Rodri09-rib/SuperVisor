package domain.model.entities;

import domain.model.enums.AllocationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
    @DisplayName("construtor completo preserva todos os campos")
    void construtorCompleto() {
        EditionScale scale = new EditionScale();
        User user = new User();
        Shift shift = new Shift();
        LocalDate date = LocalDate.of(2025, 10, 15);

        ShiftScheduling allocation = new ShiftScheduling(
                5L, scale, shift, user, date, AllocationStatus.ACCEPTED);

        assertThat(allocation.getId()).isEqualTo(5L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getShift()).isSameAs(shift);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getSpecificDate()).isEqualTo(date);
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("construtor completo aceita turno e data nulos, como o seed cria")
    void construtorCompletoAceitaNulos() {
        ShiftScheduling allocation = new ShiftScheduling(
                6L, new EditionScale(), null, new User(), null, null);

        assertThat(allocation.getShift()).isNull();
        assertThat(allocation.getSpecificDate()).isNull();
        assertThat(allocation.getAnalystAcceptanceStatus()).isNull();
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        ShiftScheduling allocation = new ShiftScheduling();
        EditionScale scale = new EditionScale();
        User user = new User();
        Shift shift = new Shift();

        allocation.setId(1L);
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        allocation.setShift(shift);
        allocation.setSpecificDate(LocalDate.of(2025, 10, 1));
        allocation.setAnalystAcceptanceStatus(AllocationStatus.REJECTED);

        assertThat(allocation.getId()).isEqualTo(1L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getShift()).isSameAs(shift);
        assertThat(allocation.getSpecificDate()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.REJECTED);
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
}
