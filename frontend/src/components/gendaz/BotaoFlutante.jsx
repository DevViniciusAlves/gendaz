import { MessageCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

export default function BotaoFlutante() {
  return (
    <Link to="ia" className="gendaz-fab" aria-label="Pergunte à IA">
      <MessageCircle size={20} />
      <span>Pergunte à IA</span>
    </Link>
  )
}
