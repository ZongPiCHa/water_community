#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env.local ]]; then
  echo "Missing .env.local. Copy .env.example and fill in the local database password." >&2
  exit 1
fi

set -a
source .env.local
set +a

exec ./mvnw spring-boot:run
