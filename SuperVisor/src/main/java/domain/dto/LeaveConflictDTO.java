package domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Alocação que calhou a um dia de folga da mesma pessoa.
 *
 * <p>Não é bloqueada na criação porque a folga pode ser registada depois de a
 * escala estar montada, e as duas coisas são mantidas em tabelas separadas sem
 * qualquer relação que as ligue. A ordem das operações é um detalhe de quem
 * clica primeiro, e o resultado de ficar a errar a ordem é alguém escalado num
 * dia em que pediu para folgar.
 *
 * <p>O motivo da folga não entra aqui: este relatório é visível a qualquer
 * utilizador autenticado, tal como a leitura das escalas, e o motivo de uma
 * baixa não é informação partilhada.
 */
public record LeaveConflictDTO(
        Long userId,
        String userName,
        Long allocationId,
        String shift,
        String interval,
        LocalDate date
) {

    /** Frase para mostrar ao supervisor. Vai no JSON para existir num sítio só. */
    @JsonProperty("descricao")
    public String descricao() {
        return userName + " está escalado em " + shift + " (" + interval + ") no dia "
                + date + ", em que está de folga";
    }
}
