#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
ACTION_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/recordFeedAction}"
REFRESH_URL="${2:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/refreshMatchFeed}"
MINT_URL="${3:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
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

  local uid
  uid="$(printf '%s' "${auth_response}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("localId")')"

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

  printf '%s|%s' "${uid}" "${token}"
}

TARGET_INFO="$(
  mint_user "PassMe" "pass.${STAMP}@example.com" "soulmint123" '["witty","cinematic","gentle"]' '["curious","steady","playful"]'
)"
TARGET_UID="${TARGET_INFO%%|*}"

REQUESTER_INFO="$(
  mint_user "Swiper" "swiper.${STAMP}@example.com" "soulmint123" '["warm","creative","bookish"]' '["witty","adventurous","gentle"]'
)"
REQUESTER_TOKEN="${REQUESTER_INFO##*|}"

INITIAL_FEED="$(
  curl -sS "${REFRESH_URL}" \
    -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{}'
)"

ACTION_RESPONSE="$(
  curl -sS "${ACTION_URL}" \
    -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "{
      \"targetId\":\"${TARGET_UID}\",
      \"action\":\"pass\"
    }"
)"

FINAL_FEED="$(
  curl -sS "${REFRESH_URL}" \
    -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{}'
)"

TARGET_UID="${TARGET_UID}" INITIAL_FEED="${INITIAL_FEED}" ACTION_RESPONSE="${ACTION_RESPONSE}" FINAL_FEED="${FINAL_FEED}" ruby -rjson -e '
target_uid = ENV.fetch("TARGET_UID")
initial_feed = JSON.parse(ENV.fetch("INITIAL_FEED")).fetch("profiles")
action_response = JSON.parse(ENV.fetch("ACTION_RESPONSE"))
final_feed = JSON.parse(ENV.fetch("FINAL_FEED")).fetch("profiles")

puts JSON.pretty_generate({
  targetUid: target_uid,
  initialFeedContainsTarget: initial_feed.any? { |profile| profile["id"] == target_uid },
  actionSaved: action_response["saved"],
  finalFeedContainsTarget: final_feed.any? { |profile| profile["id"] == target_uid }
})
'
