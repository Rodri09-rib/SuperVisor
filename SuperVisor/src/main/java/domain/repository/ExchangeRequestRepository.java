package domain.repository;

import domain.model.entities.ExchangeRequest;
import domain.model.enums.ExchangeStatus;
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
     * por cada alocação e outra por cada usuário.
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

    /**
     * Diz se o mesmo sentido de troca já está pendente.
     *
     * <p>O sentido é deliberadamente dirigido: A pede a B e B pede a A são dois
     * pedidos diferentes e ambos legítimos. Quem pidió já tem um turno para
     * ceder e espera uma resposta; o segundo pedido inverte os papéis e os dois
     * pedidos vivem lado a lado até alguém responder. Só o sentido contrário
     * seria um duplicado, e é esse que se recusa.
     *
     * <p>Um {@code exists} em vez de {@code findBy}: a pergunta é se há um
     * pedido, não qual é, e assim o histórico não é trazido para a memória
     * só para se deitar fora.
     */
    boolean existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
            ExchangeStatus status, Long sourceAllocationId, Long destinationAllocationId);

    /**
     * Diz se a alocação está sendo cedida ou pedida em algum pedido pendente.
     *
     * <p>Qualquer uma das pontas conta, e por isso é um {@code or} em vez de
     * dois métodos: a resposta é uma só pergunta, e perguntar duas vezes para
     * depois combinar os resultados dava a mesma resposta com o dobro do
     * trabalho.
     *
     * <p>Está escrito em JPQL e não como nome derivado porque o nome derivado
     * para «estado igual a isto e origem ou destino igual a aquilo» teria de
     * repetir o estado nos dois ramos, e a leitura ficaria pior do que a
     * consulta.
     */
    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM ExchangeRequest e
            WHERE e.status = :status
            AND (e.sourceAllocation.id = :alocacaoId
                 OR e.destinationAllocation.id = :alocacaoId)
            """)
    boolean existePendenteParaA(@Param("status") ExchangeStatus status,
                                @Param("alocacaoId") Long alocacaoId);
}
