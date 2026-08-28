import { useEffect } from 'react'
import { AuthProvider } from './contexts/AuthContext.jsx'
import { ThemeProvider } from './contexts/ThemeContext.jsx'
import { RefreshProvider } from './context/RefreshContext.jsx'
import CookieBanner from './components/CookieBanner.jsx'
import AppRoutes from './routes/AppRoutes.jsx'

// O Crisp é carregado pelo script do CloudPages (via CRISP_WEBSITE_ID/key).
// Não há botão manual no front-end: apenas ocultamos o launcher padrão do
// Crisp para que nenhum botão flutuante apareça. O chat continua acessível
// por onde a própria integração do Crisp permitir.
function useHideCrispLauncher() {
  useEffect(() => {
    const hide = () => {
      try {
        window.$crisp = window.$crisp || []
        window.$crisp.push(['do', 'launcher:hide'])
      } catch {
        /* no-op */
      }
    }

    hide()
    const timers = [800, 1800, 3000].map((ms) => setTimeout(hide, ms))
    window.addEventListener('load', hide)
    return () => {
      timers.forEach(clearTimeout)
      window.removeEventListener('load', hide)
    }
  }, [])
}

export default function App() {
  useHideCrispLauncher()

  return (
    <ThemeProvider>
      <AuthProvider>
        <RefreshProvider>
          <AppRoutes />
          <CookieBanner />
        </RefreshProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
