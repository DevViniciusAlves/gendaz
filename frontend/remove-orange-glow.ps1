param(
    [string]$GlobalCssPath = "src/styles/global.css",
    [string]$HeroAnimationCssPath = "src/components/hero-animation.css"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$globalCss = Join-Path $scriptDir $GlobalCssPath
$heroCss = Join-Path $scriptDir $HeroAnimationCssPath

Write-Host "=== REMOVENDO ORANGE GLOW DO PROJETO ===" -ForegroundColor Cyan
Write-Host ""

# =============================================================================
# 1. BACKUP
# =============================================================================
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item $globalCss "$globalCss.backup_$timestamp" -Force
Write-Host "Backup criado: global.css.backup_$timestamp" -ForegroundColor Green

# =============================================================================
# 2. LER CONTEUDO
# =============================================================================
$content = Get-Content $globalCss -Raw

# =============================================================================
# 3. SUBSTITUICOES - box-shadow com orange glow
# =============================================================================
Write-Host "Removendo orange glow box-shadow..." -ForegroundColor Yellow

# Padrao 1: box-shadow com multiplas linhas contendo rgba(255,180,115
# Substituir por versao sem o orange glow
$content = $content -replace '(?s)box-shadow:\s*\n\s*0 0 \d+px rgba\(255, 180, 115, [\d.]+\)[^}]*?;\n', 'box-shadow: none;
'

# Mas isso pode ser muito agressivo. Vamos fazer substituicoes mais precisas.

# Vamos reverter e fazer substituicoes cirurgicas
$content = Get-Content $globalCss -Raw

Write-Host "Fazendo substituicoes cirurgicas..." -ForegroundColor Yellow

# ===== SUBSTITUICOES ESPECIFICAS =====

# 1. --shadow-hover (remove orange glow)
$content = $content -replace '--shadow-hover: 0 12px 32px rgba\(255, 180, 115, 0\.16\);', '--shadow-hover: 0 12px 32px rgba(0, 0, 0, 0.16);'

# 2. .animated-background::before - remove orange radial-gradient glow
$content = $content -replace 'background: radial-gradient\(circle at 50% 50%, rgba\(255, 180, 115, 0\.035\), transparent 60%\);', 'background: transparent;'

# 3. .btn-primary:hover - remove orange box-shadow
$content = $content -replace '(\.btn-primary:hover \{ background: var\(--primary-2\); )box-shadow: 0 8px 24px rgba\(255, 180, 115, 0\.28\);', '${1}box-shadow: 0 8px 24px rgba(0, 0, 0, 0.16);'

# 4. .plan-card.highlight - remove gradient and orange glow
$content = $content -replace '(\.plan-card\.highlight \{ )border-color: var\(--primary\); box-shadow: var\(--shadow-hover\); background: linear-gradient\(180deg, rgba\(255, 180, 115, 0\.06\) 0%, var\(--surface-solid\) 100%\);', '${1}border-color: var(--primary); box-shadow: var(--shadow-hover);'

# 5. App shell plan cards
$content = $content -replace '(?s)(\.app-shell \.content \.plan-card \{[^}]*?box-shadow:\s*\n\s*0 18px 52px rgba\(0,0,0,0\.34\),\s*\n\s*0 0 0 1px rgba\(255,255,255,0\.02\) inset,\s*\n\s*)0 0 28px rgba\(255, 180, 115,0\.08\)( !important;)', '${1}0 0 0 rgba(255, 180, 115,0)${2}'

# 6. App shell plan card highlight
$pattern = '(?s)(\.app-shell \.content \.plan-card\.highlight \{[^}]*?box-shadow:\s*\n\s*0 18px 54px rgba\(0,0,0,0\.36\),\s*\n\s*0 0 0 1px rgba\(255, 180, 115, 0\.08\) inset,\s*\n\s*)0 0 34px rgba\(255, 180, 115,0\.14\)( !important;)'
$content = $content -replace $pattern, '${1}0 0 0 rgba(255, 180, 115,0)${2}'

# 7. App shell plan card highlight - remove radial gradient
$content = $content -replace '(?s)(\.app-shell \.content \.plan-card\.highlight \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top, rgba\(255, 180, 115, 0\.09\), transparent 42%\),\s*\n\s*', '${1}'

# 8. App shell plan card hover
$pattern = '(?s)(\.app-shell \.content \.plan-card:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 35px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 24px 60px rgba\(0,0,0,0\.40\),\s*\n\s*0 0 0 1px rgba\(255, 180, 115,0\.08\) inset( !important;)'
$content = $content -replace $pattern, '${1}0 24px 60px rgba(0,0,0,0.40),${2}'

# 9. recommended-badge glow
$content = $content -replace '(?s)(\.app-shell \.content \.recommended-badge \{[^}]*?box-shadow:\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.22\),\s*\n\s*', '${1}'

# 10. Large card glow removal (multi-line box-shadow with orange)
$content = $content -replace '(?s)(\.app-shell \.content \.dashboard-summary-card,\s*\.\.\.\s*\.admin-shell \.modal \{[^}]*?box-shadow:\s*\n\s*0 18px 52px rgba\(0, 0, 0, 0\.34\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.02\) inset,\s*\n\s*)0 0 26px rgba\(255, 180, 115, 0\.08\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 11. Avatar/schedule icon glow
$content = $content -replace '(?s)(\.app-shell \.content \.avatar,\s*\.\.\.\s*\.app-shell \.content \.schedule-icon-box\.status-cancelado \{[^}]*?background:\s*\n\s*)linear-gradient\(180deg, rgba\(255, 180, 115, 0\.18\), rgba\(255, 180, 115, 0\.10\)\)( !important;)', '${1}rgba(255, 180, 115, 0.10)${2}'

$content = $content -replace '(?s)(\.app-shell \.content \.avatar,\s*\.\.\.\s*\.schedule-icon-box\.status-cancelado \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.08\) inset,\s*\n\s*0 10px 24px rgba\(0, 0, 0, 0\.12\)( !important;)', '${1}0 10px 24px rgba(0, 0, 0, 0.12)${2}'

# 12. Orange glow in box-shadows with multiple layers - simplified approach
# Replace all box-shadow values that include orange glow lines
$content = $content -replace '0 0 \d+px rgba\(255, 180, 115, [\d.]+\)', '0 0 0 rgba(255, 180, 115, 0)'
$content = $content -replace '0 0 \d+px rgba\(255, 180, 115,[\d.]+\)', '0 0 0 rgba(255, 180, 115, 0)'
$content = $content -replace '0 0 \d+px rgba\(255, 180, 115, 0\.\d+\)', '0 0 0 rgba(255, 180, 115, 0)'
$content = $content -replace '0 0 \d+px rgba\(255, 180, 115, 0\.\d+\) !important', '0 0 0 rgba(255, 180, 115, 0) !important'
$content = $content -replace '0 0 0 rgba\(255, 180, 115, 0\) !important,\s*', ''
$content = $content -replace ',\s*0 0 0 rgba\(255, 180, 115, 0\)', ''

# 13. Remove remaining orange radial gradients in backgrounds (that create halo)
$content = $content -replace 'radial-gradient\(circle at [^)]*rgba\(255,\s*180,\s*115,[^)]*\),', ''

# 14. text-shadow orange
$content = $content -replace 'text-shadow: 0 0 18px rgba\(255, 180, 115, 0\.38\);', 'text-shadow: none;'

# 15. Hero section copy glow
$content = $content -replace '(\.hero-copy::before \{[^}]*?)background: radial-gradient\(circle, rgba\(255, 180, 115, \.2\), transparent 70%\);(?:\s*filter: blur\(18px\);)', '${1}background: transparent;'

# 16. Marketing nav shell glow
$content = $content -replace '(?s)(\.marketing-page \.marketing-nav-shell \{[^}]*?box-shadow:\s*\n\s*0 0 0 1px rgba\(255, 180, 115, 0\.14\),\s*\n\s*)0 0 20px rgba\(255, 180, 115, 0\.22\),\s*\n\s*0 0 40px rgba\(255, 200, 150, 0\.16\),\s*\n\s*0 6px 14px rgba\(0, 0, 0, 0\.08\)( !important;)', '${1}0 6px 14px rgba(0, 0, 0, 0.08)${2}'

# 17. Stats card glow
$content = $content -replace '(?s)(\.marketing-stats \.stats-card \{[^}]*?box-shadow:\s*\n\s*0 0 0 1px rgba\(255, 180, 115, 0\.14\),\s*\n\s*)0 0 18px rgba\(255, 180, 115, 0\.24\),\s*\n\s*0 0 38px rgba\(255, 200, 150, 0\.16\),\s*\n\s*0 8px 18px rgba\(0, 0, 0, 0\.08\)( !important;)', '${1}0 8px 18px rgba(0, 0, 0, 0.08)${2}'

# 18. Premium border
$content = $content -replace '(?s)(\.premium-border \{[^}]*?box-shadow:\s*\n\s*0 0 0 1px rgba\(255, 180, 115, 0\.10\),\s*\n\s*)0 0 16px rgba\(255, 180, 115, 0\.14\),\s*\n\s*0 0 32px rgba\(255, 200, 150, 0\.08\),\s*\n\s*0 8px 24px rgba\(0, 0, 0, 0\.14\)', '${1}0 8px 24px rgba(0, 0, 0, 0.14)'

# 19. Premium border ::before
$content = $content -replace '(?s)(\.premium-border::before \{[^}]*?background: linear-gradient\(135deg, rgba\(255, 255, 255, 0\.14\), )rgba\(255, 180, 115, 0\.18\), (rgba\(255, 255, 255, 0\.04\)\))', '${1}rgba(255, 255, 255, 0.04), ${2}'

# 20. Marketing bg-radial orange
$content = $content -replace 'background: radial-gradient\(circle, rgba\(255, 210, 170, 0\.55\) 0%, transparent 72%\);', 'background: transparent;'
$content = $content -replace 'background: radial-gradient\(circle, rgba\(255, 180, 115, 0\.78\) 0%, transparent 72%\);', 'background: transparent;'
$content = $content -replace 'background: radial-gradient\(circle, rgba\(255, 210, 170, 0\.42\) 0%, transparent 72%\);', 'background: transparent;'

# 21. Bg streak
$content = $content -replace 'background: linear-gradient\(90deg, transparent, rgba\(255, 180, 115, 0\.20\), rgba\(255, 200, 150, 0\.48\), rgba\(255, 180, 115, 0\.20\), transparent\);', 'background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.10), rgba(255, 255, 255, 0.05), transparent);'

# 22. Marketing page background - remove orange radial
$content = $content -replace '(?s)(\.marketing-page \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 20% 12%, rgba\(255, 180, 115, 0\.18\), transparent 30%\),\s*\n\s*radial-gradient\(circle at 78% 18%, rgba\(255, 180, 115, 0\.12\), transparent 26%\),\s*\n\s*', '${1}'

# 23. Marketing page animated-background
$content = $content -replace '(?s)(\.marketing-page \.animated-background \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 20% 20%, rgba\(255, 180, 115, 0\.24\), transparent 22%\),\s*\n\s*radial-gradient\(circle at 80% 10%, rgba\(255, 255, 255, 0\.06\), transparent 20%\),\s*\n\s*radial-gradient\(circle at 50% 80%, rgba\(255, 180, 115, 0\.12\), transparent 24%\),\s*\n\s*', '${1}'

# 24. Bg sheen orange
$content = $content -replace 'background: radial-gradient\(circle, rgba\(255, 180, 115, \.24\), transparent 70%\);', 'background: transparent;'
$content = $content -replace 'background: radial-gradient\(circle, rgba\(255, 180, 115, \.10\), transparent 72%\);', 'background: transparent;'

# 25. App shell background - remove orange halos
$content = $content -replace '(?s)(\.app-shell,\s*\n\.admin-shell \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 18% 10%, rgba\(255, 180, 115, 0\.18\), transparent 24%\),\s*\n\s*radial-gradient\(circle at 84% 14%, rgba\(255, 180, 115, 0\.10\), transparent 22%\),\s*\n\s*radial-gradient\(circle at 50% 86%, rgba\(255, 180, 115, 0\.08\), transparent 26%\),\s*\n\s*', '${1}'

# 26. App shell animated background
$content = $content -replace '(?s)(\.app-shell \.animated-background::before,\s*\n\.admin-shell \.animated-background::before \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 50% 50%, rgba\(255, 180, 115, 0\.08\), transparent 58%\);(?:\s*\})', '${1}transparent;'

# 27. Bg sheen-1 orange
$content = $content -replace '(?s)(\.app-shell \.bg-sheen-1,\s*\n\.admin-shell \.bg-sheen-1 \{[^}]*?background: )radial-gradient\(circle, rgba\(255, 180, 115, \.22\), transparent 70%\);(?:\s*\})', '${1}transparent;'

# 28. Bg sheen-3 orange
$content = $content -replace '(?s)(\.app-shell \.bg-sheen-3,\s*\n\.admin-shell \.bg-sheen-3 \{[^}]*?background: )radial-gradient\(circle, rgba\(255, 180, 115, \.12\), transparent 72%\);(?:\s*\})', '${1}transparent;'

# 29. Bg streak orange
$content = $content -replace '(?s)(\.app-shell \.bg-streak,\s*\n\.admin-shell \.bg-streak \{[^}]*?background: linear-gradient\(90deg, transparent, )rgba\(255, 180, 115, \.46\), transparent\);', '${1}rgba(255, 255, 255, 0.08), transparent);'

# 30. Sidebar orange radial
$content = $content -replace '(?s)(\.app-shell \.sidebar,\s*\n\.admin-shell \.admin-sidebar \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top left, rgba\(255, 180, 115, 0\.12\), transparent 30%\),\s*\n\s*', '${1}'

# 31. Sidebar active orange glow
$content = $content -replace '(?s)(\.app-shell \.sidebar a\.active,\s*\n\.admin-shell \.admin-sidebar button\.active \{[^}]*?background:\s*\n\s*)linear-gradient\(180deg, rgba\(255, 180, 115, 0\.22\), rgba\(255, 180, 115, 0\.12\)\);(?:\s*)', '${1}rgba(255, 180, 115, 0.14);'
$content = $content -replace '(?s)(\.app-shell \.sidebar a\.active,\s*\n\.admin-shell \.admin-sidebar button\.active \{[^}]*?box-shadow:\s*\n\s*)inset 0 0 0 1px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 24px rgba\(255, 180, 115, 0\.10\);(?:\s*)', '${1}inset 0 0 0 1px rgba(255, 180, 115, 0.18);'

# 32. Login screen background - remove orange halos
$content = $content -replace '(?s)(\.login-screen,\s*\n\.criar-conta-screen \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 14% 18%, rgba\(255, 180, 115, 0\.16\), transparent 28%\),\s*\n\s*radial-gradient\(circle at 82% 12%, rgba\(255, 180, 115, 0\.10\), transparent 24%\),\s*\n\s*radial-gradient\(circle at 50% 82%, rgba\(255, 180, 115, 0\.08\), transparent 26%\),\s*\n\s*', '${1}'

# 33. Login panel glow
$content = $content -replace '(?s)(\.login-panel,\s*\n\.criar-conta-panel \{[^}]*?box-shadow:\s*\n\s*0 22px 52px rgba\(0, 0, 0, 0\.34\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.03\) inset,\s*\n\s*)0 0 28px rgba\(255, 180, 115, 0\.10\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 34. Legal page orange halos
$content = $content -replace '(?s)(\.legal-page \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top left, rgba\(255, 180, 115, 0\.10\), transparent 32%\),\s*\n\s*radial-gradient\(circle at bottom right, rgba\(255, 180, 115, 0\.07\), transparent 28%\),\s*\n\s*', '${1}'

# 35. Legal panel glow
$content = $content -replace '(?s)(\.legal-panel \{[^}]*?box-shadow:\s*\n\s*0 18px 52px rgba\(0, 0, 0, 0\.34\),\s*\n\s*)0 0 28px rgba\(255, 180, 115, 0\.08\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 36. Booking page orange halos
$content = $content -replace '(?s)(\.booking-page \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top left, rgba\(255, 180, 115, 0\.16\), transparent 34%\),\s*\n\s*radial-gradient\(circle at 82% 14%, rgba\(255, 180, 115, 0\.12\), transparent 24%\),\s*\n\s*', '${1}'

# 37. Payment page orange halos
$content = $content -replace '(?s)(\.payment-page \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 18% 12%, rgba\(255, 180, 115, 0\.18\), transparent 26%\),\s*\n\s*radial-gradient\(circle at 82% 18%, rgba\(255, 180, 115, 0\.08\), transparent 20%\),\s*\n\s*', '${1}'

# 38. Admin login screen orange halos
$content = $content -replace '(?s)(\.admin-login-screen \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top left, rgba\(255, 180, 115, 0\.16\), transparent 34%\),\s*\n\s*radial-gradient\(circle at 82% 14%, rgba\(255, 180, 115, 0\.12\), transparent 24%\),\s*\n\s*', '${1}'

# 39. Criar conta screen orange halos
$content = $content -replace '(?s)(\.criar-conta-screen \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 16% 18%, rgba\(255, 180, 115, 0\.16\), transparent 28%\),\s*\n\s*radial-gradient\(circle at 82% 12%, rgba\(255, 180, 115, 0\.10\), transparent 24%\),\s*\n\s*radial-gradient\(circle at 50% 84%, rgba\(255, 180, 115, 0\.08\), transparent 26%\),\s*\n\s*', '${1}'

# 40. Storytelling glow
$content = $content -replace '(?s)(\.storytelling-glow \{[^}]*?background: )radial-gradient\(circle, rgba\(255, 180, 115, 0\.08\), transparent 70%\);(?:\s*)', '${1}transparent;'
$content = $content -replace '(?s)(\.storytelling-glow \{[^}]*?background: )radial-gradient\(circle, rgba\(255, 180, 115, 0\.18\), transparent 70%\)( !important;)', '${1}transparent${2}'

# 41. Mockup screen glow
$content = $content -replace '(?s)(\.mockup-screen-glow \{[^}]*?background: )radial-gradient\(circle at center, rgba\(255, 180, 115, 0\.06\), transparent 70%\);(?:\s*)', '${1}transparent;'

# 42. Bookin icon glow
$content = $content -replace '(?s)(\.booking-icon \{[^}]*?box-shadow: )0 18px 40px rgba\(255, 180, 115, 0\.14\);(?:\s*)', '${1}none;'

# 43. Booking success icon glow
$content = $content -replace '(?s)(\.booking-success-icon \{[^}]*?box-shadow: )0 18px 40px rgba\(255, 180, 115, 0\.14\);(?:\s*)', '${1}none;'

# 44. Primary link glow
$content = $content -replace '(?s)(\.primary-link,\s*\n\.marketing-nav a\.primary-link,\s*\n\.nav-signup-link \{[^}]*?box-shadow: )0 4px 14px rgba\(255, 180, 115, 0\.4\);(?:\s*)', '${1}none;'
$content = $content -replace '(?s)(\.primary-link:hover \{[^}]*?box-shadow: )0 6px 20px rgba\(255, 180, 115, 0\.5\);(?:\s*)', '${1}none;'

# 45. Marketing nav primary link
$content = $content -replace '(?s)(\.primary-link,\s*\n\.nav-signup-link \{[^}]*?box-shadow: )0 18px 28px rgba\(255, 180, 115, \.24\)( !important;)', '${1}none${2}'

# 46. Solution/plan/support card glow
$content = $content -replace '(?s)(\.solution-card,\s*\n\.plan-card,\s*\n\.support-card,\s*\n\.contact-band,\s*\n\.storytelling-mockup-wrapper \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.12\),\s*\n\s*0 0 18px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 38px rgba\(255, 200, 150, 0\.12\),\s*\n\s*0 6px 14px rgba\(0, 0, 0, 0\.08\)( !important;)', '${1}0 6px 14px rgba(0, 0, 0, 0.08)${2}'

# 47. Solution/plan/support card hover
$content = $content -replace '(?s)(\.solution-card:hover,\s*\n\.plan-card:hover,\s*\n\.support-card:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 28px rgba\(255, 180, 115, 0\.24\),\s*\n\s*0 0 54px rgba\(255, 200, 150, 0\.18\),\s*\n\s*0 14px 30px rgba\(0, 0, 0, 0\.12\)( !important;)', '${1}0 14px 30px rgba(0, 0, 0, 0.12)${2}'

# 48. Solution card hover
$content = $content -replace '(?s)(\.solution-card:hover,\s*\n\.plan-card:hover,\s*\n\.support-card:hover,\s*\n\.contact-band:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 20px rgba\(255, 180, 115, 0\.28\),\s*\n\s*0 0 40px rgba\(255, 200, 150, 0\.16\),\s*\n\s*0 8px 22px rgba\(0, 0, 0, 0\.12\)( !important;)', '${1}0 8px 22px rgba(0, 0, 0, 0.12)${2}'

# 49. App shell card orange glow in box-shadow
$content = $content -replace '(?s)0 0 28px rgba\(255, 180, 115,\s*0\.08\)( !important;)', '0 0 0 rgba(255, 180, 115, 0)${1}'
$content = $content -replace '(?s)0 0 24px rgba\(255, 180, 115, 0\.08\)( !important;)', '0 0 0 rgba(255, 180, 115, 0)${1}'
$content = $content -replace '(?s)0 0 22px rgba\(255, 180, 115, 0\.08\)( !important;)', '0 0 0 rgba(255, 180, 115, 0)${1}'
$content = $content -replace '(?s)0 0 28px rgba\(255, 180, 115, 0\.10\)( !important;)?', '0 0 0 rgba(255, 180, 115, 0)'
$content = $content -replace '(?s)0 0 28px rgba\(255, 180, 115, 0\.08\)(;)?', '0 0 0 rgba(255, 180, 115, 0)'

# 50. Stats card border
$content = $content -replace '(\.marketing-stats \.stats-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.38\)( !important;)', '${1}rgba(255, 255, 255, 0.12)${2}'
$content = $content -replace '(\.marketing-stats \.stats-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.32\)(?:\s*)', '${1}rgba(255, 255, 255, 0.12)'

# 51. Marketing nav shell border
$content = $content -replace '(\.marketing-page \.marketing-nav-shell \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.28\)( !important;)', '${1}rgba(255, 255, 255, 0.10)${2}'

# 52. Marketing stats div border
$content = $content -replace '(?s)(\.marketing-stats div \{[^}]*?background: )linear-gradient\(180deg, rgba\(255,255,255,\.02\), rgba\(255,255,255,\.01\)\);(?:\s*)', '${1}rgba(255, 255, 255, 0.02);'

# 53. Pricing card box-shadows
$content = $content -replace '(?s)(\.pricing-section \.pricing-card \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.10\),\s*\n\s*0 0 18px rgba\(255, 180, 115, 0\.16\),\s*\n\s*0 0 34px rgba\(255, 200, 150, 0\.12\),\s*\n\s*0 10px 20px rgba\(0, 0, 0, 0\.10\)( !important;)', '${1}0 10px 20px rgba(0, 0, 0, 0.10)${2}'

$content = $content -replace '(?s)(\.pricing-section \.pricing-card:hover,\s*\.\.\.\s*\.pricing-card:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 28px rgba\(255, 180, 115, 0\.22\),\s*\n\s*0 0 52px rgba\(255, 200, 150, 0\.18\),\s*\n\s*0 10px 18px rgba\(0, 0, 0, 0\.08\)( !important;)', '${1}0 10px 18px rgba(0, 0, 0, 0.08)${2}'

$content = $content -replace '(?s)(\.pricing-section \.pricing-card-pro \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.16\),\s*\n\s*0 0 22px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 0 44px rgba\(255, 200, 150, 0\.14\),\s*\n\s*0 10px 18px rgba\(0, 0, 0, 0\.08\)( !important;)', '${1}0 10px 18px rgba(0, 0, 0, 0.08)${2}'

# 54. Pricing card border
$content = $content -replace '(\.pricing-section \.pricing-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.22\)( !important;)', '${1}rgba(255, 255, 255, 0.10)${2}'
$content = $content -replace '(\.pricing-section \.pricing-card-pro \{[^}]*?border-color: )rgba\(255, 180, 115, 0\.58\)( !important;)', '${1}rgba(255, 180, 115, 0.30)${2}'

# 55. Pricing CTA pro glow
$content = $content -replace '(?s)(\.pricing-section \.pricing-cta-pro \{[^}]*?box-shadow: )0 0 0 1px rgba\(255, 180, 115, 0\.24\), 0 16px 28px rgba\(255, 180, 115, 0\.16\)( !important;)', '${1}0 0 0 1px rgba(255, 180, 115, 0.24)${2}'

# 56. Solutions section card hover
$content = $content -replace '(?s)(\.solutions-section \.solutions-section-card:hover,\s*\.\.\.\s*\.solutions-section-card\.is-visible:hover \{[^}]*?box-shadow: )0 0 35px rgba\(255, 180, 115, 0\.16\), 0 24px 42px rgba\(0, 0, 0, 0\.26\)( !important;)', '${1}0 24px 42px rgba(0, 0, 0, 0.26)${2}'

# 57. Solutions section card svg drop-shadow
$content = $content -replace '(?s)(\.solutions-section \.solutions-section-card:hover svg,\s*\.\.\.\s*\.solutions-section-card\.is-visible:hover svg \{[^}]*?filter: )drop-shadow\(0 0 14px rgba\(255, 180, 115, 0\.28\)\);(?:\s*)', '${1}none;'

# 58. Storytelling step-card hover
$content = $content -replace '(?s)(\.storytelling-step-card:hover,\s*\.\.\.\s*\.storytelling-step-card\.is-active \{[^}]*?box-shadow: )0 0 35px rgba\(255, 180, 115, 0\.18\), 0 24px 42px rgba\(0, 0, 0, 0\.24\), 0 0 0 1px rgba\(255, 180, 115, 0\.16\);(?:\s*)', '${1}0 24px 42px rgba(0, 0, 0, 0.24), 0 0 0 1px rgba(255, 180, 115, 0.16);'

# 59. Storytelling step-card border
$content = $content -replace '(\.storytelling-step-card:hover,\s*\.\.\.\s*\.storytelling-step-card\.is-active \{[^}]*?border-color: )rgba\(255, 180, 115, 0\.55\);(?:\s*)', '${1}rgba(255, 255, 255, 0.20);'

# 60. Storytelling section background gradient
$content = $content -replace '(?s)(\.storytelling-section \{[^}]*?background: )radial-gradient\(circle at 50% 0%, rgba\(255, 180, 115, 0\.06\), transparent 34%\);(?:\s*)', '${1}transparent;'

# 61. Planning payment panel glow
$content = $content -replace '(?s)(\.app-shell \.content \.plan-payment-panel \{[^}]*?box-shadow:\s*\n\s*0 18px 50px rgba\(0, 0, 0, 0\.28\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.02\) inset,\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.08\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 62. Payment pro card glow
$content = $content -replace '(?s)(\.payment-pro-card \{[^}]*?box-shadow:\s*\n\s*0 18px 50px rgba\(0, 0, 0, 0\.28\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.02\) inset,\s*\n\s*)0 0 28px rgba\(255, 180, 115, 0\.08\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 63. Payment step done glow
$content = $content -replace '(?s)(\.payment-step\.done span,\s*\n\.payment-step\.active span \{[^}]*?box-shadow: )0 10px 24px rgba\(255, 180, 115, 0\.18\);(?:\s*)', '${1}none;'

# 64. Payment status card glow
$content = $content -replace '(?s)(\.payment-status-card \{[^}]*?box-shadow:\s*\n\s*0 18px 50px rgba\(0, 0, 0, 0\.22\),\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.06\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 65. Payment checkout card glow
$content = $content -replace '(?s)(\.payment-checkout-card \{[^}]*?box-shadow:\s*\n\s*0 14px 40px rgba\(0, 0, 0, 0\.2\),\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.05\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 66. Payment page button glows
$content = $content -replace '(?s)(\.payment-page \.btn-primary,\s*\n\.payment-page \.btn:not\(\.btn-secondary\):not\(\.btn-ghost\) \{[^}]*?box-shadow:\s*\n\s*)0 12px 34px rgba\(255, 180, 115, 0\.28\),\s*\n\s*', '${1}'
$content = $content -replace '(?s)(\.payment-page \.btn-primary:hover,\s*\n\.payment-page \.btn:not\(\.btn-secondary\):not\(\.btn-ghost\):hover \{[^}]*?box-shadow:\s*\n\s*)0 16px 42px rgba\(255, 180, 115, 0\.34\),\s*\n\s*', '${1}'

# 67. Payment secondary button glow
$content = $content -replace '(?s)(\.payment-page \.btn-secondary \{[^}]*?box-shadow:\s*\n\s*0 10px 28px rgba\(0, 0, 0, 0\.18\),\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.05\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

$content = $content -replace '(?s)(\.payment-page \.btn-secondary:hover \{[^}]*?box-shadow:\s*\n\s*0 14px 34px rgba\(0, 0, 0, 0\.22\),\s*\n\s*)0 0 28px rgba\(255, 180, 115, 0\.14\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

$content = $content -replace '(?s)(\.payment-page \.btn-primary:focus-visible,\s*\n\.payment-page \.btn-secondary:focus-visible \{[^}]*?box-shadow:\s*\n\s*)0 0 0 3px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 14px 34px rgba\(0, 0, 0, 0\.22\);(?:\s*)', '${1}0 14px 34px rgba(0, 0, 0, 0.22);'

# 68. Payment pix card glow
$content = $content -replace '(?s)(\.payment-pix-card \{[^}]*?box-shadow:\s*\n\s*0 14px 40px rgba\(0, 0, 0, 0\.2\),\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.06\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 69. Payment client grid focus
$content = $content -replace '(?s)(\.payment-client-grid input:focus,\s*\n\.payment-client-grid select:focus \{[^}]*?box-shadow: )0 0 0 3px rgba\(255, 180, 115, 0\.16\);(?:\s*)', '${1}0 0 0 2px rgba(255, 180, 115, 0.20);'

# 70. Payment feedback orange background
$content = $content -replace '(?s)(\.payment-feedback \{[^}]*?background: )rgba\(255, 180, 115, 0\.12\);(?:\s*)', '${1}rgba(255, 180, 115, 0.06);'

# 71. Admin login panel glow
$content = $content -replace '(?s)(\.admin-login-panel \{[^}]*?box-shadow:\s*\n\s*0 28px 80px rgba\(0, 0, 0, 0\.42\),\s*\n\s*inset 0 1px 0 rgba\(255, 255, 255, 0\.04\);\s*\n\s*)(?:\s*backdrop-filter: blur)',
'${1}backdrop-filter: blur'

# 72. Admin login button glow
$content = $content -replace '(?s)(\.admin-login-panel \.btn-primary,\s*\n\.admin-login-panel button\[type="submit"\] \{[^}]*?box-shadow:\s*\n\s*)0 0 34px rgba\(255, 180, 115, 0\.26\),\s*\n\s*0 14px 28px rgba\(255, 180, 115, 0\.18\),\s*\n\s*', '${1}'

$content = $content -replace '(?s)(\.admin-login-panel \.btn-primary:hover,\s*\n\.admin-login-panel button\[type="submit"\]:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 44px rgba\(255, 180, 115, 0\.34\),\s*\n\s*0 18px 34px rgba\(255, 180, 115, 0\.22\),\s*\n\s*', '${1}'

# 73. Nav login/signup glow
$content = $content -replace '(?s)(\.marketing-page \.nav-login-link \{[^}]*?box-shadow:\s*\n\s*inset 0 1px 0 rgba\(255, 255, 255, 0\.08\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.02\) inset,\s*\n\s*0 8px 18px rgba\(0, 0, 0, 0\.10\),\s*\n\s*)0 0 22px rgba\(255, 180, 115, 0\.12\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

$content = $content -replace '(?s)(\.marketing-page \.nav-login-link:hover \{[^}]*?box-shadow:\s*\n\s*inset 0 1px 0 rgba\(255, 255, 255, 0\.08\),\s*\n\s*)0 0 34px rgba\(255, 180, 115, 0\.28\),\s*\n\s*0 10px 18px rgba\(0, 0, 0, 0\.10\)( !important;)', '${1}0 10px 18px rgba(0, 0, 0, 0.10)${2}'

$content = $content -replace '(?s)(\.marketing-page \.nav-signup-link \{[^}]*?box-shadow:\s*\n\s*)0 0 42px rgba\(255, 180, 115, 0\.34\),\s*\n\s*0 0 26px rgba\(255, 200, 150, 0\.20\),\s*\n\s*', '${1}'

$content = $content -replace '(?s)(\.marketing-page \.nav-signup-link:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 58px rgba\(255, 180, 115, 0\.46\),\s*\n\s*0 0 34px rgba\(255, 200, 150, 0\.26\),\s*\n\s*', '${1}'

$content = $content -replace '(?s)(\.marketing-page \.nav-login-link:focus-visible,\s*\n\.marketing-page \.nav-signup-link:focus-visible \{[^}]*?box-shadow:\s*\n\s*)0 0 0 2px rgba\(255, 180, 115, 0\.32\),\s*\n\s*0 0 0 5px rgba\(255, 180, 115, 0\.12\)( !important;)', '${1}0 0 0 2px rgba(255, 180, 115, 0.32)${2}'

# 74. Dropdown-panel glow
$content = $content -replace '(?s)(\.app-shell \.dropdown-panel,\s*\.\.\.\s*\.admin-shell \.action-menu-panel \{[^}]*?box-shadow:\s*\n\s*0 18px 42px rgba\(0, 0, 0, 0\.42\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.03\) inset,\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.10\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 75. Plan highlight box-shadow
$content = $content -replace '(?s)(\.plan-preview-plan\.highlight,\s*\n\.plan-card\.highlight \{[^}]*?box-shadow: )0 8px 32px 0 rgba\(255, 180, 115, 0\.18\);(?:\s*)', '${1}0 8px 32px 0 rgba(0, 0, 0, 0.18);'

# 76. Webkit box-shadow autofill
$content = $content -replace '0 0 0 1000px rgba\(20, 22, 30, 0\.98\) inset !important;', '0 0 0 1000px rgba(17, 19, 25, 0.98) inset !important;'

# 77. Booking page - remove orange borders (keep subtle)
$content = $content -replace '(\.booking-page \.field input:focus,\s*\.\.\.\s*\.booking-page \.field textarea:focus \{[^}]*?border-color: )rgba\(255, 180, 115, 0\.6\);(?:\s*box-shadow: 0 0 0 4px rgba\(255, 180, 115, 0\.12\);)', '${1}rgba(255, 180, 115, 0.40);'

# 78. Admin toast glow
$content = $content -replace '(?s)(\.admin-shell \.admin-toast \{[^}]*?box-shadow:\s*\n\s*0 18px 52px rgba\(0, 0, 0, 0\.34\),\s*\n\s*)0 0 24px rgba\(255, 180, 115, 0\.08\);(?:\s*)', '${1}0 0 0 rgba(255, 180, 115, 0);'

# 79. Remove orange border from hero-mockup-card (keep subtle)
$content = $content -replace '(\.hero-mockup-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.54\);(?:\s*)', '${1}rgba(255, 255, 255, 0.10);'

# 80. Remove glow from .app-shell .content .dashboard-hero background radial gradient
$content = $content -replace '(?s)(\.app-shell \.dashboard-hero \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at top right, rgba\(255, 180, 115, 0\.14\), transparent 34%\),\s*\n\s*', '${1}'

# 81. Remove primary glow from .btn-primary and plan buttons
$content = $content -replace '(?s)(\.app-shell \.btn-primary,\s*\.\.\.\s*\.admin-shell \.btn:not\(\.btn-secondary\):not\(\.btn-ghost\) \{[^}]*?box-shadow:\s*\n\s*)0 0 35px rgba\(255, 180, 115, 0\.24\),\s*\n\s*0 12px 28px rgba\(255, 180, 115, 0\.18\),\s*\n\s*', '${1}'

$content = $content -replace '(?s)(\.app-shell \.btn-primary:hover,\s*\.\.\.\s*\.admin-shell \.btn:not\(\.btn-secondary\):not\(\.btn-ghost\):hover \{[^}]*?box-shadow:\s*\n\s*)0 0 46px rgba\(255, 180, 115, 0\.34\),\s*\n\s*0 16px 32px rgba\(255, 180, 115, 0\.22\),\s*\n\s*', '${1}'

# 82. Focus-visible btn
$content = $content -replace '(?s)(\.app-shell \.btn:focus-visible,\s*\.\.\.\s*\.app-shell \.sidebar a:focus-visible \{[^}]*?box-shadow:\s*\n\s*)0 0 0 2px rgba\(255, 180, 115, 0\.32\),\s*\n\s*0 0 0 5px rgba\(255, 180, 115, 0\.12\);(?:\s*)', '${1}0 0 0 2px rgba(255, 180, 115, 0.32);'

# 83. Schedule icon box (internal)
$content = $content -replace '(?s)(\.app-shell \.schedule-icon-box \{[^}]*?background: )rgba\(255, 180, 115, 0\.12\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'

# 84. App shell background (internal) - remove orange halos
$content = $content -replace '(?s)(\.app-shell \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 92% 0%, rgba\(255, 180, 115, 0\.08\), transparent 28%\),\s*\n\s*linear-gradient\(180deg, #fffaf5 0%, #f5f7fb 100%\);(?:\s*)', '${1}linear-gradient(180deg, #f5f7fb 0%, #f5f7fb 100%);'

# 85. Criar conta screen panel glow
$content = $content -replace '(?s)(\.criar-conta-screen \.criar-conta-panel \{[^}]*?box-shadow:\s*\n\s*0 22px 52px rgba\(0, 0, 0, 0\.34\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.03\) inset,\s*\n\s*)0 0 28px rgba\(255, 180, 115, 0\.10\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

$content = $content -replace '(?s)(\.criar-conta-screen > \.criar-conta-panel \{[^}]*?box-shadow:\s*\n\s*0 22px 52px rgba\(0, 0, 0, 0\.38\),\s*\n\s*0 0 0 1px rgba\(255, 255, 255, 0\.03\) inset,\s*\n\s*)0 0 30px rgba\(255, 180, 115, 0\.12\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 86. Plano selecionado card glow
$content = $content -replace '(?s)(\.criar-conta-screen \.plano-selecionado-card \{[^}]*?box-shadow:\s*\n\s*inset 0 1px 0 rgba\(255,255,255,\.05\),\s*\n\s*0 12px 28px rgba\(0, 0, 0, 0\.18\),\s*\n\s*)0 0 18px rgba\(255, 180, 115, 0\.08\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 87. Login screen button glow 
$content = $content -replace '(?s)(\.login-screen \.btn-primary,\s*\.\.\.\s*\.criar-conta-screen button\[type="submit"\] \{[^}]*?box-shadow:\s*\n\s*)0 0 35px rgba\(255, 180, 115, 0\.24\),\s*\n\s*0 12px 26px rgba\(255, 180, 115, 0\.18\),\s*\n\s*', '${1}'

$content = $content -replace '(?s)(\.login-screen \.btn-primary:hover,\s*\.\.\.\s*\.criar-conta-screen button\[type="submit"\]:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 45px rgba\(255, 180, 115, 0\.34\),\s*\n\s*0 16px 30px rgba\(255, 180, 115, 0\.22\),\s*\n\s*', '${1}'

# 88. Secondary link hover glow
$content = $content -replace '(?s)(\.login-screen \.secondary-link:hover,\s*\n\.criar-conta-screen \.secondary-link:hover \{[^}]*?box-shadow: )0 0 28px rgba\(255, 180, 115, \.14\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 89. Internal app shell hover (panel/plan/schedule)
$content = $content -replace '(?s)(\.app-shell \.panel:hover,\s*\.\.\.\s*\.admin-shell \.admin-config div:hover \{[^}]*?box-shadow:\s*\n\s*0 22px 58px rgba\(0, 0, 0, 0\.38\),\s*\n\s*)0 0 30px rgba\(255, 180, 115, 0\.10\)( !important;)', '${1}0 0 0 rgba(255, 180, 115, 0)${2}'

# 90. Remove more orange border on hero mockup card border
$content = $content -replace '(\.hero-mockup-card:hover \{[^}]*?border-color: )rgba\(255, 180, 115, 0\.72\);(?:\s*)', '${1}rgba(255, 255, 255, 0.20);'

# 91. Hero agenda row
$content = $content -replace '(\.hero-agenda-row \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.08\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'

# 92. Various orange borders in hero
$content = $content -replace '(\.hero-metric-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.1\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.hero-metric-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.1\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.hero-overview-revenue,\s*\n\.hero-overview-chart \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.1\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.hero-finance-box \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.1\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.mockup-chip-row span,\s*\.\.\.\s*\.hero-status-pill \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.12\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.hero-float-table-head \{[^}]*?border-bottom: 1px solid )rgba\(255, 180, 115, 0\.12\);(?:\s*)', '${1}rgba(255, 255, 255, 0.06);'
$content = $content -replace '(\.hero-float-table-row \{[^}]*?border-bottom: 1px solid )rgba\(255, 180, 115, 0\.08\);(?:\s*)', '${1}rgba(255, 255, 255, 0.04);'

# 93. Hero chart bars glow
$content = $content -replace '(?s)(\.hero-chart-bars span \{[^}]*?box-shadow: )0 0 12px rgba\(255, 180, 115, 0\.18\);(?:\s*)', '${1}none;'

# 94. .app-shell .panel:hover glow (the non-premium version)
$content = $content -replace '(?s)(\.app-shell \.panel:hover,\s*\n\.app-shell \.metric-card:hover,\s*\n\.app-shell \.schedule-card:hover \{[^}]*?box-shadow: )0 16px 36px rgba\(255, 180, 115, 0\.12\)( !important;)', '${1}0 16px 36px rgba(0, 0, 0, 0.12)${2}'

# 95. Remove remaining standalone box-shadows with orange
$content = $content -replace ', 0 0 0 rgba\(255, 180, 115, 0\)', ''

# Save the result
Write-Host "Salvando global.css..." -ForegroundColor Yellow
$content | Set-Content $globalCss -NoNewline

Write-Host ""
Write-Host "=== GLOBAL.CSS CONCLUIDO ===" -ForegroundColor Green
Write-Host ""

# =============================================================================
# PROCESSAR HERO-ANIMATION.CSS
# =============================================================================
if (Test-Path $heroCss) {
    Copy-Item $heroCss "$heroCss.backup_$timestamp" -Force
    Write-Host "Backup criado: hero-animation.css.backup_$timestamp" -ForegroundColor Green
    
    $heroContent = Get-Content $heroCss -Raw
    
    # Remove orange ::before glow
    $heroContent = $heroContent -replace '(?s)(\.hero-product-visual::before \{[^}]*?background:\s*\n\s*)radial-gradient\(circle at 44% 54%, rgba\(255, 210, 170, 0\.64\), transparent 38%\),\s*\n\s*radial-gradient\(circle at 76% 28%, rgba\(255, 210, 170, 0\.42\), transparent 32%\),\s*\n\s*radial-gradient\(circle at 30% 70%, rgba\(255, 180, 115, 0\.26\), transparent 30%\);(?:\s*filter: blur\(30px\);)', 'transparent;'
    
    # Remove orange border/mockup glow
    $heroContent = $heroContent -replace '(?s)(\.hero-mockup-card \{[^}]*?border: 1px solid )rgba\(255, 180, 115, 0\.54\);(?:\s*box-shadow:\s*\n\s*0 0 0 1px rgba\(255, 180, 115, 0\.24\),\s*\n\s*0 0 18px rgba\(255, 180, 115, 0\.38\),\s*\n\s*0 0 34px rgba\(255, 200, 150, 0\.24\),\s*\n\s*0 0 54px rgba\(255, 180, 115, 0\.14\),\s*\n\s*0 6px 14px rgba\(0, 0, 0, 0\.10\);)', 'rgba(255, 255, 255, 0.10);\n  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.10);'
    
    # Remove hover glow
    $heroContent = $heroContent -replace '(?s)(\.hero-mockup-card:hover \{[^}]*?box-shadow:\s*\n\s*)0 0 0 1px rgba\(255, 180, 115, 0\.32\),\s*\n\s*0 0 24px rgba\(255, 180, 115, 0\.46\),\s*\n\s*0 0 46px rgba\(255, 200, 150, 0\.30\),\s*\n\s*0 0 72px rgba\(255, 180, 115, 0\.18\),\s*\n\s*0 8px 16px rgba\(0, 0, 0, 0\.08\);(?:\s*)', 'box-shadow: 0 8px 16px rgba(0, 0, 0, 0.08);'
    
    # Remove orange border on hover
    $heroContent = $heroContent -replace '(\.hero-mockup-card:hover \{[^}]*?border-color: )rgba\(255, 180, 115, 0\.72\);(?:\s*)', '${1}rgba(255, 255, 255, 0.20);'
    
    # Remove orange chart gradient (keep but without orange)
    $heroContent = $heroContent -replace '(?s)(\.hero-chart-bars span \{[^}]*?background: linear-gradient\(180deg, )#ffb473, rgba\(255, 180, 115, 0\.18\)\);(?:\s*box-shadow: 0 0 12px rgba\(255, 180, 115, 0\.18\);)', 'rgba(255, 180, 115, 0.40), rgba(255, 180, 115, 0.10));'
    
    $heroContent | Set-Content $heroCss -NoNewline
    Write-Host "hero-animation.css atualizado!" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== FINALIZADO ===" -ForegroundColor Cyan
Write-Host "Backups salvos com timestamp: $timestamp" -ForegroundColor Gray
Write-Host "Efeitos de brilho laranja removidos." -ForegroundColor Green
