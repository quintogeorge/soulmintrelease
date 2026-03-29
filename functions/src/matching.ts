export type MatchingProfile = {
  selfTags: string[];
  dreamTags: string[];
  selfVector: number[];
  compatibilityVector: number[];
};

export const VECTOR_DIMENSION = 64;
export const MATCH_THRESHOLD = 0.22;

export function normalizedOverlap(left: string[], right: string[]): number {
  if (left.length === 0 || right.length === 0) {
    return 0;
  }
  const rightSet = new Set(right.map((item) => item.toLowerCase()));
  const overlap = left.map((item) => item.toLowerCase()).filter((item) => rightSet.has(item)).length;
  return overlap / Math.max(left.length, right.length);
}

export function cosineSimilarity(left: number[], right: number[]): number {
  const maxLength = Math.max(left.length, right.length);
  if (maxLength === 0) {
    return 0;
  }

  let dot = 0;
  let leftMagnitude = 0;
  let rightMagnitude = 0;
  for (let index = 0; index < maxLength; index += 1) {
    const leftValue = left[index] ?? 0;
    const rightValue = right[index] ?? 0;
    dot += leftValue * rightValue;
    leftMagnitude += leftValue * leftValue;
    rightMagnitude += rightValue * rightValue;
  }

  if (leftMagnitude === 0 || rightMagnitude === 0) {
    return 0;
  }

  return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
}

export function tokenize(input: string): string[] {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, " ")
    .split(/\s+/)
    .map((token) => token.trim())
    .filter((token) => token.length >= 3);
}

export function buildSemanticVector(
  text: string,
  tags: string[],
  hashString: (value: string) => number
): number[] {
  const vector = new Array<number>(VECTOR_DIMENSION).fill(0);
  const tagSet = new Set(tags.map((tag) => tag.toLowerCase()));

  tokenize(`${text} ${tags.join(" ")}`).forEach((token, index) => {
    const hash = hashString(`${token}:${index}`);
    const slot = hash % VECTOR_DIMENSION;
    const weight = tagSet.has(token) ? 2.2 : token.length > 6 ? 1.2 : 1;
    vector[slot] += weight;
  });

  const magnitude = Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0));
  if (magnitude === 0) {
    return vector;
  }

  return vector.map((value) => Number((value / magnitude).toFixed(6)));
}

export function calculateCompatibility(
  currentUser: MatchingProfile,
  candidate: MatchingProfile
): number {
  const forward = Math.max(
    cosineSimilarity(currentUser.compatibilityVector, candidate.selfVector),
    normalizedOverlap(currentUser.dreamTags, candidate.selfTags)
  );
  const backward = Math.max(
    cosineSimilarity(candidate.compatibilityVector, currentUser.selfVector),
    normalizedOverlap(candidate.dreamTags, currentUser.selfTags)
  );
  if (forward < MATCH_THRESHOLD || backward < MATCH_THRESHOLD) {
    return 0;
  }

  const mutualScore = (forward + backward) / 2;
  const tagResonance = (
    normalizedOverlap(currentUser.dreamTags, candidate.selfTags) +
    normalizedOverlap(candidate.dreamTags, currentUser.selfTags)
  ) / 2;
  const finalScore = mutualScore * 0.8 + tagResonance * 0.2;

  return Math.round(70 + finalScore * 28);
}
