package domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Cobertura e conflitos de uma escala, lidos de uma vez.
 *
 * <p>Três respostas numa só porque as três são respostas à mesma pergunta — "esta
 * escala está correta?" — e separá-las obrigaria o frontend a fazer três
 * pedidos para pintar três blocos que se atualizam em conjunto. Quando uma
 * alocação entra, os três números mudam ao mesmo tempo.
 *
 * <p>A percentagem de cobertura é sobre os slots do período, e não sobre as
 * pessoas: o que interessa ao supervisor é se há alguém em cada turno de cada
 * dia, não se cada pessoa tem um número igual de turnos.
 *
 * <p>Os três contadores de cobertura medem coisas diferentes e é deliberado:
 * <em>totalSlots</em> é o número de turnos que a escala cria, <em>coveredSlots</em>
 * e <em>uncoveredSlots</em> dividem esse total pelos que têm e pelos que não têm
 * alguém. A soma é sempre o total, mesmo quando uma escala tem o período
 * invertido — nesse caso não há slots e os três valem zero.
 *
 * <p>{@code inactivePeopleScheduled} conta as alocações de contas
 * desativadas. Não é um conflito, é um aviso: a conta é desativada sem perder
 * as alocações que já tinha, e alguém escalado que não pode entrar na aplicação
 * é um turno que ninguém vai cobrir.
 */
public record ScaleCoverageDTO(
        Long scaleId,
        String scaleName,
        LocalDate initialDate,
        LocalDate endDate,
        int totalSlots,
        int coveredSlots,
        int uncoveredSlots,
        int coveragePercent,
        int allocationsCount,
        int inactivePeopleScheduled,
        List<SlotCoverageDTO> slots,
        List<OverlapConflictDTO> overlaps,
        List<LeaveConflictDTO> leaveConflicts
) {

    public ScaleCoverageDTO {
        slots = slots == null ? List.of() : List.copyOf(slots);
        overlaps = overlaps == null ? List.of() : List.copyOf(overlaps);
        leaveConflicts = leaveConflicts == null ? List.of() : List.copyOf(leaveConflicts);
    }

    /**
     * Há algum conflito nesta escala?
     *
     * <p>Vem para o JSON apesar de não ser um componente do record, porque o
     * frontend precisa de um sinal único para mostrar o bloco de avisos e o
     * cliente não tem de saber que existem dois tipos de conflito para decidir
     * se os soma.
     */
    @JsonProperty("temConflitos")
    public boolean temConflitos() {
        return !overlaps.isEmpty() || !leaveConflicts.isEmpty();
    }
}
