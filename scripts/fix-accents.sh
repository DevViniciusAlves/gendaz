#!/bin/bash
# Script de Correção de Acentos - Gendaz (otimizado)
set -e

PROJECT_DIR="${1:-.}"
LOGFILE="${PROJECT_DIR}/fix-accents.log"

echo "=== Iniciando correção de acentos ===" | tee "$LOGFILE"
echo "Diretório: $PROJECT_DIR" | tee -a "$LOGFILE"
echo "Data: $(date)" | tee -a "$LOGFILE"
echo "" | tee -a "$LOGFILE"

MODIFIED_COUNT=0

# Monta um único script sed com todas as substituições
SED_SCRIPT=$(cat <<'EOF'
s/\bnao\b/não/g
s/\bfuncao\b/função/g
s/\bfuncoes\b/funções/g
s/\bobservacao\b/observação/g
s/\bobservacoes\b/observações/g
s/\bacao\b/ação/g
s/\bacoes\b/ações/g
s/\breagendamento\b/reagendamento/g
s/\batencao\b/atenção/g
s/\bpersistencia\b/persistência/g
s/\binclusao\b/inclusão/g
s/\bexclusao\b/exclusão/g
s/\batualizacao\b/atualização/g
s/\bconexao\b/conexão/g
s/\bcriptografia\b/criptografia/g
s/\bdescricao\b/descrição/g
s/\bdescricoes\b/descrições/g
s/\bconfiguracao\b/configuração/g
s/\bconfiguracoes\b/configurações/g
s/\baplicacao\b/aplicação/g
s/\baplicacoes\b/aplicações/g
s/\bcolecao\b/coleção/g
s/\bcolecoes\b/coleções/g
s/\bautorizacao\b/autorização/g
s/\bauthenticacao\b/autenticação/g
s/\bvalidacao\b/validação/g
s/\bpaginacao\b/paginação/g
s/\bpermissao\b/permissão/g
s/\bpermissoes\b/permissões/g
s/\bsessao\b/sessão/g
s/\bsessoes\b/sessões/g
s/\brascunho\b/rascunho/g
s/\binformacao\b/informação/g
EOF
)

process_file() {
    local file="$1"
    local temp_file="${file}.tmp"
    cp "$file" "$temp_file"
    sed -i -f <(echo "$SED_SCRIPT") "$temp_file" 2>/dev/null || sed -i -E "$SED_SCRIPT" "$temp_file"
    if ! diff -q "$file" "$temp_file" >/dev/null 2>&1; then
        cp "$file" "${file}.bak"
        cp "$temp_file" "$file"
        echo "✓ Corrigido: $file" | tee -a "$LOGFILE"
        MODIFIED_COUNT=$((MODIFIED_COUNT + 1))
    fi
    rm -f "$temp_file"
}

echo "📝 Processando arquivos Java..." | tee -a "$LOGFILE"
while IFS= read -r file; do
    process_file "$file"
done < <(find "$PROJECT_DIR" -type f -name "*.java" ! -name "*.bak" 2>/dev/null | grep -v -E '/(node_modules|dist|build|target)/')

echo "⚙️ Processando arquivos JavaScript/JSX..." | tee -a "$LOGFILE"
while IFS= read -r file; do
    process_file "$file"
done < <(find "$PROJECT_DIR" -type f \( -name "*.js" -o -name "*.jsx" \) ! -name "*.bak" 2>/dev/null | grep -v -E '/(node_modules|dist|build|target)/')

echo "" | tee -a "$LOGFILE"
echo "=== Resultado ===" | tee -a "$LOGFILE"
echo "Total de arquivos modificados: $MODIFIED_COUNT" | tee -a "$LOGFILE"
echo "Log: $LOGFILE" | tee -a "$LOGFILE"
echo "✅ Processo concluído!" | tee -a "$LOGFILE"

if [ $MODIFIED_COUNT -gt 0 ]; then
    echo "" | tee -a "$LOGFILE"
    echo "Rollback: find $PROJECT_DIR -name '*.bak' -exec sh -c 'mv \"\$1\" \"\${1%.bak}\"' _ {} \;" | tee -a "$LOGFILE"
fi
