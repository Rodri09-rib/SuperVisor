package domain.repository;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

   /**
    * Devolve a entidade concreta, e não apenas o {@link UserDetails}, porque
    * quem autentica precisa de distinguir uma conta desativada de uma conta
    * inexistente: as duas recusam o acesso, mas só a segunda é um token órfão.
    */
   User findByEmail(String email);

    /**
     * Utilizadores ativos, por nome, para o selector de pessoas do editor de
     * alocações. Contas desativadas não podem receber turnos, logo não são
     * oferecidas.
     */
    List<User> findByActiveTrueOrderByNameAsc();

    /**
     * Utilizadores ativos com equipa atribuída, para gerar a escala de
     * presencialidade. A equipa é obrigatória para entrar na escala: sem ela não
     * há como saber se a pessoa está a presenter ou em home office naquela
     * semana, e inventar um valor por omissão produziria uma escala errada em
     * silêncio, que é pior do que não gerar linha nenhuma.
     */
    List<User> findByActiveTrueAndTeamGroupIsNotNullOrderByNameAsc();

    /**
     * Todos os ativos, com a equipa à frente do nome, para a grelha de
     * presencialidade: as linhas são agrupadas por equipa, porque a grelha tem
     * uma cor por equipa.
     */
    List<User> findByActiveTrueOrderByTeamGroupAscNameAsc();

    /**
     * Quantos supervisores ativos existem.
     *
     * <p>Existe para a regra que impede desativar o último: sem ela, desativar o
     * único supervisor deixa a aplicação sem ninguém capaz de criar contas,
     * atribuir equipas ou reativar quem ficou desativado. A conta ficava presa
     * e a única saída seria mexer na base de dados à mão.
     */
    long countByProfileAndActiveTrue(UserProfile profile);

    /** Todos os utilizadores, ativos ou não, para a vista de administração. */
    List<User> findAllByOrderByNameAsc();

}

