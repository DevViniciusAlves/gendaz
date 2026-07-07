export default function ChatMessage({ mensagem }) {
  const mine = mensagem.direcao === 'EMPRESA_PARA_CLIENTE'
  return (
    <div className={`chat-message ${mine ? 'mine' : 'theirs'}`}>
      <p>{mensagem.conteudo}</p>
      <span>{new Date(mensagem.dataEnvio).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
    </div>
  )
}
