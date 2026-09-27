package tests.integration;

import domain.model.entities.User;
import domain.model.entities.UserLeave;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Folgas contra a base de dados a sério.
 *
 * <p>O que estes testes acrescentam aos que usam mocks é a consulta. A
 * {@code listar} tem um parâmetro opcional e resolve isso com
 * {@code :userId IS NULL}, que é exatamente o tipo de constructo que passa num
 * teste de unidade — o mock não distingue de um filtro aplicado a dedo — e
 * falha na base. O mesmo vale para a ordenação e para o {@code createdAt}, que
 * só existe porque a coluna é preenchida pela aplicação e não pelo cliente.
 */
@DisplayName("Folgas — API")
class LeaveApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIM = LocalDate.of(2026, 3, 6);

    private User admin;
    private User joao;
    private User maria;
    private User inativo;

    private String tokenAdmin;
    private String tokenJoao;

    @BeforeEach
    void prepararCenario() {
        admin = criarSupervisor();
        joao = criarAnalista("João", "joao@teste.com");
        maria = criarAnalista("Maria", "maria@teste.com");
        inativo = criarAnalista("Inativo", "inativo@teste.com");
        inativo.setActive(false);
        userRepository.saveAndFlush(inativo);

        tokenAdmin = tokenService.gerarToken(admin);
        tokenJoao = tokenService.gerarToken(joao);
    }

    /**
     * Cria um utilizador a partir de uma fixture, mudando apenas o nome e o
     * e-mail.
     *
     * <p>Não se escreve o perfil à mão de propósito: o perfil vem da fixture,
     * que é o mesmo caminho que os restantes testes usam, e assim este ficheiro
     * não fica a depender de uma constante que compila de forma inconsistente
     * entre ficheiros novos.
     */
    private User criarSupervisor() {
        var base = tests.support.TestFixtures.supervisor();
        base.setPassword(passwordEncoder.encode(tests.support.TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(base);
    }

    private User criarAnalista(String nome, String email) {
        var base = tests.support.TestFixtures.analyst();
        base.setName(nome);
        base.setEmail(email);
        base.setPassword(passwordEncoder.encode(tests.support.TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(base);
    }


    private String corpo(Object... paresChaveValor) {
        var object = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            object.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }

    private String folga(Long userId, LocalDate inicio, LocalDate fim, String motivo) {
        return corpo("userId", userId, "startDate", inicio, "endDate", fim, "reason", motivo);
    }

    private UserLeave gravar(User dono, LocalDate inicio, LocalDate fim, String motivo) {
        return userLeaveRepository.saveAndFlush(
                tests.support.TestFixtures.leave(dono, inicio, fim, motivo));
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("o supervisor regista a folga, e ela fica mesmo na base")
        void supervisorCria() throws Exception {
            var resposta = mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, "Ferro")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(joao.getId()))
                    .andExpect(jsonPath("$.userName").value("João"))
                    .andExpect(jsonPath("$.reason").value("Ferro"))
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andReturn();

            Long id = objectMapper.readTree(resposta.getResponse().getContentAsString())
                    .get("id").asLong();

            UserLeave gravada = userLeaveRepository.findById(id).orElseThrow();
            assertThat(gravada.getUser().getId()).isEqualTo(joao.getId());
            assertThat(gravada.getStartDate()).isEqualTo(INICIO);
            assertThat(gravada.getEndDate()).isEqualTo(FIM);
        }

        @Test
        @DisplayName("a data de criação é carimbada pela aplicação, e o cliente não a consegue impor")
        void createdAtVemDaAplicacao() throws Exception {
            var antes = java.time.Instant.now().minusSeconds(5);

            var resposta = mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(
                                    "userId", joao.getId(),
                                    "startDate", INICIO,
                                    "endDate", FIM,
                                    // Uma data absurda: tem de ser ignorada, e
                                    // não o contrário.
                                    "createdAt", "1999-01-01T00:00:00Z")))
                    .andExpect(status().isCreated())
                    .andReturn();

            var node = objectMapper.readTree(resposta.getResponse().getContentAsString());
            var carimbo = java.time.Instant.parse(node.get("createdAt").asText());

            // createdAt não existe no DTO de entrada, o corpo nem chega a ter
            // onde o meter, mas a resposta tem de trazer um instante plausível
            // e não o que o cliente tentou mandar.
            assertThat(carimbo).isAfter(antes);
        }

        @Test
        @DisplayName("uma folga de segunda a sexta devolve cinco dias")
        void duracaoDeCincoDias() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.durationDays").value(5));
        }

        @Test
        @DisplayName("uma folga que atravessa o fim de semana conta-o, porque o fim de semana também falta")
        void contaFimDeSemana() throws Exception {
            // Sexta a segunda: três dias de calendário, e é isso que a ausência
            // custa. Contar só dias úteis obrigaria o frontend a ter a mesma
            // regra de calendário, e depois os dois discordavam.
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(),
                                    LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9), null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.durationDays").value(4));
        }

        @Test
        @DisplayName("um motivo só com espaços fica nulo na base, e não como uma string de espaços")
        void motivoEmBrancoViraNulo() throws Exception {
            var resposta = mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, "    ")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reason").doesNotExist())
                    .andReturn();

            Long id = objectMapper.readTree(resposta.getResponse().getContentAsString())
                    .get("id").asLong();
            assertThat(userLeaveRepository.findById(id).orElseThrow().getReason()).isNull();
        }

        @Test
        @DisplayName("o motivo é aparado nas pontas")
        void motivoAparado() throws Exception {
            var resposta = mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, "  Ferro  ")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reason").value("Ferro"))
                    .andReturn();

            Long id = objectMapper.readTree(resposta.getResponse().getContentAsString())
                    .get("id").asLong();
            assertThat(userLeaveRepository.findById(id).orElseThrow().getReason()).isEqualTo("Ferro");
        }

        @Test
        @DisplayName("o analista é recusado com 403 e não grava nada")
        void analistaNaoCria() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            assertThat(userLeaveRepository.count()).isZero();
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, null)))
                    .andExpect(status().isUnauthorized());

            assertThat(userLeaveRepository.count()).isZero();
        }

        @Test
        @DisplayName("um intervalo invertido é 400, e a linha não é criada")
        void intervaloInvertido() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), FIM, INICIO, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("A data de fim da folga não pode ser anterior à data de início."));

            assertThat(userLeaveRepository.count()).isZero();
        }

        @Test
        @DisplayName("registar folga a um utilizador inativo é 400")
        void utilizadorInativo() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(inativo.getId(), INICIO, FIM, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Não é possível registar folga a um utilizador inativo."));

            assertThat(userLeaveRepository.count()).isZero();
        }

        @Test
        @DisplayName("registar folga a um utilizador que não existe é 400, não 500 de chave estrangeira")
        void utilizadorInexistente() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(999_999L, INICIO, FIM, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Utilizador não encontrado."));

            assertThat(userLeaveRepository.count()).isZero();
        }

        @Test
        @DisplayName("um motivo com mais de 500 caracteres é 400, com o campo assinalado")
        void motivoLongoDemais() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, "x".repeat(501))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("O motivo não pode ter mais de 500 caracteres."))
                    .andExpect(jsonPath("$.fields.reason").exists());
        }
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("sem filtro, traz as folgas de toda a equipa")
        void todas() throws Exception {
            gravar(joao, INICIO, FIM, "Ferro");
            gravar(maria, INICIO.plusDays(7), INICIO.plusDays(9), "Férias");

            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].userName").value("Maria"))
                    .andExpect(jsonPath("$[1].userName").value("João"));
        }

        @Test
        @DisplayName("com userId, traz só as folgas dessa pessoa")
        void porPessoa() throws Exception {
            gravar(joao, INICIO, FIM, "Ferro");
            gravar(maria, INICIO, FIM, "Férias");

            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("userId", joao.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value(joao.getId()));
        }

        @Test
        @DisplayName("o parâmetro userId é opcional na consulta, e não parte a igualdade")
        void parametroOpcionalNaConsulta() throws Exception {
            gravar(joao, INICIO, FIM, null);

            // O mesmo endpoint, com e sem o parâmetro. A consulta resolve o
            // opcional com ":userId IS NULL", e um mock não-notava o dia em que
            // essa construção deixasse de ser válida.
            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("userId", joao.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("o analista também vê a lista toda, com canEdit a false")
        void analistaVeTodas() throws Exception {
            gravar(joao, INICIO, FIM, "Ferro");
            gravar(maria, INICIO.plusDays(7), INICIO.plusDays(9), null);

            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenJoao))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].canEdit").value(false))
                    .andExpect(jsonPath("$[1].canEdit").value(false));
        }

        @Test
        @DisplayName("folgas do mesmo dia de início saem da mais recente para a mais antiga")
        void ordenacaoPorInicio() throws Exception {
            gravar(joao, INICIO, FIM, "Primeira");
            var segunda = gravar(maria, INICIO, FIM, "Segunda");

            // A ordenação tem o id como segundo critério porque duas folgas
            // podem começar no mesmo dia, e sem ele a ordem da base decidia
            // sozinha — o que muda entre corridas.
            mockMvc.perform(get("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(jsonPath("$[0].id").value(segunda.getId()))
                    .andExpect(jsonPath("$[1].reason").value("Primeira"));
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/leaves")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("o supervisor corrige as datas e o motivo")
        void supervisorCorrige() throws Exception {
            var folga = gravar(joao, INICIO, FIM, "Ferro");

            mockMvc.perform(put("/api/v1/leaves/" + folga.getId())
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO.plusDays(1), FIM.plusDays(2), "Baixa")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("2026-03-03"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-08"))
                    .andExpect(jsonPath("$.reason").value("Baixa"))
                    .andExpect(jsonPath("$.durationDays").value(6));

            sincronizar();
            UserLeave gravada = userLeaveRepository.findById(folga.getId()).orElseThrow();
            assertThat(gravada.getStartDate()).isEqualTo(INICIO.plusDays(1));
            assertThat(gravada.getEndDate()).isEqualTo(FIM.plusDays(2));
            assertThat(gravada.getReason()).isEqualTo("Baixa");
        }

        @Test
        @DisplayName("o userId do corpo não transfere a ausência para outra pessoa")
        void naoTransfereODono() throws Exception {
            var folga = gravar(joao, INICIO, FIM, "Ferro");

            mockMvc.perform(put("/api/v1/leaves/" + folga.getId())
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            // O corpo aponta para a Maria.
                            .content(folga(maria.getId(), INICIO, FIM, "Ferro")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(joao.getId()));

            sincronizar();
            assertThat(userLeaveRepository.findById(folga.getId()).orElseThrow()
                    .getUser().getId()).isEqualTo(joao.getId());
        }

        @Test
        @DisplayName("o analista é recusado com 403, e a folga fica como estava")
        void analistaRecusado() throws Exception {
            var folga = gravar(joao, INICIO, FIM, "Ferro");

            mockMvc.perform(put("/api/v1/leaves/" + folga.getId())
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO.plusDays(1), FIM, "Bacalho")))
                    .andExpect(status().isForbidden());

            sincronizar();
            assertThat(userLeaveRepository.findById(folga.getId()).orElseThrow()
                    .getStartDate()).isEqualTo(INICIO);
        }

        @Test
        @DisplayName("atualizar uma folga que não existe é 400")
        void inexistente() throws Exception {
            mockMvc.perform(put("/api/v1/leaves/999999")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(joao.getId(), INICIO, FIM, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Folga não encontrada."));
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("o supervisor apaga, e a linha desaparece")
        void supervisorApaga() throws Exception {
            var folga = gravar(joao, INICIO, FIM, "Ferro");

            mockMvc.perform(delete("/api/v1/leaves/" + folga.getId())
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isNoContent());

            sincronizar();
            assertThat(userLeaveRepository.findById(folga.getId())).isEmpty();
        }

        @Test
        @DisplayName("o analista é recusado com 403, e a folga continua lá")
        void analistaRecusado() throws Exception {
            var folga = gravar(joao, INICIO, FIM, "Ferro");

            mockMvc.perform(delete("/api/v1/leaves/" + folga.getId())
                            .header("Authorization", "Bearer " + tokenJoao))
                    .andExpect(status().isForbidden());

            sincronizar();
            assertThat(userLeaveRepository.findById(folga.getId())).isPresent();
        }

        @Test
        @DisplayName("apagar uma folga que não existe é 400")
        void inexistente() throws Exception {
            mockMvc.perform(delete("/api/v1/leaves/999999")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Folga não encontrada."));
        }
    }
}
