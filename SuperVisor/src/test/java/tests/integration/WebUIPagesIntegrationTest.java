package tests.integration;

import domain.model.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Páginas internas, vistas pelo servidor.
 *
 * <p>Estas páginas são HTML montado pelo Thymeleaf, e nada mais as testa: o
 * resto da suíte fala com a API, que não sabe se a vista existe, se o
 * fragmento da navegação é válido ou se um nome de vista está com as maiúsculas
 * certainas. Um erro desses não é um aviso no console — é um 500 quando alguém
 * clica no link, que é exatamente o momento em que não se descobre um erro de
 * digitação numa expressão.
 *
 * <p>Por isso cada página é pedida e verificada, e a navegação é verificada em
 * todas: o fragmento compartilhado é o ponto único de falha de todas as páginas ao
 * mesmo tempo.
 */
@DisplayName("Páginas internas")
class WebUIPagesIntegrationTest extends AbstractApiIntegrationTest {

    private static final String[] PAGINAS = {
            "/dashboard",
            "/exchanges",
            "/work-modality",
            "/leaves"
    };

    private User supervisor;
    private User analista;
    private String tokenSupervisor;
    private String tokenAnalista;

    @BeforeEach
    void prepararCenario() {
        perfilDeAnalista = TestFixtures.analystInTeam(
                domain.model.enums.TeamGroup.EQUIPE_A).getProfile();

        supervisor = TestFixtures.supervisor();
        supervisor.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        supervisor = userRepository.saveAndFlush(supervisor);

        var base = TestFixtures.analyst();
        base.setName("João");
        base.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        analista = userRepository.saveAndFlush(base);

        tokenSupervisor = tokenService.gerarToken(supervisor);
        tokenAnalista = tokenService.gerarToken(analista);
    }

    private domain.model.enums.UserProfile perfilDeAnalista;

    private MvcResult pagina(String caminho, String token) throws Exception {
        return mockMvc.perform(get(caminho).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String html(String caminho, String token) throws Exception {
        return pagina(caminho, token).getResponse().getContentAsString();
    }

    @Nested
    @DisplayName("Cada página responde")
    class Respostas {

        @Test
        @DisplayName("as quatro páginas devolvem 200 a um usuário autenticado")
        void todasDevolvem200() throws Exception {
            for (String caminho : PAGINAS) {
                mockMvc.perform(get(caminho).header("Authorization", "Bearer " + tokenSupervisor))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("sem sessão, a página responde 200 mas sem dados de ninguém")
        void semSessaoNaoVazaDados() throws Exception {
            // As páginas são HTML público de propósito: são cascas, e quem não
            // tem token é levado para o login pelo `api.js` na primeira
            // chamada. O que não pode acontecer é a casca vir com dados — o
            // e-mail de alguém, um nome, um número de usuário. Se um dia
            // alguém puser a renderizar no servidor, este teste é o que dá
            // pelo vazamento.
            for (String caminho : PAGINAS) {
                mockMvc.perform(get(caminho)).andExpect(status().isOk());
            }

            for (String caminho : PAGINAS) {
                String conteudo = html(caminho, tokenSupervisor);
                assertThat(conteudo).as(caminho)
                        .doesNotContain("admin@teste.com")
                        .doesNotContain("joao@teste.com");
                assertThat(conteudo).as(caminho).doesNotContain(tokenSupervisor);
            }
        }

        @Test
        @DisplayName("um perfil sem permissão de escrita vê a página na mesma")
        void analistaVeAsPaginas() throws Exception {
            // A grelha e o histórico são de leitura. Se devolvessem 403 ao
            // analista, o calendário de uma pessoa deixava de funcionar por
            // causa de quem o pede.
            for (String caminho : PAGINAS) {
                mockMvc.perform(get(caminho).header("Authorization", "Bearer " + tokenAnalista))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("a página de login é pública")
        void loginEhPublico() throws Exception {
            mockMvc.perform(get("/login")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("A navegação compartilhada")
    class Navegacao {

        @Test
        @DisplayName("aparece em todas as páginas, com as quatro secções")
        void navbarEmTodas() throws Exception {
            for (String caminho : PAGINAS) {
                String conteudo = html(caminho, tokenSupervisor);

                assertThat(conteudo).as(caminho).contains("navbar-brand");
                assertThat(conteudo).as(caminho).contains("SuperVisor");
                assertThat(conteudo).as(caminho).contains("href=\"/dashboard\"");
                assertThat(conteudo).as(caminho).contains("href=\"/exchanges\"");
                assertThat(conteudo).as(caminho).contains("href=\"/work-modality\"");
                assertThat(conteudo).as(caminho).contains("href=\"/leaves\"");
            }
        }

        @Test
        @DisplayName("marca a secção corrente, e só essa")
        void marcaSecaoActiva() throws Exception {
            // Uma e uma só secção active: duas marcadas ao mesmo tempo
            // significam que a comparação do fragmento deixou de funcionar, e o
            // usuário via duas páginas como se estivesse nas duas.
            for (String caminho : PAGINAS) {
                String conteudo = html(caminho, tokenSupervisor);
                long activas = contarOcorrencias(conteudo, "nav-link active");
                assertThat(activas).as(caminho).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("cada página marca a sua própria secção")
        void marcaAApropria() throws Exception {
            for (String caminho : PAGINAS) {
                String conteudo = html(caminho, tokenSupervisor);
                String caminhoEsperado = caminho;

                assertThat(ligacaoActiva(conteudo, caminhoEsperado))
                        .as(caminho)
                        .isTrue();

                // E nenhuma das outras está marcada: sem isto, uma comparação
                // que devolva sempre verdadeiro faria as quatro páginas
                // desenharem as quatro secções como se fossem a corrente.
                for (String outra : PAGINAS) {
                    if (outra.equals(caminho)) {
                        continue;
                    }
                    assertThat(ligacaoActiva(conteudo, outra)).as(caminho + " vs " + outra).isFalse();
                }
            }
        }

        /**
         * A ligação de navegação para {@code href} está marcada como a secção
         * corrente?
         *
         * <p>Só interessam as ligações com a classe {@code nav-link}: a marca da
         * aplicação aponta também para {@code /dashboard} e não é uma secção.
         * E dentro de cada bloco {@code <a ...>} a classe é procurada em vez de
         * comparada a linha inteira, porque o Thymeleaf escreve os atributos
         * por ordem de atributo e não por ordem de código, e um teste que
         * dependesse dessa ordem passava hoje e falhava numa atualização da
         * biblioteca.
         */
        private boolean ligacaoActiva(String html, String caminho) {
            int posicao = 0;

            while (posicao < html.length()) {
                int inicio = html.indexOf("<a", posicao);
                if (inicio < 0) {
                    return false;
                }

                int fecho = html.indexOf('>', inicio);
                if (fecho < 0) {
                    return false;
                }

                String ancora = html.substring(inicio, fecho);
                if (ancora.contains("nav-link") && ancora.contains("href=\"" + caminho + "\"")) {
                    return ancora.contains("nav-link active");
                }

                posicao = fecho + 1;
            }

            return false;
        }

        @Test
        @DisplayName("o botão de sair e a saudação vêm do fragmento, e não de cada página")
        void botaoDeSairPartilhado() throws Exception {
            for (String caminho : PAGINAS) {
                String conteudo = html(caminho, tokenSupervisor);
                assertThat(conteudo).as(caminho).contains("id=\"logoutBtn\"");
                assertThat(conteudo).as(caminho).contains("id=\"userGreeting\"");
                assertThat(conteudo).as(caminho).contains("/js/layout.js");
            }
        }
    }

    @Nested
    @DisplayName("O conteúdo de cada página")
    class Conteudo {

        @Test
        @DisplayName("a página de trocas traz os filtros e a tabela do histórico")
        void paginaDeTrocas() throws Exception {
            String conteudo = html("/exchanges", tokenSupervisor);

            assertThat(conteudo).contains("id=\"filtroStatus\"");
            assertThat(conteudo).contains("id=\"filtroDataInicial\"");
            assertThat(conteudo).contains("id=\"filtroDataFim\"");
            assertThat(conteudo).contains("id=\"trocasCorpo\"");
            assertThat(conteudo).contains("/js/exchanges.js");
        }

        @Test
        @DisplayName("a página de presencialidade traz a grelha e o botão de gerar")
        void paginaDeWorkModality() throws Exception {
            String conteudo = html("/work-modality", tokenSupervisor);

            assertThat(conteudo).contains("id=\"grelhaCorpo\"");
            assertThat(conteudo).contains("id=\"btnGerarEscala\"");
            assertThat(conteudo).contains("/js/work-modality.js");
        }

        @Test
        @DisplayName("a página de folgas traz a lista e o formulário")
        void paginaDeFolgas() throws Exception {
            String conteudo = html("/leaves", tokenSupervisor);

            assertThat(conteudo).contains("id=\"folgasCorpo\"");
            assertThat(conteudo).contains("id=\"folgaForm\"");
            assertThat(conteudo).contains("id=\"btnNovaFolga\"");
            assertThat(conteudo).contains("/js/leaves.js");
        }

        @Test
        @DisplayName("o dashboard continua a ter a tabela de escalas e a navegação nova")
        void dashboardIntacto() throws Exception {
            String conteudo = html("/dashboard", tokenSupervisor);

            assertThat(conteudo).contains("id=\"escalasTableBody\"");
            assertThat(conteudo).contains("id=\"btnPedirTroca\"");
            assertThat(conteudo).contains("/js/dashboard.js");
        }
    }

    @Nested
    @DisplayName("Os recursos estáticos")
    class Estaticos {

        @Test
        @DisplayName("os scripts compartilhados são servidos")
        void scriptsServidos() throws Exception {
            for (String script : new String[]{"/js/api.js", "/js/format.js", "/js/layout.js"}) {
                mockMvc.perform(get(script))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("application/javascript"));
            }
        }

        @Test
        @DisplayName("os scripts das três páginas novas são servidos")
        void scriptsDasPaginas() throws Exception {
            for (String script : new String[]{
                    "/js/exchanges.js", "/js/work-modality.js", "/js/leaves.js"}) {
                mockMvc.perform(get(script))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("application/javascript"));
            }
        }
    }

    private long contarOcorrencias(String texto, String agulha) {
        long total = 0;
        int posicao = texto.indexOf(agulha);
        while (posicao >= 0) {
            total++;
            posicao = texto.indexOf(agulha, posicao + agulha.length());
        }
        return total;
    }
}
