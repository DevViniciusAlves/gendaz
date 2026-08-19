# Gendaz

Sistema para atendimento interno com foco em agenda, clientes, serviços, profissionais, financeiro e pagamentos.

## Estrutura

- `backend/`: Java 17 + Spring Boot, arquitetura feature-based por domínio.
- `frontend/`: React + Vite + JavaScript, com interface web responsiva.

## Como rodar

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Banco de dados

O backend usa perfis separados para desenvolvimento e produção. Em produção, configure as variáveis de ambiente do banco e do deploy conforme o arquivo `README_DEPLOY.md`.

## Observações

- O sistema é voltado para uso interno da equipe.
- O cadastro inicial usa teste gratuito de 7 dias.
- As integrações reais ficam preparadas para evoluir sem alterar a base principal.

