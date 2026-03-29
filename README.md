# SoulMint Android Prototype

Rebuilt from `soulmint-app-spec.md`.

Included in this rebuild:

- Kotlin + Jetpack Compose Android app
- Firebase Android config with `google-services.json`
- Dark cosmic-romance UI flow from onboarding through feed/profile/settings
- Firebase anonymous-auth bridge for a real app session
- Firebase Functions backend for `generateAvatarVariants`
- Stability + Pinata pipeline with fallback behavior when secrets are missing

## Project Documents

- [LICENSE.md](/Users/wangkingqq126.com/seeker/soulmint/LICENSE.md)
- [PRIVACY-POLICY.md](/Users/wangkingqq126.com/seeker/soulmint/PRIVACY-POLICY.md)
- [COPYRIGHT.md](/Users/wangkingqq126.com/seeker/soulmint/COPYRIGHT.md)
- [TERMS&CONDITIONS.md](/Users/wangkingqq126.com/seeker/soulmint/TERMS&CONDITIONS.md)

## Android

The app is configured for:

- package name `com.soulmint`
- Firebase project `soulmint-cfeb7`
- Cloud Functions base URL `https://us-central1-soulmint-cfeb7.cloudfunctions.net/`

## Firebase Functions

The backend scaffold in `functions/`:

- verifies Firebase bearer tokens
- validates `selfDescription`
- builds the prompt server-side
- runs text moderation before image generation
- runs image moderation before Pinata upload
- can call Stability and Pinata when secrets are present
- returns fallback variants otherwise

Secrets expected:

- `STABILITY_API_KEY`
- `PINATA_JWT`

## Remaining Work

- add richer moderation policy and review tooling
- replace remaining mock feed/chat/mint flows with production backends
