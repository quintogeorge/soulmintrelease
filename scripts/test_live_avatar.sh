#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
FUNCTION_URL="${1:-https://generateavatarvariants-154995870561.us-central1.run.app}"
SELF_DESCRIPTION="I am a warm, creative person with bookshop energy, silver jewelry, and a calm voice that gets brighter when I talk about art."

TOKEN="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data '{"returnSecureToken":true}' \
  | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")'
)"

curl -sS "${FUNCTION_URL}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data "{\"selfDescription\":\"${SELF_DESCRIPTION}\"}" \
| ruby -rjson -e '
response = JSON.parse(STDIN.read)
variants = response.fetch("variants")
summary = variants.map.with_index do |variant, index|
  {
    index: index + 1,
    ipfsUrl: variant["ipfsUrl"],
    previewPrefix: variant.fetch("previewUrl")[0, 32]
  }
end
puts JSON.pretty_generate(summary)
'
