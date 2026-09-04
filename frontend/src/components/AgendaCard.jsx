import { MoreVertical, Loader } from 'lucide-react'
import { useState } from 'react'

function formatarData(dataStr) {
  if (!dataStr) return ''
  const parts = dataStr.split('-')
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`
  return dataStr
}

function obterHoraFim(agendamento) {
  if (agendamento.horaFim) return agendamento.horaFim
  if (!agendamento.horaInicio) return ''
  const [h, m] = agendamento.horaInicio.split(':').map(Number)
  const date = new Date()
  date.setHours(h)
  date.setMinutes(m + 40)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function obterIniciais(nome) {
  if (!nome) return 'CL'
  const partes = nome.trim().split(/\s+/)
  if (partes.length >= 2) return (partes[0][0] + partes[1][0]).toUpperCase()
  return nome.substring(0, 2).toUpperCase()
}

function statusLabel(status) {
  const mapa = {
    PENDENTE: 'PENDENTE',
    CONFIRMADO: 'CONFIRMADO',
    EM_ATENDIMENTO: 'EM ATENDIMENTO',
    PAUSADO: 'PAUSADO',
    FINALIZADO: 'FINALIZADO',
    CANCELADO: 'CANCELADO',
  }
  return mapa[status] || status
}

export default function AgendaCard({
  agendamento,
  onIniciar,
  onPausar,
  onRetomar,
  onReabrir,
  onFinalizar,
  onEditar,
  onCancelar,
  onExcluir,
  selectionMode = false,
  selected = false,
  onToggleSelection,
  selectionDisabled = false,
  acaoCarregando = null,
}) {
  const [menuAberto, setMenuAberto] = useState(false)
  const status = agendamento.status || 'PENDENTE'
  const statusClass = status.toLowerCase()
  const iniciais = obterIniciais(agendamento.clienteNome)
  const horaFim = obterHoraFim(agendamento)
  const carregandoTipo = (tipo) => acaoCarregando?.id === agendamento.id && acaoCarregando?.tipo === tipo

  return (
    <div className="agenda-card">
      <div className="agenda-card-header">
        {selectionMode && (
          <button
            type="button"
            className={`agenda-card-select ${selected ? 'agenda-card-select-checked' : ''}`}
            onClick={(event) => {
              event.stopPropagation()
              onToggleSelection?.(agendamento.id)
            }}
            disabled={selectionDisabled}
            aria-label={`Selecionar agendamento ${agendamento.id}`}
          />
        )}
        <div className="agenda-card-avatar">{iniciais}</div>
        <div className="agenda-card-titulo">
          <span className="agenda-card-nome">{agendamento.clienteNome}</span>
          <span className="agenda-card-protocolo">#{agendamento.protocolo || '------'}</span>
        </div>
        {status !== 'FINALIZADO' && (
          <span className={`agenda-card-badge agenda-card-badge-${statusClass}`}>
            {statusLabel(status)}
          </span>
        )}
        <div className="agenda-card-menu-wrap">
          <button className="agenda-card-menu-btn" onClick={() => setMenuAberto(!menuAberto)} type="button">
            <MoreVertical size={16} />
          </button>
          {menuAberto && (
            <div className="agenda-card-dropdown">
              {onEditar && <button type="button" onClick={() => { onEditar(agendamento); setMenuAberto(false) }}>Editar</button>}
              {/* Cancelar nos estados cancelaveis operacionais
                  (PENDENTE/CONFIRMADO/EM_ATENDIMENTO/PAUSADO), acao explicita
                  "Cancelar" que chama o endpoint de cancelamento.
                  FINALIZADO/CANCELADO nunca oferecem. */}
              {onCancelar && (status === 'PENDENTE' || status === 'CONFIRMADO' || status === 'EM_ATENDIMENTO' || status === 'PAUSADO') && (
                <button type="button" onClick={() => { onCancelar(agendamento); setMenuAberto(false) }}>Cancelar</button>
              )}
              {/* Excluir (soft delete): disponivel em todos os estados, exceto
                  PAUSADO (travado no backend). FINALIZADO mantem o status e
                  apenas sai da Agenda operacional. */}
              {onExcluir && status !== 'PAUSADO' && <button type="button" className="agenda-card-dropdown-danger" onClick={() => { onExcluir(agendamento); setMenuAberto(false) }}>Excluir</button>}
            </div>
          )}
        </div>
      </div>

      <div className="agenda-card-servico">
        <span className="agenda-card-servico-nome">{agendamento.servicoNome} · {agendamento.profissionalNome}</span>
      </div>

      <div className="agenda-card-horario">
        <span className="agenda-card-horario-texto">{formatarData(agendamento.data)} · {agendamento.horaInicio} – {horaFim}</span>
      </div>

      {(status === 'PENDENTE' || status === 'CONFIRMADO') && onIniciar && (
        <button className="agenda-card-botao agenda-card-botao-iniciar" onClick={() => onIniciar(agendamento)} type="button" disabled={carregandoTipo('iniciar')}>
          {carregandoTipo('iniciar') ? <><Loader className="spin" size={17} /> Iniciando atendimento</> : 'Iniciar Atendimento'}
        </button>
      )}

      {status === 'EM_ATENDIMENTO' && (
        <div className="agenda-card-botoes-duplos">
          {onPausar && (
            <button className="agenda-card-botao agenda-card-botao-pausar" onClick={() => onPausar(agendamento)} type="button" disabled={carregandoTipo('pausar')}>
              {carregandoTipo('pausar') ? <><Loader className="spin" size={17} /> Pausando</> : 'Pausar'}
            </button>
          )}
          {onFinalizar && (
            <button className="agenda-card-botao agenda-card-botao-finalizar" onClick={() => onFinalizar(agendamento)} type="button" disabled={carregandoTipo('finalizar')}>
              {carregandoTipo('finalizar') ? <><Loader className="spin" size={17} /> Finalizando</> : 'Finalizar'}
            </button>
          )}
        </div>
      )}

      {/* PAUSADO permite Retomar e Finalizar (PAUSADO -> FINALIZADO e valido
          no backend). Reutiliza o mesmo handler onFinalizar de EM_ATENDIMENTO. */}
      {status === 'PAUSADO' && (
        <div className="agenda-card-botoes-duplos">
          {onRetomar && (
            <button className="agenda-card-botao agenda-card-botao-iniciar" onClick={() => onRetomar(agendamento)} type="button" disabled={carregandoTipo('retomar')}>
              {carregandoTipo('retomar') ? <><Loader className="spin" size={17} /> Retomando atendimento</> : 'Retomar Atendimento'}
            </button>
          )}
          {onFinalizar && (
            <button className="agenda-card-botao agenda-card-botao-finalizar" onClick={() => onFinalizar(agendamento)} type="button" disabled={carregandoTipo('finalizar')}>
              {carregandoTipo('finalizar') ? <><Loader className="spin" size={17} /> Finalizando</> : 'Finalizar'}
            </button>
          )}
        </div>
      )}

      {status === 'FINALIZADO' && (
        <div className="agenda-card-footer-finalizado">
          <span className="agenda-card-badge-finalizado">
            &#10003; FINALIZADO
          </span>
          {onReabrir && (
            <button className="agenda-card-botao agenda-card-botao-iniciar" onClick={() => onReabrir(agendamento)} type="button" disabled={carregandoTipo('reabrir')}>
              {carregandoTipo('reabrir') ? <><Loader className="spin" size={17} /> Reabrindo</> : 'Reabrir Atendimento'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
