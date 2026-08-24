import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '../layouts/AppLayout.jsx'
import { useAuth } from '../contexts/AuthContext.jsx'
import { PLANOS } from '../services/localStore.js'
import Home from '../pages/Home.jsx'
import Login from '../pages/Login.jsx'
import RecuperarSenha from '../pages/RecuperarSenha.jsx'
import RedefinirSenha from '../pages/RedefinirSenha.jsx'
import Convite from '../pages/Convite.jsx'
import AdminLogin from '../pages/admin/AdminLogin.jsx'
import AdminDashboard from '../pages/admin/AdminDashboard.jsx'
import AdminAccessGate from '../pages/admin/AdminAccessGate.jsx'
import Booking from '../pages/Booking.jsx'
import CriarConta from '../pages/CriarConta.jsx'
import PagamentoPendente from '../pages/PagamentoPendente.jsx'
import PagamentoRetorno from '../pages/PagamentoRetorno.jsx'
import ContaInativa from '../pages/ContaInativa.jsx'
import ContaEncerrada from '../pages/ContaEncerrada.jsx'
import NotFound from '../pages/NotFound.jsx'
import SessionExpiredScreen from '../pages/SessionExpiredScreen.jsx'
import TermosDeUso from '../pages/TermosDeUso.jsx'
import PoliticaPrivacidade from '../pages/PoliticaPrivacidade.jsx'
import Dashboard from '../pages/Dashboard.jsx'
import Agenda from '../pages/Agenda.jsx'
import Clientes from '../pages/Clientes.jsx'
import Crm from '../pages/Crm.jsx'
import Insights from '../pages/Insights.jsx'
import Promocoes from '../pages/Promocoes.jsx'
import Servicos from '../pages/Servicos.jsx'
import Profissionais from '../pages/Profissionais.jsx'
import Financeiro from '../pages/Financeiro.jsx'
import Relatorios from '../pages/Relatorios.jsx'
import Logs from '../pages/Logs.jsx'
import Planos from '../pages/Planos.jsx'
import Configuracoes from '../pages/Configuracoes.jsx'
import UsuariosEmpresa from '../pages/UsuariosEmpresa.jsx'
import Suporte from '../pages/Suporte.jsx'
import Conta from '../pages/Conta.jsx'
import Gendaz from '../pages/Gendaz.jsx'
import GendazDashboard from '../pages/gendaz/Dashboard.jsx'
import GendazAgenda from '../pages/gendaz/Agenda.jsx'
import GendazHistorico from '../pages/gendaz/Historico.jsx'
import GendazAssistenteIA from '../pages/gendaz/AssistenteIA.jsx'
import GendazSuporte from '../pages/gendaz/Suporte.jsx'
import GendazBeneficios from '../pages/gendaz/Beneficios.jsx'
import GendazPromocoes from '../pages/gendaz/Promocoes.jsx'
import GendazConfiguracoes from '../pages/gendaz/Configuracoes.jsx'

function PrivateRoute({ children }) {
  const { usuario, authLoading } = useAuth()
  if (authLoading) return <div className="page"><p>Carregando sessao...</p></div>
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && usuario?.motivoInatividade === 'CONTA_ENCERRADA') {
    return <Navigate to="/conta-encerrada" replace />
  }
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
  if (usuario.statusConta === 'ACCOUNT_INACTIVE' && usuario.motivoInatividade === 'CONTA_ENCERRADA' && !impersonation) {
    return <Navigate to="/conta-encerrada" replace />
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
  if (usuario?.statusConta === 'ACCOUNT_INACTIVE' && usuario?.motivoInatividade === 'CONTA_ENCERRADA' && !impersonation) {
    return <Navigate to="/conta-encerrada" replace />
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

function ContaEncerradaRoute({ children }) {
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
  const { sessionExpired } = useAuth()
  if (sessionExpired) return <SessionExpiredScreen />
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/recuperar-senha" element={<RecuperarSenha />} />
      <Route path="/redefinir-senha" element={<RedefinirSenha />} />
      <Route path="/convite" element={<Convite />} />
      <Route path="/admin/login" element={<AdminAccessGate><AdminLogin /></AdminAccessGate>} />
      <Route path="/admin/dashboard" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
      <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
      <Route path="/admin/*" element={<AdminRoute><NotFound /></AdminRoute>} />
      <Route path="/agendar/:slugOuEmpresaId" element={<Booking />} />
      <Route path="/booking/:slugOuEmpresaId" element={<Booking />} />
      <Route path="/criar-conta" element={<CriarConta />} />
      <Route path="/pagamento-pendente" element={<PagamentoPendente />} />
      <Route path="/pagamento/sucesso" element={<PagamentoRetorno tipo="sucesso" />} />
      <Route path="/pagamento/cancelado" element={<PagamentoRetorno tipo="cancelado" />} />
      <Route path="/conta-inativa" element={<ContaInativaRoute><ContaInativa /></ContaInativaRoute>} />
      <Route path="/conta-encerrada" element={<ContaEncerradaRoute><ContaEncerrada /></ContaEncerradaRoute>} />
      <Route path="/not-found" element={<NotFound />} />
      <Route path="/termos-de-uso" element={<TermosDeUso />} />
      <Route path="/politica-de-privacidade" element={<PoliticaPrivacidade />} />
      <Route path="/meu-gendaz/:slug/*" element={<Gendaz />}>
        <Route index element={<GendazDashboard />} />
        <Route path="dashboard" element={<GendazDashboard />} />
        <Route path="agenda" element={<GendazAgenda />} />
        <Route path="historico" element={<GendazHistorico />} />
        <Route path="ia" element={<GendazAssistenteIA />} />
        <Route path="suporte" element={<GendazSuporte />} />
        <Route path="beneficios" element={<GendazBeneficios />} />
        <Route path="promocoes" element={<GendazPromocoes />} />
        <Route path="configuracoes" element={<GendazConfiguracoes />} />
      </Route>
      <Route path="/dashboard" element={<Navigate to="/sistema/dashboard" replace />} />
      <Route path="/sistema" element={<ClientRoute><AppLayout /></ClientRoute>}>
        <Route index element={<Navigate to="/sistema/dashboard" replace />} />
        <Route path="dashboard" element={<PlanRoute routeKey="dashboard"><Dashboard /></PlanRoute>} />
        <Route path="agenda" element={<PlanRoute routeKey="agenda"><Agenda /></PlanRoute>} />
        <Route path="clientes" element={<PlanRoute routeKey="clientes"><Clientes /></PlanRoute>} />
        <Route path="crm" element={<PlanRoute routeKey="crm"><Crm /></PlanRoute>} />
        <Route path="insights" element={<PlanRoute routeKey="insights"><Insights /></PlanRoute>} />
        <Route path="promocoes" element={<PlanRoute routeKey="promocoes"><Promocoes /></PlanRoute>} />
        <Route path="servicos" element={<PlanRoute routeKey="servicos"><Servicos /></PlanRoute>} />
        <Route path="profissionais" element={<PlanRoute routeKey="profissionais"><Profissionais /></PlanRoute>} />
        <Route path="financeiro" element={<PlanRoute routeKey="financeiro"><Financeiro /></PlanRoute>} />
        <Route path="pagamentos/*" element={<Navigate to="/sistema/financeiro" replace />} />
        <Route path="relatorios" element={<PlanRoute routeKey="relatorios"><Relatorios /></PlanRoute>} />
        <Route path="logs" element={<PlanRoute routeKey="logs"><Logs /></PlanRoute>} />
        <Route path="planos" element={<Planos />} />
        <Route path="configuracoes" element={<PlanRoute routeKey="configuracoes"><Configuracoes /></PlanRoute>} />
        <Route path="configuracoes/usuarios" element={<PlanRoute routeKey="configuracoes"><UsuariosEmpresa /></PlanRoute>} />
        <Route path="suporte" element={<Suporte />} />
        <Route path="conta" element={<Conta />} />
        <Route path="*" element={<NotFound />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
