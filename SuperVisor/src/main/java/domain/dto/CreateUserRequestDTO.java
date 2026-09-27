package domain.dto;

import domain.model.enums.UserProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de criação de um utilizador.
 *
 * <p>A senha chega em claro e é encriptada no serviço antes de tocar na base
 * de dados: o {@code PasswordEncoder} é um detalhe da persistência e não
 * pertence ao contrato HTTP.
 *
 * <p>{@code active} é um {@link Boolean} e não um {@code boolean} para que
 * "campo omitido" e "campo a false" se distingam — o primeiro usa o
 * predefinido e o segundo grava mesmo uma conta desativada. O construtor
 * compacto normaliza o valor omisso, pelo que {@link #active()} nunca devolve
 * {@code null} e o serviço não precisa de uma defesa extra.
 */
public record CreateUserRequestDTO(

        @NotBlank(message = "O nome completo é obrigatório.")
        @Size(max = 120, message = "O nome completo não pode ter mais de 120 caracteres.")
        String name,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "O e-mail não é válido.")
        @Size(max = 160, message = "O e-mail não pode ter mais de 160 caracteres.")
        String email,

        @NotBlank(message = "A password é obrigatória.")
        @Size(min = 6, message = "A password tem de ter pelo menos 6 caracteres.")
        String password,

        @NotNull(message = "O perfil é obrigatório.")
        UserProfile profile,

        Boolean active
) {

    /** Conta sem este campo: nasce ativa, para não ficar à espera de backfill. */
    private static final boolean PREDEFINIDO_ATIVO = true;

    public CreateUserRequestDTO {
        active = active == null ? PREDEFINIDO_ATIVO : active;
    }
}
