import { useEffect } from 'react'

const SITE_BASE = 'https://gendaz.site'

function upsertMetaByName(name, content) {
  if (!content) return
  let el = document.head.querySelector(`meta[name="${name}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('name', name)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function upsertMetaByProperty(property, content) {
  if (!content) return
  let el = document.head.querySelector(`meta[property="${property}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('property', property)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function upsertCanonical(href) {
  if (!href) return
  let el = document.head.querySelector('link[rel="canonical"]')
  if (!el) {
    el = document.createElement('link')
    el.setAttribute('rel', 'canonical')
    document.head.appendChild(el)
  }
  el.setAttribute('href', href)
}

function isStageHost() {
  if (typeof window === 'undefined') return false
  return window.location.hostname === 'stage.gendaz.site'
}

function isLocalHost() {
  if (typeof window === 'undefined') return false
  const h = window.location.hostname
  return h === 'localhost' || h === '127.0.0.1' || h.endsWith('.local')
}

export default function SEO({
  title,
  description,
  canonical,
  ogTitle,
  ogDescription,
  ogUrl,
  ogType = 'website',
  twitterCard = 'summary',
  jsonLd = null,
}) {
  useEffect(() => {
    if (title) document.title = title
    if (description) upsertMetaByName('description', description)

    const canonicalUrl = canonical || (typeof window !== 'undefined' ? window.location.href.split('#')[0] : SITE_BASE)
    upsertCanonical(canonicalUrl)

    upsertMetaByProperty('og:title', ogTitle || title)
    upsertMetaByProperty('og:description', ogDescription || description)
    upsertMetaByProperty('og:url', ogUrl || canonicalUrl)
    upsertMetaByProperty('og:type', ogType)
    upsertMetaByName('twitter:card', twitterCard)

    // Stage nunca deve indexar; produção indexa; local não força nada.
    const stage = isStageHost()
    const local = isLocalHost()
    let robotsEl = document.head.querySelector('meta[name="robots"]')
    if (stage) {
      if (!robotsEl) {
        robotsEl = document.createElement('meta')
        robotsEl.setAttribute('name', 'robots')
        document.head.appendChild(robotsEl)
      }
      robotsEl.setAttribute('content', 'noindex,nofollow')
    } else if (!local && robotsEl && robotsEl.getAttribute('content') === 'noindex,nofollow') {
      robotsEl.setAttribute('content', 'index,follow')
    }

    // JSON-LD (gerenciado: remove anterior do SEO antes de inserir)
    const prev = document.head.querySelectorAll('script[data-seo-jsonld]')
    prev.forEach((n) => n.remove())
    const payloads = Array.isArray(jsonLd) ? jsonLd : jsonLd ? [jsonLd] : []
    payloads.forEach((payload) => {
      const s = document.createElement('script')
      s.setAttribute('type', 'application/ld+json')
      s.setAttribute('data-seo-jsonld', 'true')
      s.textContent = JSON.stringify(payload)
      document.head.appendChild(s)
    })

    return () => {
      document.head.querySelectorAll('script[data-seo-jsonld]').forEach((n) => n.remove())
    }
  }, [title, description, canonical, ogTitle, ogDescription, ogUrl, ogType, twitterCard, jsonLd])

  return null
}

export { SITE_BASE }
