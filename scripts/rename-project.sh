#!/usr/bin/env bash
# Turn this template into your own service:
#   scripts/rename-project.sh <artifact-name> <base.package>
#   e.g. scripts/rename-project.sh ledger-service com.acme.ledger
set -euo pipefail
[ $# -eq 2 ] || { echo "usage: $0 <artifact-name> <base.package>"; exit 1; }
NEW_NAME="$1"; NEW_PKG="$2"
OLD_NAME="payments-service"; OLD_PKG="com.example.payments"
OLD_PATH="${OLD_PKG//.//}"; NEW_PATH="${NEW_PKG//.//}"
cd "$(dirname "$0")/.."

for root in src/main/kotlin src/test/kotlin; do
  [ -d "$root/$OLD_PATH" ] || continue
  mkdir -p "$root/$NEW_PATH"
  cp -a "$root/$OLD_PATH/." "$root/$NEW_PATH/"
  rm -rf "$root/com/example"
  find "$root" -type d -empty -delete
done

FILES=$(grep -rIl --exclude-dir={.git,build,.gradle,node_modules,.idea} -e "$OLD_PKG" -e "$OLD_NAME" . || true)
for f in $FILES; do
  sed -i -e "s#$OLD_PKG#$NEW_PKG#g" -e "s#$OLD_NAME#$NEW_NAME#g" "$f"
done
grep -rIl "payments-service" .idea 2>/dev/null | xargs -r sed -i "s#payments-service#$NEW_NAME#g"
echo "Renamed to $NEW_NAME / $NEW_PKG. Review with git diff, then: ./gradlew check"
echo "Domain terms (Payment, /v1/payments, table names) are intentionally left for you to evolve."
