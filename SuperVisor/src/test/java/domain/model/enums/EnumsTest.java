package domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Enums do domínio")
class EnumsTest {

    @Test
    @DisplayName("UserProfile possui exatamente SUPERVISOR e ANALIST")
    void userProfile() {
        assertThat(UserProfile.values())
                .containsExactly(UserProfile.SUPERVISOR, UserProfile.ANALIST);
    }

    @Test
    @DisplayName("EditionStatus possui exatamente DRAFT, PUBLISHED e COMPLETED")
    void editionStatus() {
        assertThat(EditionStatus.values())
                .containsExactly(EditionStatus.DRAFT, EditionStatus.PUBLISHED, EditionStatus.COMPLETED);
    }

    @Test
    @DisplayName("AllocationStatus possui exatamente PENDING, ACCEPTED e REJECTED")
    void allocationStatus() {
        assertThat(AllocationStatus.values())
                .containsExactly(AllocationStatus.PENDING, AllocationStatus.ACCEPTED, AllocationStatus.REJECTED);
    }

    @Test
    @DisplayName("valueOf devolve a constante correspondente ao nome")
    void valueOf() {
        assertThat(UserProfile.valueOf("SUPERVISOR")).isEqualTo(UserProfile.SUPERVISOR);
        assertThat(EditionStatus.valueOf("PUBLISHED")).isEqualTo(EditionStatus.PUBLISHED);
        assertThat(AllocationStatus.valueOf("REJECTED")).isEqualTo(AllocationStatus.REJECTED);
    }

    @Test
    @DisplayName("valueOf com nome inexistente lança IllegalArgumentException")
    void valueOfInvalido() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> UserProfile.valueOf("GERENTE"))).hasMessageContaining("GERENTE");
    }
}
