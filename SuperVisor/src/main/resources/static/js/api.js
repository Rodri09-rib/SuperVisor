/**
 * Camada de acesso à API do SuperVisor.
 *
 * Concentra num único sitio o que é comum a todas as chamadas: o header
 * Authorization a partir do JWT guardado em localStorage, a serialização JSON
 * e o tratamento global de 401/403, que limpa a sessão e volta ao login.
 */
const SuperVisorApi = (() => {

    const TOKEN_KEY = 'jwt_token';

    let aRedirecionar = false;

    function token() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function limparSessao() {
        localStorage.removeItem(TOKEN_KEY);
    }

    function redirecionarParaLogin() {
        if (aRedirecionar) {
            return;
        }
        aRedirecionar = true;
        window.location.href = '/login';
    }

    function semAutorizacao(status) {
        return status === 401 || status === 403;
    }

    async function mensagemDeErro(response) {
        try {
            const corpo = await response.json();
            return corpo && corpo.message ? corpo.message : null;
        } catch (erro) {
            return null;
        }
    }

    /**
     * Devolve o corpo JSON da resposta, ou null em respostas sem conteúdo
     * (204) ou com corpo vazio. Lança um Error com a mensagem do servidor em
     * qualquer resposta de erro.
     */
    async function request(caminho, opcoes = {}) {
        const metodo = opcoes.metodo || 'GET';
        const corpo = opcoes.corpo === undefined ? null : opcoes.corpo;

        const jwt = token();
        if (!jwt) {
            redirecionarParaLogin();
            throw new Error('Sessão expirada. Inicie sessão novamente.');
        }

        const headers = { 'Authorization': 'Bearer ' + jwt };
        if (corpo !== null) {
            headers['Content-Type'] = 'application/json';
        }

        let response;
        try {
            response = await fetch(caminho, {
                method: metodo,
                headers: headers,
                body: corpo === null ? undefined : JSON.stringify(corpo)
            });
        } catch (erro) {
            console.error('Falha de comunicação com o servidor:', erro);
            throw new Error('Erro de comunicação com o servidor.');
        }

        if (semAutorizacao(response.status)) {
            limparSessao();
            redirecionarParaLogin();
            throw new Error('Sessão expirada ou sem permissões.');
        }

        if (!response.ok) {
            const mensagem = await mensagemDeErro(response);
            throw new Error(mensagem || 'Ocorreu um erro inesperado (' + response.status + ').');
        }

        if (response.status === 204) {
            return null;
        }

        const texto = await response.text();
        return texto ? JSON.parse(texto) : null;
    }

    return {
        token: token,
        limparSessao: limparSessao,
        redirecionarParaLogin: redirecionarParaLogin,

        utilizadorAtual: () => request('/api/v1/users/me'),

        listarEscalas: () => request('/api/v1/scales'),
        obterEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id)),
        criarEscala: (escala) => request('/api/v1/scales', { metodo: 'POST', corpo: escala }),
        publicarEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id) + '/publish', { metodo: 'POST' }),

        listarAlocacoes: (escalaId) => request('/api/v1/allocations'
            + (escalaId ? '?editionScaleId=' + encodeURIComponent(escalaId) : '')),

        pedirTroca: (pedido) => request('/api/v1/exchanges', { metodo: 'POST', corpo: pedido })
    };
})();
