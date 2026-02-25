#!/bin/bash

# Script per testare il servizio bizEvents di pagoPA
# Recupera le ricevute tramite codice fiscale EC, IUR e opzionalmente IUV.

set -euo pipefail

usage() {
    cat <<EOF
Uso: $(basename "$0") -k <subscription-key> -f <codice-fiscale> -r <iur> [-v <iuv>] [-b <base-url>] [-x <request-id>]

Parametri obbligatori:
  -k  Subscription key (Ocp-Apim-Subscription-Key)
  -f  Codice fiscale dell'ente creditore
  -r  IUR (Identificativo Univoco Riscossione)

Parametri opzionali:
  -v  IUV (Identificativo Univoco Versamento) - se specificato usa l'endpoint IUV+IUR
  -b  Base URL (default: https://api.platform.pagopa.it/bizevents/service/v1)
  -x  X-Request-Id (default: generato automaticamente)

Esempi:
  $(basename "$0") -k mykey123 -f 01234567890 -r abc123
  $(basename "$0") -k mykey123 -f 01234567890 -r abc123 -v 012345678901234
EOF
    exit 1
}

BASE_URL="https://api.platform.pagopa.it/bizevents/service/v1"
SUBSCRIPTION_KEY=""
FISCAL_CODE=""
IUR=""
IUV=""
REQUEST_ID=""

while getopts "k:f:r:v:b:x:h" opt; do
    case $opt in
        k) SUBSCRIPTION_KEY="$OPTARG" ;;
        f) FISCAL_CODE="$OPTARG" ;;
        r) IUR="$OPTARG" ;;
        v) IUV="$OPTARG" ;;
        b) BASE_URL="$OPTARG" ;;
        x) REQUEST_ID="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

if [ -z "$SUBSCRIPTION_KEY" ] || [ -z "$FISCAL_CODE" ] || [ -z "$IUR" ]; then
    echo "Errore: parametri obbligatori mancanti."
    echo ""
    usage
fi

if [ -n "$IUV" ]; then
    URL="${BASE_URL}/organizations/${FISCAL_CODE}/receipts/${IUR}/paymentoptions/${IUV}"
else
    URL="${BASE_URL}/organizations/${FISCAL_CODE}/receipts/${IUR}"
fi

HEADERS=(-H "Ocp-Apim-Subscription-Key: ${SUBSCRIPTION_KEY}")

if [ -n "$REQUEST_ID" ]; then
    HEADERS+=(-H "X-Request-Id: ${REQUEST_ID}")
fi

echo "GET ${URL}"
echo "---"

curl -s -w "\n\nHTTP Status: %{http_code}\n" \
    -X GET "$URL" \
    "${HEADERS[@]}" \
    -H "Accept: application/json"
