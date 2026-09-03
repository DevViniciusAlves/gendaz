package com.minhaempresa.gendaz.agendamento.service;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.shared.BusinessException;

/**
 * Fonte unica de verdade para as transicoes de {@link StatusAgendamento}.
 *
 * <p>Maquina de estados oficial:
 *
 * <pre>
 * PENDENTE ------> CONFIRMADO --\
 *    |  \              |         \
 *    |   \--> CANCELADO|          v
 *    |                \|    EM_ATENDIMENTO --\
 *    v                 v      ^  |  ^         \
 * PENDENTE            ...     |  v  |      PAUSADO
 * (reagendar)                 | PAUSADO     ^  |
 *                             |  ^  |      |  v
 *                             v  |  v      | FINALIZADO
 *                         FINALIZADO |      (finalizar)
 *                             |      |
 *                             | reabrir (acao explicita)
 *                             v      |
 *                       EM_ATENDIMENTO
 * </pre>
 *
 * <p>Regras resumidas:
 * <ul>
 *   <li>FINALIZADO so sai via REABRIR, e somente para EM_ATENDIMENTO.</li>
 *   <li>CANCELADO e terminal (sem reativacao generica).</li>
 *   <li>FINALIZADO so e alcancado via finalizar(), partindo de
 *       EM_ATENDIMENTO ou PAUSADO.</li>
 *   <li>Reagendar: PENDENTE mantem PENDENTE; CONFIRMADO volta a PENDENTE.</li>
 *   <li>Editar: apenas transicoes simples autorizadas; acoes especiais usam
 *       metodos proprios (iniciar, pausar, retomar, finalizar, reabrir,
 *       cancelar).</li>
 * </ul>
 *
 * <p>Java puro, sem dependencias externas. Todo caminho (individual, bulk,
 * Meu Gendaz, edicao) deve passar por aqui; nenhum {@code setStatus} de
 * agendamento pode existir fora de uma transicao validada por esta classe.
 */
public final class TransicaoStatusAgendamento {

    private TransicaoStatusAgendamento() {}

    /** PENDENTE -> CONFIRMADO (acao confirmar). */
    public static void exigirConfirmacao(StatusAgendamento atual) {
        if (atual == StatusAgendamento.PENDENTE) {
            return;
        }
        if (atual == StatusAgendamento.CONFIRMADO) {
            throw new BusinessException("Agendamento ja confirmado.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ser confirmado.");
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser confirmado.");
        }
        throw new BusinessException("Somente agendamentos pendentes podem ser confirmados.");
    }

    /** CONFIRMADO ou PENDENTE -> EM_ATENDIMENTO (acao iniciar). */
    public static void exigirInicio(StatusAgendamento atual) {
        if (atual == StatusAgendamento.CONFIRMADO || atual == StatusAgendamento.PENDENTE) {
            return;
        }
        if (atual == StatusAgendamento.EM_ATENDIMENTO) {
            throw new BusinessException("Atendimento ja iniciado.");
        }
        if (atual == StatusAgendamento.PAUSADO) {
            throw new BusinessException("Atendimento pausado deve ser retomado pela acao Retomar.");
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser iniciado. Para corrigir uma finalizacao, utilize Reabrir atendimento.");
        }
        throw new BusinessException("Agendamento cancelado nao pode ser iniciado.");
    }

    /** EM_ATENDIMENTO -> PAUSADO (acao pausar). */
    public static void exigirPausa(StatusAgendamento atual) {
        if (atual == StatusAgendamento.EM_ATENDIMENTO) {
            return;
        }
        if (atual == StatusAgendamento.PAUSADO) {
            throw new BusinessException("Atendimento ja pausado.");
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser pausado.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ser pausado.");
        }
        throw new BusinessException("Apenas agendamentos em atendimento podem ser pausados.");
    }

    /** PAUSADO -> EM_ATENDIMENTO (acao retomar). */
    public static void exigirRetomada(StatusAgendamento atual) {
        if (atual == StatusAgendamento.PAUSADO) {
            return;
        }
        if (atual == StatusAgendamento.EM_ATENDIMENTO) {
            throw new BusinessException("Atendimento ja esta em andamento.");
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser retomado. Para corrigir uma finalizacao, utilize Reabrir atendimento.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ser retomado.");
        }
        throw new BusinessException("Apenas atendimentos pausados podem ser retomados. Para iniciar, use a ação Iniciar Atendimento.");
    }

    /** EM_ATENDIMENTO ou PAUSADO -> FINALIZADO (acao finalizar). */
    public static void exigirFinalizacao(StatusAgendamento atual) {
        if (atual == StatusAgendamento.EM_ATENDIMENTO || atual == StatusAgendamento.PAUSADO) {
            return;
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento ja finalizado.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ser finalizado.");
        }
        if (atual == StatusAgendamento.PENDENTE) {
            throw new BusinessException("Inicie o atendimento antes de finalizar.");
        }
        throw new BusinessException("Inicie o atendimento antes de finalizar.");
    }

    /** PENDENTE ou CONFIRMADO -> CANCELADO (acao cancelar). */
    public static void exigirCancelamento(StatusAgendamento atual) {
        if (atual == StatusAgendamento.PENDENTE || atual == StatusAgendamento.CONFIRMADO) {
            return;
        }
        if (atual == StatusAgendamento.CANCELADO) {
            // Repeticao idempotente: recancelar nao muda estado nem dinheiro
            // (a regularizacao do pagamento pendente tambem e idempotente).
            // Seguro para duplo clique, retry e bulk.
            return;
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser cancelado.");
        }
        throw new BusinessException("Agendamento em atendimento nao pode ser cancelado por esta acao.");
    }

    /**
     * Destino do reagendamento a partir do estado atual.
     * PENDENTE mantem PENDENTE; CONFIRMADO volta a PENDENTE (a confirmacao
     * anterior perde a validade com a mudanca de data/horario).
     */
    public static StatusAgendamento destinoReagendamento(StatusAgendamento atual) {
        if (atual == StatusAgendamento.PENDENTE || atual == StatusAgendamento.CONFIRMADO) {
            return StatusAgendamento.PENDENTE;
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ser reagendado.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ser reagendado.");
        }
        throw new BusinessException("Agendamento em atendimento nao pode ser reagendado.");
    }

    /** FINALIZADO -> EM_ATENDIMENTO (acao explicita reabrir). */
    public static void exigirReabertura(StatusAgendamento atual) {
        if (atual == StatusAgendamento.FINALIZADO) {
            return;
        }
        throw new BusinessException("Somente agendamentos finalizados podem ser reabertos.");
    }

    /**
     * Exclusao operacional: permitida para PENDENTE, CONFIRMADO e CANCELADO
     * (este ultimo idempotente). Bloqueada para EM_ATENDIMENTO, PAUSADO e
     * FINALIZADO — excluir nao pode ser porta alternativa para uma
     * transicao que a maquina proibiu (ex.: EM_ATENDIMENTO -> CANCELADO).
     */
    public static void exigirExclusao(StatusAgendamento atual) {
        if (atual == StatusAgendamento.PENDENTE
                || atual == StatusAgendamento.CONFIRMADO
                || atual == StatusAgendamento.CANCELADO) {
            return;
        }
        if (atual == StatusAgendamento.EM_ATENDIMENTO) {
            throw new BusinessException("Um atendimento em andamento nao pode ser excluido.");
        }
        if (atual == StatusAgendamento.PAUSADO) {
            throw new BusinessException("Um atendimento pausado nao pode ser excluido.");
        }
        throw new BusinessException("Agendamentos finalizados fazem parte do historico e nao podem ser excluidos.");
    }

    /**
     * Transicao de status pelo modal de edicao generico.
     * PENDENTE: PENDENTE, CONFIRMADO, CANCELADO.
     * CONFIRMADO: CONFIRMADO, CANCELADO.
     * Demais estados: sem alteracao pelo editar (reflexivo); acoes especiais
     * usam metodos proprios (iniciar, pausar, retomar, finalizar, reabrir,
     * cancelar).
     */
    public static void exigirEdicaoStatus(StatusAgendamento atual, StatusAgendamento novo) {
        if (novo == null) {
            throw new BusinessException("Status invalido.");
        }
        if (novo == atual) {
            return;
        }
        if (atual == StatusAgendamento.PENDENTE
                && (novo == StatusAgendamento.CONFIRMADO || novo == StatusAgendamento.CANCELADO)) {
            return;
        }
        if (atual == StatusAgendamento.CONFIRMADO && novo == StatusAgendamento.CANCELADO) {
            return;
        }
        if (novo == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Para finalizar um atendimento, utilize a acao Finalizar informando o pagamento.");
        }
        if (atual == StatusAgendamento.FINALIZADO) {
            throw new BusinessException("Agendamento finalizado nao pode ter o status alterado. Para corrigir uma finalizacao, utilize Reabrir atendimento.");
        }
        if (atual == StatusAgendamento.CANCELADO) {
            throw new BusinessException("Agendamento cancelado nao pode ter o status alterado.");
        }
        throw new BusinessException("Transicao de status invalida. Utilize a acao especifica (iniciar, pausar, retomar, finalizar ou cancelar).");
    }
}
