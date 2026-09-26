package domain.repository;

import domain.model.entities.ShiftScheduling;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftSchedulingRepository extends JpaRepository <ShiftScheduling, Long> {

    List<ShiftScheduling> findByEditionScaleIdOrderByIdAsc(Long editionScaleId);

}
