package domain.model.entities;

import jakarta.persistence.*;

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

    private String status = "PENDENTE";
    private OffsetDateTime creationDate;

    @Column(length = 500)
    private String reason;

    public ExchangeRequest(){

    }

    public ExchangeRequest(Long id, ShiftScheduling sourceAllocation, ShiftScheduling destinationAllocation, User requestingUser, String status, OffsetDateTime creationDate) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(OffsetDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
