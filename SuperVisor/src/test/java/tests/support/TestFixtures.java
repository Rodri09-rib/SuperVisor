package tests.support;

import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.entities.UserLeave;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.EditionStatus;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.model.enums.WorkModality;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Fábrica de entidades usada pelas suítes. Existe para que cada teste declará
 * apenas o que lhe interessa e o estado inicial do banco seja previsível.
 */
public final class TestFixtures {

    public static final String RAW_PASSWORD = "123456";

    private TestFixtures() {
    }

    public static User user(String name, String email, UserProfile profile, String rawPassword) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(rawPassword);
        user.setProfile(profile);
        return user;
    }

    public static User user(String name, String email, UserProfile profile) {
        return user(name, email, profile, RAW_PASSWORD);
    }

    public static User supervisor() {
        return user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
    }

    public static User analyst() {
        return user("João", "joao@teste.com", UserProfile.ANALIST);
    }

    /**
     * Usuário com equipe atribuída. Existe porque a escala de
     * presencialidade só gera linhas para quem tem equipe: um teste que usa
     * {@link #analyst()} sem equipe passaria a ver uma escala vazia e não saberia
     * se o serviço estava a falhar ou se simplesmente não tinha ninguém para
     * escalar.
     */
    public static User userInTeam(String name, String email, UserProfile profile, TeamGroup team) {
        User user = user(name, email, profile);
        user.setTeamGroup(team);
        return user;
    }

    public static User supervisorInTeam(TeamGroup team) {
        return userInTeam("Administrador", "admin@teste.com", UserProfile.SUPERVISOR, team);
    }

    public static User analystInTeam(TeamGroup team) {
        return userInTeam("João", "joao@teste.com", UserProfile.ANALIST, team);
    }

    /** Usuário desativado: não pode receber alocações. */
    public static User inactiveAnalyst() {
        User user = analyst();
        user.setActive(false);
        return user;
    }

    public static User userWithEncodedPassword(PasswordEncoder encoder, String name, String email, UserProfile profile) {
        return user(name, email, profile, encoder.encode(RAW_PASSWORD));
    }

    public static EditionScale editionScale(String name, User createdBy, EditionStatus status) {
        EditionScale scale = new EditionScale();
        scale.setName(name);
        scale.setInitialDate(LocalDate.of(2025, 10, 1));
        scale.setEndDate(LocalDate.of(2025, 10, 31));
        scale.setStatus(status);
        scale.setCreatedBy(createdBy);
        return scale;
    }

    public static EditionScale draftScale(User createdBy) {
        return editionScale("Escala Outubro", createdBy, EditionStatus.DRAFT);
    }

    /**
     * Alocação mínima válida. O turno é obrigatório pelo modelo, pelo que
     * nenhum teste pode criar uma alocação sem ele.
     */
    public static ShiftScheduling allocation(EditionScale scale, User user) {
        return allocation(scale, user, ShiftType.T1_SAB, null);
    }

    public static ShiftScheduling allocation(EditionScale scale, User user,
                                             ShiftType shift, LocalDate specificDate) {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        allocation.setShift(shift);
        allocation.setSpecificDate(specificDate);
        return allocation;
    }

    /** Alocação com atribuições especiais e horário dentro do turno. */
    public static ShiftScheduling allocationCompleta(EditionScale scale, User user,
                                                     ShiftType shift, LocalDate specificDate,
                                                     List<AssignmentType> assignments,
                                                     LocalTime customStart, LocalTime customEnd) {
        ShiftScheduling allocation = allocation(scale, user, shift, specificDate);
        allocation.setAssignments(assignments == null ? Set.of() : new java.util.LinkedHashSet<>(assignments));
        allocation.setCustomStartTime(customStart);
        allocation.setCustomEndTime(customEnd);
        return allocation;
    }

    public static AllocationStatus acceptance() {
        return AllocationStatus.PENDING;
    }

    /** Célula de escala de presencialidade. */
    public static WorkModalitySchedule workModality(User user, LocalDate date,
                                                   WorkModality modality, TeamGroup team) {
        WorkModalitySchedule schedule = new WorkModalitySchedule();
        schedule.setUser(user);
        schedule.setDate(date);
        schedule.setModality(modality);
        schedule.setTeamGroup(team);
        return schedule;
    }

    /** Folga de um dia. */
    public static UserLeave leave(User user, LocalDate date) {
        return leave(user, date, date, "Motivo de teste");
    }

    public static UserLeave leave(User user, LocalDate start, LocalDate end, String reason) {
        UserLeave leave = new UserLeave();
        leave.setUser(user);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setReason(reason);
        return leave;
    }

    /**
     * Pedido de troca já com o colega pedido preenchido, que é como o serviço o
     * grava. Um teste que o construísse à mão sem o colega simularia um pedido
     * anterior à migração de {@code requestedUser}, que é um caso diferente.
     */
    public static ExchangeRequest exchangeRequest(ShiftScheduling origem, ShiftScheduling destino,
                                                  User requerente, User colega,
                                                  ExchangeStatus status) {
        ExchangeRequest request = new ExchangeRequest();
        request.setSourceAllocation(origem);
        request.setDestinationAllocation(destino);
        request.setRequestingUser(requerente);
        request.setRequestedUser(colega);
        request.setStatus(status);
        request.setCreationDate(OffsetDateTime.now());
        if (status != ExchangeStatus.PENDING) {
            request.setApprovalDate(OffsetDateTime.now());
        }
        return request;
    }
}
