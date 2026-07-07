import { AuthProvider } from './contexts/AuthContext.jsx'
import { ThemeProvider } from './contexts/ThemeContext.jsx'
import CookieBanner from './components/CookieBanner.jsx'
import AppRoutes from './routes/AppRoutes.jsx'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AppRoutes />
        <CookieBanner />
      </AuthProvider>
    </ThemeProvider>
  )
}
