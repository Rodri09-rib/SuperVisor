package domain.model.entities;

import domain.model.IntervaloHorario;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import jakarta.persistence.*;

import java.time.DayOfWeek;
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

    /**
     * O dia da semana a que esta alocação se aplica.
     *
     * <p>Vem sempre do {@link ShiftType}, mesmo quando há {@code specificDate}:
     * o turno é o que fixa o dia no domínio, e a data específica é o calendário
     * concreto em que esse turno cai. Um sábado de T1 e um domingo de T6 têm o
     * mesmo horário e mesmo assim não se cruzam, e é este o método que o
     * distingue.
     */
    public DayOfWeek diaDaSemana() {
        return shift == null ? null : shift.getDayOfWeek();
    }

    /**
     * O horário que esta alocação ocupa de facto: o especial quando existe, o do
     * turno caso contrário.
     *
     * <p>Devolve {@code null} só quando não há turno nenhum, que é um estado
     * que a base não devia ter mas que um dado antigo podia ter.
     */
    public IntervaloHorario intervaloEfetivo() {
        if (temHorarioCustomizado()) {
            return IntervaloHorario.de(customStartTime, customEndTime);
        }
        return shift == null ? null : shift.getIntervaloHorario();
    }

    /**
     * Verdadeiro quando esta alocação ocupa um dia do calendário concreto.
     *
     * <p>Sem data específica, vale para todos os dias do dia da semana do
     * turno dentro da escala; com data, vale só para esse dia.
     */
    public boolean cobreDia(LocalDate data) {
        if (data == null) {
            return false;
        }
        if (specificDate != null) {
            return specificDate.equals(data);
        }
        return diaDaSemana() != null && data.getDayOfWeek() == diaDaSemana();
    }

    /**
     * Verdadeiro quando as duas alocações disputam a mesma pessoa à mesma hora.
     *
     * <p>É o double-booking: a mesma pessoa escalada duas vezes em cima uma da
     * outra. Duas condições têm de ser verdadeiras ao mesmo tempo.
     *
     * <p>Os <em>horários</em> têm de se cruzar. T1 (08h00-12h00) e T3
     * (12h00-16h00) só se tocam à meia-noite e por isso não contam: quem acaba
     * ao meio-dia pode entrar no turno seguinte. Já T1 e T2 (11h00-15h00)
     * cruzam-se de verdade, e alguém não pode estar nos dois.
     *
     * <p>Os <em>dias</em> têm de poder ser o mesmo. Com data específica nos
     * dois lados, tem de ser a mesma data; sem data, o dia da semana dos turnos
     * tem de bater certo. Quando só um dos lados tem data, compara-se essa data
     * com o dia da semana do outro turno — é assim que um T1 sem data específica
     * ainda entra em conflito com um T1 marcado para um sábado concreto.
     */
    public boolean conflitaCom(ShiftScheduling outra) {
        if (outra == null) {
            return false;
        }

        IntervaloHorario meu = intervaloEfetivo();
        IntervaloHorario dela = outra.intervaloEfetivo();

        if (meu == null || dela == null || !meu.sobrepoe(dela)) {
            return false;
        }

        if (specificDate != null && outra.specificDate != null) {
            return specificDate.equals(outra.specificDate);
        }
        if (specificDate != null) {
            return specificDate.getDayOfWeek() == outra.diaDaSemana();
        }
        if (outra.specificDate != null) {
            return diaDaSemana() == outra.specificDate.getDayOfWeek();
        }

        return diaDaSemana() != null && diaDaSemana() == outra.diaDaSemana();
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
