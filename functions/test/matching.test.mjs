import test from "node:test";
import assert from "node:assert/strict";

import {
  buildSemanticVector,
  calculateCompatibility,
  cosineSimilarity,
  normalizedOverlap
} from "../lib/matching.js";

function hashString(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

test("normalizedOverlap returns 0 when either side is empty", () => {
  assert.equal(normalizedOverlap([], ["dreamy"]), 0);
  assert.equal(normalizedOverlap(["dreamy"], []), 0);
});

test("cosineSimilarity rewards aligned vectors", () => {
  const left = [1, 1, 0];
  const right = [1, 1, 0];
  const opposite = [0, 0, 1];

  assert.ok(cosineSimilarity(left, right) > 0.99);
  assert.ok(cosineSimilarity(left, opposite) < 0.01);
});

test("buildSemanticVector is deterministic and normalized", () => {
  const vector = buildSemanticVector("cinematic rooftop jazz", ["jazz", "rooftop"], hashString);
  const second = buildSemanticVector("cinematic rooftop jazz", ["jazz", "rooftop"], hashString);
  const magnitude = Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0));

  assert.deepEqual(vector, second);
  assert.ok(Math.abs(magnitude - 1) < 0.0001);
});

test("calculateCompatibility ranks aligned profiles above mismatches", () => {
  const seeker = {
    selfTags: ["cinema", "jazz", "night walks"],
    dreamTags: ["bookish", "warm", "poetic"],
    selfVector: buildSemanticVector("cinematic jazz night walks", ["cinema", "jazz", "night walks"], hashString),
    compatibilityVector: buildSemanticVector("bookish warm poetic", ["bookish", "warm", "poetic"], hashString)
  };
  const aligned = {
    selfTags: ["bookish", "warm", "poetic"],
    dreamTags: ["cinema", "jazz", "night walks"],
    selfVector: buildSemanticVector("bookish warm poetic", ["bookish", "warm", "poetic"], hashString),
    compatibilityVector: buildSemanticVector("cinematic jazz night walks", ["cinema", "jazz", "night walks"], hashString)
  };
  const mismatch = {
    selfTags: ["gym", "crypto", "hustle"],
    dreamTags: ["party", "chaos", "noise"],
    selfVector: buildSemanticVector("gym crypto hustle", ["gym", "crypto", "hustle"], hashString),
    compatibilityVector: buildSemanticVector("party chaos noise", ["party", "chaos", "noise"], hashString)
  };

  assert.ok(calculateCompatibility(seeker, aligned) >= 90);
  assert.equal(calculateCompatibility(seeker, mismatch), 0);
});
