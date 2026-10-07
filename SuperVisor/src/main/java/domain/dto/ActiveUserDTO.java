package domain.dto;

import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista de usuário para o frontend. Não inclui a senha: a entidade
 * {@code User} implementa {@code UserDetails} e serializá-la diretamente
 * exporia o hash.
 *
 * <p>Traz a equipe e o estado da conta porque os dois são coisas que o
 * supervisor precisa ver e corrigir. A equipe porque um colaborador sem
 * equipe não entra na escala de presencialidade gerada e o motivo tem de ser
 * visível na lista em vez de a pessoa simplesmente não aparecer; o estado porque
 * uma conta desativada continua a existir e tem de poder ser encontrada para
 * ser reativada.
 *
 * <p>Os saldos de folgas e de compensação vêm na mesma lista porque é ela que
 * alimenta as duas listagens onde os destaque aparecem — presencialidade e
 * folgas —, e separá-los num endpoint próprio faria o frontend pedir duas
 * vezes a mesma pessoa para desenhar duas tabelas. Vêm, no entanto, só para
 * quem supervisiona: a lista alimenta também o seletor de pessoas de qualquer
 * perfil, e o que é gestão não entra nesse caminho — ver {@link #semSaldos()}.
 *
 * <p>Nulos convertem-se em zero, com a mesma razão de
 * {@link CurrentUserDTO#from}: uma linha antiga sem saldo não pode chegar ao
 * browser como {@code null} e ser lida como «sem saldo» por um código que só
 * espera números. A única exceção é {@link #semSaldos()}, onde o {@code null}
 * significa «este valor não é seu para ver» e não «não há valor».
 */
public record ActiveUserDTO(
        Long id,
        String name,
        String email,
        UserProfile profile,
        TeamGroup teamGroup,
        String teamGroupLabel,
        boolean active,
        BigDecimal accumulatedLeaves,
        BigDecimal pendingCompensationDays
) {

    public static ActiveUserDTO from(User user) {
        return new ActiveUserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfile(),
                user.getTeamGroup(),
                user.getTeamGroup() == null ? "Sem equipe" : user.getTeamGroup().getRotulo(),
                user.isActive(),
                zeroSeNulo(user.getAccumulatedLeaves()),
                zeroSeNulo(user.getPendingCompensationDays()));
    }

    public static List<ActiveUserDTO> from(List<User> users) {
        return users.stream().map(ActiveUserDTO::from).toList();
    }

    /**
     * A mesma linha, sem os saldos de folgas nem os de compensação.
     *
     * <p>A lista de usuários alimenta o seletor de pessoas de qualquer
     * perfil, mas os saldos são informação de gestão: quem não supervisiona
     * não precisa de saber quanto saldo tem o colega. Os campos vêm a
     * {@code null} e não a zero — zero é um saldo verdadeiro e seria lido como
     * «esta pessoa não tem nada» em vez de «não lhe é dito», que é exatamente
     * o que se quer dizer quando o valor está escondido por direito.
     */
    public ActiveUserDTO semSaldos() {
        return new ActiveUserDTO(id, name, email, profile, teamGroup, teamGroupLabel,
                active, null, null);
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
