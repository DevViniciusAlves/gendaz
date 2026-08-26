import { useEffect, useRef, useState } from 'react'

export default function ScrollReveal({
  children,
  className = '',
  delay = 0,
  threshold = 0.18,
  rootMargin = '0px 0px -8% 0px',
  ...props
}) {
  const ref = useRef(null)
  const [visible, setVisible] = useState(false)
  const [enterCount, setEnterCount] = useState(0)
  const isBounce = className.includes('bounce-reveal')

  useEffect(() => {
    const node = ref.current
    if (!node) return undefined

    const observer = new IntersectionObserver(
      ([entry]) => {
        setVisible(entry.isIntersecting)
        if (entry.isIntersecting) {
          setEnterCount((current) => current + 1)
        }
      },
      { threshold, rootMargin },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [rootMargin, threshold])

  return (
    <div
      ref={ref}
      className={`reveal ${visible ? 'is-visible' : ''} ${className}`.trim()}
      style={{
        '--reveal-delay': `${delay}ms`,
        ...(isBounce && visible ? { animation: 'bounceReveal 720ms cubic-bezier(0.2, 0.9, 0.24, 1.15) both', animationDelay: `${delay}ms` } : {}),
        '--bounce-enter': enterCount,
      }}
      {...props}
    >
      {children}
    </div>
  )
}
