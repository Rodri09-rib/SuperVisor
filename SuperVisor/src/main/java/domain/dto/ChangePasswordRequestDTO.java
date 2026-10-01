package domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de redefinição de senha.
 *
 * <p>Não pede a senha atual: quem redefine a senha de alguém não é essa pessoa,
 * é a supervisão a recuperar o acesso de um colaborador que se esqueceu dela. Por
 * isso a operação é restrita ao perfil {@code SUPERVISOR}, e por isso o
 * utilizador redefine a sua própria senha com a verificação de que conhece a
 * antiga.
 *
 * <p>A validação de comprimento é a mesma da criação de conta, para que não
 * exista um caminho que grave uma senha que o registo rejeitaria.
 */
public record ChangePasswordRequestDTO(

        @NotBlank(message = "A password é obrigatória.")
        @Size(min = 6, message = "A password tem de ter pelo menos 6 caracteres.")
        String password
) {
}
