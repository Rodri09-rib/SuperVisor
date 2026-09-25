package tests.support;

import application.program.SupervisorApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Réplica da configuração de {@link SupervisorApplication} usada pelos testes.
 *
 * <p>A diferença intencional é excluir {@code SupervisorApplication} do
 * {@code @ComponentScan}, o que impede o {@code CommandLineRunner} que semeia
 * dados ({@code admin@teste.com}, {@code joao@teste.com}, "Escala Outubro" e duas
 * alocações) de rodar em todos os contextos de teste. O seed é testado
 * isoladamente em {@code SupervisorApplicationSeedDataTest}.
 *
 * <p>Todos os testes referenciam esta classe explicitamente via
 * {@code @ContextConfiguration(classes = ...)}, já que o layout de pacotes do
 * projeto ({@code application.program}, {@code controller}, {@code service}...)
 * impede o Spring Boot de localizar a configuração por varredura do classpath.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {"application", "domain", "security", "service", "controller", "exception"}, // <-- PACOTE ADICIONADO AQUI
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SupervisorApplication.class))
@EnableJpaRepositories(basePackages = {"domain.repository"})
@EntityScan(basePackages = {"domain.model.entities"})
public class TestApplicationConfiguration {
}