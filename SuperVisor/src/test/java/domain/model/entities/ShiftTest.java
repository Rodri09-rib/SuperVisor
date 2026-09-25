package domain.model.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade Shift")
class ShiftTest {

    @Test
    @DisplayName("construtor completo preserva sigla, horários e dia da semana")
    void construtorCompleto() {
        Shift shift = new Shift(1L, "M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA");

        assertThat(shift.getId()).isEqualTo(1L);
        assertThat(shift.getAcronym()).isEqualTo("M1");
        assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(shift.getDayiftheWeek()).isEqualTo("SEGUNDA");
    }

    @Test
    @DisplayName("construtor vazio deixa tudo nulo")
    void construtorVazio() {
        Shift shift = new Shift();

        assertThat(shift.getId()).isNull();
        assertThat(shift.getAcronym()).isNull();
        assertThat(shift.getStartTime()).isNull();
        assertThat(shift.getEndTime()).isNull();
        assertThat(shift.getDayiftheWeek()).isNull();
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        Shift shift = new Shift();

        shift.setId(3L);
        shift.setAcronym("T1");
        shift.setStartTime(LocalTime.of(13, 0));
        shift.setEndTime(LocalTime.of(18, 0));
        shift.setDayiftheWeek("TERCA");

        assertThat(shift.getId()).isEqualTo(3L);
        assertThat(shift.getAcronym()).isEqualTo("T1");
        assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(shift.getDayiftheWeek()).isEqualTo("TERCA");
    }

    @Test
    @DisplayName("o turno não tem nenhuma associação com outras entidades")
    void turnoNaoTemRelacionamentos() {
        assertThat(Shift.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getType)
                .doesNotContain(EditionScale.class, ShiftScheduling.class, User.class);
    }
}
