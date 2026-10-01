package domain.model.enums;

/**
 * Atribuições especiais que um usuário pode ter dentro de um turno. São
 * opcionais e pertencem à alocação, não ao usuário: o mesmo usuário pode
 * estar com "Redes Sociais" ao sábado e sem atribuição ao domingo.
 *
 * <p>O {@code rotulo} é o texto mostrado no dashboard e nunca é usado como
 * valor persistido, que é o nome da constante.
 */
public enum AssignmentType {

    REDES_SOCIAIS("Redes Sociais"),
    CELULAR_MARINAS("Celular da Marinas");

    private final String rotulo;

    AssignmentType(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
