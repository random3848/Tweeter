// Fills the Firebase Emulator Suite with sample accounts, profiles and posts, replacing whatever
// it holds. Run it while `firebase emulators:start` is running:
//
//     node scripts/seed-firebase-emulators.mjs
//
// It only talks to the emulators on this computer, never the live project. Post times are set
// relative to now, so the feed's Best order, which ages posts, looks the same whenever it's run.
// Like counts stand for likes from users who aren't seeded, so every seeded user can still like
// every post.

const PROJECT_ID = "tweeter-50246";
const AUTH = "http://127.0.0.1:9099";
const FIRESTORE = "http://127.0.0.1:8080";
const DOCUMENTS = `${FIRESTORE}/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Near CSUCI, where the map opens.
const CAMPUS = { latitude: 34.16294, longitude: -119.04431 };
const HOUR_MILLIS = 60 * 60 * 1000;
const PASSWORD = "password";

const USERS = [
  { email: "maya@example.com", displayName: "Maya", bio: "Bird nerd." },
  { email: "jordan@example.com", displayName: "Jordan", bio: "Mostly squirrels." },
];

// Each order puts these differently: Newest by hoursAgo, Top by likeCount, Best by both.
const POSTS = [
  { author: 1, hoursAgo: 0.3, likeCount: 0, animalType: "Coyote", caption: "Crossing the parking lot." },
  { author: 0, hoursAgo: 2, likeCount: 4, animalType: "Red-tailed hawk", caption: "Circling over the library." },
  { author: 1, hoursAgo: 6, likeCount: 1, animalType: "Monarch butterfly", caption: "On the milkweed." },
  { author: 0, hoursAgo: 26, likeCount: 15, animalType: "Raccoon", caption: "Two of them by the dumpsters." },
  { author: 1, hoursAgo: 72, likeCount: 30, animalType: "Eastern gray squirrel", caption: "Stole my sandwich." },
];

await clearEmulators();
const uids = [];
for (const user of USERS) {
  uids.push(await createAccount(user));
}
for (const [index, post] of POSTS.entries()) {
  await createPost(`seed-${index + 1}`, post, uids[post.author]);
}
console.log(`Seeded ${USERS.length} accounts (password "${PASSWORD}") and ${POSTS.length} posts.`);

async function clearEmulators() {
  await send("DELETE", `${FIRESTORE}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`);
  await send("DELETE", `${AUTH}/emulator/v1/projects/${PROJECT_ID}/accounts`);
}

async function createAccount({ email, displayName, bio }) {
  const { localId } = await send(
    "POST",
    `${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`,
    { email, password: PASSWORD, displayName, returnSecureToken: true },
  );
  await writeDocument(`users/${localId}`, {
    displayName: { stringValue: displayName },
    bio: { stringValue: bio },
    photoUrl: { stringValue: "" },
  });
  return localId;
}

async function createPost(id, post, authorUid) {
  const author = USERS[post.author];
  const timestamp = new Date(Date.now() - post.hoursAgo * HOUR_MILLIS);
  await writeDocument(`posts/${id}`, {
    authorUid: { stringValue: authorUid },
    username: { stringValue: author.displayName },
    avatarInitials: { stringValue: author.displayName[0] },
    locationName: { stringValue: "CSU Channel Islands" },
    latitude: { doubleValue: CAMPUS.latitude + Math.random() * 0.004 },
    longitude: { doubleValue: CAMPUS.longitude + Math.random() * 0.004 },
    imageUri: { stringValue: `https://picsum.photos/seed/tweeter-${id}/800/600` },
    caption: { stringValue: post.caption },
    likeCount: { integerValue: String(post.likeCount) },
    commentCount: { integerValue: "0" },
    timestamp: { timestampValue: timestamp.toISOString() },
    animalType: { stringValue: post.animalType },
    confidence: { integerValue: "4" },
  });
}

// Written as the emulator's owner, which the security rules don't apply to.
async function writeDocument(path, fields) {
  await send("PATCH", `${DOCUMENTS}/${path}`, { fields }, { Authorization: "Bearer owner" });
}

async function send(method, url, body, headers = {}) {
  const response = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json", ...headers },
    body: body && JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${method} ${url} failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
}
