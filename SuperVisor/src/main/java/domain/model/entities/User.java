package domain.model.entities;


import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
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
     * Conta ativa. Só os usuários ativos podem entrar na aplicação e
     * receber alocações, pelo que o seletor de pessoas do dashboard os lista a
     * partir daqui. O valor predefinido é {@code true} para que os usuários
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
     * Equipe a que o colaborador pertence, ou {@code null} se ainda não foi
     * atribuído a nenhuma.
     *
     * <p>Anulável de propósito: as contas criadas antes de existir o conceito
     * de equipe não têm equipe, e inventar uma seria inventar um dado. Um
     * colaborador sem equipe não entra na escala de presencialidade gerada, e a
     * lista de pessoas mostra-o como "Sem equipe" para que a situação seja
     * visível em vez de silenciosa.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "team_group", length = 20)
    private TeamGroup teamGroup;

    /**
     * Dívida de compensação pendente, em dias.
     *
     * <p>Cada falta registada na presencialidade soma aqui (um dia completo ou
     * meio dia parcial) e o supervisor abate quando o colaborador compensa.
     * O valor nunca fica negativo: uma dívida a menos não é um saldo a favor,
     * é um registo que ficou a mais, e o serviço de compensação corta-o em
     * zero.
     *
     * <p>{@link BigDecimal} e não {@code double}: os valores andam em passos de
     * 0.5 e são somados muitas vezes, e a soma de flutuantes acabaria por
     * mostrar 0.49999999999999994 dias de dívida numa grelha que só tem de
     * dizer meio dia.
     */
    @Column(name = "pending_compensation_days", nullable = false, precision = 10, scale = 1)
    private BigDecimal pendingCompensationDays = BigDecimal.ZERO;

    /**
     * Saldo de folgas acumuladas disponível, em dias.
     *
     * <p>Registar uma folga deduz do saldo (um dia inteiro ou meio dia para as
     * folgas parciais) e apagar uma folga repõe o valor. É um saldo e não uma
     * contagem de folgas pedidas: quem lê a página de folgas quer saber quanto
     * ainda pode marcar.
     */
    @Column(name = "accumulated_leaves", nullable = false, precision = 10, scale = 1)
    private BigDecimal accumulatedLeaves = BigDecimal.ZERO;

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

    public BigDecimal getPendingCompensationDays() {
        return pendingCompensationDays;
    }

    public void setPendingCompensationDays(BigDecimal pendingCompensationDays) {
        this.pendingCompensationDays = pendingCompensationDays;
    }

    public BigDecimal getAccumulatedLeaves() {
        return accumulatedLeaves;
    }

    public void setAccumulatedLeaves(BigDecimal accumulatedLeaves) {
        this.accumulatedLeaves = accumulatedLeaves;
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


