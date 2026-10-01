package domain.dto;

import domain.model.entities.User;
import domain.model.enums.UserProfile;

/**
 * Vista do usuário autenticado usada pelo frontend: identifica quem está
 * logged in (nome para a saudação) e qual o seu perfil, sem expor a senha.
 */
public record CurrentUserDTO(
        Long id,
        String name,
        String email,
        UserProfile profile
) {

    public static CurrentUserDTO from(User user) {
        return new CurrentUserDTO(user.getId(), user.getName(), user.getEmail(), user.getProfile());
    }
}
