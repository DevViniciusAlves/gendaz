import { Link } from 'react-router-dom'

const contatoEmail = import.meta.env.VITE_CONTATO_EMAIL || 'contato@gendaz.com.br'

const secoes = [
  {
    titulo: '1. Coleta de dados',
    conteudos: [
      'A Gendaz coleta apenas os dados necessários para operar a plataforma, manter a conta ativa e oferecer as funcionalidades contratadas pela empresa usuária.',
      'Esses dados podem incluir informações de cadastro da empresa, usuários internos, clientes, agendamentos, pagamentos, preferências de uso e registros técnicos de acesso.',
    ],
  },
  {
    titulo: '2. Finalidade do tratamento',
    conteudos: [
      'Os dados são utilizados para organizar a operação da conta, prestar suporte, melhorar a experiência de uso, cumprir obrigações legais e manter a segurança da plataforma.',
      'Também podemos usar informações de forma agregada e anonimizada para análises de desempenho e evolução do produto.',
    ],
  },
  {
    titulo: '3. Compartilhamento de dados',
    conteudos: [
      'Os dados da empresa usuária não são vendidos.',
      'O compartilhamento pode ocorrer apenas com fornecedores necessários para o funcionamento da plataforma, com meios de pagamento, hospedagem, integrações técnicas ou por exigência legal.',
    ],
  },
  {
    titulo: '4. Segurança da informação',
    conteudos: [
      'Adotamos medidas técnicas e administrativas razoáveis para proteger os dados contra acesso não autorizado, perda, alteração ou divulgação indevida.',
      'A empresa usuária também é responsável por proteger seus acessos, senhas e usuários internos cadastrados na conta.',
    ],
  },
  {
    titulo: '5. Uso de cookies',
    conteudos: [
      'Podemos utilizar cookies e tecnologias semelhantes para manter a sessão ativa, lembrar preferências e entender como a plataforma está sendo usada.',
      'O navegador pode ser configurado para bloquear cookies, mas isso pode afetar algumas funcionalidades do sistema.',
    ],
  },
  {
    titulo: '6. Dados de clientes da empresa',
    conteudos: [
      'As informações de clientes cadastradas dentro da conta pertencem à operação da empresa usuária, que deve garantir que possui base legal e autorização para inseri-las na plataforma.',
      'A Gendaz atua como operadora tecnológica nesses casos, seguindo as instruções da empresa titular da conta.',
    ],
  },
  {
    titulo: '7. Armazenamento e retenção',
    conteudos: [
      'Os dados permanecem armazenados enquanto a conta estiver ativa ou enquanto forem necessários para cumprir a finalidade informada, obrigações legais ou resguardo de direitos.',
      'Após a solicitação de exclusão ou encerramento da conta, os dados poderão ser removidos ou anonimizados conforme a legislação aplicável e os prazos técnicos necessários.',
    ],
  },
  {
    titulo: '8. Direitos do titular',
    conteudos: [
      'Sempre que aplicável, o titular dos dados pode solicitar confirmação, acesso, correção, atualização, anonimização, bloqueio ou eliminação de informações pessoais.',
      'Essas solicitações devem ser feitas pelo canal oficial de contato informado nesta página.',
    ],
  },
  {
    titulo: '9. Alterações desta política',
    conteudos: [
      'Esta Política de Privacidade pode ser atualizada a qualquer momento para refletir mudanças técnicas, operacionais ou legais.',
      'A versão mais recente estará sempre disponível nesta página.',
    ],
  },
  {
    titulo: '10. Responsabilidade da empresa usuária',
    conteudos: [
      'A empresa que utiliza a Gendaz é responsável pelos dados que cadastra, pela autorização de uso e pela conformidade das informações incluídas em sua conta.',
      'A plataforma não substitui as obrigações legais, fiscais, contratuais ou regulatórias da empresa usuária.',
    ],
  },
  {
    titulo: '11. Serviços de terceiros',
    conteudos: [
      'Algumas funcionalidades podem depender de serviços de terceiros, como provedores de hospedagem, autenticação, pagamentos ou integrações externas.',
      'Cada fornecedor pode possuir sua própria política de privacidade e tratamento de dados.',
    ],
  },
  {
    titulo: '12. Menores de idade',
    conteudos: [
      'A plataforma é destinada ao uso empresarial e não deve ser utilizada para cadastro indevido de dados de menores sem a base legal apropriada.',
      'Caso isso ocorra, a empresa usuária assume integral responsabilidade pela legitimidade do tratamento.',
    ],
  },
  {
    titulo: '13. Contato',
    conteudos: [
      `Em caso de dúvidas sobre esta Política de Privacidade, entre em contato pelo e-mail ${contatoEmail}.`,
      'O canal oficial de suporte também pode ser utilizado para solicitações relacionadas aos dados tratados na plataforma.',
    ],
  },
  {
    titulo: '14. Vigência',
    conteudos: [
      'Esta política entra em vigor na data de sua publicação e permanece válida até nova atualização.',
      'O uso continuado da plataforma após eventuais alterações indica ciência e concordância com a versão vigente.',
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
              Esta política explica como a Gendaz trata os dados inseridos pelas empresas que utilizam a plataforma.
            </p>
          </div>

          <div className="legal-meta">
            <strong>gendaz</strong>
            <span>gendaz</span>
            <small>Última atualização: 04/08/2026</small>
          </div>
        </div>

        <div className="legal-notice">
          <strong>Antes de continuar</strong>
          <p>
            Ao utilizar a plataforma, a empresa confirma que possui autorização para cadastrar e tratar os dados
            informados na conta, inclusive dados de clientes, usuários internos e demais registros operacionais.
          </p>
        </div>

        <div className="legal-sections">
          {secoes.map((secao) => (
            <article key={secao.titulo} className="legal-section">
              <h2>{secao.titulo}</h2>
              {secao.conteudos?.map((texto) => (
                <p key={texto}>{texto}</p>
              ))}
            </article>
          ))}
        </div>

        <div className="legal-footer-actions">
          <Link to="/termos-de-uso" className="primary-link">Ver Termos de Uso</Link>
          <a href={`mailto:${contatoEmail}`} className="secondary-link">Falar com o suporte</a>
        </div>
      </section>
    </main>
  )
}
