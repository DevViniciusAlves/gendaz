import { Link } from 'react-router-dom'

const contatoEmail = import.meta.env.VITE_CONTATO_EMAIL || 'contato@gendaz.com.br'

const secoes = [
  {
    titulo: '1. Política de Privacidade',
    conteudos: [
      'Esta Política de Privacidade explica como o gendaz coleta, usa, armazena e protege dados pessoais tratados na plataforma.',
      'Ao utilizar o sistema, você concorda com as práticas descritas nesta página.',
    ],
  },
  {
    titulo: '2. Quem somos',
    conteudos: [
      'O gendaz é um SaaS multiempresa de agendamentos desenvolvido e operado pela PloyDev.',
      'A plataforma foi criada para ajudar empresas a organizarem agenda, clientes, serviços, profissionais, financeiro e pagamentos em um só lugar.',
    ],
  },
  {
    titulo: '3. Quais dados coletamos',
    itens: [
      'Dados de cadastro da empresa, como nome fantasia, e-mail, telefone e informações de acesso.',
      'Dados do usuário responsável pela conta e de usuários autorizados pela empresa.',
      'Dados inseridos pela própria empresa sobre clientes, serviços, agendamentos, pagamentos e atendimentos.',
      'Dados técnicos de uso, como registros de acesso, IP, navegador, dispositivo, data e hora de eventos relevantes.',
    ],
  },
  {
    titulo: '4. Como usamos os dados',
    conteudos: [
      'Usamos os dados para criar e manter contas, permitir o uso do sistema, registrar agendamentos, organizar pagamentos e oferecer suporte.',
      'Também podemos usar informações técnicas para segurança, diagnóstico de erros, prevenção a fraude e melhoria da experiência da plataforma.',
    ],
  },
  {
    titulo: '5. Dados de empresas e usuários',
    conteudos: [
      'Cada empresa é responsável pelos dados que cadastra e administra dentro do gendaz.',
      'Os dados da empresa e dos usuários autorizados são utilizados para operação da conta, autenticação, suporte e gestão de permissões.',
    ],
  },
  {
    titulo: '6. Dados de clientes cadastrados pelas empresas',
    conteudos: [
      'As empresas podem inserir dados de seus próprios clientes para gerenciar atendimentos, pagamentos e histórico de uso.',
      'Esses dados pertencem à empresa responsável pelo cadastro e devem ser tratados conforme a legislação aplicável e as práticas internas de cada negócio.',
    ],
  },
  {
    titulo: '7. Pagamentos e assinatura',
    conteudos: [
      'Os pagamentos e assinaturas podem ser processados por gateways externos, como a Cakto, ou por outros provedores integrados futuramente.',
      'Informações financeiras necessárias para processar cobrança ficam restritas ao fluxo de pagamento e não são usadas para finalidades indevidas.',
    ],
  },
  {
    titulo: '8. Cookies e tecnologias semelhantes',
    conteudos: [
      'Podemos usar cookies e tecnologias semelhantes para manter sessões, lembrar preferências, medir uso da plataforma e melhorar a navegação.',
      'Você pode ajustar configurações do navegador para limitar cookies, ciente de que algumas funções podem deixar de funcionar corretamente.',
    ],
  },
  {
    titulo: '9. Compartilhamento de dados',
    conteudos: [
      'Não vendemos dados pessoais de usuários ou clientes cadastrados na plataforma.',
      'Podemos compartilhar dados com fornecedores essenciais para operar o sistema, cumprir obrigações legais, atender solicitações técnicas ou proteger a segurança da plataforma.',
    ],
  },
  {
    titulo: '10. Segurança das informações',
    conteudos: [
      'Adotamos medidas técnicas e organizacionais para proteger os dados contra acesso não autorizado, uso indevido, perda, alteração ou divulgação indevida.',
      'O acesso à conta depende das credenciais da empresa e deve ser protegido pelos responsáveis autorizados.',
    ],
  },
  {
    titulo: '11. Retenção e exclusão de dados',
    conteudos: [
      'Mantemos os dados pelo tempo necessário para operar a conta, cumprir obrigações legais, resolver disputas e garantir segurança.',
      'Quando aplicável, dados podem ser excluídos ou anonimizados mediante solicitação ou conforme regras de retenção do sistema e obrigações legais.',
    ],
  },
  {
    titulo: '12. Direitos do usuário',
    itens: [
      'Confirmar se tratamos dados relacionados à sua conta.',
      'Solicitar correção de dados incompletos ou desatualizados.',
      'Solicitar acesso, limitação ou exclusão quando aplicável.',
      'Revogar consentimentos que dependam dessa base legal.',
    ],
  },
  {
    titulo: '13. Integração futura com WhatsApp',
    conteudos: [
      'O gendaz pode, no futuro, integrar recursos com o WhatsApp para atendimento, contato e troca de mensagens dentro do fluxo do sistema.',
      'Quando essa funcionalidade for ativada, o tratamento de mensagens e contatos seguirá esta política e as configurações da empresa usuária.',
    ],
  },
  {
    titulo: '14. Alterações nesta política',
    conteudos: [
      'Esta Política de Privacidade pode ser atualizada a qualquer momento para refletir mudanças no sistema, na legislação ou nas práticas de tratamento de dados.',
      'A versão mais recente sempre estará disponível nesta página.',
    ],
  },
  {
    titulo: '15. Contato',
    conteudos: [
      `Se você tiver dúvidas sobre esta Política de Privacidade ou sobre o tratamento de dados no gendaz, entre em contato pelo e-mail ${contatoEmail}.`,
      'Se houver necessidade de atendimento adicional, o canal de suporte da plataforma poderá ser usado como ponto de contato complementar.',
    ],
  },
]

export default function PoliticaPrivacidade() {
  return (
    <main className="legal-page">
      <section className="legal-panel">
        <div className="legal-header">
          <div>
            <Link to="/" className="secondary-link compact-link">Voltar para a página inicial</Link>
            <span className="section-kicker">Legal</span>
            <h1>Política de Privacidade</h1>
            <p className="legal-intro">
              O gendaz respeita a privacidade das empresas e dos usuários que utilizam a plataforma.
              Esta página resume, de forma clara, como os dados são tratados no sistema.
            </p>
          </div>

          <div className="legal-meta">
            <strong>gendaz</strong>
            <span>PloyDev</span>
            <small>Última atualização: 22/06/2026</small>
          </div>
        </div>

        <div className="legal-notice">
          <strong>Resumo importante</strong>
          <p>
            O gendaz não vende dados pessoais. Cada empresa é responsável pelos dados que cadastra,
            inclusive informações de clientes, agendamentos e histórico operacional mantidos dentro da conta.
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
          <Link to="/" className="primary-link">Voltar para a página inicial</Link>
          <a href={`mailto:${contatoEmail}`} className="secondary-link">Falar com o suporte</a>
        </div>
      </section>
    </main>
  )
}
