#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
DELETE_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/deleteMyAccount}"
MINT_URL="${2:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
EMAIL="delete.$(date +%s)@example.com"
PASSWORD="soulmint123"

AUTH_RESPONSE="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"returnSecureToken\":true}"
)"

TOKEN="$(printf '%s' "${AUTH_RESPONSE}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")')"

curl -sS "${MINT_URL}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data '{
    "name":"DeleteMe",
    "bio":"DeleteMe is warm, creative, and intentional.",
    "dreamDescription":"DeleteMe wants someone witty and adventurous.",
    "selfTags":["warm","creative","bookish"],
    "dreamTags":["witty","adventurous","gentle"],
    "avatarPreviewUrl":"https://placehold.co/1024x1024/3F285C/E05C8A?text=DeleteMe",
    "avatarIpfsUrl":"ipfs://soulmint-test/DeleteMe"
  }' >/dev/null

DELETE_RESPONSE="$(
  curl -sS "${DELETE_URL}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{}'
)"

SIGN_IN_RESPONSE="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"returnSecureToken\":true}"
)"

DELETE_RESPONSE="${DELETE_RESPONSE}" SIGN_IN_RESPONSE="${SIGN_IN_RESPONSE}" ruby -rjson -e '
delete_response = JSON.parse(ENV.fetch("DELETE_RESPONSE"))
sign_in_response = JSON.parse(ENV.fetch("SIGN_IN_RESPONSE"))
puts JSON.pretty_generate({
  deleted: delete_response["deleted"],
  signInError: sign_in_response.dig("error", "message")
})
'
