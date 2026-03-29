#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
REFRESH_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/refreshMatchFeed}"
MINT_URL="${2:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
STAMP="$(date +%s)"

mint_user() {
  local name="$1"
  local email="$2"
  local self_tags="$3"
  local dream_tags="$4"
  local bio="$5"
  local dream_desc="$6"

  local auth_response
  auth_response="$(
    curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
      -H "Content-Type: application/json" \
      --data "{\"email\":\"${email}\",\"password\":\"soulmint123\",\"returnSecureToken\":true}"
  )"

  local token
  token="$(printf '%s' "${auth_response}" | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")')"

  curl -sS "${MINT_URL}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    --data "{
      \"name\":\"${name}\",
      \"bio\":\"${bio}\",
      \"dreamDescription\":\"${dream_desc}\",
      \"selfTags\":${self_tags},
      \"dreamTags\":${dream_tags},
      \"avatarPreviewUrl\":\"https://placehold.co/1024x1024/3F285C/E05C8A?text=${name}\",
      \"avatarIpfsUrl\":\"ipfs://soulmint-test/${name}\"
    }" >/dev/null

  printf '%s' "${token}"
}

mint_user \
  "Aligned" \
  "aligned.${STAMP}@example.com" \
  '["witty","adventurous","empathetic"]' \
  '["warm","creative","bookish"]' \
  "A witty, adventurous, empathetic soul who loves books and art." \
  "Looking for someone warm, creative, and bookish." >/dev/null

mint_user \
  "Mismatch" \
  "mismatch.${STAMP}@example.com" \
  '["aggressive","loud","chaotic"]' \
  '["dominant","flashy","reckless"]' \
  "A loud chaotic extrovert obsessed with clout and noise." \
  "Looking for someone dominant, flashy, and reckless." >/dev/null

REQUESTER_TOKEN="$(
  mint_user \
    "Requester" \
    "requester.${STAMP}@example.com" \
    '["warm","creative","bookish"]' \
    '["witty","adventurous","empathetic"]' \
    "A warm creative bookish person who values softness and emotional fluency." \
    "Looking for someone witty, adventurous, and empathetic."
)"

curl -sS "${REFRESH_URL}" \
  -H "Authorization: Bearer ${REQUESTER_TOKEN}" \
  -H "Content-Type: application/json" \
  --data '{}' \
| ruby -rjson -e '
profiles = JSON.parse(STDIN.read).fetch("profiles")
top_two = profiles.first(2).map { |profile| { name: profile["name"], compatibility: profile["compatibility"] } }
puts JSON.pretty_generate(top_two)
aligned_index = profiles.index { |profile| profile["name"] == "Aligned" }
mismatch_index = profiles.index { |profile| profile["name"] == "Mismatch" }
abort("Aligned profile missing from refreshed feed") if aligned_index.nil?
abort("Aligned profile did not outrank mismatch profile") unless mismatch_index.nil? || aligned_index < mismatch_index
'
