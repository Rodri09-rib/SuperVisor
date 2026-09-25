package controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import domain.model.entities.EditionScale;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import service.ScaleService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScaleController")
class ScaleControllerTest {

    @Mock
    private ScaleService scaleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ScaleController controller = new ScaleController();
        ReflectionTestUtils.setField(controller, "scaleService", scaleService);
        // Mesmo serializador do Spring Boot: datas em ISO-8601 ("2025-10-01") em vez de array.
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private EditionScale escala(Long id, String nome, EditionStatus status) {
        EditionScale escala = new EditionScale();
        escala.setId(id);
        escala.setName(nome);
        escala.setInitialDate(LocalDate.of(2025, 10, 1));
        escala.setEndDate(LocalDate.of(2025, 10, 31));
        escala.setStatus(status);
        escala.setCreatedBy(new User(1L, "Administrador", "admin@teste.com", "hash", UserProfile.SUPERVISOR));
        return escala;
    }

    @Nested
    @DisplayName("POST /api/v1/scales")
    class Criar {

        @Test
        @DisplayName("devolve 200 com a escala criada")
        void devolveEscalaCriada() throws Exception {
            when(scaleService.createScale(any())).thenReturn(escala(7L, "Escala Outubro", EditionStatus.DRAFT));

            mockMvc.perform(post("/api/v1/scales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Escala Outubro","initialDate":"2025-10-01",
                                     "endDate":"2025-10-31","createdById":1}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.name").value("Escala Outubro"))
                    .andExpect(jsonPath("$.initialDate").value("2025-10-01"))
                    .andExpect(jsonPath("$.endDate").value("2025-10-31"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("a resposta não expõe o utilizador criador, por causa do @JsonIgnore")
        void naoExpoeOCriador() throws Exception {
            when(scaleService.createScale(any())).thenReturn(escala(7L, "Escala Outubro", EditionStatus.DRAFT));

            mockMvc.perform(post("/api/v1/scales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Escala Outubro","initialDate":"2025-10-01",
                                     "endDate":"2025-10-31","createdById":1}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdBy").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("admin@teste.com"))));
        }

        @Test
        @DisplayName("encaminha o DTO recebido ao serviço sem alterar os campos")
        void encaminhaDtoAoServico() throws Exception {
            when(scaleService.createScale(any())).thenReturn(escala(7L, "Escala Outubro", EditionStatus.DRAFT));

            mockMvc.perform(post("/api/v1/scales")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"Escala Novembro","initialDate":"2025-11-01",
                             "endDate":"2025-11-30","createdById":42}
                            """));

            ArgumentCaptor<domain.dto.CreateScaleDTO> captor =
                    ArgumentCaptor.forClass(domain.dto.CreateScaleDTO.class);
            verify(scaleService).createScale(captor.capture());
            assertThat(captor.getValue().name()).isEqualTo("Escala Novembro");
            assertThat(captor.getValue().initialDate()).isEqualTo(LocalDate.of(2025, 11, 1));
            assertThat(captor.getValue().endDate()).isEqualTo(LocalDate.of(2025, 11, 30));
            assertThat(captor.getValue().createdById()).isEqualTo(42L);
        }

        @Test
        @DisplayName("erro do serviço (criador inexistente) propaga sem tratamento de erro")
        void criadorInexistente() {
            when(scaleService.createScale(any()))
                    .thenThrow(new RuntimeException("Utilizador não encontrado"));

            assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/scales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"Escala Outubro","initialDate":"2025-10-01",
                                     "endDate":"2025-10-31","createdById":404}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Utilizador não encontrado");
        }

        @Test
        @DisplayName("corpo sem os campos cria um DTO com valores nulos, pois não há validação")
        void corpoVazio() throws Exception {
            when(scaleService.createScale(any())).thenReturn(escala(7L, "Sem nome", EditionStatus.DRAFT));

            mockMvc.perform(post("/api/v1/scales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<domain.dto.CreateScaleDTO> captor =
                    ArgumentCaptor.forClass(domain.dto.CreateScaleDTO.class);
            verify(scaleService).createScale(captor.capture());
            assertThat(captor.getValue().name()).isNull();
            assertThat(captor.getValue().initialDate()).isNull();
            assertThat(captor.getValue().createdById()).isNull();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/scales/{id}/publish")
    class Publicar {

        @Test
        @DisplayName("devolve 204 sem conteúdo")
        void devolveSemConteudo() throws Exception {
            mockMvc.perform(post("/api/v1/scales/1/publish"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("o endpoint não chama o serviço: a publicação não está implementada")
        void naoChamaOServico() throws Exception {
            mockMvc.perform(post("/api/v1/scales/1/publish"))
                    .andExpect(status().isNoContent());

            verifyNoInteractions(scaleService);
        }

        @Test
        @DisplayName("o id do caminho é ignorado: qualquer id devolve 204")
        void idEIgnorado() throws Exception {
            mockMvc.perform(post("/api/v1/scales/999/publish"))
                    .andExpect(status().isNoContent());

            verifyNoInteractions(scaleService);
        }

        @Test
        @DisplayName("mesmo para uma escala inexistente devolve 204")
        void escalaInexistente() throws Exception {
            mockMvc.perform(post("/api/v1/scales/4242/publish"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("um id não numérico é rejeitado antes de chegar ao controller")
        void idNaoNumerico() throws Exception {
            mockMvc.perform(post("/api/v1/scales/abc/publish"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(scaleService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/scales")
    class Listar {

        @Test
        @DisplayName("devolve 200 com a lista de escalas")
        void devolveLista() throws Exception {
            when(scaleService.listAll()).thenReturn(List.of(
                    escala(1L, "Escala Outubro", EditionStatus.DRAFT),
                    escala(2L, "Escala Novembro", EditionStatus.PUBLISHED)));

            mockMvc.perform(get("/api/v1/scales"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$[0].name").value("Escala Outubro"))
                    .andExpect(jsonPath("$[1].status").value("PUBLISHED"));
        }

        @Test
        @DisplayName("devolve lista vazia quando não há escalas")
        void listaVazia() throws Exception {
            when(scaleService.listAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/scales"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        }

        @Test
        @DisplayName("nenhum item da lista expõe o utilizador criador")
        void listaNaoExpoeCriador() throws Exception {
            when(scaleService.listAll()).thenReturn(List.of(escala(1L, "Escala Outubro", EditionStatus.DRAFT)));

            mockMvc.perform(get("/api/v1/scales"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("admin@teste.com"))));
        }

        @Test
        @DisplayName("o serviço é chamado sem parâmetros de filtro ou paginação")
        void semFiltros() throws Exception {
            when(scaleService.listAll()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/scales")).andExpect(status().isOk());

            verify(scaleService).listAll();
            verify(scaleService, never()).publishSchedule(anyLong());
        }
    }

    @Nested
    @DisplayName("Contrato do recurso")
    class Contrato {

        @Test
        @DisplayName("GET no caminho de criação não é mapeado")
        void getNoPost() throws Exception {
            mockMvc.perform(get("/api/v1/scales/1/publish"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("um caminho desconhecido sob /api/v1/scales devolve 404")
        void caminhoDesconhecido() throws Exception {
            mockMvc.perform(get("/api/v1/scales/1/alterar"))
                    .andExpect(status().isNotFound());
        }
    }
}
