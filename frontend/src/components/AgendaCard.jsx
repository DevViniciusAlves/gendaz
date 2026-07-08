import { MoreVertical } from 'lucide-react'
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

export default function AgendaCard({ agendamento, onIniciar, onPausar, onFinalizar, onEditar, onCancelar, onExcluir }) {
  const [menuAberto, setMenuAberto] = useState(false)
  const status = agendamento.status || 'PENDENTE'
  const statusClass = status.toLowerCase()
  const iniciais = obterIniciais(agendamento.clienteNome)
  const horaFim = obterHoraFim(agendamento)

  return (
    <div className="agenda-card">
      <div className="agenda-card-header">
        <div className="agenda-card-avatar">{iniciais}</div>
        <div className="agenda-card-titulo">
          <span className="agenda-card-nome">{agendamento.clienteNome}</span>
          <span className="agenda-card-protocolo">#{agendamento.protocolo || '------'}</span>
        </div>
        <span className={`agenda-card-badge agenda-card-badge-${statusClass}`}>
          {statusLabel(status)}
        </span>
        <div className="agenda-card-menu-wrap">
          <button className="agenda-card-menu-btn" onClick={() => setMenuAberto(!menuAberto)} type="button">
            <MoreVertical size={16} />
          </button>
          {menuAberto && (
            <div className="agenda-card-dropdown">
              {onEditar && <button type="button" onClick={() => { onEditar(agendamento); setMenuAberto(false) }}>Editar</button>}
              {onCancelar && status !== 'CANCELADO' && status !== 'FINALIZADO' && (
                <button type="button" onClick={() => { onCancelar(agendamento); setMenuAberto(false) }}>Cancelar</button>
              )}
              {onExcluir && <button type="button" className="agenda-card-dropdown-danger" onClick={() => { onExcluir(agendamento); setMenuAberto(false) }}>Excluir</button>}
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
        <button className="agenda-card-botao agenda-card-botao-iniciar" onClick={() => onIniciar(agendamento)} type="button">
          Iniciar Atendimento
        </button>
      )}

      {status === 'EM_ATENDIMENTO' && (
        <div className="agenda-card-botoes-duplos">
          {onPausar && (
            <button className="agenda-card-botao agenda-card-botao-pausar" onClick={() => onPausar(agendamento)} type="button">
              Pausar
            </button>
          )}
          {onFinalizar && (
            <button className="agenda-card-botao agenda-card-botao-finalizar" onClick={() => onFinalizar(agendamento)} type="button">
              Finalizar
            </button>
          )}
        </div>
      )}

      {status === 'PAUSADO' && onIniciar && (
        <button className="agenda-card-botao agenda-card-botao-iniciar" onClick={() => onIniciar(agendamento)} type="button">
          Retomar Atendimento
        </button>
      )}
    </div>
  )
}
