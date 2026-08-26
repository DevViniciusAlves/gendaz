import DOMPurify from 'dompurify'

export function sanitizeHtml(input) {
  return DOMPurify.sanitize(String(input || ''))
}
