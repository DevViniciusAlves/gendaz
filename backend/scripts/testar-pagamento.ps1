<#
.SYNOPSIS
    Teste automatizado do fluxo de pagamento do Gendaz sem pagar de verdade.

.DESCRIPTION
    Executa o ciclo completo de pagamento PRO contra o gateway LOCAL (mock):
      1. Cria conta PRO (empresa entra em PENDENTE_PAGAMENTO e gera cobranca)
      2. Valida que o pagamento foi criado como PAYMENT_PENDING
      3. Simula o webhook da Cakto (purchase_approved) - mesmo endpoint/caminho de producao
      4. Verifica que o pagamento virou PAYMENT_APPROVED e a conta foi liberada (ATIVA)

    Se TODOS os passos passarem, a regra de negocio de pagamento esta correta.
    (A integracao HTTP real com a Cakto/Mercado Pago deve ser validada no sandbox do provedor.)

.EXAMPLE
    # Rodar contra localhost com os padroes do profile dev
    .\testar-pagamento.ps1

.EXAMPLE
    # Rodar contra outro host/producao com secret proprio
    .\testar-pagamento.ps1 -BaseUrl "http://localhost:8080" -WebhookSecret "meu-segredo"

.PARAMETER BaseUrl
    URL base do backend. Padrao: http://localhost:8080

.PARAMETER WebhookSecret
    Segredo do webhook (payment.cakto-webhook-secret). Padrao dev: local-dev-webhook-secret

.PARAMETER Email
    E-mail de teste. Se nao informado, gera um unico (evita conflito em reexecucoes).

.PARAMETER Senha
    Senha da conta de teste. Padrao: Teste@1234

.PARAMETER Plano
    Plano contratado. Padrao: PRO
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$WebhookSecret = 'local-dev-webhook-secret',
    [string]$Email = '',
    [string]$Senha = 'Teste@1234',
    [string]$Plano = 'PRO'
)

$ErrorActionPreference = 'Stop'
$script:PassCount = 0
$script:FailCount = 0
$script:HasFail = $false

# ---------- Helpers ----------

function Write-Step {
    param([string]$Title)
    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if ($Condition) {
        Write-Host "    [PASS] $Message" -ForegroundColor Green
        $script:PassCount++
    }
    else {
        Write-Host "    [FAIL] $Message" -ForegroundColor Red
        $script:FailCount++
        $script:HasFail = $true
    }
}

function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $params = @{
        Method        = $Method
        Uri           = $Uri
        Headers       = $Headers
        UseBasicParsing = $true
        TimeoutSec    = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    try {
        $response = Invoke-WebRequest @params
        $parsed = $null
        if ($response.Content) {
            try { $parsed = $response.Content | ConvertFrom-Json } catch { $parsed = $response.Content }
        }
        return @{ StatusCode = [int]$response.StatusCode; Data = $parsed; Ok = $true }
    }
    catch {
        $status = 0
        $bodyText = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $bodyText = $reader.ReadToEnd()
            }
            catch { }
        }
        $parsed = $null
        try { $parsed = $bodyText | ConvertFrom-Json } catch { }
        return @{ StatusCode = $status; Data = $parsed; Ok = $false; ErrorText = $bodyText }
    }
}

# ---------- Inicio ----------

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  TESTE DE PAGAMENTO - Gendaz (gateway local / webhook Cakto)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  BaseUrl      : $BaseUrl"
Write-Host "  Plano        : $Plano"
Write-Host "  WebhookSecret: $WebhookSecret"

# 0. Health check
Write-Step "Health check"
$health = Invoke-Api -Uri "$BaseUrl/api/health"
Assert-True ($health.Ok -or $health.StatusCode -eq 200) "Backend respondeu em $BaseUrl (HTTP $($health.StatusCode))"
if (-not ($health.Ok -or $health.StatusCode -eq 200)) {
    Write-Host "  Backend indisponivel. Suba a aplicacao (profile dev) e tente novamente." -ForegroundColor Yellow
    exit 1
}

# 1. Criar dados unicos de teste
if (-not $Email) {
    $sufixo = Get-Date -Format 'yyyyMMddHHmmss'
    $Email = "pag-teste-$sufixo@gendaz.local"
}
$nomeEmpresa = "Empresa Teste Pag"
$nomeProprietario = "Proprietario Teste"
$telefone = "11999999999"
$documentoTipo = "CPF"
$documentoNumero = "12345678909"
$aceiteTermos = $true

Write-Step "Criando conta $Plano (email: $Email)"
$criarBody = @{
    nomeEmpresa      = $nomeEmpresa
    nomeProprietario = $nomeProprietario
    email            = $Email
    telefone         = $telefone
    documentoTipo    = $documentoTipo
    documentoNumero  = $documentoNumero
    senha            = $Senha
    confirmarSenha   = $Senha
    plano            = $Plano
    aceiteTermos     = $aceiteTermos
}
$criar = Invoke-Api -Method 'POST' -Uri "$BaseUrl/api/auth/criar-conta" -Body $criarBody

$usuario = $criar.Data.usuario
$pagamentoPlano = $criar.Data.pagamentoPlano
$statusConta = $criar.Data.statusConta

Assert-True ($criar.Ok -and $null -ne $usuario) "Conta criada com sucesso (HTTP $($criar.StatusCode))"
if (-not ($criar.Ok -and $null -ne $usuario)) {
    Write-Host "    Resposta: $($criar.ErrorText)" -ForegroundColor Yellow
    exit 1
}

$usuarioId = $usuario.id
$empresaId = $usuario.empresaId
Write-Host "    usuarioId = $usuarioId | empresaId = $empresaId"

Assert-True ($statusConta -eq 'ACCOUNT_PENDING_PAYMENT') "statusConta = ACCOUNT_PENDING_PAYMENT (recebido: $statusConta)"
Assert-True ($null -ne $pagamentoPlano) "Cobranca do plano gerada no cadastro"

# 2. Validar o pagamento criado
Write-Step "Validando cobranca criada (gateway local)"
$pagamentoId = $null
if ($null -ne $pagamentoPlano) {
    $pagamentoId = $pagamentoPlano.id
    $paymentReference = $pagamentoPlano.paymentReference
    $providerPaymentId = $pagamentoPlano.providerPaymentId
    $valor = $pagamentoPlano.valor

    Write-Host "    pagamentoId       = $pagamentoId"
    Write-Host "    paymentReference  = $paymentReference"
    Write-Host "    providerPaymentId = $providerPaymentId"
    Write-Host "    valor             = $valor"
    Write-Host "    status            = $($pagamentoPlano.status)"

    Assert-True ($pagamentoPlano.status -eq 'PAYMENT_PENDING') "Pagamento criado como PAYMENT_PENDING"
    Assert-True (-not [string]::IsNullOrWhiteSpace($paymentReference)) "paymentReference preenchido (AGE-PRO-...)"
    Assert-True (-not [string]::IsNullOrWhiteSpace($providerPaymentId)) "providerPaymentId preenchido (pay_...)"
}

# 3. Simular webhook da Cakto (purchase_approved)
if ($null -eq $pagamentoPlano) {
    Write-Step "Simulando webhook da Cakto (purchase_approved)"
    Assert-True $false "Nao foi possivel simular o webhook: cobranca do plano nao foi criada"
}
else {
    Write-Step "Simulando webhook da Cakto (purchase_approved)"
    $headersCakto = @{ 'x-cakto-signature' = $WebhookSecret }
    $webhookBody = @{
        event             = 'purchase_approved'
        payment_reference = $paymentReference
        status            = 'approved'
        amount            = [decimal]$valor
        paidAt            = (Get-Date).ToString('o')
    }
    $webhook = Invoke-Api -Method 'POST' -Uri "$BaseUrl/api/pagamentos/planos/webhook/cakto" -Headers $headersCakto -Body $webhookBody
    # Obs: o endpoint pode responder 200 com corpo vazio mesmo quando ignora o evento;
    # a validacao real e feita no passo seguinte (status do pagamento).
}

# 4. Verificar a liberacao da conta
Write-Step "Verificando liberacao da conta"
$headersUsuario = @{ 'X-Usuario-Id' = "$usuarioId" }
if ($null -eq $pagamentoId) {
    Assert-True $false "Falha ao consultar verificacao do pagamento (sem cobranca gerada)"
}
else {
    $verificar = Invoke-Api -Method 'GET' -Uri "$BaseUrl/api/pagamentos/planos/empresa/$empresaId/$pagamentoId/verificar" -Headers $headersUsuario

    if ($verificar.Ok -and $null -ne $verificar.Data) {
        $statusVerificacao = $verificar.Data.statusVerificacao
        $statusEmpresa = $verificar.Data.statusEmpresa
        $statusAssinatura = $verificar.Data.statusAssinatura
        $statusPagamento = $verificar.Data.pagamento.status

        Write-Host "    statusVerificacao = $statusVerificacao"
        Write-Host "    statusPagamento   = $statusPagamento"
        Write-Host "    statusEmpresa     = $statusEmpresa"
        Write-Host "    statusAssinatura  = $statusAssinatura"

        Assert-True ($statusPagamento -eq 'PAYMENT_APPROVED') "Pagamento aprovado (PAYMENT_APPROVED)"
        Assert-True ($statusVerificacao -eq 'APPROVED') "Verificacao retornou APPROVED"
        Assert-True ($statusEmpresa -eq 'ATIVA') "Empresa liberada (ATIVA)"
        Assert-True ($statusAssinatura -eq 'ATIVA') "Assinatura ativa (ATIVA)"
    }
    else {
        Assert-True $false "Falha ao consultar verificacao do pagamento"
        if ($verificar.ErrorText) { Write-Host "    Resposta: $($verificar.ErrorText)" -ForegroundColor Yellow }
    }
}

# 5. Validar que o login agora funciona (conta liberada)
Write-Step "Validando login apos liberacao"
$loginBody = @{ email = $Email; senha = $Senha }
$login = Invoke-Api -Method 'POST' -Uri "$BaseUrl/api/auth/login" -Body $loginBody
if ($login.Ok -and $null -ne $login.Data) {
    $loginStatus = $login.Data.statusConta
    Write-Host "    statusConta apos login = $loginStatus"
    Assert-True ($loginStatus -ne 'ACCOUNT_PENDING_PAYMENT' -and $loginStatus -ne 'ACCOUNT_INACTIVE') "Login liberado (statusConta: $loginStatus)"
}
else {
    Assert-True $false "Falha ao autenticar apos liberacao"
    if ($login.ErrorText) { Write-Host "    Resposta: $($login.ErrorText)" -ForegroundColor Yellow }
}

# ---------- Resumo ----------

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RESULTADO: $($script:PassCount) passou / $($script:FailCount) falhou" -ForegroundColor $(if ($script:HasFail) { 'Red' } else { 'Green' })
Write-Host "============================================================" -ForegroundColor Cyan

if ($script:HasFail) {
    Write-Host ""
    Write-Host "  Dica: rode o backend com o profile dev (payment.provider=local) e" -ForegroundColor Yellow
    Write-Host "  confira se CAKTO_WEBHOOK_SECRET / payment.cakto-webhook-secret estao" -ForegroundColor Yellow
    Write-Host "  configurados com o mesmo valor de -WebhookSecret." -ForegroundColor Yellow
    exit 1
}
else {
    Write-Host ""
    Write-Host "  Fluxo de pagamento OK. Para validar a integracao real com o" -ForegroundColor Green
    Write-Host "  provedor, repita no sandbox da Cakto/Mercado Pago." -ForegroundColor Green
    exit 0
}
