package tests.support;

import domain.model.entities.EditionScale;
import domain.model.entities.Shift;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.EditionStatus;
import domain.model.enums.UserProfile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;

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

    public static ShiftScheduling allocation(EditionScale scale, User user) {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        return allocation;
    }

    public static ShiftScheduling allocation(EditionScale scale, User user, Shift shift, LocalDate specificDate) {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        allocation.setShift(shift);
        allocation.setSpecificDate(specificDate);
        return allocation;
    }

    public static Shift shift(String acronym, LocalTime start, LocalTime end, String dayOfTheWeek) {
        Shift shift = new Shift();
        shift.setAcronym(acronym);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setDayiftheWeek(dayOfTheWeek);
        return shift;
    }

    public static AllocationStatus acceptance() {
        return AllocationStatus.PENDING;
    }
}
