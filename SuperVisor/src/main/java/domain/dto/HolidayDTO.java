package domain.dto;

import domain.model.entities.Holiday;

import java.time.LocalDate;

/**
 * Feriado, pronto a serializar.
 *
 * <p>O motivo é o mesmo dos restantes DTO do projeto: a entidade não vai na
 * resposta porque não tem nada a esconder — e por isso não há aqui o problema da
 * senha —, mas manter o contrato da API num sítio explícito evita que um campo
 * novo da entidade apareça num JSON de presente sem ninguém ter decidido que
 * sim.
 */
public record HolidayDTO(
        Long id,
        LocalDate date,
        String description
) {

    public static HolidayDTO from(Holiday feriado) {
        return new HolidayDTO(feriado.getId(), feriado.getDate(), feriado.getDescription());
    }
}
