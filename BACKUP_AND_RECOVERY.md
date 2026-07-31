# Backup and Recovery - AgendNew

## Objetivo
Definir o minimo operacional para backup, restore e recuperacao apos incidente.

## 1. Banco de dados
- Manter backup automatizado do PostgreSQL do Render com retencao definida pelo ambiente de producao.
- Testar restauracao em ambiente separado antes de confiar no backup.
- Garantir que as variaveis do banco estejam fora do Git.

## 2. O que deve ser salvo
- Estrutura e dados de empresas
- Usuarios e permissões
- Clientes, servicos, profissionais e agendamentos
- Pagamentos, assinaturas, notas fiscais, entregas, conversas e auditoria

## 3. Restauração
- Verificar integridade do backup antes do restore
- Restaurar em banco temporario primeiro
- Validar login, consultas e integridade por empresa
- Reativar apenas depois da validacao

## 4. Recuperacao apos incidente
- Isolar a origem do problema
- Revogar sessoes e chaves afetadas
- Corrigir a causa raiz
- Executar restore se houver perda de dados
- Registrar evento no plano de incidente

## 5. O que ainda falta testar
- Restore completo em ambiente de homologacao
- Tempo de recuperacao real
- Consistencia de tenants apos restore
- Procedimento de reversao de migracoes, se necessario
