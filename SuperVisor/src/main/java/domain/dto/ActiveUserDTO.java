package domain.dto;

import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;

import java.util.List;

/**
 * Vista de utilizador para o frontend. Não inclui a senha: a entidade
 * {@code User} implementa {@code UserDetails} e serializá-la diretamente
 * exporia o hash.
 *
 * <p>Traz a equipa e o estado da conta porque os dois são coisas que o
 * supervisor precisa de ver e corrigir. A equipa porque um colaborador sem
 * equipa não entra na escala de presencialidade gerada e o motivo tem de ser
 * visível na lista em vez de a pessoa simplesmente não aparecer; o estado porque
 * uma conta desativada continua a existir e tem de poder ser encontrada para
 * ser reativada.
 */
public record ActiveUserDTO(
        Long id,
        String name,
        String email,
        UserProfile profile,
        TeamGroup teamGroup,
        String teamGroupLabel,
        boolean active
) {

    public static ActiveUserDTO from(User user) {
        return new ActiveUserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfile(),
                user.getTeamGroup(),
                user.getTeamGroup() == null ? "Sem equipa" : user.getTeamGroup().getRotulo(),
                user.isActive());
    }

    public static List<ActiveUserDTO> from(List<User> users) {
        return users.stream().map(ActiveUserDTO::from).toList();
    }
}
