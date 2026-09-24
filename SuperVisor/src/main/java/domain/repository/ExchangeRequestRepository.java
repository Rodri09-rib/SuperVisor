package domain.repository;

import domain.model.entities.ExchangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRequestRepository extends JpaRepository <ExchangeRequest, Long> {

}
