#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
SEND_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/sendChatMessage}"
DELETE_URL="${2:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/deleteChatThread}"
MINT_URL="${3:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
STAMP="$(date +%s)"

mint_user() {
  local name="$1"
  local email="$2"
  local password="$3"

  local auth_response
  auth_response="$(
    curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
      -H "Content-Type: application/json" \
      --data "{\"email\":\"${email}\",\"password\":\"${password}\",\"returnSecureToken\":true}"
  )"

  local token
  token="$(printf '%s' "${auth_response}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")')"

  local uid
  uid="$(printf '%s' "${auth_response}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("localId")')"

  curl -sS "${MINT_URL}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    --data "{
      \"name\":\"${name}\",
      \"bio\":\"${name} is warm, creative, and intentional.\",
      \"dreamDescription\":\"${name} wants someone witty and adventurous.\",
      \"selfTags\":[\"warm\",\"creative\",\"bookish\"],
      \"dreamTags\":[\"witty\",\"adventurous\",\"gentle\"],
      \"avatarPreviewUrl\":\"https://placehold.co/1024x1024/3F285C/E05C8A?text=${name}\",
      \"avatarIpfsUrl\":\"ipfs://soulmint-test/${name}\"
    }" >/dev/null

  printf '%s|%s' "${uid}" "${token}"
}

TARGET_INFO="$(
  mint_user "ChatTarget" "chattarget.${STAMP}@example.com" "soulmint123"
)"
TARGET_UID="${TARGET_INFO%%|*}"

REQUESTER_INFO="$(
  mint_user "ChatOwner" "chatowner.${STAMP}@example.com" "soulmint123"
)"
REQUESTER_TOKEN="${REQUESTER_INFO##*|}"

curl -sS "${SEND_URL}" \
  -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "{
    \"chatId\":\"${TARGET_UID}\",
    \"message\":\"Hi, what kind of date feels cinematic to you?\",
    \"senderName\":\"You\"
  }" >/dev/null

FIRST_DELETE="$(
  curl -sS "${DELETE_URL}" \
    -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "{
      \"chatId\":\"${TARGET_UID}\"
    }"
)"

SECOND_DELETE="$(
  curl -sS "${DELETE_URL}" \
    -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "{
      \"chatId\":\"${TARGET_UID}\"
    }"
)"

FIRST_DELETE="${FIRST_DELETE}" SECOND_DELETE="${SECOND_DELETE}" ruby -rjson -e '
first_delete = JSON.parse(ENV.fetch("FIRST_DELETE"))
second_delete = JSON.parse(ENV.fetch("SECOND_DELETE"))
puts JSON.pretty_generate({
  firstDelete: first_delete,
  secondDelete: second_delete
})
'
