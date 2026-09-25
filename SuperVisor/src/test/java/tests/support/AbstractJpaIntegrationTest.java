package tests.support;

import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes de repositório/persistência: carrega apenas a camada JPA,
 * contra um PostgreSQL real descartável (Testcontainers) com o schema
 * recriado a cada inicialização de contexto.
 *
 * <p>{@code @DataJpaTest} já rollbacka cada teste por transação; a limpeza
 * explícita em {@link #limparBanco()} protege contra qualquer seed executado
 * por outra classe de teste no mesmo contexto.
 */
@DataJpaTest
@ContextConfiguration(classes = TestApplicationConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractJpaIntegrationTest {

    @DynamicPropertySource
    static void registrarPropriedades(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerProperties(registry);
    }

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EditionScaleRepository editionScaleRepository;

    @Autowired
    protected ShiftRepository shiftRepository;

    @Autowired
    protected ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    protected ExchangeRequestRepository exchangeRequestRepository;

    @BeforeEach
    void limparBanco() {
        entityManager.createQuery("DELETE FROM ExchangeRequest").executeUpdate();
        entityManager.createQuery("DELETE FROM ShiftScheduling").executeUpdate();
        entityManager.createQuery("DELETE FROM EditionScale").executeUpdate();
        entityManager.createQuery("DELETE FROM Shift").executeUpdate();
        entityManager.createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
