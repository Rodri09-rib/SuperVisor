package domain.repository;

import domain.model.entities.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** Todos os feriados do calendário, do mais antigo para o mais recente. */
    List<Holiday> findAllByOrderByDateAsc();

    /** Procura por data, que é o campo único da tabela. */
    Optional<Holiday> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);

    /**
     * Duplicidade de data ignorando o próprio registo, para a atualização:
     * mover a descrição de um feriado para a data que já é dele não pode ser
     * recusado como duplicado.
     */
    boolean existsByDateAndIdNot(LocalDate date, Long id);
}
