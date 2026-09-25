package domain.model.entities;

import domain.model.enums.EditionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade EditionScale")
class EditionScaleTest {

    @Test
    @DisplayName("construtor completo preserva todos os campos, inclusive o criado por")
    void construtorCompleto() {
        User creator = new User(1L, "Admin", "admin@teste.com", "x", null);

        EditionScale scale = new EditionScale(
                10L, "Escala Outubro", java.time.LocalDate.of(2025, 10, 1),
                java.time.LocalDate.of(2025, 10, 31), EditionStatus.DRAFT, creator);

        assertThat(scale.getId()).isEqualTo(10L);
        assertThat(scale.getName()).isEqualTo("Escala Outubro");
        assertThat(scale.getInitialDate()).isEqualTo(java.time.LocalDate.of(2025, 10, 1));
        assertThat(scale.getEndDate()).isEqualTo(java.time.LocalDate.of(2025, 10, 31));
        assertThat(scale.getStatus()).isEqualTo(EditionStatus.DRAFT);
        assertThat(scale.getCreatedBy()).isSameAs(creator);
    }

    @Test
    @DisplayName("construtor vazio não inicializa o status, deixando-o nulo")
    void construtorVazioNaoDefineStatus() {
        EditionScale scale = new EditionScale();

        assertThat(scale.getId()).isNull();
        assertThat(scale.getName()).isNull();
        assertThat(scale.getInitialDate()).isNull();
        assertThat(scale.getEndDate()).isNull();
        assertThat(scale.getStatus()).isNull();
        assertThat(scale.getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        EditionScale scale = new EditionScale();
        User creator = new User();

        scale.setId(3L);
        scale.setName("Escala Novembro");
        scale.setInitialDate(java.time.LocalDate.of(2025, 11, 1));
        scale.setEndDate(java.time.LocalDate.of(2025, 11, 30));
        scale.setStatus(EditionStatus.PUBLISHED);
        scale.setCreatedBy(creator);

        assertThat(scale.getId()).isEqualTo(3L);
        assertThat(scale.getName()).isEqualTo("Escala Novembro");
        assertThat(scale.getInitialDate()).isEqualTo(java.time.LocalDate.of(2025, 11, 1));
        assertThat(scale.getEndDate()).isEqualTo(java.time.LocalDate.of(2025, 11, 30));
        assertThat(scale.getStatus()).isEqualTo(EditionStatus.PUBLISHED);
        assertThat(scale.getCreatedBy()).isSameAs(creator);
    }

    @Test
    @DisplayName("createdBy está anotado com @JsonIgnore, logo não vaza na resposta JSON")
    void createdByNaoEhSerializado() throws NoSuchFieldException {
        boolean ignored = EditionScale.class
                .getDeclaredField("createdBy")
                .isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore.class);

        assertThat(ignored).isTrue();
    }
}
