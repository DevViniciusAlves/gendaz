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

    const MOBILE_MAX = 768
    const BOTTOM_OFFSET = 'calc(100px + env(safe-area-inset-bottom))'

    const raiseLauncherOnMobile = () => {
      if (window.innerWidth > MOBILE_MAX) return

      const box = document.getElementById('crisp-chatbox')
      if (!box) return

      box.style.setProperty('bottom', BOTTOM_OFFSET, 'important')

      for (const child of box.querySelectorAll('*')) {
        const pos = getComputedStyle(child).position
        if (pos === 'fixed' || pos === 'absolute') {
          child.style.setProperty('bottom', BOTTOM_OFFSET, 'important')
        }
      }
    }

    raiseLauncherOnMobile()

    const observer = new MutationObserver(raiseLauncherOnMobile)
    observer.observe(document.body, { childList: true, subtree: true })

    window.addEventListener('resize', raiseLauncherOnMobile)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', raiseLauncherOnMobile)
    }
  }, [websiteId])

  return null
}
