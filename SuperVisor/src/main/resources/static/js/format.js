/**
 * Formatação e composição de elementos para o dashboard.
 *
 * Todas as funções de texto devolvem HTML já escapado: os nomes das escalas e
 * os motivos são livres e não podem ser interpolados sem isso.
 */
const SuperVisorFormat = (() => {

    const ESTADOS_ESCALA = {
        DRAFT: { rotulo: 'Rascunho', classe: 'bg-warning text-dark' },
        RASCUNHO: { rotulo: 'Rascunho', classe: 'bg-warning text-dark' },
        PUBLISHED: { rotulo: 'Publicada', classe: 'bg-success' },
        PUBLICADA: { rotulo: 'Publicada', classe: 'bg-success' },
        COMPLETED: { rotulo: 'Concluída', classe: 'bg-secondary' },
        CLOSED: { rotulo: 'Fechada', classe: 'bg-secondary' },
        FECHADA: { rotulo: 'Fechada', classe: 'bg-secondary' }
    };

    const ESTADO_SEM_VALOR = { rotulo: 'Sem estado', classe: 'bg-light text-dark border' };

    const ESTADOS_ALOCACAO = {
        PENDING: { rotulo: 'Pendente', classe: 'bg-warning text-dark' },
        ACCEPTED: { rotulo: 'Aceite', classe: 'bg-success' },
        REJECTED: { rotulo: 'Rejeitado', classe: 'bg-danger' }
    };

    const ESTADO_ALOCACAO_SEM_VALOR = { rotulo: 'Pendente', classe: 'bg-warning text-dark' };

    function escapar(valor) {
        if (valor === null || valor === undefined) {
            return '';
        }
        return String(valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** Converte "2025-10-01" (ISO, como o Jackson envia) em "01/10/2025". */
    function data(valor) {
        if (!valor) {
            return '—';
        }
        const partes = String(valor).slice(0, 10).split('-');
        if (partes.length !== 3) {
            return escapar(valor);
        }
        return partes[2] + '/' + partes[1] + '/' + partes[0];
    }

    function periodo(inicio, fim) {
        if (!inicio && !fim) {
            return '—';
        }
        return data(inicio) + ' a ' + data(fim);
    }

    /** "08:00:00" (LocalTime) passa a "08h00". */
    function hora(valor) {
        if (!valor) {
            return null;
        }
        const partes = String(valor).split(':');
        if (partes.length < 2) {
            return escapar(valor);
        }
        return partes[0] + 'h' + partes[1];
    }

    function estadoEscala(valor) {
        return ESTADOS_ESCALA[valor] || ESTADO_SEM_VALOR;
    }

    function estadoAlocacao(valor) {
        return ESTADOS_ALOCACAO[valor] || ESTADO_ALOCACAO_SEM_VALOR;
    }

    function badgeEscala(valor) {
        const estado = estadoEscala(valor);
        return '<span class="badge ' + estado.classe + '">' + escapar(estado.rotulo) + '</span>';
    }

    function badgeAlocacao(valor) {
        const estado = estadoAlocacao(valor);
        return '<span class="badge ' + estado.classe + '">' + escapar(estado.rotulo) + '</span>';
    }

    /** Texto legível de uma alocação, usado nos <option> e nas listas. */
    function rotuloAlocacao(alocacao) {
        const partes = [];
        const turno = alocacao.shiftAcronym
            ? alocacao.shiftAcronym
            : 'Turno por atribuir';

        partes.push(turno);

        if (alocacao.shiftDayOfTheWeek) {
            partes[partes.length - 1] += ' (' + alocacao.shiftDayOfTheWeek + ')';
        }

        const inicio = hora(alocacao.shiftStartTime);
        const fim = hora(alocacao.shiftEndTime);
        if (inicio && fim) {
            partes[partes.length - 1] += ' ' + inicio + '–' + fim;
        }

        if (alocacao.specificDate) {
            partes.unshift(data(alocacao.specificDate));
        }

        partes.push('#' + alocacao.id);
        return partes.join(' · ');
    }

    return {
        escapar: escapar,
        data: data,
        periodo: periodo,
        hora: hora,
        estadoEscala: estadoEscala,
        estadoAlocacao: estadoAlocacao,
        badgeEscala: badgeEscala,
        badgeAlocacao: badgeAlocacao,
        rotuloAlocacao: rotuloAlocacao
    };
})();
