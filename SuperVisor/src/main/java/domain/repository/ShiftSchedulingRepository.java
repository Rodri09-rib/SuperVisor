package domain.repository;

import domain.model.entities.ShiftScheduling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Quantos turnos a pessoa tem, em qualquer escala.
     *
     * <p>É o que impede a exclusão de uma conta que deixa turnos marcados. Um
     * turno não sobrevive à pessoa que o cobre — a coluna é {@code NOT NULL} e,
     * mesmo que não fosse, um turno sem dono é um turno que ninguém vem
     * cumprir — e apagar a escala inteira para o resolver seria perder o
     * histórico das outras pessoas por causa de uma.
     */
    long countByUserId(Long userId);

    /**
     * Turnos de uma escala, sem o resto da escala carregada.
     *
     * <p>Existe para a exclusão, que precisa dos identificadores para apagar os
     * pedidos de troca que os referenciam antes de os apagar a eles. Trazer a
     * escala inteira seria arrastar para o contexto um objeto que vai ser
     * removido logo a seguir e que ainda traz o {@code User} de cada turno.
     */
    @Query("SELECT a.id FROM ShiftScheduling a WHERE a.editionScale.id = :escalaId")
    List<Long> idsPorEscala(@Param("escalaId") Long escalaId);

}
