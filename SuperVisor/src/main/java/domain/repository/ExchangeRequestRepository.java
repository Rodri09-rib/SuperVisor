package domain.repository;

import domain.model.entities.ExchangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeRequestRepository
        extends JpaRepository<ExchangeRequest, Long>, JpaSpecificationExecutor<ExchangeRequest> {

    /**
     * Pedido pelo identificador já com as ligações carregadas, para a página de
     * resposta: sem este método, responder a um pedido dispararia uma consulta
     * por cada alocação e outra por cada utilizador.
     *
     * <p>Não traz filtros, e por isso não sofre do problema do
     * {@code :parametro is null} que obrigou o histórico a ser uma
     * {@code Specification}: sem parâmetros, o JPQL é gerado e planado uma vez e
     * reutilizado.
     */
    @Query("""
            SELECT e FROM ExchangeRequest e
            LEFT JOIN FETCH e.requestingUser
            LEFT JOIN FETCH e.requestedUser
            LEFT JOIN FETCH e.sourceAllocation
            LEFT JOIN FETCH e.destinationAllocation
            WHERE e.id = :id
            """)
    Optional<ExchangeRequest> findByIdComDetalhes(@Param("id") Long id);
}
