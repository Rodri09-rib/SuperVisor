package tests.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de escalas — /api/v1/scales")
class ScaleApiIntegrationTest extends AbstractApiIntegrationTest {

    private domain.model.entities.User admin;
    private String token;

    private void prepararUtilizador() {
        admin = criarUsuario("Administrador", "admin@teste.com",
                domain.model.enums.UserProfile.SUPERVISOR);
        token = tokenService.gerarToken(admin);
    }

    private String criarEscalaJson(String nome, String inicial, String fim, Long criadoPor) {
        return """
                {"name":"%s","initialDate":"%s","endDate":"%s","createdById":%d}
                """.formatted(nome, inicial, fim, criadoPor);
    }

    @Nested
    @DisplayName("Criar escala")
    class Criar {

        @Test
        @DisplayName("cria a escala e devolve 200 com os dados gravados")
        void criaEscala() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", admin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Escala Outubro"))
                    .andExpect(jsonPath("$.initialDate").value("2025-10-01"))
                    .andExpect(jsonPath("$.endDate").value("2025-10-31"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));

            assertThat(editionScaleRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a escala criada fica em rascunho, independentemente do que foi pedido")
        void sempreNasceEmRascunho() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala November", "2025-11-01", "2025-11-30", admin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("o estado DRAFT criado é de facto gravado na base de dados")
        void estadoGravadoNoBanco() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", admin.getId())))
                    .andExpect(status().isOk());

            sincronizar();
            assertThat(editionScaleRepository.findAll().get(0).getStatus())
                    .isEqualTo(domain.model.enums.EditionStatus.DRAFT);
        }

        @Test
        @DisplayName("a resposta não inclui o utilizador criador")
        void naoExpoeCriador() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", admin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdBy").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("password"))));
        }

        @Test
        @DisplayName("o criador é associado à escala na base de dados")
        void associaCriador() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", admin.getId())))
                    .andExpect(status().isOk());

            var escala = editionScaleRepository.findAll().get(0);
            assertThat(escala.getCreatedBy().getId()).isEqualTo(admin.getId());
        }

        @Test
        @DisplayName("criador inexistente devolve 400 com o corpo de erro estruturado, e nada é gravado")
        void criadorInexistente() throws Exception {
            prepararUtilizador();

            var resposta = mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", 9999L)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Utilizador não encontrado"))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Utilizador não encontrado");
            assertThat(editionScaleRepository.count()).isZero();
        }

        @Test
        @DisplayName("datas invertidas são aceites, por não haver validação no serviço")
        void datasInvertidasSaoAceites() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala Invertida", "2025-10-31", "2025-10-01", admin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.initialDate").value("2025-10-31"))
                    .andExpect(jsonPath("$.endDate").value("2025-10-01"));
        }

        @Test
        @DisplayName("datas inválidas no corpo devolvem 400")
        void datasInvalidas() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala", "31-10-2025", "01-10-2025", admin.getId())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("nomes repetidos são permitidos, pois não há unicidade")
        void nomesRepetidos() throws Exception {
            prepararUtilizador();

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/v1/scales")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(criarEscalaJson("Escala Outubro", "2025-10-01", "2025-10-31", admin.getId())))
                        .andExpect(status().isOk());
            }

            assertThat(editionScaleRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("qualquer utilizador autenticado pode criar escalas, não só o SUPERVISOR")
        void qualquerAutenticadoCria() throws Exception {
            var analist = criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/scales")
                            .header("Authorization", "Bearer " + tokenService.gerarToken(analist))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(criarEscalaJson("Escala do Analista", "2025-10-01", "2025-10-31", analist.getId())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Listar escalas")
    class Listar {

        @Test
        @DisplayName("devolve 200 com a lista vazia quando não há escalas")
        void listaVazia() throws Exception {
            prepararUtilizador();

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        }

        @Test
        @DisplayName("devolve todas as escalas gravadas")
        void devolveTodas() throws Exception {
            prepararUtilizador();
            criarEscala("Escala Outubro");
            criarEscala("Escala Novembro");
            criarEscala("Escala Dezembro");

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                    .andExpect(jsonPath("$[*].name",
                            org.hamcrest.Matchers.containsInAnyOrder(
                                    "Escala Outubro", "Escala Novembro", "Escala Dezembro")));
        }

        @Test
        @DisplayName("cada item traz id, nome, datas e estado, mas não o criador")
        void formatoDeCadaItem() throws Exception {
            prepararUtilizador();
            criarEscala("Escala Outubro");

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").isNumber())
                    .andExpect(jsonPath("$[0].name").value("Escala Outubro"))
                    .andExpect(jsonPath("$[0].initialDate").value("2025-10-01"))
                    .andExpect(jsonPath("$[0].endDate").value("2025-10-31"))
                    .andExpect(jsonPath("$[0].status").value("DRAFT"))
                    .andExpect(jsonPath("$[0].createdBy").doesNotExist());
        }

        @Test
        @DisplayName("o estado de cada escala reflete o valor gravado")
        void estadosVariados() throws Exception {
            prepararUtilizador();
            var rascunho = criarEscala("Escala Rascunho");
            var publicada = criarEscala("Escala Publicada");
            publicada.setStatus(domain.model.enums.EditionStatus.PUBLISHED);
            editionScaleRepository.saveAndFlush(publicada);

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].status",
                            org.hamcrest.Matchers.containsInAnyOrder("DRAFT", "PUBLISHED")));
            assertThat(rascunho.getStatus()).isEqualTo(domain.model.enums.EditionStatus.DRAFT);
        }

        @Test
        @DisplayName("não há paginação nem filtro: tudo é devolvido de uma vez")
        void semPaginacao() throws Exception {
            prepararUtilizador();
            for (int i = 1; i <= 12; i++) {
                criarEscala("Escala " + i);
            }

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(12)));
        }
    }

    @Nested
    @DisplayName("Publicar escala")
    class Publicar {

        @Test
        @DisplayName("devolve 204, mas não altera o estado da escala")
        void devolveSemAlterarOEestado() throws Exception {
            prepararUtilizador();
            var escala = criarEscala("Escala Outubro");

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/publish")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            sincronizar();
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getStatus())
                    .isEqualTo(domain.model.enums.EditionStatus.DRAFT);
        }

        @Test
        @DisplayName("devolve 204 mesmo para uma escala inexistente")
        void escalaInexistente() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales/9999/publish")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("o endpoint de publicação está desligado do ScaleService.publishSchedule")
        void naoChamaPublishSchedule() throws Exception {
            prepararUtilizador();
            var escala = criarEscala("Escala Outubro");

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/publish")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            sincronizar();
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getStatus())
                    .isNotEqualTo(domain.model.enums.EditionStatus.PUBLISHED);
        }
    }

    private domain.model.entities.EditionScale criarEscala(String nome) {
        return criarEscala(nome, admin, domain.model.enums.EditionStatus.DRAFT);
    }
}
