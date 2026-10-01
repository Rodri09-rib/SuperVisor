package domain.model.entities;

import domain.model.enums.ExchangeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_exchange_request")
public class ExchangeRequest {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "source_allocation_id")
    private ShiftScheduling sourceAllocation;

    @ManyToOne
    @JoinColumn(name = "destination_allocation_id")
    private ShiftScheduling destinationAllocation;

    @ManyToOne
    @JoinColumn(name = "requesting_user_id")
    private User requestingUser;

    /**
     * Colega a quem a troca foi pedida.
     *
     * <p>É uma cópia, e não se vai buscar a alocação de destino, porque ao
     * responder a um pedido aceite as duas alocações trocam de dono: ler o
     * destino depois da resposta daria o próprio requerente como "troca com", e
     * o histórico deixaria de dizer com quem foi. Salvar a pessoa no momento
     * do pedido mantém a história correta para sempre.
     *
     * <p>{@code ON DELETE SET NULL} está declarado aqui e não só na migração
     * porque o {@code ddl-auto} do Hibernate cria a sua própria chave estrangeira
     * quando a entidade não a declara. Uma migração que adiciona a restrição com
     * {@code SET NULL} e uma constraint do Hibernate com o default
     * {@code NO ACTION} coexistiriam, e a que o PostgreSQL acabaria por
     * respeitar seria a errada: apagar um colaborador rebentava em vez de
     * deixar o histórico vivo.
     */
    @ManyToOne
    @JoinColumn(name = "requested_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User requestedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeStatus status = ExchangeStatus.PENDING;

    private OffsetDateTime creationDate;

    /**
     * Momento da resposta. Fica nulo enquanto o pedido está pendente, que é o
     * que distingue "ainda ninguém respondeu" de "respondeu logo a seguir".
     */
    private OffsetDateTime approvalDate;

    @Column(length = 500)
    private String reason;

    public ExchangeRequest(){

    }

    public ExchangeRequest(Long id, ShiftScheduling sourceAllocation, ShiftScheduling destinationAllocation, User requestingUser, ExchangeStatus status, OffsetDateTime creationDate) {
        this.id = id;
        this.sourceAllocation = sourceAllocation;
        this.destinationAllocation = destinationAllocation;
        this.requestingUser = requestingUser;
        this.status = status;
        this.creationDate = creationDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ShiftScheduling getSourceAllocation() {
        return sourceAllocation;
    }

    public void setSourceAllocation(ShiftScheduling sourceAllocation) {
        this.sourceAllocation = sourceAllocation;
    }

    public ShiftScheduling getDestinationAllocation() {
        return destinationAllocation;
    }

    public void setDestinationAllocation(ShiftScheduling destinationAllocation) {
        this.destinationAllocation = destinationAllocation;
    }

    public User getRequestingUser() {
        return requestingUser;
    }

    public void setRequestingUser(User requestingUser) {
        this.requestingUser = requestingUser;
    }

    public User getRequestedUser() {
        return requestedUser;
    }

    public void setRequestedUser(User requestedUser) {
        this.requestedUser = requestedUser;
    }

    public ExchangeStatus getStatus() {
        return status;
    }

    public void setStatus(ExchangeStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(OffsetDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public OffsetDateTime getApprovalDate() {
        return approvalDate;
    }

    public void setApprovalDate(OffsetDateTime approvalDate) {
        this.approvalDate = approvalDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}