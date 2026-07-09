import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '../layouts/AppLayout.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import Home from '../pages/Home.jsx'
import Login from '../pages/Login.jsx'
import RecuperarSenha from '../pages/RecuperarSenha.jsx'
import RedefinirSenha from '../pages/RedefinirSenha.jsx'
import AdminLogin from '../pages/admin/AdminLogin.jsx'
import AdminDashboard from '../pages/admin/AdminDashboard.jsx'
import AdminAccessGate from '../pages/admin/AdminAccessGate.jsx'
import Booking from '../pages/Booking.jsx'
import CriarConta from '../pages/CriarConta.jsx'
import PagamentoPendente from '../pages/PagamentoPendente.jsx'
import PagamentoRetorno from '../pages/PagamentoRetorno.jsx'
import ContaInativa from '../pages/ContaInativa.jsx'
import TermosDeUso from '../pages/TermosDeUso.jsx'
import PoliticaPrivacidade from '../pages/PoliticaPrivacidade.jsx'
import Dashboard from '../pages/Dashboard.jsx'
// ⚠️ DESATIVADO - WhatsApp
// import Whatsapp from '../pages/Whatsapp.jsx'
import Agenda from '../pages/Agenda.jsx'
import Clientes from '../pages/Clientes.jsx'
import Servicos from '../pages/Servicos.jsx'
import Profissionais from '../pages/Profissionais.jsx'
import Financeiro from '../pages/Financeiro.jsx'
import Pagamentos from '../pages/Pagamentos.jsx'
import Relatorios from '../pages/Relatorios.jsx'
import Planos from '../pages/Planos.jsx'
import Configuracoes from '../pages/Configuracoes.jsx'
import Suporte from '../pages/Suporte.jsx'
import Conta from '../pages/Conta.jsx'
import NotFound from '../pages/NotFound.jsx'

function PrivateRoute({ children }) {
  const { usuario, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  return usuario ? children : <Navigate to="/login" replace />
}

function ClientRoute({ children }) {
  const { usuario, adminUsuario, impersonation, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  if (adminUsuario && !usuario) return <Navigate to="/admin/dashboard" replace />
  if (!usuario) return <Navigate to="/login" replace />
  if (usuario.perfil === 'SUPER_ADMIN' && !impersonation) {
    return <Navigate to="/admin/dashboard" replace />
  }
  if (usuario.statusConta === 'ACCOUNT_INACTIVE' && !impersonation) {
    return <Navigate to="/conta-inativa" replace />
  }
  return children
}

function PlanRoute({ routeKey, children }) {
  const { usuario, impersonation, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  if (usuario?.perfil === 'SUPER_ADMIN' && !impersonation) {
    return <Navigate to="/admin/dashboard" replace />
  }
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && !impersonation) {
    return <Navigate to="/conta-inativa" replace />
  }
  const allowed = PLANOS[usuario?.plano]?.rotas || []
  return allowed.includes(routeKey) ? children : <Navigate to="/sistema/dashboard" replace />
}

function ContaInativaRoute({ children }) {
  const { usuario, adminUsuario, impersonation, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  if (adminUsuario && !usuario) return <Navigate to="/admin/dashboard" replace />
  if (!usuario) return <Navigate to="/login" replace />
  if (usuario.perfil === 'SUPER_ADMIN' && !impersonation) {
    return <Navigate to="/admin/dashboard" replace />
  }
  return children
}

function AdminRoute({ children }) {
  const { adminUsuario, usuario, impersonation, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  if (adminUsuario) return children
  if (usuario?.perfil === 'SUPER_ADMIN' && !impersonation) return <Navigate to="/admin/login" replace />
  if (usuario) return <Navigate to="/sistema/dashboard" replace />
  return <Navigate to="/admin/login" replace />
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/recuperar-senha" element={<RecuperarSenha />} />
      <Route path="/redefinir-senha" element={<RedefinirSenha />} />
      <Route path="/admin/login" element={<AdminAccessGate><AdminLogin /></AdminAccessGate>} />
      <Route path="/admin/dashboard" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
      <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
      <Route path="/agendar/:slugOuEmpresaId" element={<Booking />} />
      <Route path="/booking/:slugOuEmpresaId" element={<Booking />} />
      <Route path="/criar-conta" element={<CriarConta />} />
      <Route path="/pagamento-pendente" element={<PagamentoPendente />} />
      <Route path="/pagamento/sucesso" element={<PagamentoRetorno tipo="sucesso" />} />
      <Route path="/pagamento/cancelado" element={<PagamentoRetorno tipo="cancelado" />} />
      <Route path="/conta-inativa" element={<ContaInativaRoute><ContaInativa /></ContaInativaRoute>} />
      <Route path="/termos-de-uso" element={<TermosDeUso />} />
      <Route path="/politica-de-privacidade" element={<PoliticaPrivacidade />} />
      <Route path="/dashboard" element={<Navigate to="/sistema/dashboard" replace />} />
      <Route path="/sistema" element={<ClientRoute><AppLayout /></ClientRoute>}>
        <Route index element={<Navigate to="/sistema/dashboard" replace />} />
        <Route path="dashboard" element={<PlanRoute routeKey="dashboard"><Dashboard /></PlanRoute>} />
        {/* ⚠️ DESATIVADO - WhatsApp */}
        {/* <Route path="whatsapp" element={<PlanRoute routeKey="whatsapp"><Whatsapp /></PlanRoute>} /> */}
        <Route path="agenda" element={<PlanRoute routeKey="agenda"><Agenda /></PlanRoute>} />
        <Route path="clientes" element={<PlanRoute routeKey="clientes"><Clientes /></PlanRoute>} />
        <Route path="servicos" element={<PlanRoute routeKey="servicos"><Servicos /></PlanRoute>} />
        <Route path="profissionais" element={<PlanRoute routeKey="profissionais"><Profissionais /></PlanRoute>} />
        <Route path="financeiro" element={<PlanRoute routeKey="financeiro"><Financeiro /></PlanRoute>} />
        <Route path="pagamentos" element={<PlanRoute routeKey="pagamentos"><Pagamentos /></PlanRoute>} />
        <Route path="relatorios" element={<PlanRoute routeKey="relatorios"><Relatorios /></PlanRoute>} />
        <Route path="planos" element={<Planos />} />
        <Route path="configuracoes" element={<PlanRoute routeKey="configuracoes"><Configuracoes /></PlanRoute>} />
        <Route path="suporte" element={<Suporte />} />
        <Route path="conta" element={<Conta />} />
      </Route>
      <Route path="/not-found" element={<NotFound />} />
      <Route path="*" element={<Navigate to="/not-found" replace />} />
    </Routes>
  )
}
