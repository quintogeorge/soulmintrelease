#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
REFRESH_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/refreshMatchFeed}"
MINT_URL="${2:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
STAMP="$(date +%s)"

mint_user() {
  local name="$1"
  local email="$2"
  local password="$3"
  local self_tags="$4"
  local dream_tags="$5"

  local auth_response
  auth_response="$(
    curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
      -H "Content-Type: application/json" \
      --data "{\"email\":\"${email}\",\"password\":\"${password}\",\"returnSecureToken\":true}"
  )"

  local token
  token="$(printf '%s' "${auth_response}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")')"

  curl -sS "${MINT_URL}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    --data "{
      \"name\":\"${name}\",
      \"bio\":\"${name} is warm, creative, and intentional.\",
      \"dreamDescription\":\"${name} wants someone witty and adventurous.\",
      \"selfTags\":${self_tags},
      \"dreamTags\":${dream_tags},
      \"avatarPreviewUrl\":\"https://placehold.co/1024x1024/3F285C/E05C8A?text=${name}\",
      \"avatarIpfsUrl\":\"ipfs://soulmint-test/${name}\"
    }" >/dev/null

  printf '%s' "${token}"
}

mint_user "Nova" "nova.${STAMP}@example.com" "soulmint123" '["witty","cinematic","gentle"]' '["curious","steady","playful"]' >/dev/null
mint_user "Iris" "iris.${STAMP}@example.com" "soulmint123" '["dreamy","adventurous","kind"]' '["bookish","warm","intentional"]' >/dev/null
mint_user "Sol" "sol.${STAMP}@example.com" "soulmint123" '["grounded","playful","intentional"]' '["creative","warm","adventurous"]' >/dev/null

REQUESTER_TOKEN="$(
  mint_user "You" "you.${STAMP}@example.com" "soulmint123" '["warm","creative","bookish"]' '["witty","adventurous","empathetic"]'
)"

curl -sS "${REFRESH_URL}" \
  -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
  -H "Content-Type: application/json" \
  --data '{}' \
| ruby -rjson -e '
response = JSON.parse(STDIN.read)
profiles = response.fetch("profiles")
summary = profiles.map do |profile|
  {
    id: profile["id"],
    name: profile["name"],
    compatibility: profile["compatibility"]
  }
end
puts JSON.pretty_generate(summary)
'
