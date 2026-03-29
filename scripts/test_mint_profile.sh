#!/bin/bash
set -euo pipefail

API_KEY="AIzaSyCM3qGhFug2I2RW-VGLxk9IYnnQzNVxaMA"
FUNCTION_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"

TOKEN="$(
  curl -sS "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}" \
    -H "Content-Type: application/json" \
    --data '{"returnSecureToken":true}' \
  | ruby -rjson -e 'print JSON.parse(STDIN.read).fetch("idToken")'
)"

curl -sS "${FUNCTION_URL}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data '{
    "name":"MintTester",
    "bio":"A warm, creative night owl with bookshop energy and silver jewelry.",
    "dreamDescription":"Looking for someone witty, adventurous, and emotionally fluent.",
    "selfTags":["warm","creative","bookish"],
    "dreamTags":["witty","adventurous","empathetic"],
    "avatarPreviewUrl":"https://gateway.pinata.cloud/ipfs/bafybeietcseq7gwpyt47irgpevadpms62d4ndqzhzrygv3aeygoetemyem",
    "avatarIpfsUrl":"ipfs://bafybeietcseq7gwpyt47irgpevadpms62d4ndqzhzrygv3aeygoetemyem",
    "ownerWalletAddress":"11111111111111111111111111111111"
  }' \
| ruby -rjson -e '
response = JSON.parse(STDIN.read)
puts JSON.pretty_generate({
  tokenId: response["tokenId"],
  tier: response["tier"],
  txHashPresent: !response["txHash"].to_s.empty?,
  mintMode: response["mintMode"],
  walletAddress: response["walletAddress"],
  metadataIpfsUrl: response["metadataIpfsUrl"],
  externalMintUrl: response["externalMintUrl"],
  externalMint: response["externalMint"],
  preparedMintPresent: !response.dig("externalMint", "preparedMint", "transactionBase64").to_s.empty?
})
'
