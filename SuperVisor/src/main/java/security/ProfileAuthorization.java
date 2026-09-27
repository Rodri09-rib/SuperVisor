package security;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Verificação de perfil para as operações restritas à supervisão.
 *
 * <p>O {@code SecurityConfig} garante apenas que o pedido está autenticado, e
 * isso é suficiente para leitura. As operações que escrevem no estado do
 * ficheiro de escalas são validadas aqui, no controlador, porque o perfil do
 * utilizador vive no principal e não na rota: a mesma rota tem de responder de
 * forma diferente conforme quem a chama.
 *
 * <p>A negação é um {@link AccessDeniedException}, tratado pelo
 * {@code GlobalExceptionHandler} para devolver 403 e não o 400 genérico das
 * regras de negócio.
 */
@Component
public class ProfileAuthorization {

    private static final String MENSAGEM =
            "Apenas o perfil SUPERVISOR pode executar esta operação.";

    /** Exige que quem chama seja um {@code SUPERVISOR}. */
    public void exigirSupervisor() {
        exigirPerfil(UserProfile.SUPERVISOR);
    }

    /**
     * Exige um perfil específico. Um principal ausente ou que não seja um
     * {@link User} também nega: assim um token válido mas sem entidade de
     * utilizador não contorna a regra.
     */
    public void exigirPerfil(UserProfile exigido) {
        User utilizador = utilizadorAutenticado();

        if (utilizador == null || utilizador.getProfile() != exigido) {
            throw new AccessDeniedException(MENSAGEM);
        }
    }

    private User utilizadorAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return null;
        }

        return user;
    }
}
