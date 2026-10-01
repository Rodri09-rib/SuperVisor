package domain.model.entities;

import domain.model.enums.TeamGroup;
import domain.model.enums.WorkModality;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Modalidade de trabalho de um colaborador num dia útil.
 *
 * <p>Uma linha por pessoa e por dia. O par {@code (user, date)} é único: a
 * escala é gerada por um endpoint que pode ser chamado mais do que uma vez, e
 * sem esta restrição a segunda passagem criaria duplicados em vez de corrigir
 * a semana.
 *
 * <p>{@code teamGroup} é uma cópia da equipe no momento da geração. Vive aqui
 * para que o histórico não mude se alguém mudar de equipe mais tarde: a escala
 * de inadmissão responde "onde é que esta pessoa estava em março", e a equipe
 * dela em março pode não ser a de hoje.
 */
@Entity
@Table(
        name = "tb_work_modality_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_work_modality_user_date",
                columnNames = {"user_id", "date"}))
public class WorkModalitySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkModality modality;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_group", nullable = false, length = 20)
    private TeamGroup teamGroup;

    public WorkModalitySchedule() {

    }

    public WorkModalitySchedule(Long id, User user, LocalDate date,
                                WorkModality modality, TeamGroup teamGroup) {
        this.id = id;
        this.user = user;
        this.date = date;
        this.modality = modality;
        this.teamGroup = teamGroup;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public WorkModality getModality() {
        return modality;
    }

    public void setModality(WorkModality modality) {
        this.modality = modality;
    }

    public TeamGroup getTeamGroup() {
        return teamGroup;
    }

    public void setTeamGroup(TeamGroup teamGroup) {
        this.teamGroup = teamGroup;
    }
}
