package tests.integration;

import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestão de utilizadores ponta a ponta, com a aplicação inteira carregada: a
 * autorização é conferida sobre o token JWT real e as alterações são lidas de
 * volta do PostgreSQL.
 *
 * <p>Estes testes fixam também a ligação do controller ao principal autenticado.
 * A desativação depende de saber <em>quem</em> está a pedir — é isso que impede
 * alguém de se desativar — e esse dado vem do contexto de segurança. Se essa
 * ligação se partir, a regra passa a proteger a conta errada sem dar erro.
 */
@DisplayName("Gestão de utilizadores — API")
class UserManagementApiIntegrationTest extends AbstractApiIntegrationTest {

    private User supervisor;
    private String tokenSupervisor;

    @BeforeEach
    void preparar() {
        supervisor = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        tokenSupervisor = tokenService.gerarToken(supervisor);
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private User recarregar(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private void desativar(Long id, String token) throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + id + "/estado")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"));
    }

    @Nested
    @DisplayName("Criar com equipa")
    class Criacao {

        @Test
        @DisplayName("a equipa enviada na criação fica gravada, o que torna a pessoa escalável")
        void gravaEquipa() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Maria Costa","email":"maria@teste.com",\
                                    "password":"segredo123","profile":"ANALIST","teamGroup":"EQUIPE_B"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.teamGroup").value("EQUIPE_B"))
                    .andExpect(jsonPath("$.teamGroupLabel").value("Equipa B"))
                    .andExpect(jsonPath("$.active").value(true));

            User gravado = userRepository.findByEmail("maria@teste.com");
            assertThat(gravado).isNotNull();
            assertThat(gravado.getTeamGroup()).isEqualTo(TeamGroup.EQUIPE_B);
            assertThat(gravado.isActive()).isTrue();
        }

        @Test
        @DisplayName("criar sem equipa é aceite: as contas anteriores ao conceito não têm")
        void semEquipa() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Maria Costa","email":"maria@teste.com",\
                                    "password":"segredo123","profile":"ANALIST"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.teamGroupLabel").value("Sem equipa"));

            assertThat(userRepository.findByEmail("maria@teste.com").getTeamGroup()).isNull();
        }
    }

    @Nested
    @DisplayName("Alterar o cadastro")
    class Alteracao {

        @Test
        @DisplayName("nome, perfil e equipa mudam na base de dados")
        void alteraOsCampos() throws Exception {
            User ana = criarUsuarioComEquipa(
                    "Ana", "ana@teste.com", UserProfile.ANALIST, TeamGroup.EQUIPE_A);

            mockMvc.perform(put("/api/v1/users/" + ana.getId())
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Ana Ribeiro","profile":"SUPERVISOR","teamGroup":"EQUIPE_B"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Ana Ribeiro"))
                    .andExpect(jsonPath("$.profile").value("SUPERVISOR"));

            User gravada = recarregar(ana);
            assertThat(gravada.getName()).isEqualTo("Ana Ribeiro");
            assertThat(gravada.getProfile()).isEqualTo(UserProfile.SUPERVISOR);
            assertThat(gravada.getTeamGroup()).isEqualTo(TeamGroup.EQUIPE_B);
        }

        @Test
        @DisplayName("enviar teamGroup nulo retira alguém da equipa")
        void retiraDaEquipa() throws Exception {
            User ana = criarUsuarioComEquipa(
                    "Ana", "ana@teste.com", UserProfile.ANALIST, TeamGroup.EQUIPE_A);

            mockMvc.perform(put("/api/v1/users/" + ana.getId())
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Ana","profile":"ANALIST","teamGroup":null}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.teamGroupLabel").value("Sem equipa"));

            assertThat(recarregar(ana).getTeamGroup()).isNull();
        }

        @Test
        @DisplayName("o e-mail não muda, mesmo que o pedido traga outro: a identidade fica intacta")
        void emailNaoMuda() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);

            mockMvc.perform(put("/api/v1/users/" + ana.getId())
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Ana Ribeiro","profile":"ANALIST","email":"outro@teste.com"}"""))
                    .andExpect(status().isOk());

            assertThat(recarregar(ana).getEmail()).isEqualTo("ana@teste.com");
        }

        @Test
        @DisplayName("um utilizador inexistente dá 400 com mensagem, não 500")
        void inexistenteDa400() throws Exception {
            mockMvc.perform(put("/api/v1/users/999999")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Fantasma","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Utilizador não encontrado."));
        }
    }

    @Nested
    @DisplayName("Ativar e desativar")
    class EstadoDaConta {

        @Test
        @DisplayName("desativar tira logo o acesso à aplicação")
        void desativaEBloqueiaOAcesso() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);

            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/estado")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            assertThat(recarregar(ana).isActive()).isFalse();

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", auth(tokenService.gerarToken(recarregar(ana)))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("reativar devolve o acesso a quem tinha sido desativado")
        void reativa() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
            desativar(ana.getId(), tokenSupervisor);
            sincronizar();

            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/estado")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(true));

            assertThat(recarregar(ana).isActive()).isTrue();
        }

        @Test
        @DisplayName("a conta desativada sai do selector de pessoas")
        void desativadoSaiDoSelector() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
            desativar(ana.getId(), tokenSupervisor);
            sincronizar();

            var resultado = mockMvc
                    .perform(get("/api/v1/users").header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(resultado.getResponse().getContentAsString())
                    .doesNotContain("ana@teste.com")
                    .contains("admin@teste.com");
        }

        @Test
        @DisplayName("a conta desativada continua visível na vista de administração, para poder ser reativada")
        void desativadoContinuaVisivel() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
            desativar(ana.getId(), tokenSupervisor);
            sincronizar();

            mockMvc.perform(get("/api/v1/users/todos").header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    // Ordenado por nome: "Administrador" antes de "Ana".
                    .andExpect(jsonPath("$[0].active").value(true))
                    .andExpect(jsonPath("$[1].id").value(ana.getId().intValue()))
                    .andExpect(jsonPath("$[1].active").value(false));
        }

        @Test
        @DisplayName("o supervisor não se desativa a si próprio")
        void naoSeDesativa() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + supervisor.getId() + "/estado")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Não pode desativar a sua própria conta."));

            assertThat(recarregar(supervisor).isActive()).isTrue();
        }

        @Test
        @DisplayName("com dois supervisores ativos, desativar um deles é uma decisão normal e passa")
        void desativarUmDeDois() throws Exception {
            User alvo = criarUsuario("Zelia", "zelia@teste.com", UserProfile.SUPERVISOR);

            mockMvc.perform(patch("/api/v1/users/" + alvo.getId() + "/estado")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            assertThat(recarregar(alvo).isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Redefinir a senha")
    class Senha {

        @Test
        @DisplayName("a senha nova é a que deixa a pessoa entrar, e a antiga deixa de servir")
        void redefinirPermiteEntrar() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/users/" + ana.getId() + "/senha")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"novasenha123\"}"))
                    .andExpect(status().isNoContent());
            sincronizar();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana@teste.com","password":"novasenha123"}"""))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana@teste.com","password":"%s"}"""
                                    .formatted(TestFixtures.RAW_PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("uma senha curta é recusada antes de chegar ao serviço")
        void senhaCurtaDa400() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/users/" + ana.getId() + "/senha")
                            .header("Authorization", auth(tokenSupervisor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").exists());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"ana@teste.com","password":"%s"}"""
                                    .formatted(TestFixtures.RAW_PASSWORD)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Reservado ao SUPERVISOR")
    class Autorizacao {

        @Test
        @DisplayName("o ANALIST recebe 403 em todas as operações de gestão, e nada muda")
        void analistNaoGerencia() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
            String tokenAna = tokenService.gerarToken(ana);

            mockMvc.perform(put("/api/v1/users/" + ana.getId())
                            .header("Authorization", auth(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Outra","profile":"SUPERVISOR"}"""))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/estado")
                            .header("Authorization", auth(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/users/" + ana.getId() + "/senha")
                            .header("Authorization", auth(tokenAna))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"novasenha123\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/users/todos").header("Authorization", auth(tokenAna)))
                    .andExpect(status().isForbidden());

            User inalterada = recarregar(ana);
            assertThat(inalterada.getName()).isEqualTo("Ana");
            assertThat(inalterada.isActive()).isTrue();
            assertThat(passwordEncoder.matches(
                    TestFixtures.RAW_PASSWORD, inalterada.getPassword())).isTrue();
        }

        @Test
        @DisplayName("sem token a resposta é 401 e não 403: não há principal para avaliar")
        void semTokenDa401() throws Exception {
            User ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);

            mockMvc.perform(get("/api/v1/users/todos"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isUnauthorized());

            assertThat(recarregar(ana).isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Leitura do próprio perfil")
    class ProprioPerfil {

        @Test
        @DisplayName("/me continua a responder depois de o controller passar a ler o contexto de segurança")
        void meContinuaAResponder() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(supervisor.getId().intValue()))
                    .andExpect(jsonPath("$.email").value("admin@teste.com"))
                    .andExpect(jsonPath("$.profile").value("SUPERVISOR"));
        }
    }
}
