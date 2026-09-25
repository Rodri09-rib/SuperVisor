package tests.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contêiner PostgreSQL único e descartável compartilhado por toda a suíte de testes.
 * A imagem espelha a definida no docker-compose.yml do projeto (PostgreSQL 16).
 */
public final class PostgresTestContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private static final PostgreSQLContainer<?> CONTAINER;

    static {
        CONTAINER = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("supervisor_test")
                .withUsername("supervisor")
                .withPassword("supervisor");
        CONTAINER.start();
    }

    private PostgresTestContainer() {
    }

    public static PostgreSQLContainer<?> container() {
        return CONTAINER;
    }

    /**
     * Sobrescreve o datasource apontado em application.yml (que hoje vai para o
     * PostgreSQL de desenvolvimento) pelo banco efêmero do Testcontainers, e
     * recria o schema a cada inicialização de contexto.
     */
    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CONTAINER::getUsername);
        registry.add("spring.datasource.password", CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", CONTAINER::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("api.security.token.secret", () -> "chave-de-teste-super-visor-0123456789-abcdef");
    }
}
