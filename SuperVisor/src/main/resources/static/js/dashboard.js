/**
 * Dashboard de escalas: listagem, detalhes, pedido de troca e, para o perfil
 * de administrador, criação e publicação de escalas.
 *
 * O estado vive aqui e é repintado por completo em cada renderização; a lista
 * tem sempre poucos elementos, pelo que não justifica paginação nem
 * reconciliação manual do DOM.
 */
document.addEventListener('DOMContentLoaded', () => {

    const PERFIS_ADMIN = ['SUPERVISOR', 'ADMIN'];
    const MOTIVO_MINIMO = 5;

    const estado = {
        utilizador: null,
        escalas: [],
        alocacoes: [],
        escalaA_Publicar: null
    };

    const el = {};

    const modais = {};

    document.querySelectorAll('[data-bs-toggle="modal"]').forEach((gatilho) => {
        const alvo = document.getElementById(gatilho.dataset.bsTarget);
        if (alvo) {
            modais[alvo.id] = new bootstrap.Modal(alvo);
        }
    });

    function guardarElementos() {
        el.greeting = document.getElementById('userGreeting');
        el.logout = document.getElementById('logoutBtn');
        el.btnNovaEscala = document.getElementById('btnNovaEscala');
        el.btnPedirTroca = document.getElementById('btnPedirTroca');
        el.tabelaCorpo = document.getElementById('escalasTableBody');
        el.toasts = document.getElementById('toastContainer');

        el.detalhesTitulo = document.getElementById('detalhesTitulo');
        el.detalhesCorpo = document.getElementById('detalhesCorpo');
        el.detalhesAlocacoes = document.getElementById('detalhesAlocacoesCorpo');
        el.btnTrocarDetalhes = document.getElementById('btnTrocarDaEscala');

        el.trocaFormulario = document.getElementById('trocaForm');
        el.trocaEscala = document.getElementById('trocaEscala');
        el.trocaOrigem = document.getElementById('trocaOrigem');
        el.trocaDestino = document.getElementById('trocaDestino');
        el.trocaAviso = document.getElementById('trocaAviso');
        el.trocaMotivo = document.getElementById('trocaMotivo');
        el.trocaSubmeter = document.getElementById('btnSubmeterTroca');

        el.escalaFormulario = document.getElementById('novaEscalaForm');
        el.escalaNome = document.getElementById('novaEscalaNome');
        el.escalaInicio = document.getElementById('novaEscalaInicio');
        el.escalaFim = document.getElementById('novaEscalaFim');
        el.escalaAviso = document.getElementById('novaEscalaAviso');
        el.escalaSubmeter = document.getElementById('btnSubmeterEscala');

        el.publicacaoNome = document.getElementById('publicacaoEscalaNome');
        el.btnConfirmarPublicacao = document.getElementById('btnConfirmarPublicacao');
    }

    /* ------------------------------------------------------------------ */
    /* Feedback                                                            */
    /* ------------------------------------------------------------------ */

    function notificar(mensagem, tipo) {
        const classe = tipo === 'erro' ? 'text-bg-danger' : 'text-bg-success';
        const icone = tipo === 'erro' ? 'bi-exclamation-triangle' : 'bi-check-circle';

        const toast = document.createElement('div');
        toast.className = 'toast align-items-center border-0 ' + classe;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');
        toast.innerHTML =
            '<div class="d-flex">'
            + '<div class="toast-body">'
            + '<i class="bi ' + icone + ' me-2"></i>'
            + '<span class="js-mensagem">' + SuperVisorFormat.escapar(mensagem) + '</span>'
            + '</div>'
            + '<button type="button" class="btn-close btn-close-white me-2 m-auto" '
            + 'data-bs-dismiss="toast" aria-label="Fechar"></button>'
            + '</div>';

        el.toasts.appendChild(toast);

        const instance = new bootstrap.Toast(toast, { delay: 4500 });
        toast.addEventListener('hidden.bs.toast', () => toast.remove());
        instance.show();
    }

    function ocupado(botao, ativo, rotulo) {
        if (!botao) {
            return;
        }
        if (ativo) {
            botao.dataset.rotuloOriginal = botao.innerHTML;
            botao.disabled = true;
            botao.innerHTML =
                '<span class="spinner-border spinner-border-sm me-2" role="status" '
                + 'aria-hidden="true"></span>' + SuperVisorFormat.escapar(rotulo);
            return;
        }
        botao.disabled = false;
        if (botao.dataset.rotuloOriginal) {
            botao.innerHTML = botao.dataset.rotuloOriginal;
            delete botao.dataset.rotuloOriginal;
        }
    }

    function mostrarAviso(elemento, mensagem, tipo) {
        if (!elemento) {
            return;
        }
        if (!mensagem) {
            elemento.classList.add('d-none');
            elemento.textContent = '';
            return;
        }
        elemento.className = 'alert alert-' + (tipo || 'danger');
        elemento.textContent = mensagem;
    }

    /* ------------------------------------------------------------------ */
    /* Sessão                                                              */
    /* ------------------------------------------------------------------ */

    function ehAdministrador() {
        return !!estado.utilizador
            && PERFIS_ADMIN.indexOf(estado.utilizador.profile) !== -1;
    }

    /**
     * Saudação imediata a partir do e-mail do token, para não haver um salto
     * visual enquanto o perfil não chega da API.
     */
    function saudacaoDoToken() {
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
            return json.sub ? json.sub : null;
        } catch (erro) {
            console.warn('Não foi possível ler o token no cliente:', erro);
            return null;
        }
    }

    async function carregarSessao() {
        const email = saudacaoDoToken();
        if (email) {
            el.greeting.textContent = 'Olá, ' + email;
        }

        try {
            estado.utilizador = await SuperVisorApi.utilizadorAtual();
        } catch (erro) {
            console.warn('Perfil do utilizador indisponível:', erro);
            return;
        }

        el.greeting.textContent = estado.utilizador.name
            ? 'Olá, ' + estado.utilizador.name
            : 'Olá, ' + estado.utilizador.email;

        el.btnNovaEscala.classList.toggle('d-none', !ehAdministrador());
    }

    /* ------------------------------------------------------------------ */
    /* Tabela de escalas                                                   */
    /* ------------------------------------------------------------------ */

    function linhaCarregando() {
        return '<tr>'
            + '<td colspan="4" class="text-center py-5 text-muted">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar escalas...</span>'
            + '</div>'
            + '<div class="mt-2">A carregar escalas...</div>'
            + '</td>'
            + '</tr>';
    }

    function linhaMensagem(colspan, html, classe) {
        return '<tr><td colspan="' + colspan + '" class="text-center py-4 ' + classe + '">'
            + html + '</td></tr>';
    }

    function linhaEscala(escala) {
        const nome = SuperVisorFormat.escapar(escala.name || 'Sem nome');
        const publicavel = escala.status === 'DRAFT' || escala.status === 'RASCUNHO';
        const id = encodeURIComponent(escala.id);

        const botaoPublicar = ehAdministrador() && publicavel
            ? '<button class="btn btn-sm btn-outline-success js-publicar" data-id="' + id + '">'
              + '<i class="bi bi-send"></i> Publicar</button> '
            : '';

        return '<tr>'
            + '<td class="align-middle fw-semibold">' + nome + '</td>'
            + '<td class="align-middle">'
            + SuperVisorFormat.periodo(escala.initialDate, escala.endDate)
            + '</td>'
            + '<td class="align-middle">'
            + SuperVisorFormat.badgeEscala(escala.status)
            + '</td>'
            + '<td class="text-end align-middle text-nowrap">'
            + '<button class="btn btn-sm btn-outline-primary js-detalhes" data-id="' + id + '">'
            + '<i class="bi bi-eye"></i> Detalhes</button> '
            + '<button class="btn btn-sm btn-outline-secondary js-trocar" data-id="' + id + '">'
            + '<i class="bi bi-arrow-left-right"></i> Trocar</button> '
            + botaoPublicar
            + '</td>'
            + '</tr>';
    }

    function renderizarTabela() {
        if (!estado.escalas.length) {
            el.tabelaCorpo.innerHTML = linhaMensagem(4,
                'Não tem escalas registadas neste momento.', 'text-muted');
            return;
        }
        el.tabelaCorpo.innerHTML = estado.escalas.map(linhaEscala).join('');
    }

    async function carregarEscalas() {
        el.tabelaCorpo.innerHTML = linhaCarregando();
        try {
            estado.escalas = await SuperVisorApi.listarEscalas() || [];
            renderizarTabela();
        } catch (erro) {
            if (erro.message.indexOf('Sessão') === 0) {
                return;
            }
            el.tabelaCorpo.innerHTML = linhaMensagem(4,
                '<i class="bi bi-exclamation-triangle me-2"></i>'
                + SuperVisorFormat.escapar(erro.message), 'text-danger fw-semibold');
            notificar(erro.message, 'erro');
        }
    }

    function escalaPorId(id) {
        const alvo = String(id);
        return estado.escalas.find((escala) => String(escala.id) === alvo) || null;
    }

    /* ------------------------------------------------------------------ */
    /* Detalhes da escala                                                  */
    /* ------------------------------------------------------------------ */

    async function abrirDetalhes(id) {
        el.btnTrocarDetalhes.dataset.id = encodeURIComponent(id);
        el.detalhesTitulo.textContent = 'Detalhes da escala';
        el.detalhesCorpo.innerHTML = '<div class="text-center py-4">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar...</span></div></div>';
        el.detalhesAlocacoes.innerHTML = '';
        modais.detalhesModal.show();

        try {
            const [escala, alocacoes] = await Promise.all([
                SuperVisorApi.obterEscala(id),
                SuperVisorApi.listarAlocacoes(id)
            ]);

            el.detalhesTitulo.textContent = escala.name || 'Escala #' + escala.id;
            el.detalhesCorpo.innerHTML = [
                linhaDetalhe('Identificador', escala.id),
                linhaDetalhe('Período', SuperVisorFormat.periodo(escala.initialDate, escala.endDate)),
                linhaDetalhe('Estado', SuperVisorFormat.badgeEscala(escala.status))
            ].join('');

            renderizarAlocacoes(alocacoes || []);
        } catch (erro) {
            el.detalhesCorpo.innerHTML = linhaMensagem(1,
                '<i class="bi bi-exclamation-triangle me-2"></i>'
                + SuperVisorFormat.escapar(erro.message), 'text-danger');
        }
    }

    function linhaDetalhe(rotulo, valorHtml) {
        return '<div class="row mb-2">'
            + '<div class="col-5 text-muted">' + rotulo + '</div>'
            + '<div class="col-7 fw-semibold">' + valorHtml + '</div>'
            + '</div>';
    }

    function renderizarAlocacoes(alocacoes) {
        if (!alocacoes.length) {
            el.detalhesAlocacoes.innerHTML = linhaMensagem(5,
                'Esta escala ainda não tem turnos atribuídos.', 'text-muted');
            return;
        }

        el.detalhesAlocacoes.innerHTML = alocacoes.map((alocacao) => {
            const turno = alocacao.shiftAcronym || 'Por atribuir';
            const horario = alocacao.shiftStartTime && alocacao.shiftEndTime
                ? ' (' + SuperVisorFormat.hora(alocacao.shiftStartTime)
                  + '–' + SuperVisorFormat.hora(alocacao.shiftEndTime) + ')'
                : '';

            return '<tr>'
                + '<td>' + SuperVisorFormat.data(alocacao.specificDate) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(turno + horario) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(alocacao.userName || 'Sem utilizador') + '</td>'
                + '<td>' + SuperVisorFormat.badgeAlocacao(alocacao.analystAcceptanceStatus) + '</td>'
                + '</tr>';
        }).join('');
    }

    /* ------------------------------------------------------------------ */
    /* Pedido de troca                                                     */
    /* ------------------------------------------------------------------ */

    function preencherOpcoes(select, itens, valorVazio) {
        select.innerHTML = '<option value="">' + valorVazio + '</option>'
            + itens.map((item) => '<option value="' + SuperVisorFormat.escapar(item.valor) + '">'
                + SuperVisorFormat.escapar(item.rotulo) + '</option>').join('');
        select.disabled = !itens.length;
    }

    function validarTroca() {
        if (!estado.utilizador) {
            mostrarAviso(el.trocaAviso,
                'Não foi possível identificar o utilizador. Inicie sessão novamente.', 'danger');
            el.trocaSubmeter.disabled = true;
            return;
        }

        const temColegas = estado.alocacoes.some((a) => a.userId !== estado.utilizador.id);
        if (!temColegas) {
            mostrarAviso(el.trocaAviso,
                'Ainda não existe nenhum colega com turno atribuído nesta escala, '
                + 'por isso não há troca possível.', 'warning');
        } else {
            mostrarAviso(el.trocaAviso, null);
        }
        el.trocaSubmeter.disabled = !temColegas;
    }

    async function atualizarOpcoesTroca() {
        const escalaId = el.trocaEscala.value;

        if (!escalaId) {
            estado.alocacoes = [];
            preencherOpcoes(el.trocaOrigem, [], 'Escolha primeiro a escala');
            preencherOpcoes(el.trocaDestino, [], 'Escolha primeiro a escala');
            el.trocaSubmeter.disabled = true;
            return;
        }

        el.trocaOrigem.disabled = true;
        el.trocaDestino.disabled = true;
        el.trocaSubmeter.disabled = true;

        try {
            estado.alocacoes = await SuperVisorApi.listarAlocacoes(escalaId) || [];
            const meuId = estado.utilizador ? estado.utilizador.id : null;

            const minhas = estado.alocacoes
                .filter((a) => meuId !== null && a.userId === meuId)
                .map((a) => ({ valor: a.id, rotulo: SuperVisorFormat.rotuloAlocacao(a) }));
            const colegas = estado.alocacoes
                .filter((a) => meuId === null || a.userId !== meuId)
                .map((a) => ({
                    valor: a.id,
                    rotulo: SuperVisorFormat.rotuloAlocacao(a) + ' — ' + (a.userName || 'Colega')
                }));

            preencherOpcoes(el.trocaOrigem, minhas, 'Sem turnos seus nesta escala');
            preencherOpcoes(el.trocaDestino, colegas, 'Sem colegas disponíveis');

            if (minhas.length) {
                el.trocaOrigem.value = String(minhas[0].valor);
            }
            if (colegas.length) {
                el.trocaDestino.value = String(colegas[0].valor);
            }
            validarTroca();
        } catch (erro) {
            estado.alocacoes = [];
            preencherOpcoes(el.trocaOrigem, [], 'Erro ao carregar');
            preencherOpcoes(el.trocaDestino, [], 'Erro ao carregar');
            mostrarAviso(el.trocaAviso, erro.message, 'danger');
        }
    }

    function abrirPedidoTroca(escalaId) {
        el.trocaFormulario.reset();
        mostrarAviso(el.trocaAviso, null);

        preencherOpcoes(
            el.trocaEscala,
            estado.escalas.map((escala) => ({
                valor: escala.id,
                rotulo: (escala.name || 'Escala #' + escala.id)
                    + ' (' + SuperVisorFormat.periodo(escala.initialDate, escala.endDate) + ')'
            })),
            'Escolha a escala');

        if (escalaId && escalaPorId(escalaId)) {
            el.trocaEscala.value = String(escalaId);
        }

        modais.trocaModal.show();
        atualizarOpcoesTroca();
    }

    async function submeterTroca(evento) {
        evento.preventDefault();

        const origem = el.trocaOrigem.value;
        const destino = el.trocaDestino.value;
        const motivo = el.trocaMotivo.value.trim();

        if (!origem || !destino) {
            mostrarAviso(el.trocaAviso, 'Escolha o turno de origem e o turno de destino.', 'warning');
            return;
        }
        if (origem === destino) {
            mostrarAviso(el.trocaAviso, 'O turno de origem e o de destino têm de ser diferentes.', 'warning');
            return;
        }
        if (motivo.length < MOTIVO_MINIMO) {
            mostrarAviso(el.trocaAviso,
                'Descreva o motivo do pedido (mínimo ' + MOTIVO_MINIMO + ' caracteres).', 'warning');
            return;
        }

        mostrarAviso(el.trocaAviso, null);
        ocupado(el.trocaSubmeter, true, 'A enviar...');

        try {
            await SuperVisorApi.pedirTroca({
                originAllocationId: Number(origem),
                destinationAllocationId: Number(destino),
                reason: motivo
            });
            modais.trocaModal.hide();
            notificar('Pedido de troca registado. Aguarda resposta do colega.', 'sucesso');
        } catch (erro) {
            mostrarAviso(el.trocaAviso, erro.message, 'danger');
            notificar(erro.message, 'erro');
        } finally {
            ocupado(el.trocaSubmeter, false);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Administração: criar e publicar                                     */
    /* ------------------------------------------------------------------ */

    function abrirNovaEscala() {
        el.escalaFormulario.reset();
        mostrarAviso(el.escalaAviso, null);
        modais.novaEscalaModal.show();
    }

    async function submeterNovaEscala(evento) {
        evento.preventDefault();

        const nome = el.escalaNome.value.trim();
        const inicio = el.escalaInicio.value;
        const fim = el.escalaFim.value;

        if (!nome || !inicio || !fim) {
            mostrarAviso(el.escalaAviso, 'Preencha o nome e o período da escala.', 'warning');
            return;
        }
        if (fim < inicio) {
            mostrarAviso(el.escalaAviso, 'A data final tem de ser igual ou posterior à inicial.', 'warning');
            return;
        }
        if (!estado.utilizador) {
            mostrarAviso(el.escalaAviso, 'Sessão expirada. Inicie sessão novamente.', 'danger');
            return;
        }

        mostrarAviso(el.escalaAviso, null);
        ocupado(el.escalaSubmeter, true, 'A criar...');

        try {
            await SuperVisorApi.criarEscala({
                name: nome,
                initialDate: inicio,
                endDate: fim,
                createdById: estado.utilizador.id
            });
            modais.novaEscalaModal.hide();
            notificar('Escala "' + nome + '" criada em rascunho.', 'sucesso');
            await carregarEscalas();
        } catch (erro) {
            mostrarAviso(el.escalaAviso, erro.message, 'danger');
        } finally {
            ocupado(el.escalaSubmeter, false);
        }
    }

    function confirmarPublicacao(id) {
        const escala = escalaPorId(id);
        estado.escalaA_Publicar = escala;
        el.publicacaoNome.textContent = escala && escala.name ? escala.name : 'Escala #' + id;
        modais.publicacaoModal.show();
    }

    async function publicarEscala() {
        if (!estado.escalaA_Publicar) {
            return;
        }
        const nome = estado.escalaA_Publicar.name;
        ocupado(el.btnConfirmarPublicacao, true, 'A publicar...');

        try {
            await SuperVisorApi.publicarEscala(estado.escalaA_Publicar.id);
            modais.publicacaoModal.hide();
            notificar('Escala "' + nome + '" publicada.', 'sucesso');
            await carregarEscalas();
        } catch (erro) {
            modais.publicacaoModal.hide();
            notificar(erro.message, 'erro');
        } finally {
            ocupado(el.btnConfirmarPublicacao, false);
            estado.escalaA_Publicar = null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Eventos                                                             */
    /* ------------------------------------------------------------------ */

    function registarEventos() {
        el.logout.addEventListener('click', () => {
            SuperVisorApi.limparSessao();
            window.location.href = '/login';
        });

        el.btnPedirTroca.addEventListener('click', () => abrirPedidoTroca(null));
        el.btnNovaEscala.addEventListener('click', abrirNovaEscala);

        el.tabelaCorpo.addEventListener('click', (evento) => {
            const detalhes = evento.target.closest('.js-detalhes');
            const trocar = evento.target.closest('.js-trocar');
            const publicar = evento.target.closest('.js-publicar');

            if (detalhes) {
                abrirDetalhes(detalhes.dataset.id);
            } else if (trocar) {
                abrirPedidoTroca(trocar.dataset.id);
            } else if (publicar) {
                confirmarPublicacao(publicar.dataset.id);
            }
        });

        el.btnTrocarDetalhes.addEventListener('click', () => {
            const id = el.btnTrocarDetalhes.dataset.id;
            modais.detalhesModal.hide();
            abrirPedidoTroca(id);
        });

        el.trocaEscala.addEventListener('change', atualizarOpcoesTroca);
        el.trocaFormulario.addEventListener('submit', submeterTroca);
        el.escalaFormulario.addEventListener('submit', submeterNovaEscala);
        el.btnConfirmarPublicacao.addEventListener('click', publicarEscala);
    }

    async function iniciar() {
        guardarElementos();

        if (!SuperVisorApi.token()) {
            SuperVisorApi.redirecionarParaLogin();
            return;
        }

        registarEventos();
        await carregarSessao();
        await carregarEscalas();
    }

    iniciar();
});
