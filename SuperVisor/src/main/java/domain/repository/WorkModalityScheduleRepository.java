package domain.repository;

import domain.model.entities.WorkModalitySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkModalityScheduleRepository extends JpaRepository<WorkModalitySchedule, Long> {

    /**
     * Escala de um intervalo, já com o usuário e o turno carregados e
     * pronta a desenhar na grelha.
     *
     * <p>O intervalo é fechado nas duas pontas, ao contrário do histórico de
     * trocas: aqui cada linha é um dia, não um instante, e o dia final da
     * semana pedido tem de aparecer na grelha.
     */
    @Query("""
            SELECT w FROM WorkModalitySchedule w
            JOIN FETCH w.user
            WHERE w.date BETWEEN :inicio AND :fim
            ORDER BY w.user.name ASC, w.date ASC
            """)
    List<WorkModalitySchedule> listarIntervalo(@Param("inicio") LocalDate inicio,
                                               @Param("fim") LocalDate fim);

    /**
     * Todas as linhas da semana, sem carregar o usuário.
     *
     * <p>É o que a geração usa: antes de escrever é preciso saber que dias já
     * têm registo, e perguntar dia a dia custaria cinco consultas por pessoa —
     * cem pessoas dariam quinhentas. Uma leitura do intervalo inteiro e um mapa
     * por {@code (usuário, dia)} resolvem o mesmo problema com uma consulta.
     */
    @Query("""
            SELECT w FROM WorkModalitySchedule w
            WHERE w.date BETWEEN :inicio AND :fim
            """)
    List<WorkModalitySchedule> listarSemUtilizadorDoIntervalo(@Param("inicio") LocalDate inicio,
                                                               @Param("fim") LocalDate fim);

    List<WorkModalitySchedule> findByUserIdAndDate(Long userId, LocalDate date);

    /**
     * Apaga a escala de um intervalo.
     *
     * <p>Devolve quantas linhas caíram, e não nada: o frontend diz o que
     * aconteceu depois da exclusão, e «a escala da semana foi apagada» sem
     * número é um resultado que não se distingue do de um intervalo já vazio.
     *
     * <p>É uma exclusão em bloco porque a escala é o único volume de dados que
     * cresce com o número de pessoas e não com o número de escalas: cem
     * colaboradores são quinhentas linhas por semana, e apagá-las uma a uma
     * seria quinhentas idas ao banco para um intervalo que já se sabe qual é.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkModalitySchedule w WHERE w.date BETWEEN :inicio AND :fim")
    int apagarDoIntervalo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Apaga a escala de uma pessoa.
     *
     * <p>Entra em cascata na exclusão da conta, pela mesma razão das folgas: a
     * célula diz onde é que a pessoa estava, e sem a pessoa não há a quem a
     * célula pertencer.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkModalitySchedule w WHERE w.user.id = :userId")
    int apagarDoUtilizador(@Param("userId") Long userId);
}
