#!/bin/sh
set -e

find scenarios -name '*.js' | while read -r script; do
  echo "=== $script ==="
  k6 run "$script"
done
