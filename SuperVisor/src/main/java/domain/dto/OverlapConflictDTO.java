package domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Duas alocações que se cruzam na mesma pessoa.
 *
 * <p>Isto é o que a regra de criação de alocações já impede, e o relatório
 * existe porque a aplicação tem dados anteriores a essa regra e porque o
 * bloqueio é por aplicação: uma inserção feita fora dela, ou dois pedidos
 * simultâneos, deixam passar o conflito. Um relatório que só olha para o estado
 * atual da base não pega nada disso.
 *
 * <p>As datas afetadas vêm à parte porque o conflito raramente é de um dia só:
 * duas alocações sem data específica cruzam todas as datas do dia da semana
 * dentro do período da escala, e dizer "conflita" sem dizer quando deixa o
 * supervisor sem saber o que tem de corrigir.
 */
public record OverlapConflictDTO(
        Long userId,
        String userName,
        Long firstAllocationId,
        String firstShift,
        String firstInterval,
        Long secondAllocationId,
        String secondShift,
        String secondInterval,
        List<LocalDate> affectedDates
) {

    public OverlapConflictDTO {
        affectedDates = affectedDates == null ? List.of() : List.copyOf(affectedDates);
    }

    /**
     * Frase para mostrar ao supervisor, já com os dois turnos e horários.
     *
     * <p>Vem no JSON para que a frase exista num lugar só: escrevê-la também em
     * JavaScript seria ter a mesma informação em duas línguas, à espera de uma
     * delas ficar desatualizada.
     */
    @JsonProperty("descricao")
    public String descricao() {
        return userName + " está com " + firstShift + " (" + firstInterval + ") e "
                + secondShift + " (" + secondInterval + ") ao mesmo tempo";
    }
}
