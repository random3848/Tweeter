# AGENTS.md

## Project

Tweeter is an Android app written in Kotlin. The UI is built with Jetpack Compose: screens and
components are `@Composable` functions, not XML layouts or View classes.

- Build: Gradle wrapper (`./gradlew build`), JDK 17. This is what the `build` check in
  `.github/workflows/build.yml` runs.
- `gradle/wrapper/gradle-wrapper.jar` must be an official Gradle release; the
  `Validate Gradle Wrapper` workflow rejects anything else. Regenerate it with `./gradlew wrapper`,
  never by hand.

## Code style

All Kotlin follows the [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide).
The guide is authoritative; the rules below are the ones most often gotten wrong.

Formatting:
- UTF-8, spaces only (no tabs), 4-space indent, 100-column limit (`package`/`import` lines exempt).
- No semicolons; one statement per line.
- Imports: one ASCII-sorted list, no wildcard imports.
- K&R braces. Braces are required on every `if`, `for`, `when` branch, `do`, and `while`, except
  a single-line `if`/`else` expression with at most one `else`, or a single-line `when` branch.
- A signature that doesn't fit on one line puts each parameter on its own line, indented +4, with
  `)` and the return type on their own line.

Naming:
- Packages: all lowercase, words concatenated, no underscores.
- Classes, objects, interfaces: PascalCase nouns.
- Functions: camelCase verbs. Underscores are allowed only in test names (`pop_emptyStack`).
- `@Composable` functions that return `Unit`: PascalCase nouns, like types (`NameTag`).
- Constants (`const val` for scalars; deeply immutable `val`s in an `object` or at top level):
  UPPER_SNAKE_CASE. Everything else: camelCase.
- Backing properties: the public name prefixed with `_` (`_uiState` backs `uiState`). No other
  prefixes or suffixes (`mName`, `sName`, `kName`).
- Files: a file with one top-level class is named after it (`TweetCard.kt`); otherwise a
  descriptive PascalCase name.

Documentation:
- KDoc on every public type and every public/protected member of such a type. The only
  exceptions are self-explanatory members (`getFoo`) and overrides.
- Each KDoc block opens with a short summary fragment.

## Git workflow (Gitflow)

The team uses Gitflow. `development` is Gitflow's `develop` branch (and the default branch);
`main` holds released code.

- **Under no circumstances touch `development` or `main` directly.** That means no commits,
  pushes, merges or rebases from the command line, edits in GitHub's web editor, or history
  rewrites. They change only when a reviewed pull request is merged on GitHub.
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
