package domain.model.entities;

import domain.model.enums.EditionStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

public class EditionScale {

    private Long id;
    private String name;
    private LocalDate initialDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private EditionStatus status;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    private User createdBy;

public EditionScale(){

}

    public EditionScale(Long id, String name, LocalDate initialDate, LocalDate endDate, EditionStatus status, User createdBy) {
        this.id = id;
        this.name = name;
        this.initialDate = initialDate;
        this.endDate = endDate;
        this.status = status;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getInitialDate() {
        return initialDate;
    }

    public void setInitialDate(LocalDate initialDate) {
        this.initialDate = initialDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public EditionStatus getStatus() {
        return status;
    }

    public void setStatus(EditionStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
}
