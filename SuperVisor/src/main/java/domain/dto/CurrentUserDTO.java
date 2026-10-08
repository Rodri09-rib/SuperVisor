package domain.dto;

import domain.model.entities.User;
import domain.model.enums.UserProfile;

import java.math.BigDecimal;

/**
 * Vista do usuário autenticado usada pelo frontend: identifica quem está
 * logged in (nome para a saudação) e qual o seu perfil, sem expor a senha.
 *
 * <p>Os dois saldos vêm aqui porque o cartão de folgas e o destaque de
 * compensação são do próprio utilizador, e ir buscá-los a outro endpoint
 * obrigaria a duas chamadas para desenhar uma mesma linha de cabeçalho.
 *
 * <p>Nulos convertem-se em zero: a coluna é {@code NOT NULL} desde a migração
 * V4, mas uma base povoada antes dela pode ainda ter linhas antigas, e um
 * {@code null} na resposta virava «Sem saldo» no cartão para quem na verdade
 * tinha saldo — ou pior, `undefined` no JavaScript a fingir que o campo não
 * existe. O frontend não tem de defender nulo porque o servidor já o entrega
 * como número.
 */
public record CurrentUserDTO(
        Long id,
        String name,
        String email,
        UserProfile profile,
        BigDecimal accumulatedLeaves,
        BigDecimal pendingCompensationDays
) {

    public static CurrentUserDTO from(User user) {
        return new CurrentUserDTO(user.getId(), user.getName(), user.getEmail(), user.getProfile(),
                zeroSeNulo(user.getAccumulatedLeaves()),
                zeroSeNulo(user.getPendingCompensationDays()));
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
