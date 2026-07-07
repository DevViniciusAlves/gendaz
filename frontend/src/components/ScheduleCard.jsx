import { BriefcaseBusiness, UserRound } from 'lucide-react'
import StatusBadge from './StatusBadge.jsx'

function formatarData(dataStr) {
  if (!dataStr) return ''
  const parts = dataStr.split('-')
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`
  }
  return dataStr
}

function obterHoraFim(agendamento) {
  if (agendamento.horaFim) return agendamento.horaFim
  if (!agendamento.horaInicio) return ''
  const [h, m] = agendamento.horaInicio.split(':').map(Number)
  const date = new Date()
  date.setHours(h)
  date.setMinutes(m + 40)
  const fh = String(date.getHours()).padStart(2, '0')
  const fm = String(date.getMinutes()).padStart(2, '0')
  return `${fh}:${fm}`
}

function obterIniciais(nome) {
  if (!nome) return 'CL'
  const partes = nome.trim().split(/\s+/)
  if (partes.length >= 2) {
    return (partes[0][0] + partes[1][0]).toUpperCase()
  }
  return nome.substring(0, 2).toUpperCase()
}

export default function ScheduleCard({ agendamento, children, leadingControl }) {
  const statusClass = (agendamento.status || '').toLowerCase()
  const iniciais = obterIniciais(agendamento.clienteNome)

  return (
    <article className="schedule-card">
      <div className="schedule-time">
        {leadingControl && <div className="schedule-select-box">{leadingControl}</div>}
        <div className={`schedule-icon-box status-${statusClass}`}>
          <span className="client-initials">{iniciais}</span>
        </div>
        <div>
          <small className="schedule-protocolo">#{agendamento.protocolo || '------'}</small>
          <strong>{agendamento.horaInicio} - {obterHoraFim(agendamento)}</strong>
          <small>{formatarData(agendamento.data)}</small>
        </div>
      </div>
      <div className="schedule-info">
        <UserRound size={17} />
        <div>
          <span>{agendamento.clienteNome}</span>
          <small>Cliente</small>
        </div>
      </div>
      <div className="schedule-info">
        <BriefcaseBusiness size={17} />
        <div>
          <span>{agendamento.servicoNome}</span>
          <small>Servico</small>
        </div>
      </div>
      <div className="schedule-info">
        <UserRound size={17} />
        <div>
          <span>{agendamento.profissionalNome}</span>
          <small>Profissional</small>
        </div>
      </div>
      <StatusBadge status={agendamento.status} />
      {children && <div className="schedule-actions">{children}</div>}
    </article>
  )
}
