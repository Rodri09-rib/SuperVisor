/**
 * Escala de presencialidade / home office.
 *
 * <p>A grelha é montada a partir das células que a API devolve, e não a partir
 * de um patrón gerado no browser. A diferença importa: o padrão alterna pela
 * semana ISO, e reproduzir essa regra em JavaScript dava dois lugares onde o
 * mesmo dia podia aparecer como presencial num e como home office no outro.
 * Aqui o browser só desenha o que o servidor já decidiu — e quando uma célula
 * não existe, a grelha mostra um traço em vez de assumir um valor.
 */
(() => {

    const el = {};
    let segunda = null;
    let celulas = [];
    let modal = null;

    const DIAS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta'];

    /* ------------------------------------------------------------------ */
    /* Datas                                                               */
    /* ------------------------------------------------------------------ */

    /** Segunda-feira da semana ISO a que a data pertence. */
    function inicioDaSemanaIso(data) {
        const dia = data.getDay();
        // getDay() devolve 0 no domingo, e a semana ISO começa na segunda:
        // a diferença é 1, não 0, senão o domingo caía na semana seguinte.
        const difereca = dia === 0 ? 1 : dia - 1;
        const inicio = new Date(data);
        inicio.setDate(data.getDate() - difereca);
        return inicio;
    }

    function iso(d) {
        return d.toISOString().slice(0, 10);
    }

    function somarDias(data, dias) {
        const novo = new Date(data);
        novo.setDate(novo.getDate() + dias);
        return novo;
    }

    /* ------------------------------------------------------------------ */
    /* Desenho                                                             */
    /* ------------------------------------------------------------------ */

    function desenharCabecalho() {
        let html = '<tr><th scope="col" class="grelha-celula">'
            + '<i class="bi bi-person"></i> Pessoa</th>';

        for (let i = 0; i < DIAS.length; i++) {
            const dia = somarDias(segunda, i);
            html += '<th scope="col" class="grelha-celula">'
                + '<div class="small fw-bold">' + DIAS[i] + '</div>'
                + '<div class="text-muted small fw-normal">'
                + SuperVisorFormat.escapar(SuperVisorFormat.data(iso(dia))) + '</div>'
                + '</th>';
        }

        el.cabecalho.innerHTML = html;
    }

    function desenharResumo() {
        if (!celulas.length) {
            el.resumo.textContent = 'Sem escala gerada para esta semana.';
            return;
        }

        const pessoas = new Set(celulas.map((celula) => celula.userId));
        const semana = celulas[0].weekNumber;
        const paridade = celulas[0].weekOdd ? 'ímpar' : 'par';

        // A paridade vai escrita na página porque é ela que explica a escala:
        // sem isso, quem olha para duas semanas seguidas vê dois padrões
        // aparentemente contraditórios e não tem forma de saber que a regra é a
        // alternância.
        el.resumo.textContent = 'Semana ISO ' + semana + ' (' + paridade
            + ') · ' + pessoas.size + (pessoas.size === 1 ? ' pessoa' : ' pessoas');
    }

    function desenharCorpo() {
        if (!celulas.length) {
            el.corpo.innerHTML = '<tr><td colspan="' + (DIAS.length + 1)
                + '" class="text-center py-5 text-muted">'
                + 'Ainda não há escala para esta semana.</td></tr>';
            return;
        }

        // Uma linha por pessoa, pela ordem em que a API devolveu — que já vem
        // ordenada por nome. Reordenar aqui seria trabalho a mais para o
        // mesmo resultado.
        const porPessoa = new Map();
        celulas.forEach((celula) => {
            if (!porPessoa.has(celula.userId)) {
                porPessoa.set(celula.userId, {
                    nome: celula.userName,
                    equipa: celula.teamGroup,
                    dias: {}
                });
            }
            porPessoa.get(celula.userId).dias[celula.date] = celula.modality;
        });

        let html = '';
        porPessoa.forEach((pessoa) => {
            html += '<tr>'
                + '<th scope="row" class="grelha-celula">'
                + '<div class="fw-semibold">'
                + SuperVisorFormat.escapar(pessoa.nome || '—') + '</div>'
                + '<small class="text-muted">'
                + SuperVisorFormat.escapar(SuperVisorFormat.rotuloEquipa(pessoa.equipa))
                + '</small></th>';

            for (let i = 0; i < DIAS.length; i++) {
                const chave = iso(somarDias(segunda, i));
                const modalidade = pessoa.dias[chave];

                // Sem célula, traço. Preencher com o valor que o padrão mandaria
                // seria inventar uma escala que o servidor não gravou, e o
                // usuário acabaria por trabalhar a partir de uma grelha que
                // ninguém confirmou.
                html += '<td class="text-center">'
                    + (modalidade
                        ? SuperVisorFormat.badgeModalidade(modalidade)
                        : '<span class="text-muted">—</span>')
                    + '</td>';
            }

            html += '</tr>';
        });

        el.corpo.innerHTML = html;
    }

    function desenhar() {
        desenharCabecalho();
        desenharCorpo();
        desenharResumo();
    }

    /* ------------------------------------------------------------------ */
    /* Dados                                                               */
    /* ------------------------------------------------------------------ */

    async function carregar() {
        const inicio = iso(segunda);
        const fim = iso(somarDias(segunda, DIAS.length - 1));

        el.corpo.innerHTML = '<tr><td colspan="' + (DIAS.length + 1)
            + '" class="text-center py-5 text-muted">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">Carregando a escala...</span></div>'
            + '<div class="mt-2">Carregando a escala...</div></td></tr>';

        try {
            celulas = (await SuperVisorApi.listarEscalaWorkModality(inicio, fim)) || [];
            desenhar();
        } catch (erro) {
            celulas = [];
            el.resumo.textContent = '';
            el.corpo.innerHTML = '<tr><td colspan="' + (DIAS.length + 1)
                + '" class="text-center py-5 text-danger">'
                + SuperVisorFormat.escapar(erro.message) + '</td></tr>';
        }
    }

    /**
     * O botão de gerar só aparece a um supervisor.
     *
     * <p>É a mesma regra do servidor,chega aqui por conveniência visual: escondê-lo
     * evita um clique que só ia devolver 403. Quem o manipulate pela console
     * continua a ser recusado pelo serviço.
     */
    async function carregarPerfil() {
        try {
            const utilizador = await SuperVisorApi.utilizadorAtual();
            const ehSupervisor = utilizador && utilizador.profile === 'SUPERVISOR';
            el.btnGerar.classList.toggle('d-none', !ehSupervisor);
        } catch (erro) {
            console.warn('Perfil indisponível:', erro);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Geração                                                             */
    /* ------------------------------------------------------------------ */

    function abrirGerar() {
        el.gerarAviso.classList.add('d-none');
        el.gerarTexto.textContent = celulas.length
            ? 'A escala desta semana já existe e vai ser reescrita. As modalidades mudam conforme a paridade da semana.'
            : 'A escala desta semana vai ser gerada para quem tem equipe atribuída.';
        modal.show();
    }

    async function gerar() {
        el.btnConfirmar.disabled = true;
        try {
            celulas = (await SuperVisorApi.gerarEscalaWorkModality(iso(segunda))) || [];
            modal.hide();
            desenhar();
        } catch (erro) {
            el.gerarAviso.textContent = erro.message;
            el.gerarAviso.className = 'alert alert-danger';
        } finally {
            el.btnConfirmar.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Arranque                                                            */
    /* ------------------------------------------------------------------ */

    function guardarElementos() {
        el.cabecalho = document.getElementById('grelhaCabecalho');
        el.corpo = document.getElementById('grelhaCorpo');
        el.resumo = document.getElementById('resumoSemana');
        el.btnGerar = document.getElementById('btnGerarEscala');
        el.btnAnterior = document.getElementById('btnSemanaAnterior');
        el.btnActual = document.getElementById('btnSemanaActual');
        el.btnSeguinte = document.getElementById('btnSemanaSeguinte');
        el.gerarAviso = document.getElementById('gerarAviso');
        el.gerarTexto = document.getElementById('gerarTexto');
        el.btnConfirmar = document.getElementById('btnConfirmarGerar');
    }

    function registarEventos() {
        el.btnAnterior.addEventListener('click', () => {
            segunda = somarDias(segunda, -7);
            carregar();
        });

        el.btnSeguinte.addEventListener('click', () => {
            segunda = somarDias(segunda, 7);
            carregar();
        });

        el.btnActual.addEventListener('click', () => {
            segunda = inicioDaSemanaIso(new Date());
            carregar();
        });

        el.btnGerar.addEventListener('click', abrirGerar);
        el.btnConfirmar.addEventListener('click', gerar);
    }

    function iniciar() {
        guardarElementos();
        registarEventos();
        segunda = inicioDaSemanaIso(new Date());
        modal = new bootstrap.Modal(document.getElementById('gerarModal'));
        carregarPerfil();
        carregar();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
