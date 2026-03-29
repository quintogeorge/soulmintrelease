#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
EMAIL="soulmint.$(date +%s)@example.com"
PASSWORD="soulmint123"

SIGN_UP_RESPONSE="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"returnSecureToken\":true}"
)"

SIGN_IN_RESPONSE="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"returnSecureToken\":true}"
)"

SIGN_UP_RESPONSE="${SIGN_UP_RESPONSE}" SIGN_IN_RESPONSE="${SIGN_IN_RESPONSE}" ruby -rjson -e '
signup = JSON.parse(ENV.fetch("SIGN_UP_RESPONSE"))
signin = JSON.parse(ENV.fetch("SIGN_IN_RESPONSE"))
puts JSON.pretty_generate({
  email: signup["email"],
  signUpLocalId: signup["localId"],
  signInLocalId: signin["localId"],
  idTokenPresent: !signin["idToken"].to_s.empty?
})
'
