import { X } from 'lucide-react'

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

export default function ConfirmacaoAcao({ open, acao, agendamento, onConfirmar, onCancelar }) {
  if (!open || !acao || !agendamento) return null

  return (
    <div className="confirmacao-overlay" onClick={onCancelar}>
      <div className="confirmacao-conteudo" onClick={(e) => e.stopPropagation()}>
        <div className="confirmacao-header">
          <h3>{titulos[acao] || acao}</h3>
          <button className="confirmacao-close" onClick={onCancelar} type="button">
            <X size={18} />
          </button>
        </div>
        <p className="confirmacao-mensagem">{mensagens[acao] || 'Confirme a ação.'}</p>
        <p className="confirmacao-cliente">{agendamento.clienteNome} — #{agendamento.protocolo || '------'}</p>
        <div className="confirmacao-botoes">
          <button className="confirmacao-botao confirmacao-botao-cancelar" onClick={onCancelar} type="button">
            Cancelar
          </button>
          <button className="confirmacao-botao confirmacao-botao-confirmar" onClick={() => onConfirmar(acao, agendamento)} type="button">
            Confirmar
          </button>
        </div>
      </div>
    </div>
  )
}
