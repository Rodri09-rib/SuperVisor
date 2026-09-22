package domain.model.entities;

import domain.model.enums.AllocationStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class ShiftScheduling {

    private Long id;

    @ManyToOne
    @JoinColumn(name = "edition_scale_id")
    private EditionScale editionScale;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "shift_id")
    private Shift shift;

    private LocalDate specificDate;

    @Enumerated(EnumType.STRING)
    private AllocationStatus analystAcceptanceStatus = AllocationStatus.PENDING;

    public ShiftScheduling(){

    }

    public ShiftScheduling(Long id, EditionScale editionScale, Shift shift, User user, LocalDate specificDate, AllocationStatus analystAcceptanceStatus) {
        this.id = id;
        this.editionScale = editionScale;
        this.shift = shift;
        this.user = user;
        this.specificDate = specificDate;
        this.analystAcceptanceStatus = analystAcceptanceStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EditionScale getEditionScale() {
        return editionScale;
    }

    public void setEditionScale(EditionScale editionScale) {
        this.editionScale = editionScale;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Shift getShift() {
        return shift;
    }

    public void setShift(Shift shift) {
        this.shift = shift;
    }

    public LocalDate getSpecificDate() {
        return specificDate;
    }

    public void setSpecificDate(LocalDate specificDate) {
        this.specificDate = specificDate;
    }

    public AllocationStatus getAnalystAcceptanceStatus() {
        return analystAcceptanceStatus;
    }

    public void setAnalystAcceptanceStatus(AllocationStatus analystAcceptanceStatus) {
        this.analystAcceptanceStatus = analystAcceptanceStatus;
    }
}
