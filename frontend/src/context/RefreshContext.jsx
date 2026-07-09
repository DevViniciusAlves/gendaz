import { createContext, useState } from 'react'

export const RefreshContext = createContext({
  refreshTrigger: 0,
  triggerRefreshAll: () => {},
})

export function RefreshProvider({ children }) {
  const [refreshTrigger, setRefreshTrigger] = useState(0)

  const triggerRefreshAll = () => {
    setRefreshTrigger((prev) => prev + 1)
  }

  return (
    <RefreshContext.Provider value={{ refreshTrigger, triggerRefreshAll }}>
      {children}
    </RefreshContext.Provider>
  )
}
