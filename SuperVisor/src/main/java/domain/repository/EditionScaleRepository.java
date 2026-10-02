package domain.repository;

import domain.model.entities.EditionScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EditionScaleRepository extends JpaRepository <EditionScale, Long> {

    /**
     * Solta as escalas criadas por uma pessoa que vai ser excluída.
     *
     * <p>A coluna é anulável e o apagamento em cascata é o comportamento
     * escolhido para a exclusão de contas: a escala sobrevive à pessoa que a
     * criou, porque é o registo de quem trabalhou que fim de semana e não tem
     * nada a ver com a conta de quem a montou. O que se perde é a autoria, e é
     * um custo certo para não se perder a escala.
     *
     * <p>É uma atualização em bloco e não um carregamento seguido de {@code
     * save}: as escalas criadas pela mesma pessoa podem ser muitas e nenhuma
     * delas é alterada em mais do que este campo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EditionScale e SET e.createdBy = null WHERE e.createdBy.id = :userId")
    int soltarCriador(@Param("userId") Long userId);
}