#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

echo "[quality] core go mod tidy"
(cd core && go mod tidy)

if ! git diff --quiet -- core/go.mod core/go.sum; then
  echo "[quality] go mod tidy changed core/go.mod or core/go.sum"
  git diff -- core/go.mod core/go.sum
  exit 1
fi

echo "[quality] core test"
(cd core && go test ./...)

echo "[quality] core vet"
(cd core && go vet ./...)

echo "[quality] core gofmt check"
(cd core && test -z "$(gofmt -l .)")
