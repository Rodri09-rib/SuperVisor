package domain.repository;

import domain.model.entities.ShiftScheduling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftSchedulingRepository extends JpaRepository <ShiftScheduling, Long> {

    List<ShiftScheduling> findByEditionScaleIdOrderByIdAsc(Long editionScaleId);

    /**
     * Alocações de uma pessoa dentro de uma escala, que é o recorte que a
     * regra de sobreposição precisa.
     *
     * <p>Por pessoa e por escala, e não por turno: a sobreposição é uma
     * comparação entre intervalos, e quem a decide são as horas ocupadas, não o
     * nome do turno. Trazer só o mesmo turno perderia o caso mais comum, que é
     * T2 a cair em cima de T1.
     */
    List<ShiftScheduling> findByEditionScaleIdAndUserIdOrderByIdAsc(Long editionScaleId, Long userId);

}
