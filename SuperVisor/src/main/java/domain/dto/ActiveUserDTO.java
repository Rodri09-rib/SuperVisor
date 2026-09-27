package domain.dto;

import domain.model.entities.User;
import domain.model.enums.UserProfile;

import java.util.List;

/**
 * Vista de utilizador ativo para alimentar o selector de pessoas do editor de
 * alocações. Não inclui a senha: a entidade {@code User} implementa
 * {@code UserDetails} e serializá-la diretamente exporia o hash.
 */
public record ActiveUserDTO(
        Long id,
        String name,
        String email,
        UserProfile profile
) {

    public static ActiveUserDTO from(User user) {
        return new ActiveUserDTO(user.getId(), user.getName(), user.getEmail(), user.getProfile());
    }

    public static List<ActiveUserDTO> from(List<User> users) {
        return users.stream().map(ActiveUserDTO::from).toList();
    }
}
