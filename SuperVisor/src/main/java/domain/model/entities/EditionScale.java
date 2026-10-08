package domain.model.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import domain.model.enums.EditionStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tb_edition_scale")
public class EditionScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private LocalDate initialDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private EditionStatus status;

    /**
     * Indica se as recompensas de folgas desta escala já foram creditadas.
     *
     * <p>É a trava de idempotência da conclusão: os dias entram no saldo dos
     * utilizadores no mesmo instante em que a escala passa a {@code COMPLETED},
     * e esta flag regista que o crédito já aconteceu. Sem ela, um duplo clique
     * ou um repetição por falha de rede creditava duas vezes o mesmo trabalho.
     */
    @Column(name = "rewards_processed", nullable = false)
    private boolean rewardsProcessed = false;

    @JsonIgnore
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

    public boolean isRewardsProcessed() {
        return rewardsProcessed;
    }

    public void setRewardsProcessed(boolean rewardsProcessed) {
        this.rewardsProcessed = rewardsProcessed;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
}
