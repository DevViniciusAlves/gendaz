import { useEffect } from 'react'
import { Crisp } from 'crisp-sdk-web'

// Inicializa o Crisp com a website ID (key) definida em VITE_CRISP_WEBSITE_ID.
// Não renderiza botão nenhum: o launcher oficial do Crisp (o que vem da key)
// aparece sozinho após o configure.
export function CrispProvider() {
  const websiteId = import.meta.env.VITE_CRISP_WEBSITE_ID

  useEffect(() => {
    if (!websiteId) return
    try {
      Crisp.configure(websiteId, { locale: 'pt-br' })
    } catch {
      /* Crisp já inicializado ou indisponível */
    }
  }, [websiteId])

  return null
}
