import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

const STORAGE_KEY = 'agendnew_cookie_consent'

export default function CookieBanner() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (!saved) setVisible(true)
  }, [])

  function saveConsent(value) {
    window.localStorage.setItem(STORAGE_KEY, value)
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div className="cookie-banner" role="dialog" aria-live="polite" aria-label="PreferÃªncias de cookies">
      <div className="cookie-banner-content">
        <strong>Cookies e privacidade</strong>
        <p>
          Usamos cookies para melhorar sua experiÃªncia, manter sua navegaÃ§Ã£o segura e entender como o site Ã© utilizado.
          VocÃª pode aceitar ou recusar cookies nÃ£o essenciais. Para saber mais, acesse nossa{' '}
          <Link to="/politica-de-privacidade">PolÃ­tica de Privacidade</Link> e nossos{' '}
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

