# SoulMint — App Specification
**Version:** 1.0 Draft  
**Platform:** Android (Kotlin + Jetpack Compose)  
**Purpose:** Web3 dating app where users mint their identity as an NFT and get matched via AI

---

## 1. Overview

SoulMint is an Android dating app that fuses NFT identity, AI-powered compatibility matching, and AI-generated avatar profiles. Each user writes a text description of themselves, which is used to generate a unique AI portrait. That image, combined with their dream partner description, is minted as a **Soulbound NFT**. An AI engine compares NFT metadata across users to suggest highly compatible matches.

No real photos are ever collected. Identity is expressed purely through words — your self-description shapes your avatar, your dream description shapes your matches.

Web3 complexity is hidden from the user — wallet creation happens silently on sign-up via an embedded wallet provider (Privy or Thirdweb).

---

## 2. Core User Flow

```
Sign Up (email/social)
    → Embedded Wallet Created (silent)
    → Onboarding Step 1: Describe Yourself (text prompt)
    → Onboarding Step 2: Describe Your Dream Partner (text prompt)
    → AI Enriches Both Descriptions → User Confirms Tags
    → AI Generates Portrait from Self-Description (Stable Diffusion / DALL·E)
    → User Picks Favourite from 4 Generated Variants
    → Preview NFT Card (AI portrait + dream tags)
    → Mint NFT (image + metadata → IPFS → Polygon)
    → Enter Match Feed
    → Like / Super Like
    → Mutual Match → Chat Unlocked
    → Milestones stored on-chain
```

---

## 3. Feature Specification

### 3.1 Authentication & Wallet

| Item | Detail |
|---|---|
| Sign-up methods | Google, Apple, Email/OTP |
| Wallet provider | Privy (embedded wallet, no seed phrase UX) |
| Wallet exposure | Hidden by default; advanced users can view in Settings |
| Chain | Polygon (low gas) — optionally Base as backup |

**Implementation notes:**
- Use Privy Android SDK or Thirdweb In-App Wallet SDK
- Generate wallet server-side and associate with user account
- Abstract all wallet/gas terminology from primary UI

---

### 3.2 Dream Description Input

The user types a free-text description of their ideal partner.

**Rules:**
- Min 20 characters, max 500 characters
- Words/sentences only (no emojis, no links)
- AI layer processes it before storing

**AI Enhancement Step (on-device call to Claude API):**
- Expand vague traits ("funny") into richer personality dimensions
- Normalize language for embedding consistency
- Suggest 3–5 trait tags (user confirms or edits)
- Generate a `compatibilityVector` (float array via embedding API)

**Stored in NFT metadata as:**
```json
{
  "dreamDescription": "raw user text",
  "dreamTags": ["witty", "adventurous", "empathetic"],
  "compatibilityVector": [0.12, -0.43, ...]
}
```

---

### 3.2 Self-Description Input

The user writes a free-text description of themselves — personality, style, vibe, appearance in words.

**Rules:**
- Min 30 characters, max 600 characters
- Used as the image generation prompt; encourage vivid, specific language
- AI layer cleans and enriches before passing to image model
- User cannot upload photos — description is the only identity input

**AI Enhancement Step (Claude API):**
- Rewrite raw description into an image-generation-optimized prompt
- Extract personality trait tags from the description
- Generate a `selfVector` (float embedding) representing the user's identity
- Suggest tone/style adjustments ("Your description sounds warm and creative — want to keep that in your avatar?")

**Stored in NFT metadata as:**
```json
{
  "selfDescription": "raw user text",
  "selfPrompt": "AI-optimized image generation prompt",
  "selfTags": ["warm", "creative", "bookish"],
  "selfVector": [0.34, -0.21, ...]
}
```

---

### 3.3 Dream Description Input

The user writes a free-text description of their ideal partner.

**Rules:**
- Min 20 characters, max 500 characters
- Words/sentences only
- AI layer processes it before storing

**AI Enhancement Step (Claude API):**
- Expand vague traits ("funny") into richer personality dimensions
- Normalize language for embedding consistency
- Suggest 3–5 trait tags (user confirms or edits)
- Generate a `compatibilityVector` (float array via embedding API)

**Stored in NFT metadata as:**
```json
{
  "dreamDescription": "raw user text",
  "dreamTags": ["witty", "adventurous", "empathetic"],
  "compatibilityVector": [0.12, -0.43, ...]
}
```

---

### 3.4 AI Avatar Generation

The user's self-description prompt is sent to an image generation API to produce their NFT avatar. No real photo is ever used.

| Item | Detail |
|---|---|
| Image model | Stable Diffusion XL via Replicate API, or DALL·E 3 via OpenAI |
| Variants | 4 images generated per session; user picks one |
| Style | Semi-stylised portrait (not photorealistic) — reduces uncanny valley and privacy concerns |
| Format | PNG, 1024×1024, compressed to ≤1.5MB |
| Regeneration | User may regenerate up to 3 times by editing their prompt |
| Storage | Uploaded to IPFS via Pinata; `ipfs://` CID stored in NFT metadata |
| Moderation | All generated images pass through content moderation (OpenAI Moderation API or similar) before display |

**Prompt construction (server-side):**
```
system: "Generate a stylised portrait avatar. Semi-illustrated, warm lighting,
         tasteful, no explicit content. Subject description:"
user:   {AI-enriched self-description}
suffix: "Style: digital portrait, artstation trending, soft focus background,
         centered composition."
```

**Variant selection UI:**
- 2×2 grid of generated images
- Tap to expand any variant
- Confirm button mints the selected one
- "Try again" regenerates all 4 (deducts one regeneration credit)

---

### 3.5 NFT Minting

**Smart Contract:** ERC-721 Soulbound (non-transferable)  
**Chain:** Polygon Mumbai (testnet) → Polygon Mainnet  
**Gas:** Sponsored via Gelato Relay (user pays nothing)

**Full NFT Metadata Schema:**
```json
{
  "name": "SoulMint #{tokenId}",
  "description": "Soulbound identity NFT",
  "image": "ipfs://<ai-avatar-CID>",
  "attributes": {
    "selfDescription": "string (raw)",
    "selfPrompt": "string (AI-optimized)",
    "selfTags": ["string"],
    "selfVector": [float],
    "dreamDescription": "string (raw)",
    "dreamTags": ["string"],
    "compatibilityVector": [float],
    "mintedAt": "ISO8601",
    "matchCount": 0,
    "tier": "seed",
    "avatarStyle": "semi-illustrated"
  }
}
```

**NFT Tier System (evolves over time):**

| Tier | Trigger |
|---|---|
| Seed | Just minted |
| Spark | First mutual match |
| Flame | 5 matches |
| Blaze | First real date (self-reported) |
| Eternal | On-chain milestone (long-term match) |

---

### 3.6 AI Matching Engine

**Input:** Current user's `compatibilityVector` + `selfVector`  
**Process:** Multi-dimensional cosine similarity across all other users  
**Scoring:** Bidirectional — both parties must score above threshold for a match suggestion  
**Exclusions:** Already-liked, blocked, or already-matched users

**Matching formula:**
```
// How well does B's actual self match what A is looking for?
score(A→B) = cosine_sim(A.compatibilityVector, B.selfVector)

// How well does A's actual self match what B is looking for?
score(B→A) = cosine_sim(B.compatibilityVector, A.selfVector)

// Final mutual compatibility score
finalScore  = (score(A→B) + score(B→A)) / 2
```

This means: you appear in someone's feed only if you match *their* dream, AND they match *yours*. Purely one-sided attraction is filtered out.

**Backend stack for matching:**
- Supabase `pgvector` extension for vector similarity search
- Claude API for generating/refreshing embeddings on description updates
- Cron job refreshes match feed every 6 hours

---

### 3.7 Match Feed

| Element | Behavior |
|---|---|
| Profile card | AI-generated avatar + self tags + dream tags + compatibility % |
| Avatar visibility | Always visible (it is AI-generated, not a real photo) |
| Swipe right | Like |
| Swipe left | Pass |
| Swipe up | Super Like (limited per day) |
| Mutual like | Triggers match event, unlocks chat, on-chain log |

**Feed ordering:** By `finalScore` descending, then by recency

---

### 3.8 Chat

- Unlocked only on mutual match
- End-to-end encrypted (use Matrix/Element SDK or Stream Chat)
- First message prompt suggested by AI: _"Here's an icebreaker based on your shared tags…"_
- Report/Block triggers on-chain flag (stored in contract)

---

### 3.9 NFT Evolution & Milestones

On-chain events written to smart contract:
- `MatchCreated(userA, userB, timestamp)`
- `MilestoneReached(userId, milestone, timestamp)`
- `UserBlocked(reporter, reported)`

NFT metadata updated off-chain (mutable pointer via token URI updater role):
- `matchCount` increments
- `tier` upgrades

---

### 3.10 Privacy & Safety

| Feature | Implementation |
|---|---|
| No real photos | Only AI-generated avatars from text prompts; no camera access required |
| Content moderation | All AI images pass moderation API before display or minting |
| Avatar regeneration abuse | Rate-limit regenerations (3 per day); flag anomalous prompt patterns |
| NFT visibility | Users choose: Public / Match-only / Hidden |
| Age verification | ZK proof via Reclaim Protocol or Worldcoin (phase 2) |
| Report system | In-app report → on-chain flag → moderation queue |
| Prompt safety | Self-description and dream prompts filtered for explicit/harmful content before image generation |
| Data deletion | Burn NFT + wipe Supabase record (GDPR flow) |

---

## 4. Screen Inventory

### 4.1 Onboarding Screens

| Screen | Description |
|---|---|
| `WelcomeScreen` | App logo, tagline, Sign Up / Log In CTAs |
| `AuthScreen` | Google / Apple / Email options |
| `SelfDescribeScreen` | Text area for user to describe themselves (personality, style, vibe) with character counter and writing prompts |
| `DreamInputScreen` | Text area for dream partner description + character counter |
| `TagConfirmScreen` | Confirm/edit AI-suggested trait tags for both self and dream descriptions |
| `AvatarGeneratingScreen` | Full-screen animation while AI generates avatar ("Crafting your soul…") |
| `AvatarPickScreen` | 2×2 grid of 4 generated avatar variants; tap to expand, confirm to proceed |
| `MintPreviewScreen` | Preview complete NFT card (chosen avatar + both tag sets) before minting |
| `MintingScreen` | Animated progress stepper: Uploading → Preparing → Signing → Confirming → Done |
| `MintSuccessScreen` | NFT card revealed with token ID, tier badge, confetti + share CTA |

### 4.2 Core App Screens

| Screen | Description |
|---|---|
| `FeedScreen` | Swipeable match cards showing AI avatars + compatibility score |
| `MatchScreen` | Celebration animation + Start Chat CTA |
| `ChatListScreen` | All matched conversations |
| `ChatDetailScreen` | Conversation thread + AI-generated icebreaker suggestion |
| `ProfileScreen` | Own NFT card, AI avatar, tier badge, match stats |
| `EditProfileScreen` | Update self-description or dream description (triggers re-embedding; optionally regenerate avatar) |
| `RegenerateAvatarScreen` | Re-enter or tweak self-description prompt, generate new avatar variants, re-mint metadata |
| `SettingsScreen` | Privacy controls, wallet info, notifications, delete account |
| `ReportScreen` | Report / block flow |

---

## 5. UI Design System

### 5.1 Design Direction

**Aesthetic:** Dark luxury / cosmic romance  
**Mood:** Premium, intimate, slightly mysterious — like holding a glowing artefact  
**References:** Deep space photography + holographic foil + minimal dating app clarity

### 5.2 Color Palette

| Token | Value | Usage |
|---|---|---|
| `--bg-primary` | `#0A0A0F` | App background |
| `--bg-surface` | `#13131A` | Card surfaces |
| `--bg-elevated` | `#1C1C28` | Elevated panels, bottom sheets |
| `--accent-violet` | `#7B5EA7` | Primary brand color |
| `--accent-glow` | `#A67BDB` | Highlights, active states |
| `--accent-rose` | `#E05C8A` | Like / match / heart actions |
| `--accent-gold` | `#D4A843` | NFT tier badges, premium elements |
| `--text-primary` | `#F0EEF8` | Primary text |
| `--text-secondary` | `#8B89A0` | Labels, subtitles |
| `--text-muted` | `#4A4860` | Placeholder, disabled |
| `--border-subtle` | `#2A2840` | Card borders |
| `--success` | `#4CAF82` | Match confirmed, minting success |

### 5.3 Typography

| Role | Font | Size | Weight |
|---|---|---|---|
| Display / Hero | Cormorant Garamond | 32–48sp | 300 Light |
| Headings | DM Sans | 20–24sp | 500 Medium |
| Body | DM Sans | 14–16sp | 400 Regular |
| Labels / Chips | DM Sans | 11–13sp | 500 Medium |
| Mono (token IDs) | JetBrains Mono | 12sp | 400 |

Import via Google Fonts in `res/font/`.

### 5.4 Key UI Components

#### NFT Card Component
```
┌─────────────────────────────┐
│                             │
│    [AI-generated portrait]  │  ← always visible; stylised, not photorealistic
│                             │
│  ◈ SEED               #042 │  ← tier badge + token ID
│                             │
│  About me:                  │
│  [bookish] [warm] [witty]   │  ← self tags
│                             │
│  Looking for:               │
│  [adventurous] [kind]       │  ← dream tags
│                             │
│  ♥ 94% compatible          │  ← match score
└─────────────────────────────┘
```

**Specs:**
- Corner radius: 20dp
- Background: `--bg-surface` with subtle violet gradient overlay at bottom (via ScrimOverlay)
- Border: 1dp `--border-subtle`, glows `--accent-glow` on hover/focus
- Avatar image: full bleed top section, ~55% of card height, `ContentScale.Crop`
- Tier badge: pill chip, gold border, monospace token ID right-aligned
- Self tags section: label "About me" in `--text-muted` 11sp above chips
- Dream tags section: label "Looking for" in `--text-muted` 11sp above chips
- Tags: filled chips, `--bg-elevated`, `--accent-glow` text
- Compatibility score: rose-colored heart icon + percentage

#### Action Buttons (Swipe Overlay)
```
  [✕ Pass]        [★ Super]       [♥ Like]
  40dp circle     52dp circle     40dp circle
  --text-muted    --accent-gold   --accent-rose
```

#### Minting Progress Component
```
  ● Uploading AI avatar to IPFS   ✓
  ● Preparing metadata            ✓
  ◉ Signing transaction…          (spinner)
  ○ Confirming on Polygon         (waiting)
  ○ NFT ready                     (waiting)
```

Style: vertical stepper, completed steps turn `--success`, active step pulses gently.

#### Avatar Picker Grid (2×2)
```
┌──────────────┬──────────────┐
│              │              │
│   Variant A  │   Variant B  │
│              │              │
├──────────────┼──────────────┤
│              │              │
│   Variant C  │   Variant D  │
│              │              │
└──────────────┴──────────────┘
      [ ↺ Regenerate (2 left) ]
```
- Selected variant: violet border glow (2dp `--accent-glow`)
- Tap to expand into full-screen preview with confirm button
- Regenerate button shows remaining count; disabled at 0

#### Tag Chip
- Height: 32dp
- Padding: 0 12dp
- Background: `--bg-elevated`
- Border: 1dp `--border-subtle`
- Selected state: `--accent-violet` background, `--text-primary` text
- Unselected: `--text-secondary` text

### 5.5 Motion & Animation

| Interaction | Animation |
|---|---|
| Card swipe like | Slide right + rose tint overlay fade in |
| Card swipe pass | Slide left + desaturate |
| Avatar generating | Particle shimmer loop + pulsing "Crafting your soul…" text |
| Avatar reveal | Blur-to-sharp fade in over 600ms per variant, staggered 150ms apart |
| Mutual match | Confetti burst + ring pulse on both avatars |
| Mint success | NFT card flips in from flat → 3D perspective reveal |
| Tier upgrade | Gold shimmer sweep across card |
| Screen transitions | Shared element transition on NFT card |

Use `androidx.compose.animation` for all transitions. Keep durations 200–400ms except avatar reveal (600ms).

---

## 6. Tech Stack

### Android App
| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Async | Coroutines + Flow |
| Navigation | Compose Navigation |
| Image loading | Coil |
| Networking | Retrofit + OkHttp |

### AI & Image Generation
| Layer | Technology |
|---|---|
| LLM / Embeddings | Claude API (Anthropic) — prompt enrichment + tag generation + vectors |
| Image generation | Replicate API (Stable Diffusion XL) or OpenAI DALL·E 3 |
| Content moderation | OpenAI Moderation API (screens prompts + generated images) |
| Prompt construction | Server-side (Node.js / Python) — never expose raw API keys on device |

### Web3
| Layer | Technology |
|---|---|
| Embedded wallet | Privy Android SDK |
| IPFS storage | Pinata SDK (REST) |
| Smart contract | Solidity ERC-721 (Soulbound / non-transferable) |
| Contract deployment | Hardhat + OpenZeppelin |
| Gas sponsorship | Gelato Relay (meta-transactions) |
| Chain | Polygon (MATIC) |

### Backend
| Layer | Technology |
|---|---|
| Database | Supabase (Postgres + pgvector) |
| Auth bridge | Supabase Auth ↔ Privy user ID |
| Matching engine | Supabase Edge Functions (Deno) |
| Vector search | pgvector cosine similarity |
| File orchestration | Node.js service: generates prompt → calls image API → uploads to IPFS |
| Real-time chat | Stream Chat SDK or Matrix/Element |
| Push notifications | Firebase Cloud Messaging (FCM) |

---

## 7. Smart Contract Specification

**Contract:** `SoulMint.sol`  
**Standard:** ERC-721 with soulbound enforcement  
**Compiler:** Solidity ^0.8.20

```solidity
// Key functions
function mint(address to, string calldata tokenURI) external onlyMinter
function updateMetadata(uint256 tokenId, string calldata newURI) external onlyMinter
function logMatch(uint256 tokenIdA, uint256 tokenIdB) external onlyMinter
function logMilestone(uint256 tokenId, string calldata milestone) external onlyMinter
function burn(uint256 tokenId) external  // only token owner

// Soulbound: block all transfers
function _beforeTokenTransfer(...) internal override {
    require(from == address(0) || to == address(0), "Soulbound: non-transferable");
}

// Events
event MatchCreated(uint256 indexed tokenIdA, uint256 indexed tokenIdB, uint256 timestamp);
event MilestoneReached(uint256 indexed tokenId, string milestone, uint256 timestamp);
event MetadataUpdated(uint256 indexed tokenId, string newURI);
```

**Access control:**
- `MINTER_ROLE` — backend service wallet (Gelato relayer)
- `DEFAULT_ADMIN_ROLE` — multisig (Gnosis Safe)

---

## 8. API Endpoints (Backend)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/onboard/enrich-self` | Claude enriches self-description → returns selfTags + selfPrompt |
| POST | `/api/onboard/enrich-dream` | Claude enriches dream description → returns dreamTags + compatibilityVector |
| POST | `/api/avatar/generate` | Generates 4 avatar variants from selfPrompt via image API |
| POST | `/api/avatar/moderate` | Runs moderation check on generated images |
| POST | `/api/nft/mint` | Uploads image to IPFS, writes metadata JSON, calls smart contract mint |
| PATCH | `/api/nft/:tokenId/metadata` | Updates NFT metadata (after description edits) |
| GET | `/api/feed` | Returns ranked match candidates for current user |
| POST | `/api/like` | Records a like action; checks for mutual match |
| POST | `/api/match/:matchId/milestone` | Records on-chain milestone event |
| DELETE | `/api/account` | Burns NFT + deletes all user data (GDPR) |

All endpoints require JWT auth (issued by Supabase Auth / Privy).

---

## 9. Development Phases

### Phase 1 — MVP (Weeks 1–8)
- Auth + embedded wallet (Privy)
- Self-description + dream description input with Claude enrichment
- AI avatar generation (single variant, no picker)
- Basic ERC-721 soulbound contract on Polygon Mumbai
- IPFS upload via Pinata
- Match feed (cosine similarity, basic)
- Like / pass actions
- Mutual match detection + chat unlock (Stream Chat)

### Phase 2 — Polish (Weeks 9–14)
- 4-variant avatar picker + regeneration credits
- Tag confirm/edit UI
- Tier system (Seed → Spark → Flame)
- On-chain milestone logging
- Animated mint flow + NFT reveal
- Push notifications (match, message)
- Report / block system

### Phase 3 — Growth (Weeks 15–20)
- Super Like with daily limit
- AI icebreaker message suggestions in chat
- NFT evolution animations (tier upgrades)
- ZK age verification (Reclaim Protocol)
- Mainnet deployment (Polygon)
- Gas sponsorship (Gelato Relay)
- Profile sharing (deep link to NFT on OpenSea-compatible explorer)

---

## 10. Open Questions for Team

1. **Image model choice:** Replicate (SDXL) gives more stylistic control; DALL·E 3 is simpler to integrate but more expensive at scale. Decide based on cost projections at 10k users.
2. **Avatar style lock-in:** Should all avatars share one unified art style (consistent feed aesthetic), or allow users to pick a style (anime, painterly, realistic-ish)? Style picker adds complexity but increases personalisation.
3. **Metadata mutability:** Token URI is updatable (for tier upgrades, description changes). Consider whether to freeze after minting for true on-chain permanence — trade-off between UX flexibility and Web3 purity.
4. **Matching threshold:** What minimum `finalScore` appears in feed? Too low → irrelevant matches; too high → empty feeds for niche users. Needs tuning with real data.
5. **Monetisation:** Regeneration credits (freemium), Super Likes, tier acceleration, or token-gated features? Decide before Phase 2 to avoid retrofitting payment flows.
