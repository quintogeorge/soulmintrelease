#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
CHAT_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/sendChatMessage}"

TOKEN="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data '{"returnSecureToken":true}' \
  | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")'
)"

curl -sS "${CHAT_URL}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data '{
    "chatId":"nova",
    "message":"Hi, what kind of date feels cinematic to you?",
    "senderName":"You"
  }'

echo
