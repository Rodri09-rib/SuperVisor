package domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo de criação e de atualização de um feriado.
 *
 * <p>O mesmo registo serve as duas operações, como em {@code UserLeaveRequestDTO}:
 * são a mesma informação, e dois registos iguais divergiriam assim que um campo
 * novo aparecesse.
 *
 * <p>Não leva {@code id}: o identificador vem da rota na atualização, e aceitá-lo
 * no corpo abriria a porta a «atualizar o id 5 para o id 9».
 */
public record HolidayRequestDTO(
        @NotNull(message = "A data do feriado é obrigatória.")
        LocalDate date,

        @NotBlank(message = "A descrição do feriado é obrigatória.")
        @Size(max = 120, message = "A descrição não pode ter mais de 120 caracteres.")
        String description
) {
}
