#!/usr/bin/env bash
# Generates TypeScript types from each backend service's live OpenAPI spec (served by springdoc
# at /v3/api-docs). Requires the services to actually be running — e.g. `docker compose up` from
# infra/ — since there's no static spec file checked into the repo to generate from instead.
#
# Usage: npm run generate:api-types
#        GATEWAY_URL=http://localhost:9080 npm run generate:api-types   # override a service URL
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
DOCUMENT_SERVICE_URL="${DOCUMENT_SERVICE_URL:-http://localhost:8081}"
QUERY_SERVICE_URL="${QUERY_SERVICE_URL:-http://localhost:8083}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/../src/lib/generated"
mkdir -p "$OUT_DIR"

generate() {
  local name="$1"
  local base_url="$2"
  echo "Generating types for $name from $base_url/v3/api-docs ..."
  npx --yes openapi-typescript "$base_url/v3/api-docs" -o "$OUT_DIR/$name.ts"
}

generate "gateway-service" "$GATEWAY_URL"
generate "document-service" "$DOCUMENT_SERVICE_URL"
generate "query-service" "$QUERY_SERVICE_URL"

echo "Done. Generated types are in $OUT_DIR (gitignored — regenerate rather than commit)."
