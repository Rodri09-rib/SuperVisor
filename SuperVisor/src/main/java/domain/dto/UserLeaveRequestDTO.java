package domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo de criação e de atualização de uma folga.
 *
 * <p>O mesmo registo serve para as duas operações. São a mesma informação, e
 * dois registos iguais divergiriam assim que um campo novo aparecesse — a
 * atualização deixaria de validar o que a criação validava.
 *
 * <p>{@code userId} é obrigatório na criação e ignorado na atualização: quem
 * edita uma folga pode corrigir as datas e o motivo, mas não transferi-la para
 * outra pessoa. Uma escala de ausência que muda de dono por um erro de
 * utilização deixaria de dizer quem falta.
 *
 * <p>{@code endDate} continua sem validação declarativa porque a relação com
 * {@code startDate} só pode ser verificada com as duas na mão, e a validação de
 * classe não expresses isso. O serviço trata disso e explica o motivo.
 */
public record UserLeaveRequestDTO(
        @NotNull(message = "O utilizador da folga é obrigatório.")
        Long userId,

        @NotNull(message = "A data de início da folga é obrigatória.")
        LocalDate startDate,

        @NotNull(message = "A data de fim da folga é obrigatória.")
        LocalDate endDate,

        @Size(max = 500, message = "O motivo não pode ter mais de 500 caracteres.")
        String reason
) {
}
