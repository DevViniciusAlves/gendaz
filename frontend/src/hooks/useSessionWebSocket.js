import { useEffect, useRef } from 'react'

export function useSessionWebSocket(onInvalidated) {
  const ws = useRef(null)

  useEffect(() => {
    function connect() {
      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      ws.current = new WebSocket(`${protocol}://${window.location.host}/ws/session`)

      ws.current.onmessage = (event) => {
        const data = JSON.parse(event.data)
        if (data.type === 'SESSION_INVALIDATED') {
          onInvalidated()
        }
      }

      ws.current.onclose = () => {
        setTimeout(connect, 5000) // Reconect
      }
    }

    connect()

    return () => {
      if (ws.current) ws.current.close()
    }
  }, [onInvalidated])
}
