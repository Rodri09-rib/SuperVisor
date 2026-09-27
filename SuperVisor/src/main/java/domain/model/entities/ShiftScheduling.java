package domain.model.entities;

import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tb_shift_scheduling")
public class ShiftScheduling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Escala a que a alocação pertence. Obrigatória: não faz sentido uma
     * alocação órfã, e a regra de negócio só permite turnos dentro de uma
     * escala de fim de semana.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "edition_scale_id", nullable = false)
    private EditionScale editionScale;

    /**
     * Utilizador cadastrado que cobre o turno. Obrigatório por regra de
     * negócio: é proibido registrar nomes em texto livre, portanto não existe
     * alocação sem {@link User} associado.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Turno de fim de semana. Substitui a antiga relação com a entidade
     * {@code Shift}: o horário e o dia são invariantes do turno e passam a
     * derivar do enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 20)
    private ShiftType shift;

    private LocalDate specificDate;

    /**
     * Atribuições especiais do utilizador dentro deste turno (opcional). A
     * coleção é um {@code Set} porque a ordem não faz parte do domínio; a
     * ordem estável para leitura (ordem de declaração do enum) é garantida na
     * transformação para DTO.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tb_shift_scheduling_assignment",
            joinColumns = @JoinColumn(name = "shift_scheduling_id"))
    @Column(name = "assignment", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<AssignmentType> assignments = new LinkedHashSet<>();

    /**
     * Horário especial dentro do turno, para os casos em que o utilizador
     * faz um horário diferenciado. Ambos são opcionais, mas têm de ser
     * informados em conjunto (ver a validação no serviço).
     */
    private LocalTime customStartTime;
    private LocalTime customEndTime;

    @Enumerated(EnumType.STRING)
    private AllocationStatus analystAcceptanceStatus = AllocationStatus.PENDING;

    public ShiftScheduling(){

    }

    public ShiftScheduling(Long id, EditionScale editionScale, ShiftType shift, User user,
                           LocalDate specificDate, AllocationStatus analystAcceptanceStatus) {
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

    public ShiftType getShift() {
        return shift;
    }

    public void setShift(ShiftType shift) {
        this.shift = shift;
    }

    public Set<AssignmentType> getAssignments() {
        return assignments;
    }

    public void setAssignments(Set<AssignmentType> assignments) {
        this.assignments = assignments == null ? new LinkedHashSet<>() : new LinkedHashSet<>(assignments);
    }

    public void addAssignment(AssignmentType assignment) {
        this.assignments.add(assignment);
    }

    public void clearAssignments() {
        this.assignments.clear();
    }

    public LocalTime getCustomStartTime() {
        return customStartTime;
    }

    public void setCustomStartTime(LocalTime customStartTime) {
        this.customStartTime = customStartTime;
    }

    public LocalTime getCustomEndTime() {
        return customEndTime;
    }

    public void setCustomEndTime(LocalTime customEndTime) {
        this.customEndTime = customEndTime;
    }

    /** Verdadeiro quando os dois horários customizados foram informados. */
    public boolean temHorarioCustomizado() {
        return customStartTime != null && customEndTime != null;
    }

    /** Horário efetivo: o customizado quando existe, o do turno caso contrário. */
    public LocalTime getInicioEfetivo() {
        return customStartTime != null ? customStartTime : (shift == null ? null : shift.getStartTime());
    }

    public LocalTime getFimEfetivo() {
        return customEndTime != null ? customEndTime : (shift == null ? null : shift.getEndTime());
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
