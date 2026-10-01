package domain.model.enums;

/**
 * Equipe a que um colaborador pertence.
 *
 * <p>Existe para que a escala de presencialidade possa intercalar as duas
 * equipes: em semanas alternadas, a equipe que está presencial à segunda-feira
 * está em home office à terça, e assim sucessivamente. A equipe é uma
 * propriedade estável do colaborador, pelo que vive em {@code User} e não na
 * escala semanal — a escala guarda uma cópia no momento em que é gerada para
 * que um histórico não mude se alguém mudar de equipe.
 */
public enum TeamGroup {

    EQUIPE_A("Equipe A"),
    EQUIPE_B("Equipe B");

    private final String rotulo;

    TeamGroup(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * A equipe com o sentido do padrão invertido, para gerar a semana par a
     * partir da semana ímpar sem duplicar a tabela de dias.
     */
    public TeamGroup oposta() {
        return this == EQUIPE_A ? EQUIPE_B : EQUIPE_A;
    }
}
