package domain.dto;

import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de alteração do cadastro de um utilizador.
 *
 * <p>O e-mail não entra aqui, e a omissão é deliberada: o e-mail é a identidade
 * da conta e ao mesmo tempo a chave de leitura da autenticação e o que vai
 * dentro do token. Alterá-lo deixaria no ar todos os tokens já emitidos para a
 * conta antiga, sem que ninguém perceba porque é que deixou de entrar, e sem
 * que o token novo exista até entrar outra vez. Trocar o e-mail de alguém é uma
 * operação de suporte, não uma edição de perfil.
 *
 * <p>{@code teamGroup} é anulável e o {@code null} é um valor pedido, não uma
 * omissão: é assim que se retira alguém de uma equipa. Um colaborador sem equipa
 * não entra na escala de presencialidade gerada, e a lista de pessoas mostra-o
 * como "Sem equipa" em vez de o esconder.
 */
public record UpdateUserRequestDTO(

        @NotBlank(message = "O nome completo é obrigatório.")
        @Size(max = 120, message = "O nome completo não pode ter mais de 120 caracteres.")
        String name,

        @NotNull(message = "O perfil é obrigatório.")
        UserProfile profile,

        TeamGroup teamGroup
) {
}
