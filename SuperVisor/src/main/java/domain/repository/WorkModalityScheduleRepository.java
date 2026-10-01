package domain.repository;

import domain.model.entities.WorkModalitySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
