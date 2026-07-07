import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../contexts/ThemeContext.jsx'

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const Icon = theme === 'light' ? Moon : Sun
  const label = theme === 'light' ? 'Tema escuro' : 'Tema claro'
  return (
    <button type="button" className="theme-toggle-menu" onClick={toggleTheme} aria-label={label}>
      <Icon size={18} />
      <span>{label}</span>
    </button>
  )
}
