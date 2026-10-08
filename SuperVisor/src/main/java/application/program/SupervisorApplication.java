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



}