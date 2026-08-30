#!/bin/bash
# Restaura todos os .bak para originais
count=0
while IFS= read -r bak; do
    orig="${bak%.bak}"
    mv "$bak" "$orig"
    count=$((count + 1))
done < <(find /mnt/e/gendazz -name '*.bak' 2>/dev/null)
echo "Restaurados: $count"
