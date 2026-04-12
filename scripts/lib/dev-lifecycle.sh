#!/usr/bin/env bash
# shellcheck shell=bash
# Shared policy helper for dev.sh backend lifecycle decisions.

should_stop_backend_on_exit() {
  local policy="${1:-auto}"
  local interrupted="${2:-false}"

  case "$policy" in
    always)
      return 0
      ;;
    never)
      return 1
      ;;
    auto)
      [[ "$interrupted" == "true" ]]
      return
      ;;
    *)
      # Safe fallback for unknown values: keep backend running.
      return 1
      ;;
  esac
}
