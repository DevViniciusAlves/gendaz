export const demoUsers = [
  { id: 1, nome: 'Ana Basico', email: 'basico@agendapro.com', senha: 'Senha123!', perfil: 'DONO', plano: 'BASICO', empresaId: 1 },
  { id: 2, nome: 'Bruno Pro', email: 'pro@agendapro.com', senha: 'Senha123!', perfil: 'DONO', plano: 'PRO', empresaId: 1 },
]

export const initialData = {
  __version: 4,
  empresa: {
    id: 1,
    nomeFantasia: 'AgendaPro Matriz',
    documento: '12.345.678/0001-90',
    telefone: '(65) 99999-0000',
    email: 'contato@agendapro.com',
  },
  usuarios: demoUsers.map(({ senha, ...usuario }) => usuario),
  equipe: [
    { id: 1, nome: 'Ana Basico', email: 'basico@agendapro.com', perfil: 'DONO', status: 'ATIVO', presenca: 'ONLINE', ultimoAcesso: 'Agora' },
    { id: 2, nome: 'Bruno Pro', email: 'pro@agendapro.com', perfil: 'DONO', status: 'INATIVO', presenca: 'OFFLINE', ultimoAcesso: 'Hoje às 09:40' },
  ],
  clientes: [
    { id: 1, nome: 'Ana Souza', telefone: '(65) 99911-1111', email: 'ana@email.com', observacoes: 'Prefere atendimento pela manhã.', totalGasto: 1058.8 },
    { id: 2, nome: 'Nori Garden', telefone: '(65) 99922-2222', email: 'nori@email.com', observacoes: 'Cliente recorrente. Sempre pede confirmação.', totalGasto: 469.6 },
    { id: 3, nome: 'Kaito Bistrô', telefone: '(65) 99933-3333', email: 'kaito@email.com', observacoes: 'Tem interesse em pacote mensal.', totalGasto: 299.8 },
    { id: 4, nome: 'Casa Midori', telefone: '(65) 99944-4444', email: 'midori@email.com', observacoes: 'Precisa de nota fiscal em todos os atendimentos.', totalGasto: 234.7 },
    { id: 5, nome: 'Lead sem pagamento', telefone: '(65) 99955-5555', email: 'lead@email.com', observacoes: 'Ainda não realizou pagamento.', totalGasto: 0 },
  ],
  servicos: [
    { id: 1, nome: 'Consulta', descricao: 'Atendimento completo', duracaoMinutos: 60, valor: 180, status: 'ATIVO', vendas: 18 },
    { id: 2, nome: 'Retorno', descricao: 'Acompanhamento rápido', duracaoMinutos: 30, valor: 90, status: 'ATIVO', vendas: 11 },
    { id: 3, nome: 'Avaliação inicial', descricao: 'Primeiro atendimento', duracaoMinutos: 45, valor: 140, status: 'ATIVO', vendas: 8 },
  ],
  profissionais: [
    { id: 1, nome: 'Dra. Marina', especialidade: 'Clínica geral', telefone: '(65) 98888-1111', status: 'ATIVO' },
    { id: 2, nome: 'Dr. Rafael', especialidade: 'Fisioterapia', telefone: '(65) 98888-2222', status: 'ATIVO' },
  ],
  agendamentos: [
    { id: 1, clienteId: 1, clienteNome: 'Ana Souza', servicoId: 1, servicoNome: 'Consulta', profissionalId: 1, profissionalNome: 'Dra. Marina', data: '2026-06-16', horaInicio: '09:00', horaFim: '10:00', status: 'CONFIRMADO', observacoes: 'Chegar 10 minutos antes.' },
    { id: 2, clienteId: 2, clienteNome: 'Nori Garden', servicoId: 2, servicoNome: 'Retorno', profissionalId: 2, profissionalNome: 'Dr. Rafael', data: '2026-06-16', horaInicio: '14:00', horaFim: '14:30', status: 'PENDENTE', observacoes: 'Aguardando confirmação.' },
    { id: 3, clienteId: 3, clienteNome: 'Kaito Bistrô', servicoId: 3, servicoNome: 'Avaliação inicial', profissionalId: 1, profissionalNome: 'Dra. Marina', data: '2026-06-17', horaInicio: '10:30', horaFim: '11:15', status: 'FINALIZADO', observacoes: 'Gerou pagamento.' },
    { id: 4, clienteId: 4, clienteNome: 'Casa Midori', servicoId: 1, servicoNome: 'Consulta', profissionalId: 1, profissionalNome: 'Dra. Marina', data: '2026-06-19', horaInicio: '18:30', horaFim: '19:30', status: 'CONFIRMADO', observacoes: 'Enviar lembrete no dia anterior.' },
  ],
  conversas: [
    { id: 1, clienteId: 1, clienteNome: 'Ana Souza', clienteTelefone: '(65) 99911-1111', status: 'ABERTA', ultimaMensagem: 'Pode confirmar minha consulta?', dataUltimaMensagem: '2026-06-16T09:05:00' },
    { id: 2, clienteId: 2, clienteNome: 'Nori Garden', clienteTelefone: '(65) 99922-2222', status: 'PENDENTE', ultimaMensagem: 'Qual o valor do retorno?', dataUltimaMensagem: '2026-06-16T08:40:00' },
    { id: 3, clienteId: 3, clienteNome: 'Kaito Bistrô', clienteTelefone: '(65) 99933-3333', status: 'ABERTA', ultimaMensagem: 'Tem horário amanhã?', dataUltimaMensagem: '2026-06-15T17:20:00' },
  ],
  mensagens: [
    { id: 1, conversaId: 1, conteudo: 'Olá, gostaria de horários para hoje.', direcao: 'CLIENTE_PARA_EMPRESA', tipo: 'TEXTO', dataEnvio: '2026-06-16T08:55:00' },
    { id: 2, conversaId: 1, conteudo: 'Temos 09:00, 10:30 e 15:00 disponíveis.', direcao: 'EMPRESA_PARA_CLIENTE', tipo: 'HORARIOS_DISPONIVEIS', dataEnvio: '2026-06-16T08:57:00' },
    { id: 3, conversaId: 1, conteudo: 'Pode confirmar minha consulta?', direcao: 'CLIENTE_PARA_EMPRESA', tipo: 'CONFIRMACAO', dataEnvio: '2026-06-16T09:05:00' },
    { id: 4, conversaId: 2, conteudo: 'Qual o valor do retorno?', direcao: 'CLIENTE_PARA_EMPRESA', tipo: 'TEXTO', dataEnvio: '2026-06-16T08:40:00' },
  ],
  pagamentos: [
    { id: 1, clienteId: 1, clienteNome: 'Ana Souza', valor: 180, metodoPagamento: 'PIX', status: 'PAGO', dataPagamento: '2026-06-16T10:05:00' },
    { id: 2, clienteId: 2, clienteNome: 'Nori Garden', valor: 90, metodoPagamento: 'CARTAO', status: 'PENDENTE', dataPagamento: null },
    { id: 3, clienteId: 3, clienteNome: 'Kaito Bistrô', valor: 299.8, metodoPagamento: 'DINHEIRO', status: 'PAGO', dataPagamento: '2026-06-15T11:20:00' },
    { id: 4, clienteId: 4, clienteNome: 'Casa Midori', valor: 234.7, metodoPagamento: 'BOLETO', status: 'PENDENTE', dataPagamento: null },
  ],
  notasFiscais: [
    { id: 1, clienteId: 3, clienteNome: 'Kaito Bistrô', pedido: '#1048', valor: 299.8, status: 'EMITIDA', numeroFake: 'NF-e 000.001', protocolo: 'BAK260615A', diagnostico: 'Documento processado sem erros.', dataEmissao: '2026-06-15T10:10:00' },
    { id: 2, clienteId: 2, clienteNome: 'Nori Garden', pedido: '#1047', valor: 179.8, status: 'EMITIDA', numeroFake: 'NF-e 000.002', protocolo: 'BAK260614F', diagnostico: 'Documento processado sem erros.', dataEmissao: '2026-06-15T12:20:00' },
    { id: 3, clienteId: 2, clienteNome: 'Nori Garden', pedido: '#1044', valor: 117.4, status: 'REPROVADA', numeroFake: 'Aguardando número', protocolo: 'BAK260611P', diagnostico: 'CNPJ do destinatário inválido ou incompleto.', dataEmissao: '2026-06-15T13:40:00' },
    { id: 4, clienteId: 4, clienteNome: 'Casa Midori', pedido: '#1045', valor: 149.9, status: 'REPROVADA', numeroFake: 'Aguardando número', protocolo: 'BAK260612R', diagnostico: 'CNPJ do destinatário inválido ou incompleto.', dataEmissao: '2026-06-14T15:40:00' },
  ],
  entregas: [
    { id: 1, protocolo: 'BAK5103246', clienteId: 2, clienteNome: 'Nori Garden', responsavel: 'Caio Mendes', endereco: 'Rua das Flores, 100', status: 'NOVO', observacoes: 'Kit pós-atendimento', dataPrevisao: '2026-06-17', horaInicio: '18:00', horaFim: '19:00', total: 524.3 },
    { id: 2, protocolo: 'BAK3744856', clienteId: 2, clienteNome: 'Nori Garden', responsavel: 'Caio Mendes', endereco: 'Av. Japão, 47', status: 'ENTREGUE', observacoes: 'Entrega concluída.', dataPrevisao: '2026-06-16', horaInicio: '18:00', horaFim: '19:00', total: 469.6 },
    { id: 3, protocolo: 'BAK260615A', clienteId: 3, clienteNome: 'Kaito Bistrô', responsavel: 'Marina Alves', endereco: 'Rua Central, 22', status: 'ENTREGUE', observacoes: 'Confirmação por e-mail.', dataPrevisao: '2026-06-16', horaInicio: '18:00', horaFim: '19:00', total: 299.8 },
  ],
  produtos: [
    { id: 1, nome: 'Notebook Dell Latitude', categoria: 'Notebook', sku: 'ELE-NB-001', estoque: 8, valor: 4290, status: 'ATIVO' },
    { id: 2, nome: 'Monitor LG UltraWide 29"', categoria: 'Monitor', sku: 'ELE-MO-002', estoque: 12, valor: 1390, status: 'ATIVO' },
    { id: 3, nome: 'Headset Logitech H390', categoria: 'Periférico', sku: 'ELE-HS-003', estoque: 25, valor: 219, status: 'ATIVO' },
    { id: 4, nome: 'Roteador TP-Link AX1800', categoria: 'Rede', sku: 'ELE-RT-004', estoque: 5, valor: 479, status: 'BAIXO_ESTOQUE' },
  ],
  pedidos: [
    { id: 1, protocolo: 'PED-1001', clienteNome: 'Nori Garden', produto: 'Notebook Dell Latitude', valor: 4290, status: 'PENDENTE', data: '2026-06-16' },
    { id: 2, protocolo: 'PED-1002', clienteNome: 'Kaito Bistrô', produto: 'Monitor LG UltraWide 29"', valor: 1390, status: 'ENTREGA', data: '2026-06-15' },
    { id: 3, protocolo: 'PED-1003', clienteNome: 'Casa Midori', produto: 'Headset Logitech H390', valor: 438, status: 'FINALIZADO', data: '2026-06-14' },
    { id: 4, protocolo: 'PED-1004', clienteNome: 'Ana Souza', produto: 'Roteador TP-Link AX1800', valor: 479, status: 'REPROVADO', data: '2026-06-13' },
  ],
}
