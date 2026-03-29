#!/bin/bash
set -euo pipefail

FUNCTION_URL="${1:-https://us-central1-soulmint-cfeb7.cloudfunctions.net/mintSoulboundProfile}"
OUTPUT="$("$(dirname "$0")/test_mint_profile.sh" "$FUNCTION_URL")"

echo "$OUTPUT" | ruby -rjson -e '
response = JSON.parse(STDIN.read)
external = response.fetch("externalMint")
raise "missing platform" if external["platform"].to_s.empty?
raise "missing action" unless external["action"] == "prepare_solana_mint"
raise "missing network" if external["network"].to_s.empty?
raise "missing owner wallet" if external["ownerWalletAddress"].to_s.empty?
raise "missing metadata ipfs" unless external["metadataIpfsUrl"].to_s.start_with?("ipfs://")
raise "missing metadata gateway url" unless external["metadataGatewayUrl"].to_s.start_with?("https://")

prepared_mint = external["preparedMint"]
raise "missing preparedMint" if prepared_mint.nil?
raise "missing transactionBase64" if prepared_mint["transactionBase64"].to_s.empty?
raise "missing mintAddress" if prepared_mint["mintAddress"].to_s.empty?
raise "missing ownerWalletAddress" if prepared_mint["ownerWalletAddress"].to_s.empty?
raise "missing rpcUrl" if prepared_mint["rpcUrl"].to_s.empty?

puts JSON.pretty_generate({
  platform: external["platform"],
  action: external["action"],
  network: external["network"],
  ownerWalletAddress: external["ownerWalletAddress"],
  preparedMintPresent: !prepared_mint.nil?,
  mintAddress: prepared_mint["mintAddress"]
})
'
