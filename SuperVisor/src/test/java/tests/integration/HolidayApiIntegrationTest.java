package tests.integration;

import domain.model.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feriados contra a base de dados a sério.
 *
 * <p>O que estes testes acrescentam aos mocks é a unicidade da data: a regra
 * «um só feriado por dia» vive numa verificação de repositório que não devolve
 * nada de observável num teste de unidade, e a coluna também é única — ver
 * duplicado como 400 com frase e não como 500 de constraint é precisamente o
 * tipo de distinção que só se vê daqui.
 */
@DisplayName("Feriados — API")
class HolidayApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final LocalDate NATAL = LocalDate.of(2025, 12, 25);
    private static final LocalDate ANO_VELHO = LocalDate.of(2025, 12, 31);

    private User supervisor;
    private User analista;

    private String tokenSupervisor;
    private String tokenAnalista;

    @BeforeEach
    void prepararCenario() {
        supervisor = criarSupervisor();
        analista = criarAnalista("João", "joao@teste.com");

        tokenSupervisor = tokenService.gerarToken(supervisor);
        tokenAnalista = tokenService.gerarToken(analista);
    }

    private User criarSupervisor() {
        var base = TestFixtures.supervisor();
        base.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(base);
    }

    private User criarAnalista(String nome, String email) {
        var base = TestFixtures.analyst();
        base.setName(nome);
        base.setEmail(email);
        base.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
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

    private String feriado(LocalDate data, String descricao) {
        return corpo("date", data, "description", descricao);
    }

    private domain.model.entities.Holiday gravar(LocalDate data, String descricao) {
        return holidayRepository.saveAndFlush(
                new domain.model.entities.Holiday(data, descricao));
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("o supervisor regista o feriado, e ele fica mesmo na base")
        void supervisorRegista() throws Exception {
            var resposta = mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.date").value("2025-12-25"))
                    .andExpect(jsonPath("$.description").value("Natal"))
                    .andReturn();

            Long id = objectMapper.readTree(resposta.getResponse().getContentAsString())
                    .get("id").asLong();

            var gravado = holidayRepository.findById(id).orElseThrow();
            assertThat(gravado.getDate()).isEqualTo(NATAL);
            assertThat(gravado.getDescription()).isEqualTo("Natal");
        }

        @Test
        @DisplayName("o analista é recusado com 403 e não grava nada")
        void analistaNaoCria() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenAnalista)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            assertThat(holidayRepository.count()).isZero();
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal")))
                    .andExpect(status().isUnauthorized());

            assertThat(holidayRepository.count()).isZero();
        }

        @Test
        @DisplayName("duas entradas para a mesma data são 400 com frase, não 500 de constraint")
        void dataDuplicada() throws Exception {
            gravar(NATAL, "Natal");

            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Outro nome para o mesmo dia")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Já existe um feriado registado para esta data."));

            assertThat(holidayRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("sem data é 400, com o campo marcado")
        void dataEmFalta() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("description", "Sem data")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.date")
                            .value("A data do feriado é obrigatória."));

            assertThat(holidayRepository.count()).isZero();
        }

        @Test
        @DisplayName("sem descrição é 400, com o campo marcado")
        void descricaoEmFalta() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.description")
                            .value("A descrição do feriado é obrigatória."));

            assertThat(holidayRepository.count()).isZero();
        }

        @Test
        @DisplayName("uma descrição com mais de 120 caracteres é 400")
        void descricaoLongoDemais() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "x".repeat(121))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.description")
                            .value("A descrição não pode ter mais de 120 caracteres."));
        }

        @Test
        @DisplayName("a descrição é aparada nas pontas")
        void descricaoAparada() throws Exception {
            mockMvc.perform(post("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "  Natal  ")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").value("Natal"));
        }
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("traz os feriados do mais antigo para o mais recente")
        void ordenadoPorData() throws Exception {
            gravar(ANO_VELHO, "Ano Velho");
            gravar(NATAL, "Natal");

            mockMvc.perform(get("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].description").value("Natal"))
                    .andExpect(jsonPath("$[1].description").value("Ano Velho"));
        }

        @Test
        @DisplayName("o analista também vê a lista: o badge de feriado é de quem vê a escala")
        void analistaVeLista() throws Exception {
            gravar(NATAL, "Natal");

            mockMvc.perform(get("/api/v1/holidays")
                            .header("Authorization", "Bearer " + tokenAnalista))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/holidays")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("o supervisor corrige data e descrição")
        void supervisorCorrige() throws Exception {
            var feriado = gravar(NATAL, "Natal");

            mockMvc.perform(put("/api/v1/holidays/" + feriado.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal do Senhor")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value("2025-12-25"))
                    .andExpect(jsonPath("$.description").value("Natal do Senhor"));

            sincronizar();
            var gravado = holidayRepository.findById(feriado.getId()).orElseThrow();
            assertThat(gravado.getDescription()).isEqualTo("Natal do Senhor");
        }

        @Test
        @DisplayName("corrigir a descrição sem mexer na data não é recusado como duplicado da própria data")
        void descricaoNaPropriaData() throws Exception {
            var feriado = gravar(NATAL, "Natal");

            mockMvc.perform(put("/api/v1/holidays/" + feriado.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("mover para uma data que já é de outro feriado é 400")
        void moverParaDataDuplicada() throws Exception {
            var natal = gravar(NATAL, "Natal");
            gravar(ANO_VELHO, "Ano Velho");

            mockMvc.perform(put("/api/v1/holidays/" + natal.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(ANO_VELHO, "Natal")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Já existe um feriado registado para esta data."));

            sincronizar();
            assertThat(holidayRepository.findById(natal.getId()).orElseThrow()
                    .getDate()).isEqualTo(NATAL);
        }

        @Test
        @DisplayName("o analista é recusado com 403, e o feriado fica como estava")
        void analistaRecusado() throws Exception {
            var feriado = gravar(NATAL, "Natal");

            mockMvc.perform(put("/api/v1/holidays/" + feriado.getId())
                            .header("Authorization", "Bearer " + tokenAnalista)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Outro dia")))
                    .andExpect(status().isForbidden());

            sincronizar();
            assertThat(holidayRepository.findById(feriado.getId()).orElseThrow()
                    .getDescription()).isEqualTo("Natal");
        }

        @Test
        @DisplayName("atualizar um feriado que não existe é 400, não 404 nem 500")
        void inexistente() throws Exception {
            mockMvc.perform(put("/api/v1/holidays/999999")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(feriado(NATAL, "Natal")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Feriado não encontrado."));
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("o supervisor apaga, e a linha desaparece")
        void supervisorApaga() throws Exception {
            var feriado = gravar(NATAL, "Natal");

            mockMvc.perform(delete("/api/v1/holidays/" + feriado.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isNoContent());

            sincronizar();
            assertThat(holidayRepository.findById(feriado.getId())).isEmpty();
        }

        @Test
        @DisplayName("o analista é recusado com 403, e o feriado continua lá")
        void analistaRecusado() throws Exception {
            var feriado = gravar(NATAL, "Natal");

            mockMvc.perform(delete("/api/v1/holidays/" + feriado.getId())
                            .header("Authorization", "Bearer " + tokenAnalista))
                    .andExpect(status().isForbidden());

            sincronizar();
            assertThat(holidayRepository.findById(feriado.getId())).isPresent();
        }

        @Test
        @DisplayName("apagar um feriado que não existe é 400")
        void inexistente() throws Exception {
            mockMvc.perform(delete("/api/v1/holidays/999999")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Feriado não encontrado."));
        }
    }
}
