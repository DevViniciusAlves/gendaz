package com.minhaempresa.gendaz.agendamento.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.minhaempresa.gendaz.agendamento.enums.StatusAgendamento;
import com.minhaempresa.gendaz.shared.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * Fonte unica de verdade: matriz completa de transicoes permitidas e
 * bloqueadas, sem mocks e sem banco.
 */
class TransicaoStatusAgendamentoTest {

    @Test
    void pendentePermiteConfirmarECancelarEMantemSe() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirConfirmacao(StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamento(StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamento(StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(StatusAgendamento.PENDENTE, StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(StatusAgendamento.PENDENTE, StatusAgendamento.CANCELADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(StatusAgendamento.CONFIRMADO, StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(StatusAgendamento.CONFIRMADO, StatusAgendamento.CANCELADO));
        assertEquals(StatusAgendamento.PENDENTE,
                TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.PENDENTE));
        assertEquals(StatusAgendamento.PENDENTE,
                TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.CONFIRMADO));
    }

    @Test
    void pendenteNaoFinalizaNemPausaPeloEditar() {
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.PENDENTE, StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.PENDENTE, StatusAgendamento.EM_ATENDIMENTO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.PENDENTE, StatusAgendamento.PAUSADO));
        assertDoesNotThrow(
                () -> TransicaoStatusAgendamento.exigirInicio(StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.PENDENTE));
    }

    @Test
    void confirmadoFluxo() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirInicio(StatusAgendamento.CONFIRMADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CONFIRMADO, StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CONFIRMADO, StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.CONFIRMADO));
    }

    @Test
    void emAtendimentoFluxo() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirPausa(StatusAgendamento.EM_ATENDIMENTO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.EM_ATENDIMENTO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.PAUSADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirRetomada(StatusAgendamento.PAUSADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.CONFIRMADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.EM_ATENDIMENTO, StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.PAUSADO, StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.PAUSADO, StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamento(StatusAgendamento.EM_ATENDIMENTO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.EM_ATENDIMENTO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.PAUSADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirInicio(StatusAgendamento.PAUSADO));
    }

    @Test
    void exclusaoPermitePendenteConfirmadoCanceladoEmAndamentoEFinalizadoEBloqueiaPausado() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.CANCELADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.EM_ATENDIMENTO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.PAUSADO));
    }

    @Test
    void finalizadoSoSaiViaReabrir() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirReabertura(StatusAgendamento.FINALIZADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.FINALIZADO, StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.FINALIZADO, StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirConfirmacao(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamento(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirInicio(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirPausa(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirRetomada(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.FINALIZADO));
        // Exclusao de FINALIZADO e soft delete (mantem o status, apenas
        // excluidoAgenda = true): nao e saida de estado via reabrir.
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirReabertura(StatusAgendamento.PENDENTE));
    }

    @Test
    void cancelamentoOperacionalPermiteEmAtendimentoEPausado() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.EM_ATENDIMENTO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.PAUSADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.FINALIZADO));
    }

    @Test
    void cancelamentoClienteMantemSelfServiceRestritivo() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.PENDENTE));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.CONFIRMADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.EM_ATENDIMENTO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.PAUSADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamentoCliente(StatusAgendamento.FINALIZADO));
    }

    @Test
    void canceladoTerminalParaTodosDestinosOperacionais() {
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CANCELADO, StatusAgendamento.CONFIRMADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CANCELADO, StatusAgendamento.EM_ATENDIMENTO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CANCELADO, StatusAgendamento.FINALIZADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirInicio(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirRetomada(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirCancelamentoOperacional(StatusAgendamento.FINALIZADO));
    }

    @Test
    void canceladoETerminalMasRecancelarEIdempotente() {
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirCancelamento(
                StatusAgendamento.CANCELADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CANCELADO, StatusAgendamento.CANCELADO));
        assertDoesNotThrow(() -> TransicaoStatusAgendamento.exigirExclusao(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class, () -> TransicaoStatusAgendamento.exigirEdicaoStatus(
                StatusAgendamento.CANCELADO, StatusAgendamento.PENDENTE));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirConfirmacao(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirFinalizacao(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.destinoReagendamento(StatusAgendamento.CANCELADO));
        assertThrows(BusinessException.class,
                () -> TransicaoStatusAgendamento.exigirReabertura(StatusAgendamento.CANCELADO));
    }
}
