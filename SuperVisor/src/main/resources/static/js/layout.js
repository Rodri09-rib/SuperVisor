/**
 * Comportamento comum a todas as páginas internas: quem está com sessão iniciada
 * e como sair.
 *
 * <p>Estava dentro do `dashboard.js`, mas a barra de navegação passou a ser um
 * fragmento compartilhado e as outras páginas passaram a tê-la também. Deixar o
 * botão de sair a funcionar numa página e não noutra seria o tipo de falha que
 * só aparece depois de alguém navega para a página nova e não consegue sair.
 *
 * <p>Os dois elementos são procurados em vez de assumidos: o fragmento é
 * compartilhado, mas esta página pode não ser a ter — e um `null` aqui rebentava a
 * página antes de ela desenhar seja o que for.
 */
const SuperVisorLayout = (() => {

    function elementoSaudacao() {
        return document.getElementById('userGreeting');
    }

    /**
     * E-mail do token, para a saudação aparecer sem esperar pela API.
     *
     * <p>O token JWT traz o assunto no claim `sub`, e usá-lo só para a primeira
     * pintura evita o salto visual de "Carregando…" para "Olá, …". Não é usado
     * para nada que precise de ser verdade: o nome que fica no fim vem do
     * servidor, e um token forjado com outro `sub` só mudaria o texto que o
     * próprio usuário está vendo antes de a página perguntar quem ele é.
     */
    function emailDoToken() {
        try {
            const jwt = SuperVisorApi.token();
            if (!jwt) {
                return null;
            }
            const payload = jwt.split('.')[1];
            if (!payload) {
                return null;
            }
            const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
            const json = decodeURIComponent(atob(base64)
                .split('')
                .map((caractere) => '%' + ('00' + caractere.charCodeAt(0).toString(16)).slice(-2))
                .join(''));
            return json.sub || null;
        } catch (erro) {
            console.warn('Não foi possível ler o token no cliente:', erro);
            return null;
        }
    }

    function saudacao() {
        const elemento = elementoSaudacao();
        if (!elemento) {
            return;
        }

        const email = emailDoToken();
        if (email) {
            elemento.textContent = 'Olá, ' + email;
        }

        SuperVisorApi.utilizadorAtual()
            .then((utilizador) => {
                if (!utilizador) {
                    return;
                }
                elemento.textContent = 'Olá, ' + (utilizador.name || utilizador.email);
            })
            .catch(() => {
                // A página não depende disto: se o perfil não vier, fica o que
                // já estava escrito e o resto da página continua a funcionar.
            });
    }

    function logout() {
        const botao = document.getElementById('logoutBtn');
        if (!botao) {
            return;
        }

        botao.addEventListener('click', () => {
            SuperVisorApi.limparSessao();
            window.location.href = '/login';
        });
    }

    function iniciar() {
        saudacao();
        logout();
    }

    return { iniciar: iniciar };
})();

document.addEventListener('DOMContentLoaded', () => SuperVisorLayout.iniciar());
