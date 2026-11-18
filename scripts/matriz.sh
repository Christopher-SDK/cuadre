#!/usr/bin/env bash
# Corre la suite completa contra el sistema limpio y contra cada bug sembrado, y arma la matriz de detección.
# Uso: scripts/matriz.sh                 (todos)
#      scripts/matriz.sh REDONDEO CARRERA (solo esos)
# Requiere Java 21, Docker en marcha y python3.
set -euo pipefail
cd "$(dirname "$0")/.."

TODOS="NINGUNO DOBLE_CARGO CARRERA REDONDEO REVERSO_PARCIAL IMPUESTO_DOBLE CRONOGRAMA TIMEOUT_A_MEDIAS"
BUGS="${*:-$TODOS}"

./mvnw -B -q -DskipTests test-compile

for bug in $BUGS; do
  destino="target/matriz/$bug"
  rm -rf "$destino" target/surefire-reports target/.jqwik-database
  mkdir -p "$destino"
  printf '▶ %-18s ' "$bug"
  inicio=$(date +%s)
  CUADRE_BUG="$bug" ./mvnw -B -q test -Dmaven.test.failure.ignore=true > "$destino/maven.log" 2>&1 || true
  echo $(( $(date +%s) - inicio )) > "$destino/segundos"
  cp target/surefire-reports/TEST-*.xml "$destino/" 2>/dev/null || true
  echo "listo en $(cat "$destino/segundos") s"
done

python3 scripts/matriz.py target/matriz --pagina docs/matriz.js
