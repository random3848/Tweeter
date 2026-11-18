# Tweeter 
Kotlin app project
Names: JK, Sol Herrera, Nancy Perez, Connor Stevenson, Kayla McNeill

## Testing against the Firebase emulators

The Firebase Emulator Suite runs Firestore, Authentication and Storage on your computer with the
security rules in `firestore.rules` and `storage.rules`, so you can try a feature, or a change to
the rules, without touching the live project the whole team uses.

One-time setup:

1. Install the Firebase CLI (`brew install firebase-cli`, or `npm install -g firebase-tools`) and
   run `firebase login`. The emulators also need Java 21 or newer.
2. Add this line to `local.properties`, which git ignores:

   ```
   firebase.emulatorHost=127.0.0.1
   ```

   Debug builds then use the emulators. Remove the line to go back to the live project. Release
   builds always use the live project.

Each time:

1. `firebase emulators:start` (the Emulator UI is at http://localhost:4000).
2. In another terminal, `node scripts/seed-firebase-emulators.mjs` fills them with sample posts
   and two accounts, `maya@example.com` and `jordan@example.com`, both with password `password`.
   Run it again to start over.
3. Forward the emulators' ports to the phone or Android emulator, after every time it starts:

   ```
   adb reverse tcp:9099 tcp:9099 && adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9199 tcp:9199
   ```

4. Run the app's debug build.

Data in the emulators is gone when they stop. Sharing a new post doesn't work there yet: the
Storage emulator's photo links are `http://`, and `firestore.rules` only accepts `https://` ones.
