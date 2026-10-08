package domain.dto;

import domain.model.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registo de falta ou ocorrência numa célula da escala de presencialidade.
 *
 * <p>Os dois campos são tudo o que o supervisor decide: o tipo de ausência,
 * que é o que a regra de compensação usa para saber se a dívida é de um dia ou
 * de meio, e a observação, que explica o porquê e é opcional porque a maioria
 * das faltas não precisa de justificação escrita.
 *
 * <p>{@code notes} não vem com {@code @NotBlank}: apagar a observação é uma
 * operação legítima — o supervisor corrigiu o registo —, e um texto vazio é
 * guardado como nulo pelo serviço, tal como o motivo das folgas. O campo
 * ausente ({@code null}) é diferente de vazio e mantém o que já lá está: um
 * cliente que só queira mudar o estado não é obrigado a repetir a observação
 * para não a apagar por acidente.
 */
public record AttendanceRequestDTO(
        @NotNull(message = "O estado de presença é obrigatório.")
        AttendanceStatus attendanceStatus,

        @Size(max = 500, message = "A observação não pode ter mais de 500 caracteres.")
        String notes
) {
}
