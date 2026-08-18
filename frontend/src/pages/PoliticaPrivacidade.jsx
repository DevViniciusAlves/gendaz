import { Link } from 'react-router-dom'

const contatoEmail = import.meta.env.VITE_CONTATO_EMAIL || 'contato@gendaz.site'

const secoes = [
  {
    titulo: '1. Introdução',
    conteudos: [
      'Esta Política de Privacidade explica como a gendaz trata os dados pessoais inseridos pelas empresas que utilizam a plataforma, em conformidade com a Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018 – LGPD) e demais legislação aplicável.',
    ],
  },
  {
    titulo: '2. Dados tratados',
    conteudos: [
      'Cadastro empresarial: nome da empresa, nome do responsável, e-mail, telefone e senha.',
      'Usuários internos: nome, e-mail, perfil de acesso, status e registro de aceite dos Termos e da Política.',
      'Clientes: nome, telefone, e-mail e observações cadastradas pela empresa.',
      'Agendamentos: dados operacionais de agenda, incluindo cliente, serviço, profissional, data e hora.',
      'Meu Gendaz: e-mail, nome, telefone e código de verificação (OTP) utilizado apenas para autenticação.',
      'Dados técnicos: endereço IP, navegador, sessão e registros necessários para segurança, auditoria e operação da plataforma.',
      'Os fluxos atuais de cadastro, login, clientes e Meu Gendaz não solicitam CPF, CNPJ ou documento de identificação.',
    ],
  },
  {
    titulo: '3. Finalidades do tratamento',
    conteudos: [
      'Conta e autenticação: permitir o cadastro, login e gestão da conta.',
      'Operação da plataforma: manter a funcionalidade dos módulos de agenda, clientes, serviços e financeiro.',
      'Agendamentos: viabilizar a gestão de horários e atendimentos da empresa.',
      'Comunicação: envio de avisos, confirmações e lembretes relacionados ao uso da plataforma.',
      'Suporte: atendimento a solicitações, dúvidas e chamados.',
      'Segurança e fraude: prevenção de acessos não autorizados, abuso e fraude.',
      'Pagamentos: processamento de cobranças dos planos contratados.',
      'Obrigação legal: cumprimento de obrigações legais, regulatórias e fiscais.',
      'Exercício de direitos: defesa da plataforma e da empresa em âmbito administrativo ou judicial.',
      'Melhoria do produto: uso de dados agregados e anonimizados para análises de desempenho.',
    ],
  },
  {
    titulo: '4. Bases legais',
    conteudos: [
      'O tratamento de dados na gendaz não depende exclusivamente de consentimento.',
      'A base legal varia conforme a operação e pode incluir o cumprimento de obrigação legal, a execução do contrato, o legítimo interesse, o exercício regular de direitos ou o consentimento, quando aplicável.',
      'O consentimento é utilizado apenas quando o tratamento assim exigir, e pode ser revogado a qualquer momento.',
    ],
  },
  {
    titulo: '5. Controlador e operador',
    conteudos: [
      'Em relação aos dados que a empresa decide coletar e tratar de seus próprios clientes, a empresa usuária pode atuar como controladora e a gendaz como operadora, processando os dados conforme as instruções da empresa.',
      'Em relação aos dados da conta gendaz, segurança, prevenção a fraude, suporte, cobrança e obrigações próprias, a gendaz pode possuir responsabilidades próprias como controladora ou operadora, conforme a operação real.',
      'Os papéis dependem da operação realizada em cada contexto.',
    ],
  },
  {
    titulo: '6. Campos livres e observações',
    conteudos: [
      'Alguns campos da plataforma permitem texto livre, como observações de clientes e de agendamentos.',
      'A empresa deve evitar inserir dados excessivos ou dados pessoais sensíveis sem necessidade, como dados de saúde, filiação sindical, orientação sexual ou convicções religiosas.',
      'A empresa é responsável pelo conteúdo inserido nesses campos.',
    ],
  },
  {
    titulo: '7. Pagamentos',
    conteudos: [
      'Os pagamentos dos planos são processados pela Stripe, provedor de pagamentos.',
      'Os dados de cartão são coletados e processados diretamente pela Stripe; a gendaz não armazena dados completos de cartão.',
    ],
  },
  {
    titulo: '8. Compartilhamento com terceiros',
    conteudos: [
      'Os dados da empresa usuária não são vendidos.',
      'O compartilhamento pode ocorrer com fornecedores necessários para o funcionamento da plataforma, como provedores de hospedagem e banco de dados (por exemplo, Render e Neon), envio de e-mails (por exemplo, Resend), pagamentos (por exemplo, Stripe) e outras integrações técnicas, ou por exigência legal.',
      'Cada fornecedor pode possuir sua própria política de privacidade e tratamento de dados.',
    ],
  },
  {
    titulo: '9. Transferência internacional',
    conteudos: [
      'O uso da plataforma pode envolver o processamento de dados em servidores localizados fora do Brasil, conforme a infraestrutura dos fornecedores contratados.',
      'Quando isso ocorrer, a gendaz adota medidas para que o tratamento observe a legislação aplicável, incluindo, quando aplicável, cláusulas contratuais ou outros mecanismos previstos na LGPD.',
    ],
  },
  {
    titulo: '10. Cookies',
    conteudos: [
      'A plataforma utiliza cookies e tecnologias semelhantes para funções necessárias ao funcionamento, como manter a sessão ativa e a segurança da navegação.',
      'Cookies opcionais, como os usados para entender como o site é utilizado, podem ser aceitos ou recusados pelo visitante por meio do aviso de cookies exibido na página.',
      'A recusa de cookies não essenciais não impede o uso das funcionalidades principais.',
    ],
  },
  {
    titulo: '11. Segurança da informação',
    conteudos: [
      'A gendaz adota medidas técnicas e administrativas razoáveis para proteger os dados contra acesso não autorizado, perda, alteração ou divulgação indevida, incluindo isolamento entre contas, criptografia de informações sensíveis e controle de acesso.',
      'Nenhuma medida é absolutamente garantida. A empresa usuária também é responsável por proteger seus acessos, senhas e usuários internos cadastrados na conta.',
    ],
  },
  {
    titulo: '12. Retenção dos dados',
    conteudos: [
      'Os dados permanecem armazenados enquanto a conta estiver ativa ou enquanto forem necessários para cumprir a finalidade informada, obrigações legais ou resguardo de direitos.',
      'O encerramento da conta revoga os acessos e cessa o uso da plataforma, mas não significa exclusão imediata de todos os dados. Dados podem ser conservados pelo tempo necessário para cumprimento de obrigações legais, fiscais, contratuais ou para resguardo de direitos.',
    ],
  },
  {
    titulo: '13. Direitos do titular',
    conteudos: [
      'O titular dos dados pode solicitar, quando aplicável: confirmação da existência de tratamento, acesso aos dados, correção, atualização, anonimização, bloqueio ou eliminação de dados desnecessários ou excessivos, portabilidade, revogação de consentimento e informação sobre o compartilhamento.',
      'As solicitações devem ser feitas pelo canal oficial de contato informado nesta página. O suporte da plataforma também pode ser utilizado como canal complementar.',
      'Solicitações que envolvam dados controlados por uma empresa cliente podem exigir a atuação da empresa controladora com o suporte da gendaz.',
    ],
  },
  {
    titulo: '14. Crianças e adolescentes',
    conteudos: [
      'A plataforma é voltada à gestão empresarial e não é direcionada ao cadastro de dados de crianças ou adolescentes.',
      'Caso ocorra tratamento de dados de crianças ou adolescentes, ele observará a legislação aplicável e o melhor interesse do titular, considerando a finalidade e o contexto da operação.',
    ],
  },
  {
    titulo: '15. Alterações desta Política',
    conteudos: [
      'Esta Política de Privacidade pode ser atualizada para refletir mudanças técnicas, operacionais ou legais.',
      'Alterações relevantes serão comunicadas com transparência, e a versão mais recente estará sempre disponível nesta página.',
      'Alterações que envolvam novas finalidades de tratamento não se baseiam exclusivamente na continuidade de uso.',
    ],
  },
  {
    titulo: '16. Contato',
    conteudos: [
      `Em caso de dúvidas sobre esta Política de Privacidade ou para exercer seus direitos, entre em contato pelo e-mail ${contatoEmail}.`,
      'O canal oficial de suporte também pode ser utilizado para solicitações relacionadas aos dados tratados na plataforma.',
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
              Esta política explica como a gendaz trata os dados inseridos pelas empresas que utilizam a plataforma.
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

        <div className="legal-notice">
          <strong>Nota</strong>
          <p>
            Esta Política reflete o produto em sua versão atual. Itens como a identificação formal do controlador, a
            política definitiva de retenção e a validação contratual de transferências internacionais estão sujeitos a
            consolidação e podem ser atualizados nesta página.
          </p>
        </div>

        <div className="legal-footer-actions">
          <Link to="/termos-de-uso" className="primary-link">Ver Termos de Uso</Link>
          <a href={`mailto:${contatoEmail}`} className="secondary-link">Falar com o suporte</a>
        </div>
      </section>
    </main>
  )
}
