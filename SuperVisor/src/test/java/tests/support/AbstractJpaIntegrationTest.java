package tests.support;

import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
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
    protected ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    protected ExchangeRequestRepository exchangeRequestRepository;

    @Autowired
    protected domain.repository.WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    protected domain.repository.UserLeaveRepository userLeaveRepository;

    @BeforeEach
    void limparBanco() {
        entityManager.createQuery("DELETE FROM ExchangeRequest").executeUpdate();
        // A ordem segue as chaves estrangeiras: as escalas e as folgas apontam
        // para `tb_user` e têm de desaparecer antes dela.
        entityManager.createQuery("DELETE FROM WorkModalitySchedule").executeUpdate();
        entityManager.createQuery("DELETE FROM UserLeave").executeUpdate();
        // A tabela das atribuições especiais é uma @ElementCollection e não tem
        // entidade: tem de ser limpa por SQL nativo e antes da tabela-mãe, para
        // não chocar com a chave estrangeira.
        entityManager.createNativeQuery("DELETE FROM tb_shift_scheduling_assignment").executeUpdate();
        entityManager.createQuery("DELETE FROM ShiftScheduling").executeUpdate();
        entityManager.createQuery("DELETE FROM EditionScale").executeUpdate();
        entityManager.createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
