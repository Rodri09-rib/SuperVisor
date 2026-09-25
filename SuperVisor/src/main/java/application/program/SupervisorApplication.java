package application.program;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@ComponentScan(basePackages = {"application", "domain", "security", "service", "controller"})
@EnableJpaRepositories(basePackages = {"domain.repository"})
@EntityScan(basePackages = {"domain.model.entities"})
public class SupervisorApplication {

    public static void main(String[] args){
        SpringApplication.run(SupervisorApplication.class, args);
    }

    @Bean
    public CommandLineRunner carregarDados(
            UserRepository userRepository,
            EditionScaleRepository editionScaleRepository,
            ShiftSchedulingRepository shiftSchedulingRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {

            User admin = (User) userRepository.findByEmail("admin@teste.com");
            if (admin == null) {
                admin = new User();
                admin.setName("Administrador");
                admin.setEmail("admin@teste.com");
                admin.setPassword(passwordEncoder.encode("123456"));
                admin.setProfile(domain.model.enums.UserProfile.SUPERVISOR);
                admin = userRepository.save(admin);
                System.out.println("Utilizador Administrador criado!");
            }


            User joao = (User) userRepository.findByEmail("joao@teste.com");
            if (joao == null) {
                joao = new User();
                joao.setName("João");
                joao.setEmail("joao@teste.com");
                joao.setPassword(passwordEncoder.encode("123456"));
                joao.setProfile(domain.model.enums.UserProfile.ANALIST);
                joao = userRepository.save(joao);
                System.out.println("Utilizador João criado com sucesso!");
            }


            if (editionScaleRepository.count() == 0) {
                EditionScale escala = new EditionScale();
                escala.setName("Escala Outubro");
                escala.setInitialDate(java.time.LocalDate.now());
                escala.setEndDate(java.time.LocalDate.now().plusDays(30));
                escala.setCreatedBy(admin);
                escala = editionScaleRepository.save(escala);


                ShiftScheduling alocacaoAdmin = new ShiftScheduling();
                alocacaoAdmin.setEditionScale(escala);
                alocacaoAdmin.setUser(admin);
                shiftSchedulingRepository.save(alocacaoAdmin);
                ShiftScheduling alocacaoJoao = new ShiftScheduling();
                alocacaoJoao.setEditionScale(escala);
                alocacaoJoao.setUser(joao);
                shiftSchedulingRepository.save(alocacaoJoao);

                System.out.println("Alocações de teste (IDs 1 e 2) criadas com sucesso!");
            }
        };
    }
}