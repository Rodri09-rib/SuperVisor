package domain.model.entities;


import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "tb_user")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Column(unique = true)
    private String email;
    private String password;

    @Enumerated(EnumType.STRING)
    private UserProfile profile;

    /**
     * Conta ativa. Só os utilizadores ativos podem entrar na aplicação e
     * receber alocações, pelo que o selector de pessoas do dashboard os lista a
     * partir daqui. O valor predefinido é {@code true} para que os utilizadores
     * existentes continuem a poder ser escalados sem necessidade de backfill.
     *
     * <p>O nome da coluna é explícito porque o DDL de arranque a cria com
     * {@code DEFAULT true}: sem esse default, o {@code ALTER TABLE} seria
     * rejeitado pelo PostgreSQL numa tabela que já tem linhas, e a aplicação
     * arrancaria sem a coluna.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Equipa a que o colaborador pertence, ou {@code null} se ainda não foi
     * atribuído a nenhuma.
     *
     * <p>Anulável de propósito: as contas criadas antes de existir o conceito
     * de equipa não têm equipa, e inventar uma seria inventar um dado. Um
     * colaborador sem equipa não entra na escala de presencialidade gerada, e a
     * lista de pessoas mostra-o como "Sem equipa" para que a situação seja
     * visível em vez de silenciosa.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "team_group", length = 20)
    private TeamGroup teamGroup;

    public User (){

    }

    public User(Long id, String name, String email, String password, UserProfile profile) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.profile = profile;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public void setProfile(UserProfile profile) {
        this.profile = profile;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public TeamGroup getTeamGroup() {
        return teamGroup;
    }

    public void setTeamGroup(TeamGroup teamGroup) {
        this.teamGroup = teamGroup;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        if(this.profile == null) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.profile.name()));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return this.active;
    }
}


