package domain.repository;

import domain.model.entities.UserLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserLeaveRepository extends JpaRepository<UserLeave, Long> {

    /**
     * Lista de folgas, opcionalmente restrita a uma pessoa.
     *
     * <p>Devolve as entidades, e não um DTO de projeção, porque a data de
     * criação entra na resposta e a montagem do nome do utilizador continua a
     * depender de {@code User}. Com a lista vir uma por pessoa e o Hibernate
     * resolve a N+1 na mesma transação, e o volume de uma escala de folgas é
     * pequeno.
     */
    @Query("""
            SELECT l FROM UserLeave l
            JOIN FETCH l.user
            WHERE (:userId IS NULL OR l.user.id = :userId)
            ORDER BY l.startDate DESC, l.id DESC
            """)
    List<UserLeave> listar(@Param("userId") Long userId);

    /**
     * Folgas que cobrem um dia, para cruzamento com a escala de presencialidade.
     *
     * <p>As duas condições em conjunto são o que implementa "o intervalo
     * contém o dia": a folga tem de ter começado e ainda não ter terminado. Só
     * comparar com o fim deixava passar as folgas que começam depois, e só
     * comparar com o início deixava passar as que já acabaram.
     */
    @Query("""
            SELECT l FROM UserLeave l
            JOIN FETCH l.user
            WHERE l.startDate <= :data AND l.endDate >= :data
            ORDER BY l.startDate ASC
            """)
    List<UserLeave> listarQueCobrem(@Param("data") LocalDate data);
}
