import { Loader, X } from 'lucide-react'

const titulos = {
  INICIAR: 'Iniciar atendimento',
  PAUSAR: 'Pausar atendimento',
  FINALIZAR: 'Finalizar atendimento',
}

const mensagens = {
  INICIAR: 'Tem certeza que deseja iniciar o atendimento deste cliente?',
  PAUSAR: 'Tem certeza que deseja pausar o atendimento? O agendamento voltará para pendente.',
  FINALIZAR: 'Tem certeza que deseja finalizar o atendimento deste cliente?',
}

export default function ConfirmacaoAcao({ open, ação, agendamento, onConfirmar, onCancelar, carregando = false }) {
  if (!open || !ação || !agendamento) return null

  return (
    <div className="confirmacao-overlay" onClick={onCancelar}>
      <div className="confirmacao-conteudo" onClick={(e) => e.stopPropagation()}>
        <div className="confirmacao-header">
          <h3>{titulos[ação] || ação}</h3>
          <button className="confirmacao-close" onClick={onCancelar} type="button">
            <X size={18} />
          </button>
        </div>
        <p className="confirmacao-mensagem">{mensagens[ação] || 'Confirme a ação.'}</p>
        <p className="confirmacao-cliente">{agendamento.clienteNome} — #{agendamento.protocolo || '------'}</p>
        <div className="confirmacao-botoes">
          <button className="confirmacao-botao confirmacao-botao-cancelar" onClick={onCancelar} type="button" disabled={carregando}>
            Cancelar
          </button>
          <button className="confirmacao-botao confirmacao-botao-confirmar" onClick={() => onConfirmar(ação, agendamento)} type="button" disabled={carregando}>
            {carregando ? <><Loader className="spin" size={16} /> Confirmando...</> : 'Confirmar'}
          </button>
        </div>
      </div>
    </div>
  )
}
