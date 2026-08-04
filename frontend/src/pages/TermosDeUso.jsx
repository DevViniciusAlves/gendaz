import { Link } from 'react-router-dom'

const contatoEmail = import.meta.env.VITE_CONTATO_EMAIL || 'contato@gendaz.com.br'

const secoes = [
  {
    titulo: '1. Termos de Uso',
    conteudos: [
      'Estes Termos de Uso regulam a utilização da Gendaz, SaaS de agendamentos desenvolvido e operado pela PloyDev.',
      'Ao acessar ou utilizar a plataforma, a empresa usuária declara que leu, entendeu e concorda com as regras descritas nesta página.',
    ],
  },
  {
    titulo: '2. Quem somos',
    conteudos: [
      'A Gendaz é uma plataforma online criada para ajudar empresas a organizar agenda, clientes, serviços, profissionais, pagamentos e financeiro em um único sistema.',
      'A PloyDev é a responsável pela operação técnica da plataforma e pela manutenção do serviço.',
    ],
  },
  {
    titulo: '3. Aceitação dos termos',
    conteudos: [
      'O uso da conta, o cadastro da empresa e o acesso às funcionalidades indicam aceitação integral destes Termos de Uso.',
      'Se a empresa não concordar com qualquer parte destes termos, o uso da plataforma deve ser interrompido.',
    ],
  },
  {
    titulo: '4. Uso da plataforma',
    conteudos: [
      'A Gendaz deve ser utilizada para organizar atividades legítimas da empresa, como atendimento, agenda, clientes, serviços e recebimentos.',
      'A empresa é responsável por manter seus dados corretos, atualizar as informações da conta e proteger o acesso dos usuários autorizados.',
    ],
  },
  {
    titulo: '5. Cadastro e responsabilidade da conta',
    conteudos: [
      'A conta é vinculada à empresa cadastrada e deve ser administrada pelo responsável autorizado.',
      'A empresa responde pelo uso da conta, pela criação de usuários internos e pelos dados inseridos na plataforma.',
    ],
  },
  {
    titulo: '6. Planos, pagamentos e assinatura',
    conteudos: [
      'A plataforma pode oferecer planos como Básico e Pro, com condições exibidas no site ou no painel no momento da contratação.',
      'Pagamentos e assinaturas podem ser processados por gateways externos, como a Cakto, ou por outros fornecedores integrados futuramente.',
    ],
  },
  {
    titulo: '7. Teste grátis, bloqueio e cancelamento',
    conteudos: [
      'Quando houver teste grátis, o acesso pode ficar disponível pelo período informado na oferta escolhida.',
      'O acesso pode ser bloqueado em caso de inadimplência, cancelamento, uso indevido, tentativa de fraude ou violação destes Termos.',
    ],
  },
  {
    titulo: '8. Responsabilidade das empresas usuárias',
    conteudos: [
      'Cada empresa é responsável pelos dados que cadastra, pelos seus próprios clientes e pelo uso correto das permissões concedidas aos usuários da conta.',
      'A PloyDev não responde por decisões operacionais, comerciais ou legais tomadas pela empresa usuária dentro da plataforma.',
    ],
  },
  {
    titulo: '9. Dados cadastrados no sistema',
    conteudos: [
      'Os dados inseridos na Gendaz pela empresa devem ser verdadeiros, atualizados e compatíveis com a operação do negócio.',
      'A empresa é a responsável por revisar e excluir, quando necessário, dados indevidos ou desatualizados cadastrados em sua conta.',
    ],
  },
  {
    titulo: '10. Limitações de responsabilidade',
    conteudos: [
      'A plataforma é oferecida para organização e gestão de atendimentos, não substituindo decisões profissionais, obrigações legais ou políticas internas da empresa usuária.',
      'Interrupções temporárias, manutenção programada, falhas de rede ou indisponibilidade de serviços de terceiros podem afetar o acesso a determinados recursos.',
    ],
  },
  {
    titulo: '11. Condutas proibidas',
    itens: [
      'Usar a plataforma para atividades ilícitas, abusivas ou fraudulentas.',
      'Tentar acessar contas de terceiros sem autorização.',
      'Interferir, sobrecarregar ou comprometer a segurança do sistema.',
      'Inserir conteúdo falso, ofensivo ou indevido na conta da empresa.',
    ],
  },
  {
    titulo: '12. Alterações nos termos',
    conteudos: [
      'Os Termos de Uso podem ser atualizados a qualquer momento para refletir mudanças no produto, nas integrações ou nas exigências legais.',
      'A versão mais recente sempre estará disponível nesta página.',
    ],
  },
  {
    titulo: '13. Contato',
    conteudos: [
      `Em caso de dúvidas sobre estes Termos de Uso, entre em contato pelo e-mail ${contatoEmail}.`,
      'Se houver necessidade de suporte adicional, o canal oficial da plataforma poderá ser utilizado como ponto de contato complementar.',
    ],
  },
]

export default function TermosDeUso() {
  return (
    <main className="legal-page">
      <section className="legal-panel">
        <div className="legal-header">
          <div>
            <Link to="/" className="secondary-link compact-link">Voltar para a página inicial</Link>
            <span className="section-kicker">Legal</span>
            <h1>Termos de Uso</h1>
            <p className="legal-intro">
              Estes termos explicam como a Gendaz deve ser usada por empresas que administram sua própria operação
              dentro da plataforma.
            </p>
          </div>

          <div className="legal-meta">
            <strong>gendaz</strong>
            <span>PloyDev</span>
            <small>Última atualização: 22/06/2026</small>
          </div>
        </div>

        <div className="legal-notice">
          <strong>Antes de continuar</strong>
          <p>
            Ao utilizar o sistema, a empresa confirma que tem autorização para cadastrar seus dados, seus usuários e
            as informações de seus clientes dentro da conta.
          </p>
        </div>

        <div className="legal-sections">
          {secoes.map((secao) => (
            <article key={secao.titulo} className="legal-section">
              <h2>{secao.titulo}</h2>
              {secao.conteudos?.map((texto) => (
                <p key={texto}>{texto}</p>
              ))}
              {secao.itens ? (
                <ul>
                  {secao.itens.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
            </article>
          ))}
        </div>

        <div className="legal-footer-actions">
          <Link to="/politica-de-privacidade" className="primary-link">Ver Política de Privacidade</Link>
          <a href={`mailto:${contatoEmail}`} className="secondary-link">Falar com o suporte</a>
        </div>
      </section>
    </main>
  )
}
