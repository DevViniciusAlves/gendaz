#!/bin/bash
# Remove acentos de identificadores Java para restabelecer consistência de compilação
set -e
PROJECT_DIR="${1:-.}"
LOGFILE="${PROJECT_DIR}/deaccent-java.log"
echo "=== Removendo acentos de identificadores Java ===" | tee "$LOGFILE"
MODIFIED=0
SED_SCRIPT=$(cat <<'EOF'
s/\bdescrição\b/descricao/g
s/\bdescrições\b/descricoes/g
s/\bobservação\b/observacao/g
s/\bobservações\b/observacoes/g
s/\bação\b/acao/g
s/\bações\b/acoes/g
s/\batencao\b/atencao/g
s/\bpersistência\b/persistencia/g
s/\binclusão\b/inclusao/g
s/\bexclusão\b/exclusao/g
s/\batualização\b/atualizacao/g
s/\bconexão\b/conexao/g
s/\bconfiguração\b/configuracao/g
s/\bconfigurações\b/configuracoes/g
s/\baplicação\b/aplicacao/g
s/\baplicações\b/aplicacoes/g
s/\bcoleção\b/colecao/g
s/\bcoleções\b/colecoes/g
s/\bautorização\b/autorizacao/g
s/\bautenticação\b/autenticacao/g
s/\bvalidação\b/validacao/g
s/\bpaginação\b/paginacao/g
s/\bpermissão\b/permissao/g
s/\bpermissões\b/permissoes/g
s/\bsessão\b/sessao/g
s/\bsessões\b/sessoes/g
s/\binformação\b/informacao/g
s/\binformações\b/informacoes/g
s/\bfunção\b/funcao/g
s/\bfunções\b/funcoes/g
s/\brascunho\b/rascunho/g
s/\bnao\b/nao/g
EOF
)
while IFS= read -r file; do
    tmp="${file}.tmp"
    cp "$file" "$tmp"
    sed -i -E "$SED_SCRIPT" "$tmp"
    if ! diff -q "$file" "$tmp" >/dev/null 2>&1; then
        cp "$file" "${file}.bak"
        cp "$tmp" "$file"
        echo "✓ $file" | tee -a "$LOGFILE"
        MODIFIED=$((MODIFIED+1))
    fi
    rm -f "$tmp"
done < <(find "$PROJECT_DIR" -type f -name "*.java" ! -name "*.bak" 2>/dev/null | grep -v -E '/(node_modules|dist|build|target)/')
echo "Total: $MODIFIED" | tee -a "$LOGFILE"
