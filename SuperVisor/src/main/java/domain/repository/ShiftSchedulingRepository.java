package domain.repository;

import domain.model.entities.ShiftScheduling;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftSchedulingRepository extends JpaRepository <ShiftScheduling, Long> {

}
