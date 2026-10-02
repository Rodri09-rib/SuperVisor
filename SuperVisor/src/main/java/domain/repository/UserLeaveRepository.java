package domain.repository;

import domain.model.entities.UserLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserLeaveRepository extends JpaRepository<UserLeave, Long> {

    /**
     * Lista de folgas, opcionalmente restrita a uma pessoa.
     *
     * <p>Devolve as entidades, e não um DTO de projeção, porque a data de
     * criação entra na resposta e a montagem do nome do usuário continua a
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

    /**
     * Folgas que se cruzam com um período, para o relatório de conflitos.
     *
     * <p>Uma consulta para o período inteiro em vez de uma por dia: o relatório
     * percorre todas as datas da escala, e repetir a consulta por cada uma
     * transformava uma leitura em dezenas de idas ao base de dados para o mesmo
     * dado.
     *
     * <p>A condição é a mesma de {@link #listarQueCobrem} aplicada às pontas do
     * período, e não «a folga está dentro do período»: uma folga de 20 dias que
     * começa antes da escala e acaba dentro dela é exatamente o caso que este
     * relatório existe para pegar.
     */
    @Query("""
            SELECT l FROM UserLeave l
            JOIN FETCH l.user
            WHERE l.startDate <= :fim AND l.endDate >= :inicio
            ORDER BY l.startDate ASC, l.id ASC
            """)
    List<UserLeave> listarQueSeCruzamCom(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Apaga as folgas de uma pessoa.
     *
     * <p>Entra em cascata na exclusão da conta, e é o que torna a exclusão
     * possível: uma folga descreve a ausência de uma pessoa, e sem ela a conta
     * ficava presa atrás de linhas que já não têm a quem pertencer.
     *
     * <p>Por isso o contraste com os turnos é deliberado. Um turno é um
     * compromisso com uma escala e sobrevive à pessoa que o tinha; uma folga é
     * informação sobre a própria pessoa e não tem sentido sem ela.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserLeave l WHERE l.user.id = :userId")
    int apagarDoUtilizador(@Param("userId") Long userId);
}
