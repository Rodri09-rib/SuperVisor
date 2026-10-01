package application.program;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ShiftType;
import domain.model.enums.TeamGroup;
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
@ComponentScan(basePackages = {"application", "domain", "security", "service", "controller", "exception"})
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
                System.out.println("Usuário Administrador criado!");
            }


            User joao = (User) userRepository.findByEmail("joao@teste.com");
            if (joao == null) {
                joao = new User();
                joao.setName("João");
                joao.setEmail("joao@teste.com");
                joao.setPassword(passwordEncoder.encode("123456"));
                joao.setProfile(domain.model.enums.UserProfile.ANALIST);
                joao = userRepository.save(joao);
                System.out.println("Usuário João criado com sucesso!");
            }

            // A escala de presencialidade só existe para quem tem equipe, e as
            // contas não nascem com uma equipe atribuída. As duas contas de teste
            // ficam em equipes opostas para que a geração tenha o seu caso
            // principal — as duas com a mesma escala na mesma semana, e o
            // resultado a ser diferente para cada uma.
            //
            // A atribuição é feita fora do `if (joao == null)`: a conta já
            // existente é de arranques anteriores, quando a coluna `team_group`
            // ainda não existia, e deixá-la sem equipe faria a escala não ter
            // ninguém em quem demonstrar a alternância.
            if (admin.getTeamGroup() == null) {
                admin.setTeamGroup(TeamGroup.EQUIPE_A);
                userRepository.save(admin);
            }

            if (joao.getTeamGroup() == null) {
                joao.setTeamGroup(TeamGroup.EQUIPE_B);
                userRepository.save(joao);
            }


            if (editionScaleRepository.count() == 0) {
                EditionScale escala = new EditionScale();
                escala.setName("Escala Outubro");
                escala.setInitialDate(java.time.LocalDate.now());
                escala.setEndDate(java.time.LocalDate.now().plusDays(30));
                escala.setCreatedBy(admin);
                escala = editionScaleRepository.save(escala);

                // Tuas alocações são o par mínimo que o pedido de troca
                // necesita: um turno de cada usuário dentro da mesma escala.
                // O turno é obrigatório, pelo que a enum indica-o.
                shiftSchedulingRepository.save(alocacao(escala, admin, ShiftType.T1_SAB));
                shiftSchedulingRepository.save(alocacao(escala, joao, ShiftType.T2_SAB));

                System.out.println("Alocações de teste criadas com sucesso!");
            }
        };
    }

    private static ShiftScheduling alocacao(EditionScale escala, User utilizador, ShiftType turno) {
        ShiftScheduling alocacao = new ShiftScheduling();
        alocacao.setEditionScale(escala);
        alocacao.setUser(utilizador);
        alocacao.setShift(turno);
        return alocacao;
    }
}