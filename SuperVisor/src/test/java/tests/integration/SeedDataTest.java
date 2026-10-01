package tests.integration;

import application.program.SupervisorApplication;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import tests.support.PostgresTestContainer;
import tests.support.TestApplicationConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercita o {@link CommandLineRunner} de {@link SupervisorApplication}. A
 * aplicação real foi excluída da configuração de teste porque executa o seeder
 * em todo arranque; aqui o runner é instanciado à mão para se poder controlar
 * quantas vezes corre.
 */
@SpringJUnitConfig(classes = TestApplicationConfiguration.class)
@Transactional
@DisplayName("Carregamento inicial de dados — SupervisorApplication.carregarDados")
class SeedDataTest {

    @DynamicPropertySource
    static void registrarPropriedades(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerProperties(registry);
    }

    @Autowired
    private domain.repository.UserRepository userRepository;

    @Autowired
    private domain.repository.EditionScaleRepository editionScaleRepository;

    @Autowired
    private domain.repository.ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private CommandLineRunner runner() {
        return new SupervisorApplication().carregarDados(
                userRepository, editionScaleRepository, shiftSchedulingRepository, passwordEncoder);
    }

    @Test
    @DisplayName("cria o administrador e o analista com a senha 123456 codificada")
    void criaUtilizadoresPadrao() throws Exception {
        runner().run(new String[0]);

        User admin = (User) userRepository.findByEmail("admin@teste.com");
        User joao = (User) userRepository.findByEmail("joao@teste.com");

        assertThat(admin).isNotNull();
        assertThat(joao).isNotNull();
        assertThat(admin.getName()).isEqualTo("Administrador");
        assertThat(admin.getProfile()).isEqualTo(UserProfile.SUPERVISOR);
        assertThat(joao.getName()).isEqualTo("João");
        assertThat(joao.getProfile()).isEqualTo(UserProfile.ANALIST);
        assertThat(passwordEncoder.matches("123456", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("123456", joao.getPassword())).isTrue();
    }

    @Test
    @DisplayName("cria uma escala de 30 dias, contada a partir de hoje, com duas alocações")
    void criaEscalaEAlocacoes() throws Exception {
        runner().run(new String[0]);

        List<EditionScale> escalas = editionScaleRepository.findAll();
        assertThat(escalas).hasSize(1);
        EditionScale escala = escalas.get(0);
        assertThat(escala.getName()).isEqualTo("Escala Outubro");
        assertThat(escala.getInitialDate()).isEqualTo(java.time.LocalDate.now());
        assertThat(escala.getEndDate()).isEqualTo(java.time.LocalDate.now().plusDays(30));
        assertThat(escala.getCreatedBy().getEmail()).isEqualTo("admin@teste.com");

        List<ShiftScheduling> alocacoes = shiftSchedulingRepository.findAll();
        assertThat(alocacoes).hasSize(2)
                .extracting(a -> a.getUser().getEmail())
                .containsExactlyInAnyOrder("admin@teste.com", "joao@teste.com");
        assertThat(alocacoes).allSatisfy(a ->
                assertThat(a.getEditionScale().getId()).isEqualTo(escala.getId()));
    }

    @Test
    @DisplayName("a escala criada fica sem estado, porque EditionStatus não tem valor por omissão explícito")
    void escalaCriadaSemEstado() throws Exception {
        runner().run(new String[0]);

        EditionScale escala = editionScaleRepository.findAll().get(0);
        assertThat(escala.getStatus()).isNull();
    }

    @Test
    @DisplayName("numa segunda execução não duplica usuários nem escala")
    void execucaoRepetidaNaoDuplica() throws Exception {
        runner().run(new String[0]);
        runner().run(new String[0]);
        runner().run(new String[0]);

        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(editionScaleRepository.count()).isEqualTo(1);
        assertThat(shiftSchedulingRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("preserva o estado de um administrador já existente, em vez de o recriar")
    void naoSobrescreveAdministradorExistente() throws Exception {
        User existente = new User();
        existente.setName("Nome Original");
        existente.setEmail("admin@teste.com");
        existente.setPassword(passwordEncoder.encode("outra-senha"));
        existente.setProfile(UserProfile.ANALIST);
        userRepository.saveAndFlush(existente);

        runner().run(new String[0]);

        User admin = (User) userRepository.findByEmail("admin@teste.com");
        assertThat(admin.getName()).isEqualTo("Nome Original");
        assertThat(admin.getProfile()).isEqualTo(UserProfile.ANALIST);
        assertThat(passwordEncoder.matches("outra-senha", admin.getPassword())).isTrue();
    }

    @Test
    @DisplayName("se já existir qualquer escala, não cria a escala de exemplo")
    void naoCriaEscalaSeJaExistirAlguma() throws Exception {
        User outroCriador = new User();
        outroCriador.setName("Outro");
        outroCriador.setEmail("outro@teste.com");
        outroCriador.setPassword(passwordEncoder.encode("123456"));
        outroCriador.setProfile(UserProfile.SUPERVISOR);
        userRepository.saveAndFlush(outroCriador);

        EditionScale existente = new EditionScale();
        existente.setName("Escala Já Existente");
        existente.setInitialDate(java.time.LocalDate.of(2025, 1, 1));
        existente.setEndDate(java.time.LocalDate.of(2025, 1, 31));
        existente.setStatus(EditionStatus.PUBLISHED);
        existente.setCreatedBy(outroCriador);
        editionScaleRepository.saveAndFlush(existente);

        runner().run(new String[0]);

        assertThat(editionScaleRepository.count()).isEqualTo(1);
        assertThat(editionScaleRepository.findAll().get(0).getName()).isEqualTo("Escala Já Existente");
        assertThat(shiftSchedulingRepository.count()).isZero();
    }

    @Test
    @DisplayName("a verificação de escala existente conta qualquer registro, mesmo de outro criador")
    void contaQualquerEscala() throws Exception {
        runner().run(new String[0]);

        assertThat(editionScaleRepository.count()).isEqualTo(1);
        assertThat(shiftSchedulingRepository.count()).isEqualTo(2);
    }
}
