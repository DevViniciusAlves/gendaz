import { useEffect } from 'react'
import { Crisp } from 'crisp-sdk-web'

export function CrispProvider() {
  const websiteId = import.meta.env.VITE_CRISP_WEBSITE_ID

  useEffect(() => {
    if (!websiteId) return
    try {
      Crisp.configure(websiteId, { locale: 'pt-br' })
    } catch {
      /* Crisp já inicializado ou indisponível */
    }

    // No mobile, sobe o launcher do Crisp para não bloquear a nav inferior.
    // Mantém position:fixed para o ícone permanecer fixo ao rolar a página.
    const MOBILE_MAX = 768

    const raiseLauncherOnMobile = () => {
      if (window.innerWidth > MOBILE_MAX) return

      const box = document.getElementById('crisp-chatbox')
      if (!box) return

      // Procura o launcher: primeiro tenta o botão <button>, depois o .crisp-client
      const launcher =
        box.querySelector('button') ||
        box.querySelector('.crisp-client')

      if (!launcher) return

      // Já vem com position:fixed do Crisp; garante o bottom elevado
      launcher.style.setProperty('bottom', 'calc(84px + env(safe-area-inset-bottom))', 'important')
    }

    raiseLauncherOnMobile()
    window.addEventListener('resize', raiseLauncherOnMobile)
    return () => window.removeEventListener('resize', raiseLauncherOnMobile)
  }, [websiteId])

  return null
}
