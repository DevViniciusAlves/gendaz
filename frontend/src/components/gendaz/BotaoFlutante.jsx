import { MessageCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'

export default function BotaoFlutante() {
  const { slug } = useParams()
  return (
    <Link to={`/meu-gendaz/${slug}/ia`} className="gendaz-fab" aria-label="Pergunte à IA">
      <MessageCircle size={20} />
      <span>Pergunte à IA</span>
    </Link>
  )
}
