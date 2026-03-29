import { ImageAnnotatorClient, protos } from "@google-cloud/vision";
import {
  MPL_TOKEN_METADATA_PROGRAM_ID,
  getCreateMasterEditionV3InstructionDataSerializer,
  getCreateMetadataAccountV3InstructionDataSerializer
} from "@metaplex-foundation/mpl-token-metadata";
import {
  MINT_SIZE,
  TOKEN_PROGRAM_ID,
  createAssociatedTokenAccountInstruction,
  createInitializeMintInstruction,
  createMintToInstruction,
  getAssociatedTokenAddress
} from "@solana/spl-token";
import {
  Connection,
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
  Transaction,
  TransactionInstruction,
  SYSVAR_RENT_PUBKEY
} from "@solana/web3.js";
import * as admin from "firebase-admin";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { PinataSDK } from "pinata";
import {
  buildSemanticVector,
  calculateCompatibility as calculateCompatibilityScore
} from "./matching.js";
import { createHash, randomInt } from "crypto";
import { ed25519 } from "@noble/curves/ed25519";

admin.initializeApp({
  serviceAccountId: "soulmint-cfeb7@appspot.gserviceaccount.com"
});

const stabilityApiKey = defineSecret("STABILITY_API_KEY");
const pinataJwt = defineSecret("PINATA_JWT");
const deepseekApiKey = defineSecret("DEEPSEEK_API_KEY");

type GenerateAvatarVariantsRequest = {
  selfDescription?: string;
  selfTags?: string[];
  selectedGender?: string;
};

type MintSoulboundProfileRequest = {
  name?: string;
  bio?: string;
  dreamDescription?: string;
  selfTags?: string[];
  dreamTags?: string[];
  avatarPreviewUrl?: string;
  avatarIpfsUrl?: string;
  ownerWalletAddress?: string;
};

type SendChatMessageRequest = {
  chatId?: string;
  message?: string;
  senderName?: string;
};

type ReportAndBlockUserRequest = {
  targetId?: string;
  reason?: string;
};

type RecordFeedActionRequest = {
  targetId?: string;
  action?: string;
};

type DeleteChatThreadRequest = {
  chatId?: string;
};

type CheckUsagePaymentRequest = {
  reference?: string;
};

type WalletAuthChallengeRequest = {
  walletAddress?: string;
};

type WalletAuthChallengeResponse = {
  walletAddress: string;
  nonce: string;
  message: string;
};

type CompleteWalletSignInRequest = {
  walletAddress?: string;
  nonce?: string;
  message?: string;
  signatureBase64?: string;
};

type CompleteWalletSignInResponse = {
  customToken: string;
  uid: string;
  walletAddress: string;
};

type DeleteMyAccountResponse = {
  deleted: boolean;
};

type RefreshMatchFeedResponse = {
  profiles: MatchFeedProfileResponse[];
};

type SendChatMessageResponse = {
  chatId: string;
  reply: string;
};

type ReportAndBlockUserResponse = {
  targetId: string;
  blocked: boolean;
};

type RecordFeedActionResponse = {
  targetId: string;
  action: string;
  saved: boolean;
};

type DeleteChatThreadResponse = {
  chatId: string;
  deleted: boolean;
  removedMessages: number;
};

type UsagePaywallResponse = {
  trigger: "pass_limit" | "message_limit";
  reference: string;
  recipientWalletAddress: string;
  solAmount: number;
  skrAmount: number;
  skrTokenMint: string;
  solanaPaySolUrl: string;
  solanaPaySkrUrl: string;
};

type CheckUsagePaymentResponse = {
  reference: string;
  status: "pending" | "confirmed";
  unlocked: boolean;
  usageUnlockCount: number;
};

type MatchFeedProfileResponse = {
  id: string;
  name: string;
  tier: string;
  tokenId: number;
  selfTags: string[];
  dreamTags: string[];
  compatibility: number;
  avatarPreviewUrl: string;
  avatarIpfsUrl: string;
  selfVector: number[];
  compatibilityVector: number[];
  aiPresentation?: string;
  aiOpenerTone?: string;
};

type CompanionPersona = {
  id: string;
  name: string;
  presentation: "female" | "male";
  selfTags: string[];
  dreamTags: string[];
  openerTone: string;
};

type UserChatContext = {
  bio: string;
  dreamDescription: string;
  selfTags: string[];
  dreamTags: string[];
};

type VariantResponse = {
  previewUrl: string;
  ipfsUrl: string;
};

type GenerateAvatarVariantsResponse = {
  variants: VariantResponse[];
};

type MintSoulboundProfileResponse = {
  tokenId: number;
  tier: string;
  txHash: string;
  mintMode?: "solana_prepared";
  walletAddress?: string;
  metadataIpfsUrl?: string;
  externalMintUrl?: string;
  externalMint?: SolanaMintHandoffResponse;
};

type SolanaMintHandoffResponse = {
  platform: string;
  action: "prepare_solana_mint";
  network: string;
  ownerWalletAddress: string;
  metadataIpfsUrl: string;
  metadataGatewayUrl: string;
  preparedMint: PreparedSolanaMintResponse;
};

type PreparedSolanaMintResponse = {
  transactionBase64: string;
  mintAddress: string;
  ownerWalletAddress: string;
  minContextSlot?: number;
  rpcUrl: string;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Access-Control-Allow-Methods": "POST, OPTIONS"
};

const PINATA_GATEWAY_BASE_URL = "https://gateway.pinata.cloud/ipfs/";
const USAGE_PAYMENT_RECIPIENT = "CmxjnzCpvbQqtDVBp8bAg8QDkdfM7iZdexdnk1Ez26S8";
const USAGE_PAYMENT_SKR_MINT = "SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3";
const USAGE_PAYMENT_SOL_AMOUNT = 0.05;
const USAGE_PAYMENT_SKR_AMOUNT = 200;
const FREE_PASS_LIMIT = 3;
const FREE_MESSAGE_LIMIT = 50;
const USAGE_PASS_INCREMENT = 3;
const USAGE_MESSAGE_INCREMENT = 50;
const unsafePromptTerms = [
  "nude",
  "naked",
  "nsfw",
  "sex",
  "sexual",
  "fetish",
  "porn",
  "explicit",
  "gore",
  "blood splatter",
  "violent injury",
  "self harm",
  "suicide",
  "underage",
  "child"
];
const weightedPortraitGenders = [
  { label: "female", weight: 80 },
  { label: "male", weight: 20 }
] as const;
const femaleHairStyles = [
  "soft layered long hair",
  "sleek shoulder-length bob",
  "loose wavy medium-length hair",
  "curly volume with natural movement",
  "straight long hair with subtle shine",
  "messy textured bob",
  "elegant tied-back ponytail",
  "short pixie cut with texture"
];
const maleHairStyles = [
  "soft textured crop",
  "slightly messy medium-length hair",
  "clean side-part with movement",
  "wavy longer top with tapered sides",
  "curly short cut",
  "undone curtain hairstyle",
  "close fade with textured top",
  "medium shag with soft layers",
  "clean buzz cut",
  "short quiff with low fade"
];
const faceShapes = [
  "oval face shape",
  "heart-shaped face",
  "soft round face",
  "defined square face",
  "diamond face shape",
  "long face shape with balanced proportions"
];
const expressionStyles = [
  "quietly confident expression",
  "gentle half-smile",
  "intense thoughtful gaze",
  "warm open expression",
  "playful knowing smile",
  "calm emotionally readable expression"
];
const stylingAccents = [
  "minimal jewelry and tactile fabrics",
  "clean grooming with subtle statement details",
  "cinematic color contrast and soft shadowing",
  "elevated casual wardrobe with personality",
  "tasteful editorial styling with human warmth",
  "distinctive silhouette and memorable presence"
];
const maleFacialStructureNotes = [
  "defined masculine jawline and adult male bone structure",
  "subtle brow ridge and masculine cheek structure",
  "lean masculine facial proportions with adult male features",
  "clearly masculine face shape with restrained grooming"
];
const femaleFacialStructureNotes = [
  "soft feminine facial proportions with elegant contouring",
  "graceful feminine cheek structure and delicate features",
  "feminine face shape with soft balanced symmetry",
  "adult feminine facial structure with refined softness"
];
const maleWardrobeNotes = [
  "masculine styling with understated layers",
  "clean masculine wardrobe details",
  "adult menswear-inspired silhouette",
  "minimal masculine fashion accents"
];
const femaleWardrobeNotes = [
  "feminine styling with elegant texture",
  "soft feminine wardrobe cues",
  "adult womenswear-inspired silhouette",
  "refined feminine fashion accents"
];
const youthfulAgeNotes = [
  "young adult in their mid-20s",
  "young adult around 24 to 30 years old",
  "visibly youthful adult with fresh skin and modern styling",
  "mid-20s to early-30s adult with youthful energy"
];
const visionClient = new ImageAnnotatorClient();
const TOKEN_METADATA_PROGRAM_ID = new PublicKey(MPL_TOKEN_METADATA_PROGRAM_ID);
const DEFAULT_SOLANA_RPC_URL = "https://api.mainnet-beta.solana.com";
const femaleCompanions: CompanionPersona[] = [
  {
    id: "match-sera",
    name: "Sera",
    presentation: "female",
    selfTags: ["warm", "romantic", "curious", "bookish"],
    dreamTags: ["gentle", "creative", "grounded", "communicative"],
    openerTone: "soft, emotionally present, quietly flirtatious"
  },
  {
    id: "match-lina",
    name: "Lina",
    presentation: "female",
    selfTags: ["playful", "witty", "fashion-forward", "cinematic"],
    dreamTags: ["confident", "romantic", "curious", "empathetic"],
    openerTone: "charming, lively, intimate, slightly teasing"
  },
  {
    id: "match-iris",
    name: "Iris",
    presentation: "female",
    selfTags: ["grounded", "introspective", "artistic", "soft-spoken"],
    dreamTags: ["bookish", "gentle", "warm", "honest"],
    openerTone: "calm, thoughtful, emotionally intelligent"
  }
];
const maleCompanions: CompanionPersona[] = [
  {
    id: "match-kael",
    name: "Kael",
    presentation: "male",
    selfTags: ["confident", "gentle", "adventurous", "communicative"],
    dreamTags: ["warm", "creative", "grounded", "romantic"],
    openerTone: "steady, charming, emotionally fluent"
  },
  {
    id: "match-luca",
    name: "Luca",
    presentation: "male",
    selfTags: ["witty", "artistic", "cinematic", "playful"],
    dreamTags: ["curious", "soft-spoken", "romantic", "fashion-forward"],
    openerTone: "easygoing, magnetic, lightly playful"
  },
  {
    id: "match-evan",
    name: "Evan",
    presentation: "male",
    selfTags: ["bookish", "grounded", "gentle", "introspective"],
    dreamTags: ["warm", "bookish", "creative", "communicative"],
    openerTone: "quiet, attentive, emotionally grounded"
  }
];
const femaleNames = ["Sera", "Lina", "Iris", "Mira", "Elia", "Nora", "Celine", "Ayla", "Vera", "Noemi"];
const maleNames = ["Kael", "Luca", "Evan", "Milo", "Theo", "Jules", "Arin", "Rowan", "Leon", "Ezra"];
const traitPool = [
  "warm", "romantic", "curious", "bookish", "playful", "witty",
  "fashion-forward", "cinematic", "grounded", "introspective",
  "artistic", "soft-spoken", "confident", "gentle", "adventurous",
  "communicative", "creative", "empathetic", "honest", "magnetic"
];
const openerTonePool = [
  "soft, emotionally present, quietly flirtatious",
  "charming, lively, intimate, slightly teasing",
  "calm, thoughtful, emotionally intelligent",
  "steady, magnetic, quietly romantic",
  "warm, playful, confident without trying too hard"
];

export const generateAvatarVariants = onRequest(
  {
    region: "us-central1",
    secrets: [stabilityApiKey, pinataJwt],
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
        response.set(corsHeaders).status(204).send("");
        return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as GenerateAvatarVariantsRequest;
      const selfDescription = normalizeSelfDescription(body.selfDescription);
      const selfTags = normalizeOptionalTags(body.selfTags, 8);
      moderatePrompt(selfDescription);
      const selectedGender = normalizeSelectedGender(body.selectedGender);
      const prompt = buildAvatarPrompt(selfDescription, selfTags, selectedGender);

      const variants = await generateVariants({
        uid,
        prompt,
        selectedGender,
        stabilityApiKey: stabilityApiKey.value(),
        pinataJwt: pinataJwt.value()
      });

      const payload: GenerateAvatarVariantsResponse = { variants };
      response.status(200).json(payload);
    } catch (error) {
      logger.error("generateAvatarVariants failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const requestWalletSignInChallenge = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 60,
    memory: "256MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const body = (request.body ?? {}) as WalletAuthChallengeRequest;
      const payload = await createWalletAuthChallenge(body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("requestWalletSignInChallenge failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const completeWalletSignIn = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 60,
    memory: "256MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const body = (request.body ?? {}) as CompleteWalletSignInRequest;
      const payload = await completeWalletAuth(body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("completeWalletSignIn failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const mintSoulboundProfile = onRequest(
  {
    region: "us-central1",
    secrets: [pinataJwt],
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as MintSoulboundProfileRequest;
      const minted = await mintProfile(uid, body);
      const payload: MintSoulboundProfileResponse = minted;
      response.status(200).json(payload);
    } catch (error) {
      logger.error("mintSoulboundProfile failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const refreshMatchFeed = onRequest(
  {
    region: "us-central1",
    secrets: [stabilityApiKey, pinataJwt],
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const profiles = await regenerateFeed(uid);
      const payload: RefreshMatchFeedResponse = { profiles };
      response.status(200).json(payload);
    } catch (error) {
      logger.error("refreshMatchFeed failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const sendChatMessage = onRequest(
  {
    region: "us-central1",
    secrets: [deepseekApiKey],
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as SendChatMessageRequest;
      const payload = await sendChatReply(uid, body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("sendChatMessage failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message, ...(error.details ?? {}) });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const checkUsagePayment = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 60,
    memory: "256MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as CheckUsagePaymentRequest;
      const payload = await checkUsagePaymentStatus(uid, body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("checkUsagePayment failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message, ...(error.details ?? {}) });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const reportAndBlockUser = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as ReportAndBlockUserRequest;
      const payload = await reportAndBlock(uid, body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("reportAndBlockUser failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const deleteMyAccount = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const payload = await deleteAccount(uid);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("deleteMyAccount failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const recordFeedAction = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as RecordFeedActionRequest;
      const payload = await recordFeedActionForUser(uid, body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("recordFeedAction failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message, ...(error.details ?? {}) });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

export const deleteChatThread = onRequest(
  {
    region: "us-central1",
    timeoutSeconds: 120,
    memory: "512MiB"
  },
  async (request, response) => {
    if (request.method === "OPTIONS") {
      response.set(corsHeaders).status(204).send("");
      return;
    }

    response.set(corsHeaders);

    if (request.method !== "POST") {
      response.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const uid = await verifyFirebaseUser(request.headers.authorization);
      const body = (request.body ?? {}) as DeleteChatThreadRequest;
      const payload = await deleteChatForUser(uid, body);
      response.status(200).json(payload);
    } catch (error) {
      logger.error("deleteChatThread failed", error);
      if (error instanceof HttpError) {
        response.status(error.statusCode).json({ error: error.message });
        return;
      }
      response.status(500).json({ error: "Internal server error" });
    }
  }
);

class HttpError extends Error {
  constructor(
    public readonly statusCode: number,
    message: string,
    public readonly details?: Record<string, unknown>
  ) {
    super(message);
  }
}

async function verifyFirebaseUser(authorizationHeader?: string): Promise<string> {
  if (!authorizationHeader?.startsWith("Bearer ")) {
    throw new HttpError(401, "Missing Firebase Auth bearer token");
  }

  const idToken = authorizationHeader.slice("Bearer ".length).trim();
  if (!idToken) {
    throw new HttpError(401, "Empty Firebase Auth bearer token");
  }

  const decoded = await admin.auth().verifyIdToken(idToken);
  return decoded.uid;
}

function normalizeSelfDescription(raw?: string): string {
  const value = raw?.trim() ?? "";
  if (value.length < 30) {
    throw new HttpError(400, "selfDescription must be at least 30 characters");
  }
  if (value.length > 600) {
    throw new HttpError(400, "selfDescription must be at most 600 characters");
  }
  return value;
}

function buildAvatarPrompt(
  selfDescription: string,
  selfTags: string[],
  selectedGender: "female" | "male" | null
): string {
  const normalizedDescription = selfDescription.replace(/\s+/g, " ").trim();
  const portraitGender = selectedGender ?? pickWeightedPortraitGender();
  const hairstyle = randomChoice(
    portraitGender === "female" ? femaleHairStyles : maleHairStyles
  );
  const faceShape = randomChoice(faceShapes);
  const expression = randomChoice(expressionStyles);
  const stylingAccent = randomChoice(stylingAccents);
  const facialStructure = randomChoice(
    portraitGender === "female" ? femaleFacialStructureNotes : maleFacialStructureNotes
  );
  const wardrobeNote = randomChoice(
    portraitGender === "female" ? femaleWardrobeNotes : maleWardrobeNotes
  );
  const ageNote = randomChoice(youthfulAgeNotes);
  const traitLine = selfTags.length > 0
    ? `Core traits and energy to express visually: ${selfTags.join(", ")}.`
    : "Core traits and energy to express visually: warm, distinct, emotionally readable, human.";
  const genderTemplate = portraitGender === "male"
    ? [
        "Generate a stylised portrait avatar of one young adult man.",
        "The result must read immediately and unambiguously as male.",
        "Prioritize youthful male identity cues in the face, jawline, grooming, hairline, neck, shoulders, and silhouette.",
        "Make him clearly look like a handsome modern man in his 20s to early 30s.",
        "Do not make the subject androgynous or feminine."
      ]
    : [
        "Generate a stylised portrait avatar of one young adult woman.",
        "The result must read immediately and unambiguously as female.",
        "Prioritize feminine identity cues in the face, cheek structure, grooming, hair styling, neck, shoulders, and silhouette.",
        "Make her clearly look like an attractive modern woman in her 20s to early 30s.",
        "Do not make the subject androgynous or masculine."
      ];
  return [
    ...genderTemplate,
    "Generate a stylised portrait avatar of one person.",
    "Use the user's self-description and selected traits to create a distinct identity, not a generic face.",
    "Semi-illustrated digital portrait, cinematic but tasteful, warm lighting, soft focus background, centered composition, chest-up framing.",
    "Avoid duplicate-looking outputs, avoid generic stock-photo faces, avoid text, avoid explicit content.",
    "Translate personality cues into styling, expression, posture, grooming, wardrobe, color mood, and overall presence.",
    `Visual gender presentation must clearly read as ${portraitGender}.`,
    `Facial structure, hair, grooming, and overall silhouette should consistently reinforce a ${portraitGender} presentation.`,
    `Use ${ageNote}, ${facialStructure}, ${wardrobeNote}, ${faceShape}, ${hairstyle}, ${expression}, and ${stylingAccent}.`,
    "Keep the identity coherent but allow noticeable variety in hairstyle, face shape, and styling between generations.",
    traitLine,
    "User self-description:",
    normalizedDescription
  ].join(" ");
}

function normalizeSelectedGender(raw: string | undefined): "female" | "male" | null {
  const value = raw?.trim().toLowerCase();
  return value === "female" || value === "male" ? value : null;
}

function pickWeightedPortraitGender(): "female" | "male" {
  const roll = randomInt(1, 101);
  return roll <= weightedPortraitGenders[0].weight ? "female" : "male";
}

function randomChoice<T>(items: readonly T[]): T {
  return items[randomInt(0, items.length)];
}

function normalizeOptionalTags(raw: string[] | undefined, maxCount: number): string[] {
  return (raw ?? [])
    .map((tag) => tag.trim().toLowerCase())
    .filter(Boolean)
    .slice(0, maxCount);
}

function moderatePrompt(selfDescription: string): void {
  const normalized = selfDescription.toLowerCase();
  const blockedTerm = unsafePromptTerms.find((term) => normalized.includes(term));
  if (blockedTerm) {
    throw new HttpError(400, "Prompt contains disallowed content for avatar generation");
  }
}

async function generateVariants(input: {
  uid: string;
  prompt: string;
  selectedGender: "female" | "male" | null;
  stabilityApiKey: string | undefined;
  pinataJwt: string | undefined;
}): Promise<VariantResponse[]> {
  const canRunRealPipeline =
    Boolean(input.stabilityApiKey) &&
    Boolean(input.pinataJwt);

  if (!canRunRealPipeline) {
    logger.info("Using fallback avatar variants because one or more secrets are missing.", {
      uid: input.uid,
      hasStabilityApiKey: Boolean(input.stabilityApiKey),
      hasPinataJwt: Boolean(input.pinataJwt)
    });
    return createFallbackVariants(input.uid, input.prompt);
  }

  return generateRealVariants({
    uid: input.uid,
    prompt: input.prompt,
    selectedGender: input.selectedGender,
    stabilityApiKey: input.stabilityApiKey!,
    pinataJwt: input.pinataJwt!
  });
}

function createFallbackVariants(uid: string, prompt: string): VariantResponse[] {
  const seed = randomInt(0, 1_000_000_000).toString(16);
  return [{
    previewUrl: "https://placehold.co/1024x1024/3F285C/E05C8A?text=SoulMint",
    ipfsUrl: `ipfs://soulmint-fallback/${uid}/${seed}/avatar`
  }];
}

function hashString(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

async function generateRealVariants(input: {
  uid: string;
  prompt: string;
  selectedGender: "female" | "male" | null;
  stabilityApiKey: string;
  pinataJwt: string;
}): Promise<VariantResponse[]> {
  const imageBytes = await createStabilityImage(input);
  await ensureImageIsSafe(imageBytes);
  const pinata = new PinataSDK({ pinataJwt: input.pinataJwt });
  const file = new File(
    [imageBytes],
    `soulmint-${input.uid}.png`,
    { type: "image/png" }
  );
  const result = await pinata.upload.public.file(file).name(`soulmint-${input.uid}`);
  const ipfsUrl = `ipfs://${result.cid}`;
  return [{
    previewUrl: toGatewayUrl(ipfsUrl),
    ipfsUrl
  }];
}

async function createStabilityImage(input: {
  uid: string;
  prompt: string;
  selectedGender: "female" | "male" | null;
  stabilityApiKey: string;
}): Promise<ArrayBuffer> {
  const attempts = input.selectedGender ? 3 : 1;
  let lastErrorBody = "";

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const generationSeed = randomInt(1, 2_147_483_647);
    const formData = new FormData();
    formData.append("prompt", buildAttemptPrompt(input.prompt, input.selectedGender, attempt));
    formData.append("negative_prompt", buildNegativePrompt(input.selectedGender, attempt));
    formData.append("style_preset", "digital-art");
    formData.append("output_format", "png");
    formData.append("aspect_ratio", "1:1");
    formData.append("seed", String(generationSeed));

    const response = await fetch("https://api.stability.ai/v2beta/stable-image/generate/core", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${input.stabilityApiKey}`,
        Accept: "image/*"
      },
      body: formData
    });

    if (response.ok) {
      logger.info("Stability portrait generation succeeded", {
        uid: input.uid,
        selectedGender: input.selectedGender,
        attempt: attempt + 1
      });
      return response.arrayBuffer();
    }

    lastErrorBody = await response.text();
    logger.error("Stability request failed", {
      status: response.status,
      selectedGender: input.selectedGender,
      attempt: attempt + 1,
      errorBody: lastErrorBody
    });
  }

  throw new HttpError(502, "Stability image generation failed");
}

function buildAttemptPrompt(
  basePrompt: string,
  selectedGender: "female" | "male" | null,
  attempt: number
): string {
  if (!selectedGender) return basePrompt;
  if (selectedGender === "male") {
    const maleEscalations = [
      "This must be a young adult male portrait with unmistakably masculine presentation.",
      "Make the subject clearly male: handsome young man, masculine jawline, masculine grooming, masculine silhouette, no feminine styling, no older age.",
      "Absolutely no feminine presentation. The subject must read as a handsome young adult man at first glance."
    ];
    return `${basePrompt} ${maleEscalations[Math.min(attempt, maleEscalations.length - 1)]}`;
  }

  const femaleEscalations = [
    "This must be a young adult female portrait with unmistakably feminine presentation.",
    "Make the subject clearly female: attractive young woman, feminine facial proportions, feminine grooming, feminine silhouette, no masculine styling, no older age.",
    "Absolutely no masculine presentation. The subject must read as an elegant young adult woman at first glance."
  ];
  return `${basePrompt} ${femaleEscalations[Math.min(attempt, femaleEscalations.length - 1)]}`;
}

function buildNegativePrompt(selectedGender: "female" | "male" | null, attempt: number): string {
  const base = [
    "nsfw",
    "nudity",
    "explicit",
    "deformed hands",
    "blurry",
    "duplicate",
    "extra fingers",
    "low detail",
    "generic face"
  ];

  if (selectedGender === "male") {
    const maleBlocks = [
      ...base,
      "female",
      "woman",
      "girl",
      "feminine face",
      "soft feminine makeup",
      "lipstick",
      "heavy eyeliner",
      "long feminine lashes",
      "clearly feminine presentation",
      "female silhouette",
      "womanly styling",
      "androgynous feminine face",
      "older face",
      "middle-aged face",
      "elderly face",
      "wrinkles"
    ];
    if (attempt >= 1) {
      maleBlocks.push("soft feminine features", "delicate feminine jawline", "feminine hairstyle");
    }
    if (attempt >= 2) {
      maleBlocks.push("makeup", "glam makeup", "feminine accessories", "female portrait");
    }
    return maleBlocks.join(", ");
  }

  if (selectedGender === "female") {
    const femaleBlocks = [
      ...base,
      "male",
      "man",
      "boy",
      "beard",
      "mustache",
      "stubble",
      "masculine jawline",
      "bulky masculine silhouette",
      "clearly masculine presentation",
      "androgynous masculine face",
      "older face",
      "middle-aged face",
      "elderly face",
      "wrinkles"
    ];
    if (attempt >= 1) {
      femaleBlocks.push("hard masculine features", "rough masculine grooming", "male hairstyle");
    }
    if (attempt >= 2) {
      femaleBlocks.push("bearded portrait", "male portrait", "masculine accessories");
    }
    return femaleBlocks.join(", ");
  }

  return base.join(", ");
}

function toGatewayUrl(ipfsUrl: string): string {
  return `${PINATA_GATEWAY_BASE_URL}${ipfsUrl.replace("ipfs://", "")}`;
}

async function buildGeneratedCompanionCandidate(
  uid: string,
  currentProfile: MatchFeedProfileResponse,
  selectedGender: "female" | "male" | null,
  stabilityApiKeyValue: string | undefined,
  pinataJwtValue: string | undefined
): Promise<MatchFeedProfileResponse> {
  const companion = createGeneratedCompanionPersona(selectedGender);
  const portrait = await ensureCompanionPortrait({
    uid,
    companion,
    currentProfile,
    stabilityApiKeyValue,
    pinataJwtValue,
    cacheByCompanionId: false
  });
  const candidate: MatchFeedProfileResponse = {
    id: companion.id,
    name: companion.name,
    tier: "Curated Match",
    tokenId: 0,
    selfTags: companion.selfTags,
    dreamTags: companion.dreamTags,
    compatibility: 0,
    avatarPreviewUrl: portrait.previewUrl,
    avatarIpfsUrl: portrait.ipfsUrl,
    selfVector: buildSemanticVector(companion.selfTags.join(" "), companion.selfTags, hashString),
    compatibilityVector: buildSemanticVector(companion.dreamTags.join(" "), companion.dreamTags, hashString),
    aiPresentation: companion.presentation,
    aiOpenerTone: companion.openerTone
  };
  return {
    ...candidate,
    compatibility: Math.max(82, calculateCompatibilityScore(currentProfile, candidate))
  };
}

function orderedCompanionPool(
  uid: string,
  selectedGender: "female" | "male" | null
): CompanionPersona[] {
  const targetGender = selectedGender === "male" ? "female" : "male";
  const pool = targetGender === "female" ? femaleCompanions : maleCompanions;
  const start = hashString(uid) % pool.length;
  return pool.map((_, index) => pool[(start + index) % pool.length]);
}

function createGeneratedCompanionPersona(
  selectedGender: "female" | "male" | null
): CompanionPersona {
  const targetGender: "female" | "male" = selectedGender === "male" ? "female" : "male";
  const namePool = targetGender === "female" ? femaleNames : maleNames;
  const shuffled = [...traitPool].sort(() => randomInt(-10, 11));
  return {
    id: `ai:${Date.now().toString(36)}-${randomInt(1000, 999999).toString(36)}`,
    name: randomChoice(namePool),
    presentation: targetGender,
    selfTags: shuffled.slice(0, 4),
    dreamTags: shuffled.slice(4, 8),
    openerTone: randomChoice(openerTonePool)
  };
}

function buildCompanionPortraitDataUrl(companion: CompanionPersona): string {
  const accent = companion.presentation === "female" ? "E4A3C4" : "8BB6FF";
  const shadow = companion.presentation === "female" ? "5E2B59" : "233B67";
  const initials = companion.name.slice(0, 1).toUpperCase();
  return `https://placehold.co/1024x1024/${shadow}/${accent}?text=${encodeURIComponent(initials)}`;
}

async function ensureCompanionPortrait(input: {
  uid: string;
  companion: CompanionPersona;
  currentProfile: MatchFeedProfileResponse;
  stabilityApiKeyValue: string | undefined;
  pinataJwtValue: string | undefined;
  cacheByCompanionId?: boolean;
}): Promise<VariantResponse> {
  const prompt = buildCompanionAvatarPrompt(input.companion, input.currentProfile);
  const promptHash = createHash("sha256").update(prompt).digest("hex");
  const cacheRef = admin.firestore()
    .collection("users")
    .doc(input.uid)
    .collection("companionPortraits")
    .doc(input.companion.id);
  if (input.cacheByCompanionId == false) {
    if (!input.stabilityApiKeyValue || !input.pinataJwtValue) {
      return {
        previewUrl: buildCompanionPortraitDataUrl(input.companion),
        ipfsUrl: `ipfs://soulmint-companion/${input.companion.id}`
      };
    }
    const [generated] = await generateRealVariants({
      uid: `${input.uid}-${input.companion.id}`,
      prompt,
      selectedGender: input.companion.presentation,
      stabilityApiKey: input.stabilityApiKeyValue,
      pinataJwt: input.pinataJwtValue
    });
    return generated;
  }
  const cacheSnapshot = await cacheRef.get();
  const cachedPreviewUrl = String(cacheSnapshot.get("previewUrl") ?? "").trim();
  const cachedIpfsUrl = String(cacheSnapshot.get("ipfsUrl") ?? "").trim();
  const cachedPromptHash = String(cacheSnapshot.get("promptHash") ?? "").trim();

  if (
    cacheSnapshot.exists &&
    cachedPreviewUrl &&
    cachedIpfsUrl &&
    cachedPromptHash === promptHash
  ) {
    return {
      previewUrl: cachedPreviewUrl,
      ipfsUrl: cachedIpfsUrl
    };
  }

  if (!input.stabilityApiKeyValue || !input.pinataJwtValue) {
    return {
      previewUrl: buildCompanionPortraitDataUrl(input.companion),
      ipfsUrl: `ipfs://soulmint-companion/${input.companion.id}`
    };
  }

  const [generated] = await generateRealVariants({
    uid: `${input.uid}-${input.companion.id}`,
    prompt,
    selectedGender: input.companion.presentation,
    stabilityApiKey: input.stabilityApiKeyValue,
    pinataJwt: input.pinataJwtValue
  });

  await cacheRef.set(
    {
      previewUrl: generated.previewUrl,
      ipfsUrl: generated.ipfsUrl,
      promptHash,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  return generated;
}

function buildCompanionAvatarPrompt(
  companion: CompanionPersona,
  currentProfile: MatchFeedProfileResponse
): string {
  const targetRead = companion.presentation === "female" ? "young adult woman" : "young adult man";
  const userTraits = currentProfile.selfTags.join(", ");
  const desiredTraits = currentProfile.dreamTags.join(", ");
  return [
    `Generate a premium dating profile portrait of one ${targetRead}.`,
    `This portrait is for ${companion.name}, whose personality feels ${companion.openerTone}.`,
    `Their core traits: ${companion.selfTags.join(", ")}.`,
    `They are especially drawn to: ${companion.dreamTags.join(", ")}.`,
    userTraits ? `The user they are being matched with feels like: ${userTraits}.` : "",
    desiredTraits ? `The user wants someone like: ${desiredTraits}.` : "",
    "Create a visually striking, romantic, highly attractive portrait suitable for a modern dating app.",
    "Chest-up framing, expressive eyes, cinematic lighting, stylish grooming, clear facial structure, natural skin, emotionally warm presence.",
    "Keep the face distinct and believable, not generic, not cartoonish, not childlike, not elderly.",
    "Vary the hairstyle, face shape, and styling details naturally.",
    companion.presentation === "female"
      ? "Must read clearly as a woman at first glance with elegant feminine styling."
      : "Must read clearly as a man at first glance with handsome masculine styling."
  ].filter(Boolean).join(" ");
}

function currentUserSnapshotToMatchProfile(
  id: string,
  snapshot: admin.firestore.DocumentSnapshot
): MatchFeedProfileResponse {
  const selfTags = toStringList(snapshot.get("selfTags"));
  const dreamTags = toStringList(snapshot.get("dreamTags"));
  const bio = String(snapshot.get("bio") ?? "").trim();
  const dreamDescription = String(snapshot.get("dreamDescription") ?? "").trim();

  if (selfTags.length === 0 || dreamTags.length === 0 || !bio || !dreamDescription) {
    throw new HttpError(400, "User profile must have self tags, dream tags, and descriptions before refreshing feed");
  }

  const avatarPreviewUrl = String(snapshot.get("avatarPreviewUrl") ?? "").trim() || "https://placehold.co/1024x1024/15121F/E4A3C4?text=SoulMint";
  const avatarIpfsUrl = String(snapshot.get("avatarIpfsUrl") ?? "").trim() || `ipfs://soulmint-user/${id}/avatar`;
  const tokenIdValue = snapshot.get("tokenId");
  const tokenId = typeof tokenIdValue === "number" ? tokenIdValue : 0;
  const selfVector = toNumberList(snapshot.get("selfVector"));
  const compatibilityVector = toNumberList(snapshot.get("compatibilityVector"));

  return {
    id,
    name: String(snapshot.get("name") ?? "Soul"),
    tier: String(snapshot.get("tier") ?? "Seed"),
    tokenId,
    selfTags,
    dreamTags,
    compatibility: 0,
    avatarPreviewUrl,
    avatarIpfsUrl,
    selfVector: selfVector.length > 0
      ? selfVector
      : buildSemanticVector(`${String(snapshot.get("name") ?? "Soul")} ${bio}`, selfTags, hashString),
    compatibilityVector: compatibilityVector.length > 0
      ? compatibilityVector
      : buildSemanticVector(dreamDescription, dreamTags, hashString)
  };
}

async function regenerateFeed(uid: string): Promise<MatchFeedProfileResponse[]> {
  const firestore = admin.firestore();
  const userRef = firestore.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  if (!userSnapshot.exists) {
    throw new HttpError(404, "User profile not found");
  }

  const selfTags = toStringList(userSnapshot.get("selfTags"));
  const dreamTags = toStringList(userSnapshot.get("dreamTags"));
  if (selfTags.length === 0 || dreamTags.length === 0) {
    throw new HttpError(400, "User profile must have selfTags and dreamTags before refreshing feed");
  }

  const blockedSnapshots = await userRef.collection("blockedUsers").get();
  const hiddenSnapshots = await userRef.collection("hiddenFeedTargets").get();
  const blockedIds = new Set(blockedSnapshots.docs.map((doc) => doc.id));
  const hiddenIds = new Set(hiddenSnapshots.docs.map((doc) => doc.id));
  const currentProfile = currentUserSnapshotToMatchProfile(uid, userSnapshot);
  const selectedGender = String(userSnapshot.get("gender") ?? "").trim().toLowerCase();
  const generatedCompanion = await buildGeneratedCompanionCandidate(
    uid,
    currentProfile,
    selectedGender === "male" || selectedGender === "female" ? selectedGender : null,
    stabilityApiKey.value(),
    pinataJwt.value()
  );
  const scored = [generatedCompanion]
    .filter((candidate) => !blockedIds.has(candidate.id) && !hiddenIds.has(candidate.id));

  const batch = firestore.batch();
  const feedCollection = userRef.collection("feed");
  const existing = await feedCollection.get();
  existing.docs.forEach((doc) => batch.delete(doc.ref));
  scored.forEach((profile) => {
    batch.set(feedCollection.doc(profile.id), {
      name: profile.name,
      tier: profile.tier,
      tokenId: profile.tokenId,
      selfTags: profile.selfTags,
      dreamTags: profile.dreamTags,
      compatibility: profile.compatibility,
      avatarTitle: `${profile.name} Avatar`,
      avatarPreviewUrl: profile.avatarPreviewUrl,
      avatarIpfsUrl: profile.avatarIpfsUrl,
      aiPresentation: profile.aiPresentation,
      aiOpenerTone: profile.aiOpenerTone,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  });
  batch.set(userRef, { updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  await batch.commit();

  return scored;
}

async function getUsageMetrics(uid: string): Promise<{
  passCount: number;
  messageCount: number;
  usageUnlockCount: number;
}> {
  const snapshot = await admin.firestore().collection("users").doc(uid).get();
  return {
    passCount: Number(snapshot.get("passCount") ?? 0),
    messageCount: Number(snapshot.get("messageCount") ?? 0),
    usageUnlockCount: Number(snapshot.get("usageUnlockCount") ?? 0)
  };
}

function buildUsageAllowance(usageUnlockCount: number): {
  maxPassCount: number;
  maxMessageCount: number;
} {
  return {
    maxPassCount: FREE_PASS_LIMIT + (usageUnlockCount * USAGE_PASS_INCREMENT),
    maxMessageCount: FREE_MESSAGE_LIMIT + (usageUnlockCount * USAGE_MESSAGE_INCREMENT)
  };
}

function buildSolanaPayTransferUrl(input: {
  recipient: string;
  amount: number;
  reference: string;
  splToken?: string;
  label: string;
  message: string;
}): string {
  const url = new URL(`solana:${input.recipient}`);
  url.searchParams.set("amount", String(input.amount));
  url.searchParams.set("reference", input.reference);
  url.searchParams.set("label", input.label);
  url.searchParams.set("message", input.message);
  if (input.splToken) {
    url.searchParams.set("spl-token", input.splToken);
  }
  return url.toString();
}

async function createUsagePaywall(
  uid: string,
  trigger: "pass_limit" | "message_limit"
): Promise<UsagePaywallResponse> {
  const reference = Keypair.generate().publicKey.toBase58();
  await admin.firestore()
    .collection("users")
    .doc(uid)
    .collection("usagePayments")
    .doc(reference)
    .set({
      trigger,
      status: "pending",
      recipientWalletAddress: USAGE_PAYMENT_RECIPIENT,
      solAmount: USAGE_PAYMENT_SOL_AMOUNT,
      skrAmount: USAGE_PAYMENT_SKR_AMOUNT,
      skrTokenMint: USAGE_PAYMENT_SKR_MINT,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

  const label = "SoulMint";
  const message = trigger === "pass_limit"
    ? "Unlock more introductions"
    : "Unlock more messages";

  return {
    trigger,
    reference,
    recipientWalletAddress: USAGE_PAYMENT_RECIPIENT,
    solAmount: USAGE_PAYMENT_SOL_AMOUNT,
    skrAmount: USAGE_PAYMENT_SKR_AMOUNT,
    skrTokenMint: USAGE_PAYMENT_SKR_MINT,
    solanaPaySolUrl: buildSolanaPayTransferUrl({
      recipient: USAGE_PAYMENT_RECIPIENT,
      amount: USAGE_PAYMENT_SOL_AMOUNT,
      reference,
      label,
      message
    }),
    solanaPaySkrUrl: buildSolanaPayTransferUrl({
      recipient: USAGE_PAYMENT_RECIPIENT,
      amount: USAGE_PAYMENT_SKR_AMOUNT,
      reference,
      splToken: USAGE_PAYMENT_SKR_MINT,
      label,
      message
    })
  };
}

async function requireUsageAllowance(
  uid: string,
  trigger: "pass_limit" | "message_limit"
): Promise<void> {
  const metrics = await getUsageMetrics(uid);
  const allowance = buildUsageAllowance(metrics.usageUnlockCount);
  const hasRemaining = trigger === "pass_limit"
    ? metrics.passCount < allowance.maxPassCount
    : metrics.messageCount < allowance.maxMessageCount;
  if (hasRemaining) {
    return;
  }

  const paywall = await createUsagePaywall(uid, trigger);
  throw new HttpError(
    402,
    trigger === "pass_limit"
      ? "You have reached the current free pass limit."
      : "You have reached the current free message limit.",
    {
      code: "usage_payment_required",
      paywall
    }
  );
}

function flattenParsedInstructions(transaction: Awaited<ReturnType<Connection["getParsedTransaction"]>>): Array<Record<string, any>> {
  const outer = (transaction?.transaction.message.instructions ?? []) as Array<Record<string, any>>;
  const inner = (transaction?.meta?.innerInstructions ?? []).flatMap((group) => group.instructions as Array<Record<string, any>>);
  return [...outer, ...inner];
}

async function verifyUsagePaymentOnChain(reference: string): Promise<string | null> {
  const connection = getSolanaConnection();
  const referenceKey = new PublicKey(reference);
  const signatures = await connection.getSignaturesForAddress(referenceKey, { limit: 10 }, "confirmed");
  if (signatures.length === 0) {
    return null;
  }

  const recipient = new PublicKey(USAGE_PAYMENT_RECIPIENT);
  const skrMint = new PublicKey(USAGE_PAYMENT_SKR_MINT);
  const recipientAta = await getAssociatedTokenAddress(skrMint, recipient);
  const expectedLamports = Math.round(USAGE_PAYMENT_SOL_AMOUNT * LAMPORTS_PER_SOL);

  for (const entry of signatures) {
    const transaction = await connection.getParsedTransaction(entry.signature, {
      commitment: "confirmed",
      maxSupportedTransactionVersion: 0
    });
    if (!transaction) continue;

    const instructions = flattenParsedInstructions(transaction);
    const hasValidSolTransfer = instructions.some((instruction) => {
      const parsed = instruction.parsed as any;
      return instruction.program === "system" &&
        parsed?.type === "transfer" &&
        parsed?.info?.destination === USAGE_PAYMENT_RECIPIENT &&
        Number(parsed?.info?.lamports ?? 0) >= expectedLamports;
    });

    const hasValidSkrTransfer = instructions.some((instruction) => {
      const parsed = instruction.parsed as any;
      if (instruction.program !== "spl-token" && instruction.program !== "spl-token-2022") return false;
      const destination = String(parsed?.info?.destination ?? "");
      if (destination !== recipientAta.toBase58()) return false;
      const rawAmount = Number(parsed?.info?.amount ?? parsed?.info?.tokenAmount?.amount ?? 0);
      const uiAmount = Number(parsed?.info?.tokenAmount?.uiAmount ?? parsed?.info?.tokenAmount?.uiAmountString ?? 0);
      return uiAmount >= USAGE_PAYMENT_SKR_AMOUNT || rawAmount >= USAGE_PAYMENT_SKR_AMOUNT;
    });

    if (hasValidSolTransfer || hasValidSkrTransfer) {
      return entry.signature;
    }
  }

  return null;
}

async function checkUsagePaymentStatus(
  uid: string,
  request: CheckUsagePaymentRequest
): Promise<CheckUsagePaymentResponse> {
  const reference = request.reference?.trim() ?? "";
  if (!reference) {
    throw new HttpError(400, "reference is required");
  }

  const paymentRef = admin.firestore().collection("users").doc(uid).collection("usagePayments").doc(reference);
  const paymentSnapshot = await paymentRef.get();
  if (!paymentSnapshot.exists) {
    throw new HttpError(404, "payment reference not found");
  }

  if (String(paymentSnapshot.get("status") ?? "") === "confirmed") {
    const metrics = await getUsageMetrics(uid);
    return {
      reference,
      status: "confirmed",
      unlocked: true,
      usageUnlockCount: metrics.usageUnlockCount
    };
  }

  const signature = await verifyUsagePaymentOnChain(reference);
  if (!signature) {
    const metrics = await getUsageMetrics(uid);
    return {
      reference,
      status: "pending",
      unlocked: false,
      usageUnlockCount: metrics.usageUnlockCount
    };
  }

  const userRef = admin.firestore().collection("users").doc(uid);
  const batch = admin.firestore().batch();
  batch.set(paymentRef, {
    status: "confirmed",
    signature,
    confirmedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  batch.set(userRef, {
    usageUnlockCount: admin.firestore.FieldValue.increment(1),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  await batch.commit();

  const metrics = await getUsageMetrics(uid);
  return {
    reference,
    status: "confirmed",
    unlocked: true,
    usageUnlockCount: metrics.usageUnlockCount
  };
}

function toStringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map((item) => String(item).trim()).filter(Boolean) : [];
}

function toNumberList(value: unknown): number[] {
  return Array.isArray(value)
    ? value.map((item) => Number(item)).filter((item) => Number.isFinite(item))
    : [];
}

async function sendChatReply(
  uid: string,
  request: SendChatMessageRequest
): Promise<SendChatMessageResponse> {
  await requireUsageAllowance(uid, "message_limit");
  const chatId = request.chatId?.trim() ?? "";
  const message = request.message?.trim() ?? "";
  const senderName = request.senderName?.trim() || "You";

  if (!chatId) {
    throw new HttpError(400, "chatId is required");
  }
  if (message.length < 1) {
    throw new HttpError(400, "message is required");
  }
  if (message.length > 1000) {
    throw new HttpError(400, "message is too long");
  }

  const firestore = admin.firestore();
  const chatRef = firestore.collection("users").doc(uid).collection("chats").doc(chatId);
  const isBlocked = await firestore.collection("users").doc(uid).collection("blockedUsers").doc(chatId).get();
  if (isBlocked.exists) {
    throw new HttpError(403, "You have blocked this user");
  }
  let chatSnapshot = await chatRef.get();
  if (!chatSnapshot.exists) {
    const companion = findCompanionPersona(chatId);
    if (!companion) {
      throw new HttpError(404, "Chat not found");
    }
    await chatRef.set({
      name: companion.name,
      isAi: true,
      hasUserMessages: false,
      lastMessage: "",
      timestamp: "new",
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    chatSnapshot = await chatRef.get();
  }

  const chatName = chatSnapshot.get("name") as string | undefined ?? "Match";
  const companion = companionPersonaFromChatSnapshot(chatId, chatSnapshot);
  const isAi = chatSnapshot.get("isAi") === true || companion !== null;
  const historySnapshot = await chatRef.collection("messages")
    .orderBy("createdAt", "asc")
    .limitToLast(12)
    .get();
  const userSnapshot = await firestore.collection("users").doc(uid).get();
  const userContext: UserChatContext = {
    bio: String(userSnapshot.get("bio") ?? "").trim(),
    dreamDescription: String(userSnapshot.get("dreamDescription") ?? "").trim(),
    selfTags: toStringList(userSnapshot.get("selfTags")),
    dreamTags: toStringList(userSnapshot.get("dreamTags"))
  };
  const reply = isAi
    ? await generateCompanionReply(
      companion,
      chatName,
      message,
      historySnapshot.docs.map((doc) => ({
        senderName: String(doc.get("senderName") ?? ""),
        senderUid: String(doc.get("senderUid") ?? ""),
        content: String(doc.get("content") ?? "")
      })),
      userContext,
      deepseekApiKey.value()
    )
    : generateAutoReply(message, chatName);

  const batch = firestore.batch();
  batch.set(chatRef.collection("messages").doc(), {
    senderUid: uid,
    senderName,
    content: message,
    timestamp: "now",
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  batch.set(chatRef.collection("messages").doc(), {
    senderUid: `match:${chatId}`,
    senderName: chatName,
    content: reply,
    timestamp: "now",
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  batch.set(chatRef, {
    lastMessage: reply,
    hasUserMessages: true,
    timestamp: "now",
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  await batch.commit();
  await firestore.collection("users").doc(uid).set({
    messageCount: admin.firestore.FieldValue.increment(1),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  return { chatId, reply };
}

function generateAutoReply(message: string, name: string): string {
  const normalized = message.toLowerCase();
  if (normalized.includes("hello") || normalized.includes("hi")) {
    return `Hi. ${name} is into that energy too. What kind of night feels cinematic to you?`;
  }
  if (normalized.includes("music")) {
    return `${name} says music taste is a personality test. What's on your repeat right now?`;
  }
  if (normalized.includes("date")) {
    return `${name} would probably pick a late bookstore, a walk, and something sweet after.`;
  }
  return `${name} is curious about that. Tell them a little more.`;
}

function findCompanionPersona(chatId: string): CompanionPersona | null {
  return [...femaleCompanions, ...maleCompanions].find((item) => item.id === chatId) ?? null;
}

function companionPersonaFromChatSnapshot(
  chatId: string,
  chatSnapshot: admin.firestore.DocumentSnapshot
): CompanionPersona | null {
  const storedName = String(chatSnapshot.get("name") ?? "").trim();
  const storedPresentation = String(chatSnapshot.get("aiPresentation") ?? "").trim();
  const storedOpenerTone = String(chatSnapshot.get("aiOpenerTone") ?? "").trim();
  const storedSelfTags = toStringList(chatSnapshot.get("selfTags"));
  const storedDreamTags = toStringList(chatSnapshot.get("dreamTags"));
  if (
    storedName &&
    (storedPresentation === "female" || storedPresentation === "male") &&
    storedOpenerTone &&
    storedSelfTags.length > 0 &&
    storedDreamTags.length > 0
  ) {
    return {
      id: chatId,
      name: storedName,
      presentation: storedPresentation,
      openerTone: storedOpenerTone,
      selfTags: storedSelfTags,
      dreamTags: storedDreamTags
    };
  }
  return findCompanionPersona(chatId);
}

function isAiTargetId(targetId: string): boolean {
  return targetId.startsWith("ai:") || findCompanionPersona(targetId) !== null;
}

async function generateCompanionReply(
  companion: CompanionPersona | null,
  chatName: string,
  latestUserMessage: string,
  history: Array<{ senderName: string; senderUid: string; content: string }>,
  userContext: UserChatContext,
  apiKey: string | undefined
): Promise<string> {
  if (!companion || !apiKey || apiKey === "not-configured") {
    return generateAutoReply(latestUserMessage, chatName);
  }

  const systemPrompt = [
    `You are ${companion.name}, a ${companion.presentation} romantic match in a dating app.`,
    `Your personality should feel ${companion.openerTone}.`,
    `Your own traits: ${companion.selfTags.join(", ")}.`,
    `What you are drawn to: ${companion.dreamTags.join(", ")}.`,
    userContext.selfTags.length > 0 ? `The other person feels like: ${userContext.selfTags.join(", ")}.` : "",
    userContext.bio ? `Their self-description: ${userContext.bio}` : "",
    userContext.dreamDescription ? `What they want in a match: ${userContext.dreamDescription}` : "",
    "Reply like a real person in an intimate dating conversation.",
    "Never mention being AI, a bot, an assistant, a language model, a prompt, a system message, or any hidden setup.",
    "Keep replies concise, warm, and naturally flirtatious when appropriate.",
    "Avoid sounding robotic, formal, or overexplaining."
  ].filter(Boolean).join(" ");

  const messages = [
    { role: "system", content: systemPrompt },
    ...history.flatMap((item) => {
      const role = item.senderUid.startsWith("match:") ? "assistant" : "user";
      return item.content ? [{ role, content: item.content }] : [];
    }),
    { role: "user", content: latestUserMessage }
  ];

  const response = await fetch("https://api.deepseek.com/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: "deepseek-chat",
      temperature: 1.05,
      max_tokens: 120,
      messages
    })
  });

  if (!response.ok) {
    logger.error("DeepSeek chat completion failed", {
      status: response.status,
      body: await response.text()
    });
    return generateAutoReply(latestUserMessage, chatName);
  }

  const payload = await response.json() as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const content = payload.choices?.[0]?.message?.content?.trim();
  return content || generateAutoReply(latestUserMessage, chatName);
}

async function mintProfile(
  uid: string,
  request: MintSoulboundProfileRequest
): Promise<MintSoulboundProfileResponse> {
  const name = normalizeDisplayName(request.name);
  const bio = normalizeMintText(request.bio, "bio", 20, 600);
  const dreamDescription = normalizeMintText(request.dreamDescription, "dreamDescription", 20, 600);
  const selfTags = normalizeTags(request.selfTags, "selfTags");
  const dreamTags = normalizeTags(request.dreamTags, "dreamTags");
  const avatarPreviewUrl = request.avatarPreviewUrl?.trim() ?? "";
  const avatarIpfsUrl = request.avatarIpfsUrl?.trim() ?? "";
  const ownerWalletAddress = normalizeSolanaWalletAddress(request.ownerWalletAddress);

  if (!avatarPreviewUrl || !avatarIpfsUrl) {
    throw new HttpError(400, "Minting requires a generated avatar preview and IPFS URL");
  }

  const selfVector = buildSemanticVector(`${name} ${bio}`, selfTags, hashString);
  const compatibilityVector = buildSemanticVector(dreamDescription, dreamTags, hashString);
  const metadataIpfsUrl = pinataJwt.value()
    ? await uploadMetadataToIpfs(
        {
          uid,
          name,
          bio,
          dreamDescription,
          selfTags,
          dreamTags,
          selfVector,
          compatibilityVector,
          avatarIpfsUrl,
          mintedAt: new Date().toISOString()
        },
        pinataJwt.value()!
      )
    : `ipfs://soulmint-fallback/${uid}/metadata`;
  const preparedMint = await buildPreparedSolanaMint({
    metadataIpfsUrl,
    name,
    symbol: "SOUL",
    sellerFeeBasisPoints: 0,
    ownerWalletAddress
  });

  const firestore = admin.firestore();
  const counterRef = firestore.collection("system").doc("mintCounter");
  const userRef = firestore.collection("users").doc(uid);

  const { tokenId } = await firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(counterRef);
    const current = snapshot.get("nextTokenId") as number | undefined;
    const tokenId = current ?? 1000;
    transaction.set(counterRef, { nextTokenId: tokenId + 1 }, { merge: true });
    transaction.set(
      userRef,
      {
        name,
        bio,
        dreamDescription,
        selfTags,
        dreamTags,
        selfVector,
        compatibilityVector,
        avatarPreviewUrl,
        avatarIpfsUrl,
        metadataIpfsUrl,
        walletAddress: ownerWalletAddress,
        tokenId,
        tier: "Seed",
        mintedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      },
      { merge: true }
    );
    return { tokenId };
  });

  return {
    tokenId,
    tier: "Seed",
    txHash: "",
    mintMode: "solana_prepared",
    walletAddress: ownerWalletAddress,
    metadataIpfsUrl,
    externalMintUrl: buildExternalMintUrl(metadataIpfsUrl),
    externalMint: buildSolanaMintHandoff(metadataIpfsUrl, ownerWalletAddress, preparedMint)
  };
}

function normalizeDisplayName(raw: string | undefined): string {
  const value = raw?.trim() ?? "";
  if (!value) {
    return "Soul";
  }
  return value.slice(0, 40);
}

function normalizeMintText(
  raw: string | undefined,
  fieldName: string,
  minLength: number,
  maxLength: number
): string {
  const value = raw?.trim() ?? "";
  if (value.length < minLength) {
    throw new HttpError(400, `${fieldName} must be at least ${minLength} characters`);
  }
  if (value.length > maxLength) {
    throw new HttpError(400, `${fieldName} must be at most ${maxLength} characters`);
  }
  return value;
}

function normalizeTags(raw: string[] | undefined, fieldName: string): string[] {
  const tags = (raw ?? []).map((tag) => tag.trim()).filter(Boolean).slice(0, 12);
  if (tags.length === 0) {
    throw new HttpError(400, `${fieldName} must contain at least one tag`);
  }
  return tags;
}

function normalizeSolanaWalletAddress(raw: string | undefined): string {
  const value = raw?.trim() ?? "";
  if (!value) {
    throw new HttpError(400, "ownerWalletAddress is required to prepare a Solana mint transaction");
  }

  try {
    return new PublicKey(value).toBase58();
  } catch {
    throw new HttpError(400, "ownerWalletAddress must be a valid Solana wallet address");
  }
}

async function createWalletAuthChallenge(
  request: WalletAuthChallengeRequest
): Promise<WalletAuthChallengeResponse> {
  const walletAddress = normalizeSolanaWalletAddress(request.walletAddress);
  const nonce = createNonce();
  const message = buildWalletSignInMessage(walletAddress, nonce);
  logger.info("Creating wallet sign-in challenge", {
    walletAddress,
    nonce
  });
  const challengeRef = admin.firestore().collection("walletAuthChallenges").doc(nonce);
  await challengeRef.set({
    walletAddress,
    message,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAtMs: Date.now() + 5 * 60 * 1000,
    used: false
  });
  return { walletAddress, nonce, message };
}

async function completeWalletAuth(
  request: CompleteWalletSignInRequest
): Promise<CompleteWalletSignInResponse> {
  const walletAddress = normalizeSolanaWalletAddress(request.walletAddress);
  const nonce = request.nonce?.trim() ?? "";
  const message = request.message?.trim() ?? "";
  const signatureBase64 = request.signatureBase64?.trim() ?? "";

  if (!nonce || !message || !signatureBase64) {
    throw new HttpError(400, "wallet sign-in requires nonce, message, and signature");
  }

  logger.info("Completing wallet sign-in", {
    walletAddress,
    nonce,
    messageLength: message.length,
    signatureLength: signatureBase64.length
  });

  const challengeRef = admin.firestore().collection("walletAuthChallenges").doc(nonce);
  const challengeSnapshot = await challengeRef.get();
  if (!challengeSnapshot.exists) {
    throw new HttpError(400, "wallet sign-in challenge was not found");
  }

  const storedWalletAddress = String(challengeSnapshot.get("walletAddress") ?? "");
  const storedMessage = String(challengeSnapshot.get("message") ?? "");
  const expiresAtMs = Number(challengeSnapshot.get("expiresAtMs") ?? 0);
  const used = Boolean(challengeSnapshot.get("used") ?? false);

  if (used) {
    throw new HttpError(400, "wallet sign-in challenge was already used");
  }
  if (storedWalletAddress != walletAddress || storedMessage != message) {
    throw new HttpError(400, "wallet sign-in challenge did not match");
  }
  if (Date.now() > expiresAtMs) {
    throw new HttpError(400, "wallet sign-in challenge expired");
  }

  let signature: Uint8Array;
  try {
    signature = Buffer.from(signatureBase64, "base64");
  } catch {
    throw new HttpError(400, "wallet signature was not valid base64");
  }

  if (signature.length !== 64) {
    throw new HttpError(400, `wallet signature must be 64 bytes, got ${signature.length}`);
  }

  const messageBytes = new TextEncoder().encode(message);
  const publicKeyBytes = new PublicKey(walletAddress).toBytes();

  let verified = false;
  try {
    verified = ed25519.verify(signature, messageBytes, publicKeyBytes);
  } catch (error) {
    logger.error("Wallet signature verification threw", {
      walletAddress,
      nonce,
      error
    });
    throw new HttpError(400, "wallet signature verification could not be completed");
  }

  if (!verified) {
    throw new HttpError(401, "wallet signature verification failed");
  }

  await challengeRef.set({ used: true, usedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });

  const uid = walletUidFor(walletAddress);
  let customToken: string;
  try {
    customToken = await admin.auth().createCustomToken(uid, {
      walletAddress,
      authProvider: "solana_wallet"
    });
  } catch (error) {
    logger.error("Failed to create Firebase custom token for wallet sign-in", {
      walletAddress,
      uid,
      error
    });
    throw new HttpError(500, "wallet signature was valid but Firebase token creation failed");
  }

  await admin.firestore().collection("users").doc(uid).set(
    {
      walletAddress,
      authProvider: "solana_wallet",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  return { customToken, uid, walletAddress };
}

function createNonce(): string {
  return createHash("sha256")
    .update(`${Date.now()}-${randomInt(1, 2_147_483_647)}`)
    .digest("hex")
    .slice(0, 24);
}

function buildWalletSignInMessage(walletAddress: string, nonce: string): string {
  return [
    "SoulMint wallet sign-in",
    `Wallet: ${walletAddress}`,
    `Nonce: ${nonce}`,
    "Sign this message to continue into SoulMint.",
    "This request will not trigger a blockchain transaction."
  ].join("\n");
}

function walletUidFor(walletAddress: string): string {
  return `sol_${walletAddress}`;
}

function profileSnapshotToMatchProfile(
  id: string,
  snapshot: admin.firestore.DocumentSnapshot
): MatchFeedProfileResponse | null {
  const tokenId = snapshot.get("tokenId");
  const selfTags = toStringList(snapshot.get("selfTags"));
  const dreamTags = toStringList(snapshot.get("dreamTags"));
  const avatarPreviewUrl = String(snapshot.get("avatarPreviewUrl") ?? "").trim();
  const avatarIpfsUrl = String(snapshot.get("avatarIpfsUrl") ?? "").trim();
  const bio = String(snapshot.get("bio") ?? "").trim();
  const dreamDescription = String(snapshot.get("dreamDescription") ?? "").trim();
  const selfVector = toNumberList(snapshot.get("selfVector"));
  const compatibilityVector = toNumberList(snapshot.get("compatibilityVector"));

  if (
    typeof tokenId !== "number" ||
    selfTags.length === 0 ||
    dreamTags.length === 0 ||
    !avatarPreviewUrl ||
    !avatarIpfsUrl ||
    !bio ||
    !dreamDescription
  ) {
    return null;
  }

  return {
    id,
    name: String(snapshot.get("name") ?? "Soul"),
    tier: String(snapshot.get("tier") ?? "Seed"),
    tokenId,
    selfTags,
    dreamTags,
    compatibility: 0,
    avatarPreviewUrl,
    avatarIpfsUrl,
    selfVector: selfVector.length > 0
      ? selfVector
      : buildSemanticVector(`${String(snapshot.get("name") ?? "Soul")} ${bio}`, selfTags, hashString),
    compatibilityVector: compatibilityVector.length > 0
      ? compatibilityVector
      : buildSemanticVector(dreamDescription, dreamTags, hashString)
  };
}

async function uploadMetadataToIpfs(
  input: {
    uid: string;
    name: string;
    bio: string;
    dreamDescription: string;
    selfTags: string[];
    dreamTags: string[];
    selfVector: number[];
    compatibilityVector: number[];
    avatarIpfsUrl: string;
    mintedAt: string;
  },
  jwt: string
): Promise<string> {
  const pinata = new PinataSDK({ pinataJwt: jwt });
  const imageGatewayUrl = toGatewayUrl(input.avatarIpfsUrl);
  const metadataPayload = {
    name: `SoulMint ${input.name}`,
    symbol: "SOUL",
    description: "Soulbound identity NFT",
    seller_fee_basis_points: 0,
    image: imageGatewayUrl,
    external_url: imageGatewayUrl,
    attributes: [
      { trait_type: "Tier", value: "seed" },
      { trait_type: "Avatar Style", value: "semi-illustrated" },
      { trait_type: "Minted At", value: input.mintedAt },
      { trait_type: "Self Tags", value: input.selfTags.join(", ") },
      { trait_type: "Dream Tags", value: input.dreamTags.join(", ") }
    ],
    properties: {
      category: "image",
      files: [
        {
          uri: imageGatewayUrl,
          type: "image/png"
        },
        {
          uri: input.avatarIpfsUrl,
          type: "image/png"
        }
      ],
      creators: []
    },
    soulmint: {
      avatarIpfsUrl: input.avatarIpfsUrl,
      selfDescription: input.bio,
      selfPrompt: input.bio,
      selfTags: input.selfTags,
      selfVector: input.selfVector,
      dreamDescription: input.dreamDescription,
      dreamTags: input.dreamTags,
      compatibilityVector: input.compatibilityVector,
      mintedAt: input.mintedAt,
      matchCount: 0
    }
  };
  const metadataFile = new File(
    [JSON.stringify(metadataPayload, null, 2)],
    `soulmint-metadata-${input.uid}.json`,
    { type: "application/json" }
  );
  const result = await pinata.upload.public.file(metadataFile).name(`soulmint-metadata-${input.uid}`);
  return `ipfs://${result.cid}`;
}

function getSolanaConnection(): Connection {
  const configured = (process.env.SOULMINT_SOLANA_RPC_URL ?? process.env.SOLANA_RPC_URL ?? "").trim();
  return new Connection(configured || DEFAULT_SOLANA_RPC_URL, "confirmed");
}

function getMetadataPda(mint: PublicKey): PublicKey {
  return PublicKey.findProgramAddressSync(
    [
      Buffer.from("metadata"),
      TOKEN_METADATA_PROGRAM_ID.toBuffer(),
      mint.toBuffer()
    ],
    TOKEN_METADATA_PROGRAM_ID
  )[0];
}

function getMasterEditionPda(mint: PublicKey): PublicKey {
  return PublicKey.findProgramAddressSync(
    [
      Buffer.from("metadata"),
      TOKEN_METADATA_PROGRAM_ID.toBuffer(),
      mint.toBuffer(),
      Buffer.from("edition")
    ],
    TOKEN_METADATA_PROGRAM_ID
  )[0];
}

function createMetadataAccountV3Instruction(input: {
  metadataPda: PublicKey;
  mint: PublicKey;
  owner: PublicKey;
  metadataData: {
    name: string;
    symbol: string;
    uri: string;
    sellerFeeBasisPoints: number;
    creators: null;
    collection: null;
    uses: null;
  };
}): TransactionInstruction {
  const data = Buffer.from(
    getCreateMetadataAccountV3InstructionDataSerializer().serialize({
      data: input.metadataData,
      isMutable: true,
      collectionDetails: null
    })
  );

  return new TransactionInstruction({
    programId: TOKEN_METADATA_PROGRAM_ID,
    keys: [
      { pubkey: input.metadataPda, isSigner: false, isWritable: true },
      { pubkey: input.mint, isSigner: false, isWritable: false },
      { pubkey: input.owner, isSigner: true, isWritable: false },
      { pubkey: input.owner, isSigner: true, isWritable: true },
      { pubkey: input.owner, isSigner: true, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: SYSVAR_RENT_PUBKEY, isSigner: false, isWritable: false }
    ],
    data
  });
}

function createMasterEditionV3Instruction(input: {
  masterEditionPda: PublicKey;
  mint: PublicKey;
  owner: PublicKey;
  metadataPda: PublicKey;
}): TransactionInstruction {
  const data = Buffer.from(
    getCreateMasterEditionV3InstructionDataSerializer().serialize({
      maxSupply: BigInt(0)
    })
  );

  return new TransactionInstruction({
    programId: TOKEN_METADATA_PROGRAM_ID,
    keys: [
      { pubkey: input.masterEditionPda, isSigner: false, isWritable: true },
      { pubkey: input.mint, isSigner: false, isWritable: true },
      { pubkey: input.owner, isSigner: true, isWritable: false },
      { pubkey: input.owner, isSigner: true, isWritable: false },
      { pubkey: input.owner, isSigner: true, isWritable: true },
      { pubkey: input.metadataPda, isSigner: false, isWritable: true },
      { pubkey: TOKEN_PROGRAM_ID, isSigner: false, isWritable: false },
      { pubkey: SystemProgram.programId, isSigner: false, isWritable: false },
      { pubkey: SYSVAR_RENT_PUBKEY, isSigner: false, isWritable: false }
    ],
    data
  });
}

async function buildPreparedSolanaMint(input: {
  metadataIpfsUrl: string;
  name: string;
  symbol: string;
  sellerFeeBasisPoints: number;
  ownerWalletAddress: string;
}): Promise<PreparedSolanaMintResponse> {
  const connection = getSolanaConnection();
  const owner = new PublicKey(input.ownerWalletAddress);
  const mintKeypair = Keypair.generate();
  const rent = await connection.getMinimumBalanceForRentExemption(MINT_SIZE);
  const associatedTokenAccount = await getAssociatedTokenAddress(mintKeypair.publicKey, owner);
  const metadataPda = getMetadataPda(mintKeypair.publicKey);
  const masterEditionPda = getMasterEditionPda(mintKeypair.publicKey);

  const metadataData = {
    name: input.name,
    symbol: input.symbol,
    uri: toGatewayUrl(input.metadataIpfsUrl),
    sellerFeeBasisPoints: input.sellerFeeBasisPoints,
    creators: null,
    collection: null,
    uses: null
  };

  const transaction = new Transaction();
  transaction.add(
    SystemProgram.createAccount({
      fromPubkey: owner,
      newAccountPubkey: mintKeypair.publicKey,
      lamports: rent,
      space: MINT_SIZE,
      programId: TOKEN_PROGRAM_ID
    }),
    createInitializeMintInstruction(
      mintKeypair.publicKey,
      0,
      owner,
      owner,
      TOKEN_PROGRAM_ID
    ),
    createAssociatedTokenAccountInstruction(
      owner,
      associatedTokenAccount,
      owner,
      mintKeypair.publicKey
    ),
    createMintToInstruction(
      mintKeypair.publicKey,
      associatedTokenAccount,
      owner,
      1,
      [],
      TOKEN_PROGRAM_ID
    ),
    createMetadataAccountV3Instruction({
      metadataPda,
      mint: mintKeypair.publicKey,
      owner,
      metadataData
    }),
    createMasterEditionV3Instruction({
      masterEditionPda,
      mint: mintKeypair.publicKey,
      owner,
      metadataPda
    })
  );

  const {
    context: { slot: minContextSlot },
    value: latestBlockhash
  } = await connection.getLatestBlockhashAndContext("finalized");
  transaction.recentBlockhash = latestBlockhash.blockhash;
  transaction.feePayer = owner;
  transaction.partialSign(mintKeypair);

  const simulation = await connection.simulateTransaction(transaction, undefined, false);
  if (simulation.value.err) {
    logger.error("Prepared Solana mint transaction failed simulation", {
      ownerWalletAddress: input.ownerWalletAddress,
      mintAddress: mintKeypair.publicKey.toBase58(),
      err: simulation.value.err,
      logs: simulation.value.logs ?? []
    });
    throw new HttpError(500, `Prepared mint transaction failed simulation: ${JSON.stringify(simulation.value.err)}`);
  }

  const serialized = transaction.serialize({
    requireAllSignatures: false,
    verifySignatures: false
  });

  return {
    transactionBase64: serialized.toString("base64"),
    mintAddress: mintKeypair.publicKey.toBase58(),
    ownerWalletAddress: input.ownerWalletAddress,
    minContextSlot,
    rpcUrl: connection.rpcEndpoint
  };
}

function buildSolanaMintHandoff(
  metadataIpfsUrl: string,
  ownerWalletAddress: string,
  preparedMint: PreparedSolanaMintResponse
): SolanaMintHandoffResponse {
  return {
    platform: "solana-mobile-wallet-adapter",
    action: "prepare_solana_mint",
    network: preparedMint.rpcUrl.includes("mainnet") ? "mainnet-beta" : preparedMint.rpcUrl.includes("devnet") ? "devnet" : "custom",
    ownerWalletAddress,
    metadataIpfsUrl,
    metadataGatewayUrl: toGatewayUrl(metadataIpfsUrl),
    preparedMint
  };
}

function buildExternalMintUrl(metadataIpfsUrl: string): string {
  const gatewayUrl = toGatewayUrl(metadataIpfsUrl);
  const configuredBase = (process.env.SOULMINT_EXTERNAL_MINT_BASE_URL ?? "").trim();
  if (!configuredBase) {
    return gatewayUrl;
  }

  const separator = configuredBase.includes("?") ? "&" : "?";
  return `${configuredBase}${separator}metadata=${encodeURIComponent(gatewayUrl)}`;
}

async function reportAndBlock(
  uid: string,
  request: ReportAndBlockUserRequest
): Promise<ReportAndBlockUserResponse> {
  const targetId = request.targetId?.trim() ?? "";
  const reason = request.reason?.trim() ?? "unspecified";
  if (!targetId) {
    throw new HttpError(400, "targetId is required");
  }
  if (targetId === uid) {
    throw new HttpError(400, "You cannot block yourself");
  }

  const firestore = admin.firestore();
  const userRef = firestore.collection("users").doc(uid);
  const targetSnapshot = await firestore.collection("users").doc(targetId).get();
  const isCompanionTarget = isAiTargetId(targetId);
  if (!targetSnapshot.exists && !isCompanionTarget) {
    throw new HttpError(404, "Target user not found");
  }
  const reportRef = firestore.collection("reports").doc();
  const blockedRef = userRef.collection("blockedUsers").doc(targetId);
  const chatRef = userRef.collection("chats").doc(targetId);
  const feedRef = userRef.collection("feed").doc(targetId);
  const messageSnapshots = await chatRef.collection("messages").get();

  const batch = firestore.batch();
  batch.set(reportRef, {
    reporterUid: uid,
    targetUid: targetId,
    reason,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  batch.set(blockedRef, {
    reason,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  messageSnapshots.docs.forEach((doc) => batch.delete(doc.ref));
  batch.delete(chatRef);
  batch.delete(feedRef);
  batch.set(userRef, {
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  await batch.commit();

  return {
    targetId,
    blocked: true
  };
}

async function deleteAccount(uid: string): Promise<DeleteMyAccountResponse> {
  const firestore = admin.firestore();
  const userRef = firestore.collection("users").doc(uid);
  const collections = await userRef.listCollections();

  for (const collection of collections) {
    const snapshot = await collection.get();
    for (const doc of snapshot.docs) {
      const nestedCollections = await doc.ref.listCollections();
      for (const nestedCollection of nestedCollections) {
        const nestedSnapshot = await nestedCollection.get();
        const nestedBatch = firestore.batch();
        nestedSnapshot.docs.forEach((nestedDoc) => nestedBatch.delete(nestedDoc.ref));
        if (!nestedSnapshot.empty) {
          await nestedBatch.commit();
        }
      }
    }

    const batch = firestore.batch();
    snapshot.docs.forEach((doc) => batch.delete(doc.ref));
    if (!snapshot.empty) {
      await batch.commit();
    }
  }

  await userRef.delete();
  await admin.auth().deleteUser(uid);
  return { deleted: true };
}

async function recordFeedActionForUser(
  uid: string,
  request: RecordFeedActionRequest
): Promise<RecordFeedActionResponse> {
  const targetId = request.targetId?.trim() ?? "";
  const action = request.action?.trim().toLowerCase() ?? "";
  if (!targetId) {
    throw new HttpError(400, "targetId is required");
  }
  if (targetId === uid) {
    throw new HttpError(400, "You cannot record a feed action on yourself");
  }
  if (!["pass", "like", "super_like"].includes(action)) {
    throw new HttpError(400, "action must be pass, like, or super_like");
  }

  const firestore = admin.firestore();
  const targetSnapshot = await firestore.collection("users").doc(targetId).get();
  const isCompanionTarget = isAiTargetId(targetId);
  if (!targetSnapshot.exists && !isCompanionTarget) {
    throw new HttpError(404, "Target user not found");
  }

  if (action === "pass") {
    await requireUsageAllowance(uid, "pass_limit");
  }

  await firestore.collection("users").doc(uid).collection("hiddenFeedTargets").doc(targetId).set({
    action,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });

  if (action === "pass") {
    await firestore.collection("users").doc(uid).set({
      passCount: admin.firestore.FieldValue.increment(1),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  }

  return {
    targetId,
    action,
    saved: true
  };
}

async function deleteChatForUser(
  uid: string,
  request: DeleteChatThreadRequest
): Promise<DeleteChatThreadResponse> {
  const chatId = request.chatId?.trim() ?? "";
  if (!chatId) {
    throw new HttpError(400, "chatId is required");
  }

  const firestore = admin.firestore();
  const chatRef = firestore.collection("users").doc(uid).collection("chats").doc(chatId);
  const chatSnapshot = await chatRef.get();
  if (!chatSnapshot.exists) {
    return {
      chatId,
      deleted: false,
      removedMessages: 0
    };
  }

  const messageSnapshot = await chatRef.collection("messages").get();
  const batch = firestore.batch();
  messageSnapshot.docs.forEach((doc) => batch.delete(doc.ref));
  batch.delete(chatRef);
  await batch.commit();

  return {
    chatId,
    deleted: true,
    removedMessages: messageSnapshot.size
  };
}

async function ensureImageIsSafe(imageBytes: ArrayBuffer): Promise<void> {
  const [result] = await visionClient.safeSearchDetection({
    image: {
      content: Buffer.from(imageBytes)
    }
  });

  const annotation = result.safeSearchAnnotation;
  if (!annotation) {
    throw new HttpError(502, "Image moderation check failed");
  }

  if (isBlockedLikelihood(annotation.adult) || isBlockedLikelihood(annotation.violence) || isBlockedLikelihood(annotation.racy)) {
    logger.warn("Generated image blocked by moderation", {
      adult: annotation.adult,
      violence: annotation.violence,
      racy: annotation.racy
    });
    throw new HttpError(400, "Generated image failed moderation");
  }
}

function isBlockedLikelihood(
  likelihood?: protos.google.cloud.vision.v1.Likelihood | string | null
): boolean {
  return likelihood === "LIKELY" || likelihood === "VERY_LIKELY";
}
