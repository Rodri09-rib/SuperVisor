package application.program;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"application", "domain", "security", "service", "controller", "exception"})
@EnableJpaRepositories(basePackages = {"domain.repository"})
@EntityScan(basePackages = {"domain.model.entities"})
public class SupervisorApplication {

    public static void main(String[] args){
        SpringApplication.run(SupervisorApplication.class, args);
    }



}