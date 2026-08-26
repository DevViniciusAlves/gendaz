<#
.SYNOPSIS
  Verifica mojibake / perda de encoding (U+FFFD) nos arquivos-fonte de texto do projeto.

.DESCRIPTION
  Detecta sequências típicas de mojibake UTF-8 (ex.: Ã£, Ãµ, Ã©, â€", Â°) e
  caracteres U+FFFD (perda definitiva de dado). Ignora gerados/binários.
  Retorna exit code 1 quando encontra ocorrências.

.EXAMPLE
  powershell -File scripts/check-mojibake.ps1
  powershell -File scripts/check-mojibake.ps1 -Root C:\projeto
#>
param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$exts = @('.js','.jsx','.ts','.tsx','.java','.json','.properties','.yml','.yaml','.xml','.sql','.html','.css','.scss','.md','.vue','.txt','.graphql','.kt')
$skipDirs = 'node_modules|dist|build|target|\.git|\.idea|out|__pycache__|\.next|coverage'

# Mojibake = U+00C3(U+00C2) seguido de byte alto (U+0080-U+00BF),
# sequencia U+00E2 U+20AC (mojibake de aspas/travessoes), ou U+FFFD.
$mojibakeRegex = '[\u00C3\u00C2][\u0080-\u00BF]|\u00E2\u20AC|\uFFFD'

$files = Get-ChildItem -Path $Root -Recurse -File |
  Where-Object { $exts -contains $_.Extension.ToLower() -and $_.FullName -notmatch $skipDirs }

$found = @()
foreach ($f in $files) {
  try {
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
  } catch { continue }
  $lines = $text -split "`n", -1
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match $mojibakeRegex) {
      $rel = $f.FullName.Substring($Root.Length)
      $ctx = $lines[$i] -replace '\s+', ' '
      if ($ctx.Length -gt 120) { $ctx = $ctx.Substring(0, 120) }
      $found += ("{0}:{1}: {2}" -f $rel, ($i + 1), $ctx)
    }
  }
}

if ($found.Count -gt 0) {
  Write-Output ("MOJIBAKE/ENCODING DETECTADO ({0} linha(s)):" -f $found.Count)
  $found | Sort-Object | ForEach-Object { Write-Output $_ }
  exit 1
}
else {
  Write-Output "OK: nenhum mojibake ou perda de encoding (U+FFFD) encontrado."
  exit 0
}