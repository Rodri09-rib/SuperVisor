/**
 * Histórico de pedidos de troca.
 *
 * <p>A página tem duas responsabilidades que não podem misturar-se: mostrar o
 * histórico, e permitir responder ao que está por responder. Quem pode responder
 * é o colega que vai receber o turno, ou um supervisor — e essa regra está no
 * servidor. Aqui os botões aparecem só nos pedidos que ainda estão pendentes
 * para não haver um botão que devolve 403; mas o botão aparecer não é o que
 * autoriza, e o `try/catch` em volta da resposta existe porque um pedido pode
 * deixar de estar pendente entre a lista ser desenhada e o clique acontecer.
 */
(() => {

    const el = {};
    let pedidos = [];
    let pedidoEmResposta = null;
    let modal = null;

    /* ------------------------------------------------------------------ */
    /* Desenho                                                             */
    /* ------------------------------------------------------------------ */

    function linhaVazia(colspan, mensagem) {
        return '<tr><td colspan="' + colspan + '" class="text-center py-5 text-muted">'
            + SuperVisorFormat.escapar(mensagem) + '</td></tr>';
    }

    /**
     * Célula de uma das pontas da troca: quem é, que turno é, e em que dia
     * cai. O turno sem a data não dizia nada de útil — `T1_SAB` é igual para
     * todos os sábados da escala.
     */
    function celulaTurno(nome, turno, data) {
        return '<td><div class="fw-semibold">' + SuperVisorFormat.escapar(nome || '—')
            + '</div><small class="text-muted d-block">'
            + SuperVisorFormat.escapar(turno || 'Turno') + ' · '
            + SuperVisorFormat.escapar(SuperVisorFormat.data(data)) + '</small></td>';
    }

    function botaoResposta(pedido) {
        if (pedido.status !== 'PENDING') {
            return '';
        }
        return '<button class="btn btn-sm btn-outline-primary js-responder" data-id="'
            + pedido.id + '">Responder</button>';
    }

    function desenhar() {
        if (!pedidos.length) {
            el.corpo.innerHTML = linhaVazia(7,
                'Não há pedidos de troca para estes filtros.');
            return;
        }

        el.corpo.innerHTML = pedidos.map((pedido) => {
            const resposta = pedido.approvalDate
                ? SuperVisorFormat.dataHora(pedido.approvalDate)
                : '—';

            return '<tr>'
                + '<td>#' + pedido.id + '</td>'
                + celulaTurno(pedido.requesterName, pedido.originalShift, pedido.originalShiftDate)
                + celulaTurno(pedido.requestedName, pedido.targetShift, pedido.targetShiftDate)
                + '<td>' + SuperVisorFormat.escapar(
                    SuperVisorFormat.dataHora(pedido.requestDate)) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(resposta) + '</td>'
                + '<td>' + SuperVisorFormat.badgeTroca(pedido.status) + '</td>'
                + '<td class="text-end">' + botaoResposta(pedido) + '</td>'
                + '</tr>';
        }).join('');
    }

    function mostrarErro(mensagem) {
        el.corpo.innerHTML = linhaVazia(7, mensagem);
    }

    /* ------------------------------------------------------------------ */
    /* Dados                                                               */
    /* ------------------------------------------------------------------ */

    function filtrosAtivos() {
        return {
            status: el.status.value || null,
            userId: el.utilizador.value || null,
            dataInicial: el.dataInicial.value || null,
            dataFim: el.dataFim.value || null
        };
    }

    async function carregar() {
        el.corpo.innerHTML = '<tr><td colspan="7" class="text-center py-5 text-muted">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar trocas...</span></div>'
            + '<div class="mt-2">A carregar trocas...</div></td></tr>';

        try {
            pedidos = (await SuperVisorApi.listarTrocas(filtrosAtivos())) || [];
            desenhar();
        } catch (erro) {
            mostrarErro(erro.message);
        }
    }

    /**
     * Opções do filtro de pessoa.
     *
     * <p>Só para quem tem utilizador visível. A lista vem de `/users`, que é
     * autenticado mas não é de supervisão, e uma falha ali não pode impedir a
     * página de mostrar o histórico: o filtro de pessoa é uma conveniência, e
     * a lista de trocas não depende dele.
     */
    async function carregarUtilizadores() {
        try {
            const utilizadores = (await SuperVisorApi.listarUtilizadores()) || [];
            utilizadores.forEach((utilizador) => {
                const opcao = document.createElement('option');
                opcao.value = utilizador.id;
                opcao.textContent = utilizador.name || utilizador.email;
                el.utilizador.appendChild(opcao);
            });
        } catch (erro) {
            console.warn('Lista de utilizadores indisponível para o filtro:', erro);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Resposta                                                            */
    /* ------------------------------------------------------------------ */

    function abrirResposta(pedido) {
        pedidoEmResposta = pedido;

        el.respostaResumo.innerHTML = '<p class="mb-0">'
            + SuperVisorFormat.escapar(pedido.requesterName || 'Alguém')
            + ' cede <strong>'
            + SuperVisorFormat.escapar(pedido.originalShift || 'o turno')
            + '</strong> e fica com <strong>'
            + SuperVisorFormat.escapar(pedido.targetShift || 'o outro turno')
            + '</strong>.</p>'
            + '<p class="text-muted small mb-0">Aceitar move as duas alocações de dono. '
            + 'Não dá para desfazer depois.</p>';

        el.respostaAviso.classList.add('d-none');
        modal.show();
    }

    async function responder(aceitar) {
        if (!pedidoEmResposta) {
            return;
        }

        [el.btnAprovar, el.btnRecusar].forEach((botao) => {
            botao.disabled = true;
        });

        try {
            await SuperVisorApi.responderTroca(pedidoEmResposta.id, aceitar);
            modal.hide();
            await carregar();
        } catch (erro) {
            // O pedido pode já ter sido respondido por outra pessoa entre a
            // lista ser desenhada e o clique. O aviso explica o que aconteceu
            // e a lista é recarregada, em vez de se ficar com um botão que
            // volta a falhar.
            el.respostaAviso.textContent = erro.message;
            el.respostaAviso.className = 'alert alert-danger';
            await carregar();
        } finally {
            [el.btnAprovar, el.btnRecusar].forEach((botao) => {
                botao.disabled = false;
            });
        }
    }

    /* ------------------------------------------------------------------ */
    /* Arranque                                                            */
    /* ------------------------------------------------------------------ */

    function guardarElementos() {
        el.corpo = document.getElementById('trocasCorpo');
        el.status = document.getElementById('filtroStatus');
        el.utilizador = document.getElementById('filtroUtilizador');
        el.dataInicial = document.getElementById('filtroDataInicial');
        el.dataFim = document.getElementById('filtroDataFim');
        el.formulario = document.getElementById('filtrosForm');
        el.btnLimpar = document.getElementById('btnLimparFiltros');
        el.respostaAviso = document.getElementById('respostaAviso');
        el.respostaResumo = document.getElementById('respostaResumo');
        el.btnAprovar = document.getElementById('btnAprovarTroca');
        el.btnRecusar = document.getElementById('btnRecusarTroca');
    }

    function registarEventos() {
        el.formulario.addEventListener('submit', (evento) => {
            evento.preventDefault();
            carregar();
        });

        el.btnLimpar.addEventListener('click', () => {
            [el.status, el.utilizador, el.dataInicial, el.dataFim]
                .forEach((campo) => {
                    campo.value = '';
                });
            carregar();
        });

        el.corpo.addEventListener('click', (evento) => {
            const botao = evento.target.closest('.js-responder');
            if (!botao) {
                return;
            }
            const pedido = pedidos.find((item) => String(item.id) === botao.dataset.id);
            if (pedido) {
                abrirResposta(pedido);
            }
        });

        el.btnAprovar.addEventListener('click', () => responder(true));
        el.btnRecusar.addEventListener('click', () => responder(false));
    }

    function iniciar() {
        guardarElementos();
        registarEventos();
        modal = new bootstrap.Modal(document.getElementById('respostaModal'));
        carregarUtilizadores();
        carregar();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
