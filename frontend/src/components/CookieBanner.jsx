import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

const STORAGE_KEY = 'gendaz_cookie_consent'
const LEGACY_STORAGE_KEY = 'agendnew_cookie_consent'

export default function CookieBanner() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    const legacySaved = window.localStorage.getItem(LEGACY_STORAGE_KEY)
    if (!saved && legacySaved) {
      window.localStorage.setItem(STORAGE_KEY, legacySaved)
      window.localStorage.removeItem(LEGACY_STORAGE_KEY)
      return
    }
    if (!saved) setVisible(true)
  }, [])

  function saveConsent(value) {
    window.localStorage.setItem(STORAGE_KEY, value)
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div className="cookie-banner" role="dialog" aria-live="polite" aria-label="Preferências de cookies">
      <div className="cookie-banner-content">
        <strong>Cookies e privacidade</strong>
        <p>
          Usamos cookies para melhorar sua experiência, manter sua navegação segura e entender como o site é utilizado.
          Você pode aceitar ou recusar cookies não essenciais. Para saber mais, acesse nossa{' '}
          <Link to="/politica-de-privacidade">Política de Privacidade</Link> e nossos{' '}
          <Link to="/termos-de-uso">Termos de Uso</Link>.
        </p>
      </div>
      <div className="cookie-banner-actions">
        <button type="button" className="secondary-link cookie-banner-btn" style={{ color: '#000000' }} onClick={() => saveConsent('declined')}>
          Recusar
        </button>
        <button type="button" className="primary-link cookie-banner-btn" style={{ color: '#000000' }} onClick={() => saveConsent('accepted')}>
          Aceitar
        </button>
      </div>
    </div>
  )
}
