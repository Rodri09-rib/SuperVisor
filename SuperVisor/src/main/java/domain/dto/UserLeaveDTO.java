package domain.dto;

import domain.model.entities.UserLeave;
import domain.model.enums.LeaveDuration;
import domain.model.enums.UserProfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Folga de um colaborador, achatada para consumo pelo frontend.
 *
 * <p>{@code canEdit} vem calculado no servidor e não no navegador. A interface
 * esconde o botão de editar e apagar a quem não pode usá-los, mas esconder um
 * botão é uma conveniência visual, não uma restrição: o que impede o pedido de
 * ser feito é o perfil verificado no serviço, e este campo mantém as duas pontas
 * de acordo sem duplicar a regra de "quem pode mexer" em JavaScript.
 *
 * <p>{@code durationDays} conta os dias de calendário, com as extremidades
 * incluídas. Uma folga de segunda a sexta é de 5 dias e não de 4: quem lê o
 * cartão quer saber o tamanho da ausência, e o número de noites dormidas em
 * casa não é esse número.
 *
 * <p>{@code leaveDuration} e {@code costDays} respondem a outra pergunta: o
 * que a folga consome do saldo. Dia inteiro de segunda a sexta consome cinco
 * dias; uma folga só de manhã consome meio, e é esse número — e não o de
 * calendário — que a coluna de duração da página de folgas mostra ao lado do
 * período.
 */
public record UserLeaveDTO(
        Long id,
        Long userId,
        String userName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        Instant createdAt,
        int durationDays,
        LeaveDuration leaveDuration,
        String leaveDurationLabel,
        BigDecimal costDays,
        boolean canEdit
) {

    public static UserLeaveDTO from(UserLeave folga, UserProfile perfilDeQuemPede) {
        var utilizador = folga.getUser();
        var duracao = folga.getLeaveDuration() == null ? LeaveDuration.FULL_DAY : folga.getLeaveDuration();

        return new UserLeaveDTO(
                folga.getId(),
                utilizador == null ? null : utilizador.getId(),
                utilizador == null ? null : utilizador.getName(),
                folga.getStartDate(),
                folga.getEndDate(),
                folga.getReason(),
                folga.getCreatedAt(),
                duracaoEmDias(folga.getStartDate(), folga.getEndDate()),
                duracao,
                duracao.getRotulo(),
                folga.custoEmDias(),
                perfilDeQuemPede == UserProfile.SUPERVISOR);
    }

    private static int duracaoEmDias(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return 0;
        }
        // Math.toIntExact e não um cast: a diferença entre duas datas num
        // Intervalo de um dia é um long, e o transbordo para int só apareceria
        // com um intervalo de 2 mil milhões de dias.
        return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) + 1);
    }
}
