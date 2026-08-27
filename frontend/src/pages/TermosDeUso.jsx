import { Link } from 'react-router-dom'

const contatoEmail = import.meta.env.VITE_CONTATO_EMAIL || 'contato@gendaz.site'

const secoes = [
  {
    titulo: '1. Termos de Uso',
    conteudos: [
      'Estes Termos de Uso regulam a utilização da gendaz, plataforma de gestão e agendamentos.',
      'Ao acessar ou utilizar a plataforma, a empresa usuária declara que leu, entendeu e concorda com as regras descritas nesta página.',
    ],
  },
  {
    titulo: '2. Sobre a gendaz',
    conteudos: [
      'A gendaz é uma plataforma online (Software como Serviço – SaaS) criada para ajudar empresas a organizar agenda, clientes, serviços, profissionais, pagamentos e financeiro em um único sistema.',
      'A plataforma é operada e mantida pelo responsável pela gendaz (pessoa física), que atua como fornecedora da ferramenta de gestão.',
    ],
  },
  {
    titulo: '3. Aceitação dos Termos e da Política de Privacidade',
    conteudos: [
      'O uso da conta, o cadastro da empresa e o acesso às funcionalidades indicam aceitação integral destes Termos de Uso e da Política de Privacidade da plataforma.',
      'Se a empresa não concordar com qualquer parte destes Termos ou da Política de Privacidade, o uso da plataforma deve ser interrompido.',
    ],
  },
  {
    titulo: '4. Cadastro e responsabilidade da empresa',
    conteudos: [
      'O cadastro da empresa exige informações verdadeiras, atuais e completas: nome da empresa, nome do responsável, e-mail, telefone e senha.',
      'A conta é vinculada à empresa cadastrada e deve ser administrada pelo responsável autorizado.',
      'A empresa responde pelo uso da conta, pela criação de usuários internos e pelos dados inseridos na plataforma.',
    ],
  },
  {
    titulo: '5. Usuários e credenciais',
    conteudos: [
      'Os usuários internos são criados pela empresa e recebem permissões de acordo com o perfil atribuído.',
      'A empresa é responsável por proteger suas credenciais de acesso, senhas e dispositivos autorizados, e por comunicar imediatamente qualquer uso não autorizado.',
    ],
  },
  {
    titulo: '6. Uso permitido e condutas proibidas',
    conteudos: [
      'A plataforma deve ser utilizada para organizar atividades legítimas da empresa, como atendimento, agenda, clientes, serviços e recebimentos.',
      'É proibido usar a plataforma para atividades ilícitas, abusivas ou fraudulentas; tentar acessar contas de terceiros sem autorização; interferir, sobrecarregar ou comprometer a segurança do sistema; ou inserir conteúdo falso, ofensivo ou indevido na conta.',
    ],
  },
  {
    titulo: '7. Dados cadastrados e papéis de tratamento',
    conteudos: [
      'Os dados inseridos na gendaz pela empresa devem ser verdadeiros, atualizados e compatíveis com a operação do negócio.',
      'Em relação aos dados que a empresa decide coletar e tratar de seus próprios clientes, a empresa pode atuar como controladora e a gendaz como operadora, seguindo as instruções da empresa.',
      'Em relação aos dados da própria conta gendaz, segurança, suporte, cobrança e obrigações legais, a gendaz possui responsabilidades próprias como operadora da plataforma.',
      'Os papéis dependem da operação real realizada e são detalhados na Política de Privacidade.',
    ],
  },
  {
    titulo: '8. Privacidade',
    conteudos: [
      'O tratamento de dados pessoais realizado pela gendaz segue a Política de Privacidade, disponível nesta página, e a legislação brasileira aplicável.',
      'A gendaz adota medidas técnicas e administrativas razoáveis para proteger os dados contra acesso não autorizado, perda, alteração ou divulgação indevida.',
    ],
  },
  {
    titulo: '9. Planos e assinatura',
    conteudos: [
      'A plataforma pode oferecer planos como Básico e Pro, com condições exibidas no site ou no painel no momento da contratação.',
      'A contratação do plano e o período de vigência seguem as condições apresentadas na oferta escolhida.',
    ],
  },
  {
    titulo: '10. Pagamentos e Stripe',
    conteudos: [
      'Os pagamentos dos planos são processados pela Stripe, provedor de pagamentos. Os dados de cartão são coletados diretamente pela Stripe, que possui política de privacidade própria.',
      'A gendaz não armazena dados completos de cartão.',
    ],
  },
  {
    titulo: '11. Teste grátis',
    conteudos: [
      'Quando houver teste grátis, o acesso pode ficar disponível pelo período informado na oferta escolhida.',
      'Ao final do período de teste, a continuidade do uso depende da contratação de um plano.',
    ],
  },
  {
    titulo: '12. Cancelamento e encerramento',
    conteudos: [
      'A empresa pode solicitar o encerramento da conta a qualquer momento. O encerramento revoga o acesso de todos os usuários vinculados e coloca a conta em estado terminal.',
      'O encerramento não significa eliminação imediata de todos os dados. Dados podem permanecer armazenados pelo tempo necessário para cumprir obrigações legais, contratuais, fiscais ou para resguardo de direitos, conforme a Política de Privacidade.',
    ],
  },
  {
    titulo: '13. Suspensão e bloqueio',
    conteudos: [
      'O acesso pode ser bloqueado ou suspenso em caso de inadimplência, cancelamento, uso indevido, tentativa de fraude, violação destes Termos ou determinação legal.',
      'A gendaz informará o responsável da conta sobre bloqueios sempre que possível.',
    ],
  },
  {
    titulo: '14. Disponibilidade do serviço',
    conteudos: [
      'A plataforma é oferecida com esforço de disponibilidade contínua, mas interrupções temporárias, manutenção programada, falhas de rede ou indisponibilidade de serviços de terceiros podem afetar o acesso a determinados recursos.',
    ],
  },
  {
    titulo: '15. Propriedade intelectual',
    conteudos: [
      'A plataforma, seu código, layout, marcas e demais elementos são de titularidade da gendaz ou de seus licenciantes.',
      'A empresa não adquire direitos sobre a plataforma, apenas o direito limitado de uso conforme estes Termos.',
    ],
  },
  {
    titulo: '16. Responsabilidades das partes',
    conteudos: [
      'A gendaz é responsável pela operação da plataforma e pelo cumprimento das obrigações previstas nestes Termos e na legislação aplicável.',
      'A empresa usuária é responsável pelos dados que cadastra, pelo uso correto das permissões concedidas aos usuários da conta e pela conformidade de suas próprias operações.',
      'Nenhuma cláusula destes Termos exclui ou limita obrigações legais que não possam ser legalmente excluídas.',
    ],
  },
  {
    titulo: '17. Serviços de terceiros',
    conteudos: [
      'Algumas funcionalidades podem depender de serviços de terceiros, como provedores de hospedagem, banco de dados, envio de e-mails e pagamentos.',
      'Cada fornecedor pode possuir sua própria política de privacidade e tratamento de dados. O uso da plataforma pode envolver processamento em diferentes localidades, conforme descrito na Política de Privacidade.',
    ],
  },
  {
    titulo: '18. Alterações destes Termos',
    conteudos: [
      'Estes Termos podem ser atualizados para refletir mudanças no produto, nas integrações ou em exigências legais.',
      'Alterações relevantes serão comunicadas com transparência, e a versão mais recente estará sempre disponível nesta página.',
      'Alterações que envolvam novas finalidades de tratamento de dados pessoais não se baseiam exclusivamente na continuidade de uso.',
    ],
  },
  {
    titulo: '19. Legislação aplicável',
    conteudos: [
      'Estes Termos são regidos pela legislação brasileira, em especial pela Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018 – LGPD) e pelo Código de Defesa do Consumidor, quando aplicável.',
    ],
  },
  {
    titulo: '20. Contato',
    conteudos: [
      `Em caso de dúvidas sobre estes Termos de Uso, entre em contato pelo e-mail ${contatoEmail}.`,
      'O canal de suporte da plataforma também pode ser utilizado como ponto de contato complementar.',
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
              Estes termos explicam como a gendaz deve ser usada por empresas que administram sua própria operação
              dentro da plataforma.
            </p>
          </div>

          <div className="legal-meta">
            <strong>gendaz</strong>
            <span>gendaz</span>
            <small>Última atualização: 18/08/2026</small>
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
