#!/bin/bash
cp "/mnt/e/gendazz/backend/src/main/java/com/minhaempresa/gendaz/admin/controller/AdminController.java" /tmp/teste.java
sed -i -E "s/\bnao\b/não/g; s/\bfuncao\b/função/g; s/\bconfiguracao\b/configuração/g" /tmp/teste.java
grep -n "não\|função\|configuração" /tmp/teste.java | head
