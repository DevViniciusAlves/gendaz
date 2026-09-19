// Dados das landing pages SEO por segmento — PARTE 1 (6 segmentos).
// Arquitetura reutilizável: SegmentLandingPage.jsx consome este arquivo.

export const SEGMENTS = {
  'clinica-de-estetica': {
    key: 'clinica-de-estetica',
    iaExamples: [
      'Quais clientes devo tentar recuperar esta semana?',
      'Quais serviços estão com menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/sistema-para-clinica-de-estetica',
    seoTitle: 'Sistema para Clínica de Estética | Gendaz',
    seoDescription:
      'Organize agenda, clientes, pagamentos, financeiro e retornos da sua clínica de estética em um único sistema com o Gendaz.',
    eyebrow: 'Sistema para clínica de estética',
    h1: 'Sua clínica organizada do agendamento ao pagamento.',
    heroDescription:
      'Centralize agenda, clientes, atendimentos e financeiro em um só painel e acompanhe quem está deixando de voltar.',
    agenda: [
      { time: '09:00', service: 'Limpeza de pele', status: 'Confirmado' },
      { time: '10:30', service: 'Drenagem linfática', status: 'Confirmado' },
      { time: '13:00', service: 'Microagulhamento', status: 'Agendado' },
      { time: '15:30', service: 'Retorno facial', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar uma clínica de estética',
    problems: [
      {
        title: 'Agenda espalhada',
        description: 'Horários, alterações e confirmações acabam divididos entre WhatsApp, anotações e memória.',
      },
      {
        title: 'Financeiro difícil de acompanhar',
        description: 'Atendimentos acontecem, mas pagamentos e pendências ficam espalhados.',
      },
      {
        title: 'Clientes que param de voltar',
        description: 'Sem acompanhar frequência, você percebe tarde quando uma cliente desaparece.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os clientes que deixaram de voltar?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda sua clínica',
    helpItems: [
      { title: 'Agenda centralizada', description: 'Horários, profissionais, confirmações e remarcações em um só painel.' },
      { title: 'Clientes e retornos', description: 'Histórico de atendimentos e acompanhamento de quem precisa retornar.' },
      { title: 'Pagamentos e financeiro', description: 'Recebidos, pendências e movimentação organizados no mesmo lugar.' },
    ],
    faq: [
      { q: 'O Gendaz funciona para clínica de estética pequena?', a: 'Sim. Você organiza agenda, clientes e pagamentos em um único painel, sem precisar de várias ferramentas.' },
      { q: 'Consigo organizar mais de um profissional?', a: 'Sim. É possível organizar profissionais, serviços e horários no mesmo sistema.' },
      { q: 'Consigo acompanhar clientes que deixaram de retornar?', a: 'Sim. O Gendaz ajuda você a acompanhar seus clientes e perceber quem está reduzindo a frequência ou deixando de retornar.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. Você acompanha recebidos, pendências e o financeiro em um só lugar.' },
      { q: 'O cliente consegue fazer o próprio agendamento?', a: 'Sim. O cliente pode solicitar o agendamento online e você mantém o controle pelo painel.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. O Gendaz funciona na web, direto do navegador.' },
    ],
    related: [
      { label: 'Estéticas & Spas', path: '/sistema-para-estetica-e-spa' },
      { label: 'Depilação', path: '/sistema-para-depilacao' },
      { label: 'Sobrancelhas e cílios', path: '/agenda-para-sobrancelhas-e-cilios' },
    ],
  },
  'estetica-e-spa': {
    key: 'estetica-e-spa',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/sistema-para-estetica-e-spa',
    seoTitle: 'Sistema para Estética e Spa | Gendaz',
    seoDescription:
      'Organize agenda, clientes, tratamentos, pagamentos e retornos do seu espaço de estética ou spa com o Gendaz.',
    eyebrow: 'Sistema para estética e spa',
    h1: 'Mais controle da agenda. Mais tempo para cuidar dos clientes.',
    heroDescription:
      'Organize tratamentos, horários, clientes e pagamentos sem depender de várias ferramentas diferentes.',
    agenda: [
      { time: '09:00', service: 'Massagem relaxante', status: 'Confirmado' },
      { time: '10:30', service: 'Drenagem', status: 'Confirmado' },
      { time: '13:00', service: 'Tratamento facial', status: 'Agendado' },
      { time: '16:00', service: 'Sessão corporal', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma travar a rotina de estética e spa',
    problems: [
      {
        title: 'Agenda cheia de alterações',
        description: 'Remarcações e encaixes chegam no meio do atendimento e bagunçam o dia.',
      },
      {
        title: 'Clientes com tratamentos recorrentes',
        description: 'Sessões em sequência exigem controle de frequência que o WhatsApp não dá.',
      },
      {
        title: 'Dificuldade para acompanhar recebimentos',
        description: 'Tratamentos acontecem, mas pagamentos e pendências ficam sem controle claro.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os retornos que não voltaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu espaço',
    helpItems: [
      { title: 'Agenda de tratamentos', description: 'Sessões, horários e profissionais organizados em um só painel.' },
      { title: 'Acompanhamento de clientes', description: 'Veja frequência e perceba quem está deixando de retornar.' },
      { title: 'Pagamentos claros', description: 'Recebidos e pendências acompanhados junto com os atendimentos.' },
    ],
    faq: [
      { q: 'O Gendaz serve para spa com vários profissionais?', a: 'Sim. Você organiza profissionais, serviços e horários no mesmo painel.' },
      { q: 'Consigo acompanhar tratamentos recorrentes?', a: 'Sim. O histórico de atendimentos ajuda a acompanhar frequência e retornos.' },
      { q: 'Consigo controlar pagamentos e pendências?', a: 'Sim. Recebidos e pendências ficam organizados em um só lugar.' },
      { q: 'O cliente pode agendar sozinho?', a: 'Sim. O cliente solicita o agendamento online e você confirma pelo painel.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. O Gendaz funciona direto do navegador.' },
    ],
    related: [
      { label: 'Clínica de Estética', path: '/sistema-para-clinica-de-estetica' },
      { label: 'Depilação', path: '/sistema-para-depilacao' },
      { label: 'Sobrancelhas e cílios', path: '/agenda-para-sobrancelhas-e-cilios' },
    ],
  },
  'salao-de-beleza': {
    key: 'salao-de-beleza',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/sistema-para-salao-de-beleza',
    seoTitle: 'Sistema para Salão de Beleza | Gendaz',
    seoDescription:
      'Organize agenda, clientes, profissionais e pagamentos do seu salão de beleza em um único sistema com o Gendaz.',
    eyebrow: 'Sistema para salão de beleza',
    h1: 'Seu salão organizado em um só lugar.',
    heroDescription:
      'Centralize horários, clientes, profissionais e financeiro enquanto sua equipe foca no atendimento.',
    agenda: [
      { time: '09:00', service: 'Coloração', status: 'Confirmado' },
      { time: '10:30', service: 'Corte + escova', status: 'Confirmado' },
      { time: '13:00', service: 'Progressiva', status: 'Agendado' },
      { time: '16:00', service: 'Hidratação', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar a rotina do salão',
    problems: [
      {
        title: 'Agenda dividida entre profissionais',
        description: 'Cada profissional com uma agenda diferente gera conflito de horários.',
      },
      {
        title: 'Alterações pelo WhatsApp durante o atendimento',
        description: 'Mensagens chegam no meio do corte e a resposta atrasa o serviço.',
      },
      {
        title: 'Clientes recorrentes que desaparecem',
        description: 'Sem acompanhar frequência, o salão só percebe quando a cadeira já esvaziou.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as clientes que sumiram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu salão',
    helpItems: [
      { title: 'Agenda por profissional', description: 'Horários, serviços e equipe organizados sem conflito.' },
      { title: 'Clientes e recorrência', description: 'Acompanhe frequência e perceba quem está deixando de voltar.' },
      { title: 'Financeiro junto da agenda', description: 'Pagamentos e movimentação organizados no mesmo painel.' },
    ],
    faq: [
      { q: 'O Gendaz organiza a agenda de vários profissionais?', a: 'Sim. Cada profissional tem seus horários e serviços organizados no mesmo sistema.' },
      { q: 'Consigo ver clientes que pararam de vir?', a: 'Sim. O Gendaz ajuda você a acompanhar frequência e perceber quem deixou de retornar.' },
      { q: 'O cliente agenda sozinho?', a: 'Sim. O cliente solicita o horário online e você mantém o controle pelo painel.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. Recebidos e pendências ficam organizados junto com os atendimentos.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. O Gendaz funciona direto do navegador.' },
    ],
    related: [
      { label: 'Manicure', path: '/agenda-para-manicure' },
      { label: 'Sobrancelhas e cílios', path: '/agenda-para-sobrancelhas-e-cilios' },
      { label: 'Barbearia', path: '/sistema-para-barbearia' },
    ],
  },
  manicure: {
    key: 'manicure',
    iaExamples: [
      'Quais clientes estão demorando mais para voltar?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/agenda-para-manicure',
    seoTitle: 'Agenda para Manicure e Controle de Clientes | Gendaz',
    seoDescription:
      'Organize horários, clientes, serviços, pagamentos e retornos da sua rotina de manicure com o Gendaz.',
    eyebrow: 'Agenda para manicure',
    h1: 'Sua agenda organizada para você cuidar das clientes, não das mensagens.',
    heroDescription:
      'Centralize horários, retornos, clientes e pagamentos sem depender de conversa perdida no WhatsApp.',
    agenda: [
      { time: '09:00', service: 'Alongamento', status: 'Confirmado' },
      { time: '11:00', service: 'Manutenção em gel', status: 'Confirmado' },
      { time: '13:30', service: 'Banho de gel', status: 'Agendado' },
      { time: '15:30', service: 'Mão + pé', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma complicar a rotina da manicure',
    problems: [
      {
        title: 'Horários espalhados no WhatsApp',
        description: 'Pedidos de horário se perdem entre conversas e você perde tempo procurando.',
      },
      {
        title: 'Encaixes e alterações durante atendimento',
        description: 'Com a cliente na mesa, fica difícil responder e reorganizar o dia.',
      },
      {
        title: 'Clientes que esquecem da manutenção',
        description: 'Sem acompanhar retornos, a manutenção atrasa e o encaixe aperta.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as manutenções que não voltaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda sua rotina',
    helpItems: [
      { title: 'Agenda simples', description: 'Horários, serviços e confirmações organizados em um só lugar.' },
      { title: 'Retornos acompanhados', description: 'Histórico de clientes para perceber quem está atrasando a manutenção.' },
      { title: 'Pagamentos organizados', description: 'Recebidos e pendências controlados junto com a agenda.' },
    ],
    faq: [
      { q: 'O Gendaz serve para manicure autônoma?', a: 'Sim. Foi feito para organizar agenda, clientes e pagamentos mesmo para quem trabalha sozinha.' },
      { q: 'Consigo controlar manutenções?', a: 'Sim. O histórico ajuda a acompanhar frequência e retornos das clientes.' },
      { q: 'A cliente agenda sozinha?', a: 'Sim. Ela solicita o horário online e você confirma pelo painel.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. Recebidos e pendências ficam organizados no mesmo lugar.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador, inclusive no celular.' },
    ],
    related: [
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
      { label: 'Sobrancelhas e cílios', path: '/agenda-para-sobrancelhas-e-cilios' },
      { label: 'Estéticas & Spas', path: '/sistema-para-estetica-e-spa' },
    ],
  },
  'sobrancelhas-e-cilios': {
    key: 'sobrancelhas-e-cilios',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais serviços estão com menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/agenda-para-sobrancelhas-e-cilios',
    seoTitle: 'Agenda para Sobrancelhas e Cílios | Gendaz',
    seoDescription:
      'Organize agenda, clientes, manutenções e pagamentos do seu studio de sobrancelhas ou cílios com o Gendaz.',
    eyebrow: 'Agenda para sobrancelhas e cílios',
    h1: 'A cliente precisa voltar no prazo. Sua agenda também precisa lembrar disso.',
    heroDescription:
      'Organize atendimentos, manutenções, retornos e clientes em um único painel.',
    agenda: [
      { time: '09:00', service: 'Design com henna', status: 'Confirmado' },
      { time: '10:30', service: 'Manutenção de cílios', status: 'Confirmado' },
      { time: '13:00', service: 'Brow lamination', status: 'Agendado' },
      { time: '15:00', service: 'Design + buço', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar o studio',
    problems: [
      {
        title: 'Manutenções que precisam acontecer no prazo',
        description: 'Cílios e design têm ciclo certo; sem controle, a cliente atrasa e perde o resultado.',
      },
      {
        title: 'Clientes que somem depois do primeiro atendimento',
        description: 'Sem acompanhar retornos, você só percebe quando a agenda já esvaziou.',
      },
      {
        title: 'Mensagens chegando durante procedimentos',
        description: 'Com a cliente na maca, responder WhatsApp quebra o foco e atrasa tudo.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as manutenções perdidas?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu studio',
    helpItems: [
      { title: 'Agenda de procedimentos', description: 'Atendimentos e manutenções organizados por horário.' },
      { title: 'Retornos acompanhados', description: 'Perceba quem está atrasando a manutenção ou sumiu.' },
      { title: 'Clientes e pagamentos', description: 'Histórico, recebidos e pendências no mesmo painel.' },
    ],
    faq: [
      { q: 'O Gendaz ajuda a controlar manutenções?', a: 'Sim. O histórico de atendimentos ajuda a acompanhar frequência e retornos.' },
      { q: 'Serve para quem atende sozinha?', a: 'Sim. Agenda, clientes e pagamentos ficam organizados em um só lugar.' },
      { q: 'A cliente agenda sozinha?', a: 'Sim. Ela solicita o horário online e você confirma pelo painel.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. Recebidos e pendências ficam organizados junto com a agenda.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Manicure', path: '/agenda-para-manicure' },
      { label: 'Clínica de Estética', path: '/sistema-para-clinica-de-estetica' },
      { label: 'Estéticas & Spas', path: '/sistema-para-estetica-e-spa' },
    ],
  },
  barbearia: {
    key: 'barbearia',
    iaExamples: [
      'Quais clientes costumavam voltar e pararam?',
      'Qual serviço teve menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-barbearia',
    seoTitle: 'Sistema para Barbearia e Agenda Online | Gendaz',
    seoDescription:
      'Organize agenda, clientes, profissionais e pagamentos da sua barbearia em um único sistema com o Gendaz.',
    eyebrow: 'Sistema para barbearia',
    h1: 'Agenda organizada mesmo quando você está com a máquina na mão.',
    heroDescription:
      'Centralize horários, clientes, equipe e pagamentos sem precisar parar o atendimento para organizar tudo.',
    agenda: [
      { time: '09:00', service: 'Corte', status: 'Confirmado' },
      { time: '10:00', service: 'Corte + barba', status: 'Confirmado' },
      { time: '11:30', service: 'Barba', status: 'Agendado' },
      { time: '14:00', service: 'Corte degradê', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma travar a barbearia',
    problems: [
      {
        title: 'WhatsApp chegando durante o corte',
        description: 'Com a máquina na mão, responder mensagem atrasa o atendimento.',
      },
      {
        title: 'Horários e encaixes difíceis de controlar',
        description: 'Encaixe sem controle gera espera, atraso e cadeira vazia.',
      },
      {
        title: 'Clientes recorrentes que param de aparecer',
        description: 'Corte tem ciclo certo; sem acompanhar, você perde o cliente sem perceber.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os cortes que deixaram de voltar?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda sua barbearia',
    helpItems: [
      { title: 'Agenda por barbeiro', description: 'Horários e equipe organizados sem parar o atendimento.' },
      { title: 'Clientes recorrentes', description: 'Acompanhe frequência e perceba quem parou de aparecer.' },
      { title: 'Pagamentos no mesmo lugar', description: 'Recebidos e pendências organizados junto com a agenda.' },
    ],
    faq: [
      { q: 'O Gendaz organiza vários barbeiros?', a: 'Sim. Cada profissional tem seus horários organizados no mesmo sistema.' },
      { q: 'O cliente agenda sozinho?', a: 'Sim. Ele solicita o horário online e você mantém o controle pelo painel.' },
      { q: 'Consigo ver quem parou de cortar comigo?', a: 'Sim. O Gendaz ajuda a acompanhar frequência e perceber quem deixou de retornar.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. Recebidos e pendências ficam organizados em um só lugar.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
      { label: 'Estúdio de Tatuagem', path: '/agenda-para-estudio-de-tatuagem' },
      { label: 'Personal Trainer', path: '/agenda-para-personal-trainer' },
    ],
  },
  depilacao: {
    key: 'depilacao',
    iaExamples: [
      'Quais clientes deixaram de retornar recentemente?',
      'Quais serviços tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-depilacao',
    seoTitle: 'Sistema para Depilação e Agenda de Clientes | Gendaz',
    seoDescription:
      'Organize sessões, agenda, clientes, pagamentos e retornos do seu negócio de depilação com o Gendaz.',
    eyebrow: 'Sistema para depilação',
    h1: 'Sessões organizadas sem perder o próximo retorno.',
    heroDescription:
      'Centralize horários, clientes, sessões e pagamentos em um único painel.',
    agenda: [
      { time: '09:00', service: 'Depilação a laser', status: 'Confirmado' },
      { time: '10:30', service: 'Axilas', status: 'Confirmado' },
      { time: '13:00', service: 'Pernas', status: 'Agendado' },
      { time: '15:30', service: 'Retorno de sessão', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar a rotina de depilação',
    problems: [
      {
        title: 'Sessões recorrentes difíceis de acompanhar',
        description: 'Protocolos com várias sessões se perdem quando o controle é manual.',
      },
      {
        title: 'Reagendamentos espalhados no WhatsApp',
        description: 'Trocas de horário em conversas diferentes, sem visão da sequência.',
      },
      {
        title: 'Clientes que atrasam o próximo retorno',
        description: 'Sem acompanhamento, o intervalo entre sessões estoura e o resultado atrasa.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os retornos que atrasaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu negócio de depilação',
    helpItems: [
      { title: 'Sequência de sessões organizada', description: 'Acompanhe em qual sessão cada cliente está e qual é a próxima.' },
      { title: 'Agenda de retornos', description: 'Programe os próximos retornos sem depender de anotações soltas.' },
      { title: 'Pagamentos por sessão ou pacote', description: 'Recebidos e pendências organizados por cliente.' },
    ],
    faq: [
      { q: 'Dá para acompanhar protocolos com várias sessões?', a: 'Sim. O histórico mostra as sessões realizadas e os próximos retornos.' },
      { q: 'Consigo controlar pacotes?', a: 'Sim. Pagamentos e atendimentos ficam vinculados ao cadastro do cliente.' },
      { q: 'Funciona para atendimento individual?', a: 'Sim. Atende profissionais autônomas e equipes.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Clínica de Estética', path: '/sistema-para-clinica-de-estetica' },
      { label: 'Estética e Spa', path: '/sistema-para-estetica-e-spa' },
      { label: 'Sobrancelhas e cílios', path: '/agenda-para-sobrancelhas-e-cilios' },
    ],
  },
  'locacao-de-quadra': {
    key: 'locacao-de-quadra',
    iaExamples: [
      'Quais horários tiveram menos agendamentos?',
      'Quais serviços tiveram menos movimento?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-locacao-de-quadra',
    seoTitle: 'Sistema para Locação de Quadra e Reservas | Gendaz',
    seoDescription:
      'Organize reservas, horários, clientes e pagamentos da sua quadra em um único sistema com o Gendaz.',
    eyebrow: 'Sistema para locação de quadra',
    h1: 'Horários ocupados. Reservas organizadas.',
    heroDescription:
      'Controle reservas, clientes e pagamentos sem depender de mensagens e anotações espalhadas.',
    agenda: [
      { time: '08:00', service: 'Beach tennis', status: 'Confirmado' },
      { time: '10:00', service: 'Futebol society', status: 'Confirmado' },
      { time: '14:00', service: 'Vôlei', status: 'Agendado' },
      { time: '18:00', service: 'Beach tennis', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar a locação de quadra',
    problems: [
      {
        title: 'Dois clientes tentando o mesmo horário',
        description: 'Sem um quadro único de reservas, o choque de horários vira discussão.',
      },
      {
        title: 'Reservas controladas manualmente',
        description: 'Caderno e mensagens não mostram a ocupação real da semana.',
      },
      {
        title: 'Pagamentos difíceis de acompanhar',
        description: 'Sinal, restante e mensalistas espalhados sem fechamento claro.',
      },
    ],
    simulator: true,
    simulatorKind: 'slots',
    simulatorTitle: 'Quanto vale um horário vazio?',
    simulatorDescription:
      'Informe os valores e veja uma estimativa simples do potencial associado aos horários informados.',
    helpTitle: 'Como o Gendaz ajuda sua quadra',
    helpItems: [
      { title: 'Quadro de reservas único', description: 'Ocupação por quadra, dia e horário em uma só visão.' },
      { title: 'Clientes e grupos organizados', description: 'Mensalistas e avulsos com histórico de reservas.' },
      { title: 'Pagamentos por reserva', description: 'Sinais, recebidos e pendências vinculados a cada horário.' },
    ],
    faq: [
      { q: 'Dá para controlar mais de uma quadra?', a: 'Sim. As reservas são organizadas por quadra, data e horário.' },
      { q: 'Consigo separar mensalistas de avulsos?', a: 'Sim. Cada cliente tem seu cadastro com histórico de reservas e pagamentos.' },
      { q: 'O cliente reserva pelo link?', a: 'Sim. A reserva pode ser solicitada pelo link da empresa.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Personal Trainer', path: '/agenda-para-personal-trainer' },
      { label: 'Yoga e Pilates', path: '/agenda-para-yoga-e-pilates' },
      { label: 'Cursos e Tutoria', path: '/agenda-para-cursos-e-tutoria' },
    ],
  },
  'clinica-odontologica': {
    key: 'clinica-odontologica',
    iaExamples: [
      'Quais clientes deixaram de retornar?',
      'Quais períodos tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-clinica-odontologica',
    seoTitle: 'Sistema de Agenda para Clínica Odontológica | Gendaz',
    seoDescription:
      'Organize agenda, clientes, profissionais e pagamentos da sua clínica odontológica com o Gendaz.',
    eyebrow: 'Agenda para clínica odontológica',
    h1: 'Mais organização para a rotina da clínica.',
    heroDescription:
      'Centralize horários, profissionais, clientes e pagamentos em um só painel. Foco administrativo: agenda, organização e financeiro.',
    agenda: [
      { time: '08:00', service: 'Avaliação', status: 'Confirmado' },
      { time: '09:30', service: 'Retorno', status: 'Confirmado' },
      { time: '11:00', service: 'Limpeza', status: 'Agendado' },
      { time: '14:00', service: 'Consulta', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar a clínica',
    problems: [
      {
        title: 'Agenda distribuída entre profissionais',
        description: 'Dentistas, horários e cadeiras sem uma visão única geram conflito.',
      },
      {
        title: 'Alterações e confirmações manuais',
        description: 'Remarcações por telefone consomem a recepção o dia inteiro.',
      },
      {
        title: 'Dificuldade para acompanhar retornos',
        description: 'Retornos de avaliação e manutenção se perdem sem acompanhamento.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os retornos que não voltaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda sua clínica',
    helpItems: [
      { title: 'Agenda por profissional e cadeira', description: 'Horários organizados sem choque entre atendimentos.' },
      { title: 'Confirmações centralizadas', description: 'Acompanhe confirmados, faltas e reagendamentos.' },
      { title: 'Financeiro da clínica', description: 'Recebidos e pendências por paciente e período.' },
    ],
    faq: [
      { q: 'O Gendaz é um prontuário odontológico?', a: 'Não. O Gendaz é um sistema de agenda, organização e financeiro. Não armazena diagnóstico, prescrição ou laudos.' },
      { q: 'Funciona com vários dentistas?', a: 'Sim. A agenda é organizada por profissional.' },
      { q: 'Dá para acompanhar retornos?', a: 'Sim. O histórico administrativo mostra atendimentos e próximos retornos.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Consultórios', path: '/sistema-para-consultorios' },
      { label: 'Clínica de Estética', path: '/sistema-para-clinica-de-estetica' },
      { label: 'Psicólogos e Terapeutas', path: '/agenda-para-psicologos-e-terapeutas' },
    ],
  },
  'personal-trainer': {
    key: 'personal-trainer',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/agenda-para-personal-trainer',
    seoTitle: 'Agenda para Personal Trainer e Alunos | Gendaz',
    seoDescription:
      'Organize alunos, horários, sessões e pagamentos como personal trainer utilizando o Gendaz.',
    eyebrow: 'Agenda para personal trainer',
    h1: 'Sua agenda organizada entre um treino e outro.',
    heroDescription:
      'Centralize alunos, sessões, horários e pagamentos em um único painel.',
    agenda: [
      { time: '07:00', service: 'Treino individual', status: 'Confirmado' },
      { time: '09:00', service: 'Avaliação', status: 'Confirmado' },
      { time: '16:00', service: 'Treino funcional', status: 'Agendado' },
      { time: '18:00', service: 'Treino individual', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar a rotina do personal',
    problems: [
      {
        title: 'Mudanças de horário durante o dia',
        description: 'Aluno remarca em cima da hora e a sequência da semana desmonta.',
      },
      {
        title: 'Sessões difíceis de acompanhar',
        description: 'Anotar atendimentos de cabeça gera cobrança errada e retrabalho.',
      },
      {
        title: 'Alunos que deixam de marcar',
        description: 'Sem acompanhamento, o aluno some em silêncio.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os alunos que pararam?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu trabalho',
    helpItems: [
      { title: 'Agenda de treinos clara', description: 'Sessões do dia e da semana organizadas por aluno.' },
      { title: 'Histórico de atendimentos', description: 'Acompanhe os últimos atendimentos e a frequência do cliente.' },
      { title: 'Pagamentos organizados', description: 'Recebidos e pendências claros por aluno e período.' },
    ],
    faq: [
      { q: 'Dá para acompanhar as sessões de cada aluno?', a: 'Sim. O histórico de atendimentos mostra os últimos atendimentos e a frequência do aluno.' },
      { q: 'Funciona para treinos em vários locais?', a: 'Sim. Os atendimentos ficam organizados por dia, horário e aluno.' },
      { q: 'Consigo controlar mensalidades?', a: 'Sim. Pagamentos recebidos e pendentes ficam no financeiro.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador, inclusive no celular.' },
    ],
    related: [
      { label: 'Yoga e Pilates', path: '/agenda-para-yoga-e-pilates' },
      { label: 'Locação de quadra', path: '/sistema-para-locacao-de-quadra' },
      { label: 'Cursos e Tutoria', path: '/agenda-para-cursos-e-tutoria' },
    ],
  },
  consultorios: {
    key: 'consultorios',
    iaExamples: [
      'Quais clientes deixaram de retornar?',
      'Quais períodos tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-consultorios',
    seoTitle: 'Sistema de Agenda para Consultórios | Gendaz',
    seoDescription:
      'Organize agenda, profissionais, clientes e pagamentos do seu consultório com o Gendaz.',
    eyebrow: 'Sistema de agenda para consultórios',
    h1: 'A rotina do consultório em um único painel.',
    heroDescription:
      'Organize horários, atendimentos, profissionais e pagamentos com mais clareza. Foco administrativo, sem prontuário ou prescrição.',
    agenda: [
      { time: '08:00', service: 'Atendimento', status: 'Confirmado' },
      { time: '10:00', service: 'Primeira consulta', status: 'Confirmado' },
      { time: '14:00', service: 'Retorno', status: 'Agendado' },
      { time: '16:00', service: 'Atendimento', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar o consultório',
    problems: [
      {
        title: 'Agenda manual',
        description: 'Caderno e mensagens não acompanham remarcações e faltas.',
      },
      {
        title: 'Reagendamentos constantes',
        description: 'Trocas sem registro geram horários duplicados.',
      },
      {
        title: 'Dificuldade para acompanhar retornos',
        description: 'Retornos programados se perdem sem uma lista clara.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os retornos perdidos?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu consultório',
    helpItems: [
      { title: 'Agenda por profissional', description: 'Horários organizados mesmo com mais de um atendente.' },
      { title: 'Confirmações e reagendamentos', description: 'Acompanhe mudanças sem perder o histórico.' },
      { title: 'Financeiro claro', description: 'Recebidos e pendências por período.' },
    ],
    faq: [
      { q: 'O Gendaz é um sistema médico?', a: 'Não. É um sistema de agenda, organização e financeiro. Não oferece prontuário, prescrição, laudos, diagnóstico ou telemedicina.' },
      { q: 'Atende consultórios com vários profissionais?', a: 'Sim. A agenda é separada por profissional.' },
      { q: 'Dá para controlar retornos?', a: 'Sim. O histórico mostra atendimentos e próximos retornos.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Clínica Odontológica', path: '/sistema-para-clinica-odontologica' },
      { label: 'Psicólogos e Terapeutas', path: '/agenda-para-psicologos-e-terapeutas' },
      { label: 'Clínica de Estética', path: '/sistema-para-clinica-de-estetica' },
    ],
  },
  'yoga-e-pilates': {
    key: 'yoga-e-pilates',
    iaExamples: [
      'Quais clientes estão frequentando menos?',
      'Quais serviços tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/agenda-para-yoga-e-pilates',
    seoTitle: 'Agenda para Yoga e Pilates | Gendaz',
    seoDescription:
      'Organize alunos, aulas, horários, pagamentos e retornos do seu studio de yoga ou pilates com o Gendaz.',
    eyebrow: 'Agenda para yoga e pilates',
    h1: 'Aulas organizadas. Alunos acompanhados.',
    heroDescription:
      'Centralize horários, alunos e pagamentos sem depender de planilhas e mensagens.',
    agenda: [
      { time: '07:00', service: 'Pilates', status: 'Confirmado' },
      { time: '09:00', service: 'Yoga', status: 'Confirmado' },
      { time: '17:00', service: 'Pilates', status: 'Agendado' },
      { time: '19:00', service: 'Yoga', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar a rotina do studio',
    problems: [
      {
        title: 'Mudanças frequentes de horários',
        description: 'Reposições e trocas bagunçam a semana quando tudo é no grupo do WhatsApp.',
      },
      {
        title: 'Aulas e atendimentos difíceis de acompanhar',
        description: 'Sem lista clara, a organização de cada horário é um chute.',
      },
      {
        title: 'Alunos que param de frequentar',
        description: 'A ausência aparece tarde demais para recuperar o aluno.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os alunos que pararam?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu studio',
    helpItems: [
      { title: 'Horários organizados', description: 'Organize horários, clientes e profissionais em uma visão por dia.' },
      { title: 'Histórico de atendimentos', description: 'Acompanhe o histórico de atendimentos e perceba quem está sumindo.' },
      { title: 'Pagamentos claros', description: 'Recebidos e pendências por aluno e período.' },
    ],
    faq: [
      { q: 'Dá para organizar aulas individuais e em grupo?', a: 'Sim. A agenda organiza horários, clientes e profissionais.' },
      { q: 'Consigo ver a frequência dos alunos?', a: 'Sim. O histórico de atendimentos mostra os últimos atendimentos.' },
      { q: 'Funciona para mais de um instrutor?', a: 'Sim. A agenda é separada por profissional.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Personal Trainer', path: '/agenda-para-personal-trainer' },
      { label: 'Locação de quadra', path: '/sistema-para-locacao-de-quadra' },
      { label: 'Estética e Spa', path: '/sistema-para-estetica-e-spa' },
    ],
  },
  'cursos-e-tutoria': {
    key: 'cursos-e-tutoria',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais serviços tiveram menos movimento?',
      'Como estão meus recebimentos?',
    ],
    path: '/agenda-para-cursos-e-tutoria',
    seoTitle: 'Agenda para Cursos e Aulas Particulares | Gendaz',
    seoDescription:
      'Organize alunos, aulas, horários e pagamentos de cursos e tutorias com o Gendaz.',
    eyebrow: 'Agenda para cursos e tutoria',
    h1: 'Menos tempo organizando horários. Mais tempo ensinando.',
    heroDescription:
      'Centralize alunos, aulas, horários e pagamentos em uma única rotina.',
    agenda: [
      { time: '08:00', service: 'Aula particular', status: 'Confirmado' },
      { time: '10:00', service: 'Reforço escolar', status: 'Confirmado' },
      { time: '14:00', service: 'Tutoria', status: 'Agendado' },
      { time: '17:00', service: 'Aula individual', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma complicar a rotina de quem ensina',
    problems: [
      {
        title: 'Horários espalhados em mensagens',
        description: 'Cada aluno combina de um jeito e a semana vira um quebra-cabeça.',
      },
      {
        title: 'Reagendamentos sem registro',
        description: 'Reposições sem controle geram aula duplicada ou esquecida.',
      },
      {
        title: 'Pagamentos e alunos difíceis de acompanhar',
        description: 'Pagamentos sem fechamento claro no fim do mês.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os alunos que pausaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado às aulas que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda suas aulas',
    helpItems: [
      { title: 'Agenda de aulas clara', description: 'Todos os alunos e horários da semana em uma visão.' },
      { title: 'Reposições organizadas', description: 'Remarcações registradas sem perder o histórico.' },
      { title: 'Pagamentos por aluno', description: 'Recebidos e pendências organizados por aluno.' },
    ],
    faq: [
      { q: 'Funciona para aulas particulares e em grupo?', a: 'Sim. A agenda organiza horários, alunos e profissionais.' },
      { q: 'Dá para controlar reposições?', a: 'Sim. Remarcações ficam registradas no histórico do aluno.' },
      { q: 'Consigo controlar pagamentos?', a: 'Sim. O financeiro mostra recebidos e pendências por aluno.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Personal Trainer', path: '/agenda-para-personal-trainer' },
      { label: 'Yoga e Pilates', path: '/agenda-para-yoga-e-pilates' },
      { label: 'Estúdio Fotográfico', path: '/agenda-para-estudio-fotografico' },
    ],
  },
  hospedagens: {
    key: 'hospedagens',
    iaExamples: [
      'Quais períodos tiveram menos agendamentos?',
      'Quais serviços tiveram menos movimento?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-hospedagens',
    seoTitle: 'Sistema de Reservas para Pequenas Hospedagens | Gendaz',
    seoDescription:
      'Organize reservas, hóspedes, horários e pagamentos de pequenas hospedagens utilizando o Gendaz.',
    eyebrow: 'Gestão para pequenas hospedagens',
    h1: 'Reservas organizadas sem depender de conversas espalhadas.',
    heroDescription:
      'Centralize reservas, clientes e pagamentos em uma única rotina, feita para pequenas hospedagens.',
    agenda: [
      { time: '08:00', service: 'Check-out', status: 'Confirmado' },
      { time: '11:00', service: 'Preparação', status: 'Agendado' },
      { time: '14:00', service: 'Check-in', status: 'Confirmado' },
      { time: '17:00', service: 'Check-in', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar pequenas hospedagens',
    problems: [
      {
        title: 'Reservas anotadas manualmente',
        description: 'Datas em caderno e mensagens geram overbooking e esquecimento.',
      },
      {
        title: 'Alterações de datas',
        description: 'Antecipações e prorrogações sem registro bagunçam a ocupação.',
      },
      {
        title: 'Pagamentos difíceis de acompanhar',
        description: 'Sinais, diárias e restantes sem fechamento claro.',
      },
    ],
    simulator: true,
    simulatorKind: 'slots',
    simulatorTitle: 'Quanto valem as diárias vagas?',
    simulatorDescription:
      'Informe os valores e veja uma estimativa simples do potencial associado às vagas informadas.',
    helpTitle: 'Como o Gendaz ajuda sua hospedagem',
    helpItems: [
      { title: 'Reservas como agendamentos', description: 'Trate cada reserva como um agendamento: data, horário, cliente e observações em um só lugar.' },
      { title: 'Cadastro de clientes', description: 'Contato e histórico de atendimentos registrados por hóspede.' },
      { title: 'Pagamentos por reserva', description: 'Sinais, recebidos e pendências organizados no financeiro.' },
    ],
    faq: [
      { q: 'O Gendaz é um PMS hoteleiro completo?', a: 'Não. É um sistema de agenda, clientes e financeiro: as reservas são organizadas como agendamentos. Não oferece channel manager, integração com Airbnb ou Booking, mapa de ocupação hoteleira ou controle avançado de quartos.' },
      { q: 'Dá para controlar entradas e saídas?', a: 'Sim. As reservas ficam organizadas por data, com horários de check-in e check-out.' },
      { q: 'Consigo controlar pagamentos de diárias?', a: 'Sim. Sinais, recebidos e pendências ficam vinculados a cada reserva.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Locação de quadra', path: '/sistema-para-locacao-de-quadra' },
      { label: 'Cursos e Tutoria', path: '/agenda-para-cursos-e-tutoria' },
      { label: 'Serviços Automotivos', path: '/agenda-para-servicos-automotivos' },
    ],
  },
  'servicos-automotivos': {
    key: 'servicos-automotivos',
    iaExamples: [
      'Quais clientes estão há mais tempo sem retornar?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/agenda-para-servicos-automotivos',
    seoTitle: 'Agenda para Serviços Automotivos | Gendaz',
    seoDescription:
      'Organize clientes, horários, serviços, pagamentos e retornos do seu negócio automotivo com o Gendaz.',
    eyebrow: 'Agenda para serviços automotivos',
    h1: 'Serviços organizados do agendamento ao pagamento.',
    heroDescription:
      'Centralize clientes, serviços, horários e recebimentos em um único painel.',
    agenda: [
      { time: '08:00', service: 'Troca de óleo', status: 'Confirmado' },
      { time: '10:00', service: 'Revisão', status: 'Confirmado' },
      { time: '13:00', service: 'Alinhamento', status: 'Agendado' },
      { time: '15:30', service: 'Higienização', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma travar a rotina automotiva',
    problems: [
      {
        title: 'Horários anotados manualmente',
        description: 'Serviço atrasado e cliente esperando sem previsão.',
      },
      {
        title: 'Clientes que esquecem revisões',
        description: 'Revisão periódica sem acompanhamento significa agenda vazia.',
      },
      {
        title: 'Pagamentos espalhados',
        description: 'Serviços e mão de obra sem fechamento único por cliente.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as revisões perdidas?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos serviços que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu negócio',
    helpItems: [
      { title: 'Agenda por serviço e profissional', description: 'Horários organizados por tipo de serviço e duração.' },
      { title: 'Histórico de atendimentos do cliente', description: 'Últimos serviços e retornos registrados por cliente.' },
      { title: 'Recebimentos claros', description: 'Pagamentos por serviço, sem planilha paralela.' },
    ],
    faq: [
      { q: 'O Gendaz controla estoque de peças?', a: 'Não. O foco é agenda, clientes, serviços e financeiro, sem controle de peças ou estoque.' },
      { q: 'Dá para acompanhar revisões periódicas?', a: 'Sim. O histórico mostra últimos serviços e próximos retornos.' },
      { q: 'Funciona com mais de uma vaga de atendimento?', a: 'Sim. Os horários são organizados por serviço e profissional.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Locação de quadra', path: '/sistema-para-locacao-de-quadra' },
      { label: 'Petshop e Veterinária', path: '/sistema-para-petshop-e-veterinaria' },
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
    ],
  },
  'petshop-e-veterinaria': {
    key: 'petshop-e-veterinaria',
    iaExamples: [
      'Quais clientes deixaram de retornar?',
      'Quais serviços tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/sistema-para-petshop-e-veterinaria',
    seoTitle: 'Sistema de Agenda para Petshop e Veterinária | Gendaz',
    seoDescription:
      'Organize horários, clientes, serviços e pagamentos de petshops e pequenos atendimentos veterinários com o Gendaz.',
    eyebrow: 'Agenda para petshop e veterinária',
    h1: 'Atendimentos organizados para você cuidar melhor da rotina.',
    heroDescription:
      'Centralize horários, clientes, serviços e pagamentos em um único sistema.',
    agenda: [
      { time: '08:30', service: 'Banho', status: 'Confirmado' },
      { time: '10:00', service: 'Banho + tosa', status: 'Confirmado' },
      { time: '13:00', service: 'Atendimento', status: 'Agendado' },
      { time: '15:00', service: 'Retorno', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar a rotina pet',
    problems: [
      {
        title: 'Agenda cheia de alterações',
        description: 'Banho, tosa e consultas remarcados o dia todo sem registro.',
      },
      {
        title: 'Retornos difíceis de acompanhar',
        description: 'Banhos recorrentes e retornos se perdem sem lista clara.',
      },
      {
        title: 'Pagamentos e clientes espalhados',
        description: 'Serviços recorrentes e avulsos sem fechamento único.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam os banhos que não voltaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu petshop',
    helpItems: [
      { title: 'Agenda por serviço e profissional', description: 'Banho, tosa e atendimentos organizados por horário e profissional.' },
      { title: 'Cadastro de clientes', description: 'Dados de contato e observações operacionais registrados por cliente.' },
      { title: 'Histórico de atendimentos', description: 'Últimos atendimentos acompanhados por cliente, com recebidos e pendências no financeiro.' },
    ],
    faq: [
      { q: 'O Gendaz é um prontuário veterinário?', a: 'Não. É um sistema de agenda, organização e financeiro. Não oferece prescrição, diagnóstico, ficha clínica ou controle de vacinação.' },
      { q: 'Dá para acompanhar os atendimentos de cada cliente?', a: 'Sim. O histórico de atendimentos fica vinculado ao cadastro do cliente.' },
      { q: 'Funciona com mais de um profissional?', a: 'Sim. A agenda é separada por profissional e serviço.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Serviços Automotivos', path: '/agenda-para-servicos-automotivos' },
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
      { label: 'Consultórios', path: '/sistema-para-consultorios' },
    ],
  },
  'estudio-fotografico': {
    key: 'estudio-fotografico',
    iaExamples: [
      'Quais serviços tiveram menos movimento?',
      'Quais períodos tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/agenda-para-estudio-fotografico',
    seoTitle: 'Agenda para Estúdio Fotográfico | Gendaz',
    seoDescription:
      'Organize sessões, clientes, horários e pagamentos do seu estúdio fotográfico com o Gendaz.',
    eyebrow: 'Agenda para estúdio fotográfico',
    h1: 'Cada sessão no horário. Cada cliente organizado.',
    heroDescription:
      'Centralize sessões, clientes e pagamentos sem depender de mensagens espalhadas.',
    agenda: [
      { time: '09:00', service: 'Ensaio corporativo', status: 'Confirmado' },
      { time: '11:00', service: 'Ensaio família', status: 'Confirmado' },
      { time: '14:00', service: 'Gestante', status: 'Agendado' },
      { time: '17:00', service: 'Retrato', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma desorganizar o estúdio',
    problems: [
      {
        title: 'Datas espalhadas pelo WhatsApp',
        description: 'Ensaios marcados em conversas diferentes, sem calendário único.',
      },
      {
        title: 'Reagendamentos frequentes',
        description: 'Mudança de data por clima ou cliente sem registro claro.',
      },
      {
        title: 'Pagamentos difíceis de acompanhar',
        description: 'Sinal, restante e entrega sem vínculo com a sessão.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as sessões perdidas?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado às sessões que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu estúdio',
    helpItems: [
      { title: 'Agenda de sessões', description: 'Ensaios organizados por data, serviço e cliente.' },
      { title: 'Observações operacionais', description: 'Use as observações do atendimento para registrar informações operacionais de cada sessão.' },
      { title: 'Sinais e restantes', description: 'Pagamentos vinculados a cada sessão contratada.' },
    ],
    faq: [
      { q: 'O Gendaz armazena ou entrega fotos?', a: 'Não. O foco é agenda, clientes e financeiro, sem armazenamento ou entrega de arquivos.' },
      { q: 'Dá para controlar sinal e restante?', a: 'Sim. Pagamentos ficam vinculados a cada sessão.' },
      { q: 'Funciona para externas e studio?', a: 'Sim. Use as observações do atendimento para registrar informações operacionais como local e horário.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Estúdio de Tatuagem', path: '/agenda-para-estudio-de-tatuagem' },
      { label: 'Cursos e Tutoria', path: '/agenda-para-cursos-e-tutoria' },
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
    ],
  },
  'estudio-de-tatuagem': {
    key: 'estudio-de-tatuagem',
    iaExamples: [
      'Quais clientes deixaram de retornar?',
      'Quais serviços tiveram menos movimento?',
      'Como está meu financeiro este mês?',
    ],
    path: '/agenda-para-estudio-de-tatuagem',
    seoTitle: 'Agenda para Estúdio de Tatuagem | Gendaz',
    seoDescription:
      'Organize clientes, sessões, horários e pagamentos do seu estúdio de tatuagem utilizando o Gendaz.',
    eyebrow: 'Agenda para estúdio de tatuagem',
    h1: 'Sua agenda organizada entre uma sessão e outra.',
    heroDescription:
      'Centralize clientes, sessões, retornos e pagamentos em um único painel.',
    agenda: [
      { time: '09:00', service: 'Sessão pequena', status: 'Confirmado' },
      { time: '11:00', service: 'Continuação', status: 'Confirmado' },
      { time: '14:30', service: 'Sessão', status: 'Agendado' },
      { time: '17:30', service: 'Retorno', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma bagunçar o studio',
    problems: [
      {
        title: 'Orçamentos e horários em conversas diferentes',
        description: 'Orçamento aprovado num chat, sessão marcada em outro — e nada se conecta.',
      },
      {
        title: 'Sessões longas e reagendamentos',
        description: 'Projetos com várias sessões sem sequência clara viram confusão.',
      },
      {
        title: 'Clientes que precisam retornar',
        description: 'Retoques e continuações sem data marcada esfriam.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as sessões que não voltaram?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado às sessões que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda seu studio',
    helpItems: [
      { title: 'Sessões por projeto', description: 'Acompanhe continuações e retoques por cliente.' },
      { title: 'Agenda por tatuador', description: 'Cada profissional com seus horários e sessões longas organizadas.' },
      { title: 'Sinais e pagamentos', description: 'Entradas e restantes vinculados a cada projeto.' },
    ],
    faq: [
      { q: 'Dá para organizar projetos com várias sessões?', a: 'Sim. As sessões ficam vinculadas ao cadastro do cliente.' },
      { q: 'Funciona com mais de um tatuador?', a: 'Sim. A agenda é separada por profissional.' },
      { q: 'Consigo controlar sinais?', a: 'Sim. Pagamentos ficam vinculados a cada sessão.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Estúdio Fotográfico', path: '/agenda-para-estudio-fotografico' },
      { label: 'Barbearia', path: '/sistema-para-barbearia' },
      { label: 'Salão de Beleza', path: '/sistema-para-salao-de-beleza' },
    ],
  },
  'psicologos-e-terapeutas': {
    key: 'psicologos-e-terapeutas',
    iaExamples: [
      'Quais clientes reduziram a frequência?',
      'Quais períodos tiveram menos agendamentos?',
      'Como estão meus recebimentos?',
    ],
    path: '/agenda-para-psicologos-e-terapeutas',
    seoTitle: 'Agenda para Psicólogos e Terapeutas | Gendaz',
    seoDescription:
      'Organize horários, clientes, atendimentos e pagamentos da sua rotina profissional com o Gendaz.',
    eyebrow: 'Agenda para psicólogos e terapeutas',
    h1: 'Sua agenda organizada para você focar nos atendimentos.',
    heroDescription:
      'Centralize horários, clientes e pagamentos em uma rotina mais clara. Foco em agenda e organização, sem prontuário clínico.',
    agenda: [
      { time: '08:00', service: 'Atendimento', status: 'Confirmado' },
      { time: '09:00', service: 'Atendimento', status: 'Confirmado' },
      { time: '14:00', service: 'Retorno', status: 'Agendado' },
      { time: '16:00', service: 'Atendimento', status: 'Confirmado' },
    ],
    problemsTitle: 'O que costuma pesar na rotina de atendimentos',
    problems: [
      {
        title: 'Reagendamentos frequentes',
        description: 'Trocas semanais sem registro geram encaixes duplicados.',
      },
      {
        title: 'Horários espalhados',
        description: 'Atendimentos combinados no WhatsApp, sem visão da semana.',
      },
      {
        title: 'Pagamentos difíceis de acompanhar',
        description: 'Sessões avulsas e pacotes sem fechamento claro no mês.',
      },
    ],
    simulator: true,
    simulatorTitle: 'Quanto representam as sessões remarcadas?',
    simulatorDescription:
      'Informe seus números e veja o valor potencial associado aos atendimentos que deixaram de acontecer.',
    helpTitle: 'Como o Gendaz ajuda sua rotina',
    helpItems: [
      { title: 'Agenda semanal clara', description: 'Atendimentos fixos e reposições organizados por dia.' },
      { title: 'Reagendamentos simples', description: 'Remarcar sem perder o histórico de frequência.' },
      { title: 'Financeiro da rotina', description: 'Sessões recebidas e pendentes por período.' },
    ],
    faq: [
      { q: 'O Gendaz é um prontuário psicológico?', a: 'Não. É um sistema de agenda, organização e financeiro. Não oferece prontuário, diagnóstico, laudo, anamnese, registro terapêutico ou prescrição, nem armazena dados clínicos.' },
      { q: 'Dá para organizar atendimentos recorrentes?', a: 'Sim. Os horários fixos ficam visíveis na agenda semanal.' },
      { q: 'Consigo controlar pagamentos por sessão?', a: 'Sim. Recebidos e pendências ficam organizados por cliente e período.' },
      { q: 'Preciso instalar algum programa?', a: 'Não. Funciona direto do navegador.' },
    ],
    related: [
      { label: 'Consultórios', path: '/sistema-para-consultorios' },
      { label: 'Clínica Odontológica', path: '/sistema-para-clinica-odontologica' },
      { label: 'Yoga e Pilates', path: '/agenda-para-yoga-e-pilates' },
    ],
  },
}

export const SEGMENT_LIST = Object.values(SEGMENTS)

export const SEGMENT_PATHS = SEGMENT_LIST.map((s) => s.path)

export function getSegmentByPath(pathname) {
  return SEGMENT_LIST.find((s) => s.path === pathname) || null
}
