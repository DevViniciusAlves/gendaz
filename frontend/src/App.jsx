import { AuthProvider } from './contexts/AuthContext.jsx'
import { ThemeProvider } from './contexts/ThemeContext.jsx'
import { RefreshProvider } from './context/RefreshContext.jsx'
import CookieBanner from './components/CookieBanner.jsx'
import AppRoutes from './routes/AppRoutes.jsx'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <RefreshProvider>
          <AppRoutes />
          <CookieBanner />
        </RefreshProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
