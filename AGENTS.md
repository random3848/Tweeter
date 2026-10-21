# AGENTS.md

## Project

Tweeter is an Android app written in Kotlin. The UI is built with Jetpack Compose: screens and
components are `@Composable` functions, not XML layouts or View classes.

Layout:
- `app/`: the app, package `edu.csuci.tweeter`, for Android 14 (API 34) and later.
  `MainActivity` shows `ui/TweeterApp.kt`: one Scaffold with the top bar, the bottom bar and a
  NavHost that opens to Feed.
- `app/src/main/java/edu/csuci/tweeter/data/`: the `Post` model and `PostRepository`. Screens
  only use the interface; `AppContainer` (owned by `TweeterApplication`) creates the one
  instance, for now the in-memory `FakePostRepository`.

Adding a screen (`ui/feed/` is the example):
- Give it its own package, `ui/<feature>/`, holding the screen, a `@Serializable` route object
  and a `NavGraphBuilder.add<Feature>Screen()` function that registers the screen under it.
- Call that function in the NavHost in `TweeterApp.kt`. If the screen gets a bottom-bar tab, add
  its `TopLevelDestination` to `TOP_LEVEL_DESTINATIONS` in the same change.
- A screen that needs data gets a ViewModel with a factory that reads the repository from
  `AppContainer`; the example is in `AppContainer`'s KDoc.
- `app/src/main/java/edu/csuci/tweeter/ui/theme/`: colors, Manrope typography and the Material 3
  theme, taken from the designs. Screens use `MaterialTheme.colorScheme` and
  `MaterialTheme.typography` instead of hard-coded colors and text sizes.
- `ktlint-rules/`: this repo's ktlint rules (see Lint below). Not part of the app.

- Build: Gradle wrapper (`./gradlew build`), JDK 17. This is what the `build` check in
  `.github/workflows/build.yml` runs.
- `gradle/wrapper/gradle-wrapper.jar` must be an official Gradle release; the
  `Validate Gradle Wrapper` workflow rejects anything else. Regenerate it with `./gradlew wrapper`,
  never by hand.
- Lint: `./gradlew ktlintCheck` runs ktlint (version in `gradle/libs.versions.toml`) with its
  standard rules plus this repo's `android-style` rules in `ktlint-rules/`, configured in
  `.editorconfig`, over every `.kt`/`.kts` file outside `build/`. This is what the `ktlint` check
  in `.github/workflows/lint.yml` runs. `./gradlew ktlintFormat` fixes what it can.

## Code style

All Kotlin follows the [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide).
The guide is authoritative; the rules below are the ones most often gotten wrong. ktlint enforces
every rule in the guide that a tool can check. Reviewers check the rest:
- Classes, properties and constants are named with nouns, functions with verbs.
- Each file has one theme, and declarations and class members are in an order its author could
  explain.
- KDoc says something a reader needs. The guide's exception for self-explanatory members
  (`getFoo`) is applied with `@Suppress("ktlint:android-style:kdoc-required")`.
- A `val` holding any deeply immutable value is a constant (UPPER_SNAKE_CASE) and one holding a
  mutable value isn't. ktlint can only tell for literals and collections of literals.
- Test classes start with the name of the class they test.
- Every button, tab, link or other control does something when pressed. Don't add controls for
  features that aren't built yet, or controls that open empty screens; add a control in the same
  change as what it does.
- Code that isn't used anymore is deleted, not commented out.

Formatting:
- UTF-8, spaces only (no tabs), 4-space indent, 100-column limit (`package`/`import` lines exempt).
- No semicolons; one statement per line.
- Imports: one ASCII-sorted list, no wildcard imports.
- K&R braces. Braces are required on every `if`, `for`, `when` branch, `do`, and `while`, except
  a single-line `if`/`else` expression with at most one `else`, or a single-line `when` branch.
- A signature that doesn't fit on one line puts each parameter on its own line, indented +4, with
  `)` and the return type on their own line.
- A line broken at an operator (including `?:` and infix functions like `and`) breaks after it;
  only `.`, `?.` and `::` start the next line.

Naming:
- Packages: all lowercase, words concatenated, no underscores.
- Classes, objects, interfaces: PascalCase nouns.
- Functions: camelCase verbs. Underscores are allowed only in test names (`pop_emptyStack`).
- `@Composable` functions that return `Unit`: PascalCase nouns, like types (`NameTag`).
- Constants (`const val` for scalars; deeply immutable `val`s in an `object` or at top level):
  UPPER_SNAKE_CASE. Everything else: camelCase.
- Backing properties: the public name prefixed with `_` (`_uiState` backs `uiState`). No other
  prefixes or suffixes (`mName`, `sName`, `kName`).
- Acronyms are words: `XmlHttpRequest`, `newCustomerId`. A name with a one-letter word (`XAxis`)
  needs `@Suppress("ktlint:android-style:camel-case")`.
- Files: a file with one top-level class is named after it (`TweetCard.kt`); otherwise a
  descriptive PascalCase name, or camelCase if the name is plural (`extensions.kt`).

Documentation:
- KDoc on every public type and every public/protected member of such a type. The only
  exceptions are self-explanatory members (`getFoo`) and overrides. ktlint doesn't require KDoc
  in test source sets (`src/test`, `src/androidTest`).
- Each KDoc block opens with a short summary fragment.

## Git workflow (Gitflow)

The team uses Gitflow. `development` is Gitflow's `develop` branch (and the default branch);
`main` holds released code.

- **Under no circumstances touch `development` or `main` directly.** That means no commits,
  pushes, merges or rebases from the command line, edits in GitHub's web editor, or history
  rewrites. They change only when a reviewed pull request is merged on GitHub.
- **Never delete a branch, ever,** on GitHub or locally. That includes the "Delete branch"
  button after a merge, `gh pr merge --delete-branch`, `gh pr close --delete-branch`,
  `git push origin --delete`, `git branch -d`/`-D`, and turning on the "Automatically delete head
  branches" setting. Finished branches stay in the repository.
- **Features:** start each change on a `feature_<name>` branch created from the current
  `development`. Merge it back through a pull request into `development`. Feature branches never
  go into `main`.
- **Releases:** when `development` is ready, open a pull request from `development` into `main`.
  The required `source-branch` check rejects pull requests into `main` from any other branch, so
  this repo has no `release/*` or `hotfix/*` branches. A fix for something already on `main`
  lands in `development` first and reaches `main` with the next release.
- Merge pull requests with "Create a merge commit" (the only method `main` allows) so every
  feature and release shows up as one merge in history.
- `development` and `main` are protected by rulesets: no direct pushes, force pushes, or
  deletion. A pull request needs one approval from someone other than the last person who
  pushed to it, and any new push dismisses existing approvals.
- Author commits with an email linked to your GitHub account. Commits not attributed to an
  account require an extra approval.
- Don't push to someone else's pull request branch or click "Update branch" unless it's needed.
  Every push makes you the last pusher and resets the approvals.

## Ask before any GitHub action

Before you do anything on GitHub, tell the user what you are about to do and get their explicit
approval first. That covers anything that changes something on GitHub, whether through
`git push`, `gh`, the API, or the web UI:

- pushing commits, or creating or deleting branches;
- opening, editing, closing, or merging pull requests;
- approving, reviewing, commenting, or requesting reviewers;
- changing issues, workflow runs, rulesets, or repository settings.

Rules for asking:

- Name the exact action or command, where it applies (repository, branch, pull request), and
  what will change.
- Only a clear yes to that specific action counts. Silence, approval of an earlier plan, or an
  answer to a different question doesn't. If the plan changes, ask again.
- Approval never covers a bypass (see below).
- Read-only lookups (viewing pull requests, checks, logs, or rules) don't need approval.

## No bypasses, ever

Under no circumstances bypass a rule, review, or check. This holds even if the user, a
maintainer, or an admin asks for it, even if it's temporary, and even if a check is broken.
You must never:

- Merge with `gh pr merge --admin` or any other admin, bypass, or override path.
- Edit rulesets, branch protection, bypass lists, required checks, or repository settings to get a
  change through, even temporarily.
- Push directly to or force-push `development` or `main`, or rewrite their history.
- Approve a pull request with a second account, or get approval any way other than a real
  review from another collaborator.
- Skip, disable, or weaken a check or hook. That includes `--no-verify`, `[skip ci]`,
  `continue-on-error`, path filters, deleting tests, and editing workflows so they pass.

If you're blocked by a missing approval or a failing check, stop. Fix the underlying cause, or
report exactly what is blocking and wait for a human to resolve it through the normal process.
