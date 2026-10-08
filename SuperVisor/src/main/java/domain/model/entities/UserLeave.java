package domain.model.entities;

import domain.model.enums.LeaveDuration;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Folga de um colaborador, de {@code startDate} a {@code endDate}, inclusive.
 *
 * <p>Um intervalo e não um dia solto porque é assim que a falta se pede: quem
 * vai de férias marca o início e o fim de uma vez. Já quem falta um dia marca
 * os dois com a mesma data.
 *
 * <p>{@code createdAt} é o registo de quando a folga foi lançada, e não um dado
 * pedido ao usuário: serve para ordenar o histórico e para se perceber quem
 * foi atualizado mais tarde. Fica preenchido pela aplicação, nunca pelo
 * cliente.
 */
@Entity
@Table(name = "tb_user_leave")
public class UserLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 500)
    private String reason;

    /**
     * Duração da folga dentro do dia: dia inteiro, só a manhã ou só a tarde.
     *
     * <p>Nasce {@code FULL_DAY} porque as folgas anteriores a este campo eram
     * todas de dia inteiro, e uma célula nula numa linha já gravada faria a
     * leitura antiga devolver "sem duração" para uma folga que existe.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_duration", nullable = false, length = 20)
    private LeaveDuration leaveDuration = LeaveDuration.FULL_DAY;

    /**
     * Momento do registo. Anotado como {@code Instant} e não {@code OffsetDateTime}
     * porque é um instante, e não uma data com fusos: o que interessa é "quando
     * foi escrito", e a base salva o mesmo formato nos dois casos.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserLeave() {

    }

    public UserLeave(Long id, User user, LocalDate startDate, LocalDate endDate, String reason) {
        this.id = id;
        this.user = user;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    /**
     * Verdadeiro quando a data dada está dentro da folga, contando as
     * extremidades. É o que responde "quem está de folga no dia X", e a
     * comparação tem de ser feita aqui e não em SQL para não depender de
     * funções de data que mudam entre versões do PostgreSQL.
     */
    public boolean cobre(LocalDate data) {
        return data != null
                && startDate != null
                && endDate != null
                && !data.isBefore(startDate)
                && !data.isAfter(endDate);
    }

    /**
     * Quanto a folga custa ao saldo, em dias.
     *
     * <p>Uma folga de dia inteiro custa um dia por cada dia de calendário com
     * as extremidades incluídas; uma folga de manhã ou de tarde custa sempre
     * meio dia, porque cobre meio expediente. É esta função que garante que os
     * meios expedientes entram como 0.5 e não como 1 por arredondamento — e é
     * aqui, e não na leitura, que o valor é calculado, para o que a base
     * guarda em saldo e o que a tabela mostra serem o mesmo número.
     */
    public BigDecimal custoEmDias() {
        if (leaveDuration != null && leaveDuration.isMeiaJornada()) {
            return BigDecimal.valueOf(0.5);
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }

    public LeaveDuration getLeaveDuration() {
        return leaveDuration;
    }

    public void setLeaveDuration(LeaveDuration leaveDuration) {
        this.leaveDuration = leaveDuration;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
