package domain.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Como um turno ficou coberto num dia concreto da escala.
 *
 * <p>Um "slot" é o par turno + dia: T1 num sábado é um slot, T1 noutro sábado
 * é outro. O relatório não diz se a cobertura chega — isso depende de quantas
 * pessoas são precisas por turno e ninguém escreveu essa regra — e diz quantas
 * há, para que o número zero seja visível.
 */
public record SlotCoverageDTO(
        LocalDate date,
        DayOfWeek dayOfWeek,
        String dayLabel,
        String shift,
        String shiftLabel,
        String interval,
        int peopleCount,
        boolean covered,
        List<String> people
) {

    public SlotCoverageDTO {
        people = people == null ? List.of() : List.copyOf(people);
    }
}
