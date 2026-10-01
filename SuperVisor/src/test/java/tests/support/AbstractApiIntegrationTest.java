package tests.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ShiftType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base dos testes de integração end-to-end: carrega a aplicação inteira
 * (controllers, services, filtro de segurança, repositórios) contra o
 * PostgreSQL do Testcontainers e expõe {@link MockMvc} para exercitar a API
 * exatamente como o faria um cliente HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = TestApplicationConfiguration.class)
@Transactional
public abstract class AbstractApiIntegrationTest {

    @DynamicPropertySource
    static void registrarPropriedades(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerProperties(registry);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected security.TokenService tokenService;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected domain.repository.UserRepository userRepository;

    @Autowired
    protected domain.repository.EditionScaleRepository editionScaleRepository;

    @Autowired
    protected domain.repository.ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    protected domain.repository.ExchangeRequestRepository exchangeRequestRepository;

    @Autowired
    protected domain.repository.WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    protected domain.repository.UserLeaveRepository userLeaveRepository;

    @BeforeEach
    void limparBanco() {
        entityManager.createQuery("DELETE FROM ExchangeRequest").executeUpdate();
        // A ordem segue as chaves estrangeiras: escalas e folgas apontam para
        // `tb_user` e têm de ser limpas antes dela.
        entityManager.createQuery("DELETE FROM WorkModalitySchedule").executeUpdate();
        entityManager.createQuery("DELETE FROM UserLeave").executeUpdate();
        // A tabela das atribuições especiais é uma @ElementCollection e não tem
        // entidade: tem de ser limpa por SQL nativo e antes da tabela-mãe.
        entityManager.createNativeQuery("DELETE FROM tb_shift_scheduling_assignment").executeUpdate();
        entityManager.createQuery("DELETE FROM ShiftScheduling").executeUpdate();
        entityManager.createQuery("DELETE FROM EditionScale").executeUpdate();
        entityManager.createQuery("DELETE FROM User").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    protected User criarUsuario(String nome, String email, domain.model.enums.UserProfile perfil) {
        return userRepository.saveAndFlush(
                TestFixtures.userWithEncodedPassword(passwordEncoder, nome, email, perfil));
    }

    /** Usuário ativo com equipe, que é o que a escala de presencialidade exige. */
    protected User criarUsuarioComEquipa(String nome, String email,
                                         domain.model.enums.UserProfile perfil,
                                         domain.model.enums.TeamGroup equipa) {
        return userRepository.saveAndFlush(TestFixtures.userInTeam(
                nome, email, perfil, equipa));
    }

    protected EditionScale criarEscala(String nome, User criadoPor) {
        return criarEscala(nome, criadoPor, domain.model.enums.EditionStatus.DRAFT);
    }

    protected EditionScale criarEscala(String nome, User criadoPor,
                                       domain.model.enums.EditionStatus estado) {
        return editionScaleRepository.saveAndFlush(
                TestFixtures.editionScale(nome, criadoPor, estado));
    }

    protected ShiftScheduling criarAlocacao(EditionScale escala, User usuario) {
        return criarAlocacao(escala, usuario, ShiftType.T1_SAB);
    }

    protected ShiftScheduling criarAlocacao(EditionScale escala, User usuario, ShiftType turno) {
        return shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, usuario, turno, null));
    }

    protected ExchangeRequest recarregarSolicitacao(Long id) {
        return exchangeRequestRepository.findById(id).orElseThrow();
    }

    protected ShiftScheduling recarregarAlocacao(Long id) {
        return shiftSchedulingRepository.findById(id).orElseThrow();
    }

    /**
     * Os testes correm dentro de uma transação que é revertida no fim. Como
     * {@code save()} de uma entidade já gerenciada não gera SQL, é preciso forçar
     * o flush antes de limpar o contexto de persistência; caso contrário
     * alterações por flush-pendente seriam descartadas em vez de observadas.
     */
    protected void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Executa o pedido e devolve a exceção que lançou, se tiver lançado uma.
     *
     * <p>Útil nos casos em que a exceção escapa do {@code MockMvc} em vez de
     * virar resposta — o que acontece quando a exceção não é de um tipo que o
     * {@code GlobalExceptionHandler} trata, e em que o teste quer ver a
     * exceção em si e não um código de estado.
     */
    protected Throwable erroDaRequisicao(ThrowingSupplier requisicao) {
        try {
            requisicao.get();
            return null;
        } catch (Throwable erro) {
            return erro;
        }
    }

    @FunctionalInterface
    protected interface ThrowingSupplier {
        void get() throws Exception;
    }
}
