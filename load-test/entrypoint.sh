#!/bin/sh
set -e

TESTID="$(date +%s)"

find scenarios -name '*.js' | while read -r script; do
  echo "=== $script ==="
  k6 run --out experimental-prometheus-rw --tag testid="$TESTID" "$script"
done
