package com.minhaempresa.gendaz.whatsapp.service;

import com.minhaempresa.gendaz.assinatura.service.AssinaturaService;
import com.minhaempresa.gendaz.whatsapp.entity.WhatsAppUsoCicloEntity;
import com.minhaempresa.gendaz.whatsapp.enums.WhatsAppCategoriaCota;
import com.minhaempresa.gendaz.whatsapp.policy.WhatsAppPlanoPolicy;
import com.minhaempresa.gendaz.whatsapp.repository.WhatsAppUsoCicloRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dominio de cotas do WhatsApp por ciclo da assinatura.
 *
 * Fonte do plano e do ciclo: a assinatura vigente resolvida por
 * {@link AssinaturaService#buscarAtualPorEmpresa(Long)} (a mesma fonte de
 * verdade do restante do gendaz, incluindo trial). A franquia acompanha o
 * intervalo [dataInicio, dataFim) da assinatura, nunca o mes-calendario.
 * Sem assinatura vigente nao ha ciclo confiavel: nenhum registro e criado
 * e a empresa equivale ao Basico (sem WhatsApp), sem inventar datas.
 *
 * Mecanica reserva/consumo/liberacao (usada pelo motor da Fase 5):
 * antes de enviar reserva 1; enviou com sucesso converte 1 reservado em
 * 1 enviado; falhou definitivamente/cancelou libera 1 reservado sem tocar
 * em enviados. Todas as mutacoes leem o registro com lock pessimista de
 * escrita, entao reservas concorrentes nunca ocupam a mesma ultima vaga.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppQuotaService {

    private final WhatsAppUsoCicloRepository usoRepository;
    private final AssinaturaService assinaturaService;
    private final WhatsAppUsoCicloInitializer initializer;

    private record CicloVigente(LocalDate inicio, LocalDate fim, String planoNome) {
    }

    private Optional<CicloVigente> resolverCiclo(Long empresaId) {
        return assinaturaService.buscarAtualPorEmpresa(empresaId)
                .filter(a -> a.getDataInicio() != null)
                .map(a -> new CicloVigente(
                        a.getDataInicio(),
                        a.getDataFim(),
                        a.getPlano().getNome()));
    }

    @Transactional
    public WhatsAppUsoResponse consultarUso(Long empresaId) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return usoZerado(empresaId, null, null, null);
        }
        CicloVigente vigente = ciclo.get();
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(vigente.planoNome())) {
            return usoZerado(empresaId, vigente.planoNome(), vigente.inicio(), vigente.fim());
        }
        WhatsAppUsoCicloEntity uso = obterOuCriarComLock(empresaId, vigente.inicio());
        return montarResposta(empresaId, vigente, uso);
    }

    @Transactional
    public WhatsAppDisponibilidade podeReservar(Long empresaId, WhatsAppCategoriaCota categoria) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return WhatsAppDisponibilidade.PLANO_SEM_WHATSAPP;
        }
        CicloVigente vigente = ciclo.get();
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(vigente.planoNome())) {
            return WhatsAppDisponibilidade.PLANO_SEM_WHATSAPP;
        }
        WhatsAppUsoCicloEntity uso = obterOuCriarComLock(empresaId, vigente.inicio());
        return disponibilidadePara(categoria, vigente.planoNome(), uso) > 0
                ? WhatsAppDisponibilidade.DISPONIVEL
                : WhatsAppDisponibilidade.LIMITE_ATINGIDO;
    }

    @Transactional
    public WhatsAppReserva reservar(Long empresaId, WhatsAppCategoriaCota categoria) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return WhatsAppReserva.PLANO_SEM_WHATSAPP;
        }
        CicloVigente vigente = ciclo.get();
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(vigente.planoNome())) {
            return WhatsAppReserva.PLANO_SEM_WHATSAPP;
        }
        WhatsAppUsoCicloEntity uso = obterOuCriarComLock(empresaId, vigente.inicio());
        if (disponibilidadePara(categoria, vigente.planoNome(), uso) <= 0) {
            return WhatsAppReserva.LIMITE_ATINGIDO;
        }
        if (categoria == WhatsAppCategoriaCota.LEMBRETE) {
            uso.setLembretesReservados(uso.getLembretesReservados() + 1);
        } else {
            uso.setCrmReservados(uso.getCrmReservados() + 1);
        }
        usoRepository.save(uso);
        return WhatsAppReserva.RESERVADA;
    }

    @Transactional
    public void confirmarEnvio(Long empresaId, WhatsAppCategoriaCota categoria) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return;
        }
        confirmarEnvioNoCiclo(empresaId, categoria, ciclo.get().inicio());
    }

    /**
     * Reserva no ciclo vigente retornando tambem em qual ciclo foi reservada.
     * O worker grava esse ciclo na notificacao (quotaCycleStart) e confirma
     * ou libera exatamente nele, mesmo que a assinatura vire o ciclo no meio
     * dos retries.
     */
    @Transactional
    public ReservaCota reservarNoCicloAtual(Long empresaId, WhatsAppCategoriaCota categoria) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return new ReservaCota(WhatsAppReserva.PLANO_SEM_WHATSAPP, null);
        }
        CicloVigente vigente = ciclo.get();
        if (!WhatsAppPlanoPolicy.possuiWhatsApp(vigente.planoNome())) {
            return new ReservaCota(WhatsAppReserva.PLANO_SEM_WHATSAPP, null);
        }
        WhatsAppUsoCicloEntity uso = obterOuCriarComLock(empresaId, vigente.inicio());
        if (disponibilidadePara(categoria, vigente.planoNome(), uso) <= 0) {
            return new ReservaCota(WhatsAppReserva.LIMITE_ATINGIDO, null);
        }
        if (categoria == WhatsAppCategoriaCota.LEMBRETE) {
            uso.setLembretesReservados(uso.getLembretesReservados() + 1);
        } else {
            uso.setCrmReservados(uso.getCrmReservados() + 1);
        }
        usoRepository.save(uso);
        return new ReservaCota(WhatsAppReserva.RESERVADA, vigente.inicio());
    }

    /**
     * Converte exatamente 1 reservado em 1 enviado no ciclo informado
     * (o ciclo gravado na notificacao), sem resolver o ciclo atual.
     */
    @Transactional
    public void confirmarEnvioNoCiclo(Long empresaId, WhatsAppCategoriaCota categoria, LocalDate cicloInicio) {
        if (cicloInicio == null) {
            return;
        }
        Optional<WhatsAppUsoCicloEntity> atual =
                usoRepository.findByEmpresaIdAndCicloInicioForUpdate(empresaId, cicloInicio);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppUsoCicloEntity uso = atual.get();
        if (categoria == WhatsAppCategoriaCota.LEMBRETE) {
            if (uso.getLembretesReservados() <= 0) {
                return;
            }
            uso.setLembretesReservados(uso.getLembretesReservados() - 1);
            uso.setLembretesEnviados(uso.getLembretesEnviados() + 1);
        } else {
            if (uso.getCrmReservados() <= 0) {
                return;
            }
            uso.setCrmReservados(uso.getCrmReservados() - 1);
            uso.setCrmEnviados(uso.getCrmEnviados() + 1);
        }
        usoRepository.save(uso);
    }

    @Transactional
    public void liberarReserva(Long empresaId, WhatsAppCategoriaCota categoria) {
        Optional<CicloVigente> ciclo = resolverCiclo(empresaId);
        if (ciclo.isEmpty()) {
            return;
        }
        liberarReservaNoCiclo(empresaId, categoria, ciclo.get().inicio());
    }

    /**
     * Libera exatamente 1 reservado no ciclo informado (o ciclo gravado na
     * notificacao), sem tocar em enviados e sem resolver o ciclo atual.
     */
    @Transactional
    public void liberarReservaNoCiclo(Long empresaId, WhatsAppCategoriaCota categoria, LocalDate cicloInicio) {
        if (cicloInicio == null) {
            return;
        }
        Optional<WhatsAppUsoCicloEntity> atual =
                usoRepository.findByEmpresaIdAndCicloInicioForUpdate(empresaId, cicloInicio);
        if (atual.isEmpty()) {
            return;
        }
        WhatsAppUsoCicloEntity uso = atual.get();
        if (categoria == WhatsAppCategoriaCota.LEMBRETE) {
            if (uso.getLembretesReservados() <= 0) {
                return;
            }
            uso.setLembretesReservados(uso.getLembretesReservados() - 1);
        } else {
            if (uso.getCrmReservados() <= 0) {
                return;
            }
            uso.setCrmReservados(uso.getCrmReservados() - 1);
        }
        usoRepository.save(uso);
    }

    private WhatsAppUsoCicloEntity obterOuCriarComLock(Long empresaId, LocalDate cicloInicio) {
        Optional<WhatsAppUsoCicloEntity> atual =
                usoRepository.findByEmpresaIdAndCicloInicioForUpdate(empresaId, cicloInicio);
        if (atual.isPresent()) {
            return atual.get();
        }
        try {
            // Insercao em transacao propria: se outra transacao venceu a
            // corrida, a UNIQUE (empresa, ciclo) protege a duplicidade e so a
            // transacao interna sofre rollback. Sem retry: uma releitura basta.
            initializer.inicializar(empresaId, cicloInicio);
        } catch (DataIntegrityViolationException corrida) {
            // Outra transacao criou o registro do ciclo primeiro; segue para
            // a releitura com lock abaixo, na transacao corrente intacta.
        }
        return usoRepository.findByEmpresaIdAndCicloInicioForUpdate(empresaId, cicloInicio)
                .orElseThrow(() -> new IllegalStateException(
                        "Registro de uso do ciclo WhatsApp nao foi criado."));
    }

    private int disponibilidadePara(
            WhatsAppCategoriaCota categoria, String planoNome, WhatsAppUsoCicloEntity uso) {
        int limite = WhatsAppPlanoPolicy.limitePara(categoria, planoNome);
        if (categoria == WhatsAppCategoriaCota.LEMBRETE) {
            return limite - uso.getLembretesEnviados() - uso.getLembretesReservados();
        }
        return limite - uso.getCrmEnviados() - uso.getCrmReservados();
    }

    private WhatsAppUsoResponse montarResposta(
            Long empresaId, CicloVigente vigente, WhatsAppUsoCicloEntity uso) {
        WhatsAppPlanoPolicy.Limites limites = WhatsAppPlanoPolicy.limitesPara(vigente.planoNome());
        return new WhatsAppUsoResponse(
                empresaId,
                vigente.planoNome(),
                vigente.inicio(),
                vigente.fim(),
                limites.lembretes(),
                limites.crm(),
                uso.getLembretesReservados(),
                uso.getLembretesEnviados(),
                uso.getCrmReservados(),
                uso.getCrmEnviados(),
                Math.max(0, limites.lembretes() - uso.getLembretesEnviados() - uso.getLembretesReservados()),
                Math.max(0, limites.crm() - uso.getCrmEnviados() - uso.getCrmReservados()));
    }

    private WhatsAppUsoResponse usoZerado(
            Long empresaId, String planoNome, LocalDate cicloInicio, LocalDate cicloFim) {
        return new WhatsAppUsoResponse(
                empresaId, planoNome, cicloInicio, cicloFim,
                0, 0, 0, 0, 0, 0, 0, 0);
    }
}
