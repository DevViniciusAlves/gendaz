#!/bin/bash
count=0
while IFS= read -r bak; do
    mv "$bak" "${bak%.bak}"
    count=$((count + 1))
done < <(find /mnt/e/gendazz -name '*.java.bak' 2>/dev/null)
echo "Restaurados (java): $count"
