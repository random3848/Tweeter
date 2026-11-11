# AGENTS.md

## Project

Tweeter is an Android app written in Kotlin. The UI is built with Jetpack Compose: screens and
components are `@Composable` functions, not XML layouts or View classes. The backend is Firebase:
Firestore, Storage and Authentication.

Layout:
- `app/`: the app, package `edu.csuci.tweeter`, for Android 14 (API 34) and later.
  `MainActivity` shows `ui/TweeterApp.kt`: one Scaffold with the top bar, the bottom bar and a
  NavHost that opens to Feed.
- `app/src/main/java/edu/csuci/tweeter/data/`: the models (`Post`, `UserProfile`) and the
  repositories screens use through interfaces (`PostRepository`, `ProfileRepository`,
  `AccountRepository`, `PhotoRepository`, `AnimalRepository`). `AppContainer` (owned by
  `TweeterApplication`) creates one Firebase implementation of each, except `AnimalRepository`,
  which is the in-memory `FakeAnimalRepository` until the iNaturalist one is built; Profile uses
  `PlaceholderAccountRepository` directly until log-in is built. `PostDocument` and `ProfileDocument` map the models to Firestore fields, and a
  field added to `PostDocument` also goes into the `hasOnly` list in `firestore.rules`.
  `FakePostRepository` is an in-memory `PostRepository` for tests and for working offline.
- `app/src/main/java/edu/csuci/tweeter/location/`: finding where a photo was taken
  (`PhotoLocator`, from its EXIF tags or the device) and removing its location tags before upload
  (`LocationTagRemover`).
- `app/src/main/java/edu/csuci/tweeter/ui/theme/`: colors, Manrope typography and the Material 3
  theme, taken from the designs. Screens use `MaterialTheme.colorScheme` and
  `MaterialTheme.typography` instead of hard-coded colors and text sizes.
- `app/src/test/`: JVM unit tests, in the same packages as the code they test. There are no
  instrumented tests (`app/src/androidTest`) yet.
- `app/google-services.json`: the Firebase config the build needs, committed even though
  `.gitignore` lists it. `firestore.rules` and `storage.rules`: the Firebase security rules.
- `ktlint-rules/`: this repo's ktlint rules (see Lint below). Not part of the app.

Adding a screen (`ui/feed/` is the example):
- Give it its own package, `ui/<feature>/`, holding the screen, a `@Serializable` route object
  and a `NavGraphBuilder.add<Feature>Screen()` function that registers the screen under it.
- Call that function in the NavHost in `TweeterApp.kt`. If the screen gets a bottom-bar tab, add
  its `BottomBarItem`, with `route` set to the screen's route object, to `BOTTOM_BAR_ITEMS` there
  in the same change.
- A screen that needs data gets a ViewModel with a factory that reads the repository from
  `AppContainer`; the example is in `AppContainer`'s KDoc.

Build and checks:
- Build: `./gradlew build` builds the app and runs every unit test in one command; keep it that
  way. It's what the `build` check in `.github/workflows/build.yml` runs; `./gradlew test` runs
  just the tests. Starting Gradle takes JDK 17 or newer; Gradle then runs on the JDK 25 that
  `gradle/gradle-daemon-jvm.properties` asks for (downloading it if it's missing) and compiles the
  code for Java 17.
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
- A `val` at top level or in an `object` that holds a deeply immutable value is a constant
  (UPPER_SNAKE_CASE); one holding a mutable value isn't, and neither is a `val` in a class or
  function. ktlint can only tell for literals and collections of literals.
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

## Clean code

Rules from Robert C. Martin's *Clean Code* (2008), restated for Kotlin and Compose. They add to
Code style; where one conflicts with Code style or with "No bypasses, ever", those win. The book's
wildcard imports, its objection to required doc comments and its `@Ignore` for unclear tests don't
apply. Reviewers check the rules about code; the author follows the rules about how to work (test
first, learning tests, coverage), since a diff doesn't show them.

In order of priority, code passes all its tests, contains no duplication, says what its author
meant, and has as few classes and functions as those three allow. Don't add a class or interface
that none of the first three calls for (an interface for every class, say).

Working on code:
- Logic, meaning anything a JVM unit test can reach, is test-driven: write a failing unit test
  before the production code that makes it pass, only as much test as it takes to fail (not
  compiling counts), and only as much code as it takes to pass.
- Working code isn't finished: get it working, then clean it (split functions, rename, remove
  duplication) before you call it done. Leave the code you change a little cleaner than you found
  it, and keep that cleanup inside the code your change is about.
- Change structure in small steps that each keep the build and tests passing, never in one big
  rewrite. To replace something, add the new version beside the old one, move callers over one at
  a time, then delete the old one.
- When the next feature would make the structure much worse, restructure before adding it.
- Know why your code works, not only that the tests pass. If you can't tell, refactor until it's
  obvious.
- Be precise: when a call can return null, handle the null; when a query can match more than one
  result, don't assume one.
- Don't override safeties: fix a compiler warning or lint finding instead of suppressing it, and a
  failing test instead of turning it off. A suppression the code really needs, other than the ones
  Code style documents, says why next to it (see `MissingPermission` in `DeviceLocationProvider`).
- Build what today's requirements need, with concerns kept separate so later ones stay cheap. Put
  off big decisions until you have to make them, and add a library only for value you can name.

Names:
- A name says why the thing exists, what it does and how it's used; one that needs a comment is
  the wrong name. Put units in it when the type doesn't carry them (`timeoutMillis`). Rename as
  soon as you find a better name.
- No false clues: `postList` only if it's a `List` (`posts` is better anyway), no abbreviations
  that already mean something else, no names that differ by a few letters, never `l` or `O`.
- Different names mean different things. No number series (`post1`, `post2`) and no noise words:
  `PostInfo`, `PostData` or `thePost` beside `Post` says nothing about the difference. Class names
  avoid vague words like `Manager` and `Processor`.
- Names are pronounceable and searchable, and longer the wider their scope: one-letter names and
  `it` only in scopes a few lines long.
- One word per concept across the codebase (not `get`, `fetch` and `load` for the same kind of
  call), one concept per word, and similar things done the same way everywhere.
- Technical things get computer-science and pattern names (`Repository`, `Factory`, `Queue`); the
  rest get names from the problem domain (`Post`, `Feed`, `Profile`).
- Names fit the level of abstraction, not the implementation. An interface gets the plain name
  (`PostRepository`, not `IPostRepository`); its implementations say how they differ
  (`FirestorePostRepository`, `FakePostRepository`).
- A function's name covers everything it does, side effects included (`getOrCreateSession`, not
  `getSession`), and shows whether it changes its receiver (`addDays(5)`) or returns a new value
  (`plusDays(5)`). Boolean functions and properties read as yes/no questions or facts (`isEmpty`,
  `hasLocation`, `uploadFailed`).
- Where a constructor call wouldn't say what its arguments mean, use named arguments or a factory
  function named after them (`Complex.fromRealNumber(23.0)`).
- Context comes from the enclosing class, file or package, not from prefixes, and a name carries
  no more context than it needs: no app or module name in front of everything. No jokes or slang.

Functions:
- Small: hardly ever 20 lines, counting a call whose named arguments take several lines as one.
  The body of an `if`, `else`, loop or `when` branch is ideally one call, and `if`, `when`, loops
  and `try` nest one or two levels deep at most. Layout calls (`Column { Row { … } }`) don't count
  toward that, but a deeply nested piece of layout moves into its own named composable.
- One thing, at one level of abstraction: every step sits one level below the function's name. If
  it falls into sections, or you can extract a function from it with a name that isn't just a
  restatement of the code, it does more than one thing.
- Few parameters: none is best, then one, then two. Three need a reason and more than three a very
  good one. Values that travel together become one class (`Point`, not `x, y`). Constructors
  called with named arguments (a `data class` such as `Post`, a class receiving its dependencies)
  don't count, nor do a composable's optional parameters with defaults, such as `modifier`.
- No flag or selector arguments: a Boolean, enum or number that switches a function between
  different procedures means it should be separate functions (`renderForSuite()` and
  `renderForSingleTest()`, not `render(true)`), even when the value describes the data. UI state a
  composable draws (`enabled`) and a value that tunes one procedure (a sort order) aren't flags.
- No output arguments: a function that changes something changes its receiver (as a member or
  extension) or returns the new value.
- A function does all that its name promises (a day-name parser accepts `monday` as well as
  `Monday`) and nothing else: no hidden side effects. When steps must run in order, pass each
  step's result to the next so they can't be called out of order.
- A function either changes something or answers a question, not both, except that a function
  that makes something may return it (`getOrCreateSession`, an upload returning the file's URL).
- Name a condition instead of spelling it out (`if (shouldBeDeleted(timer))`, not
  `if (timer.hasExpired() && !timer.isRecurrent())`), and state it positively
  (`shouldCompact()`, not `!shouldNotCompact()`).
- Break calculations into named intermediate `val`s, and compute a boundary value once
  (`val nextLevel = level + 1`) instead of repeating `level + 1`.
- Early `return`, `break` and `continue` are fine in small functions.
- Code that can't run is dead too: delete checks for conditions that can't happen and `catch`
  blocks for exceptions nothing throws.

Errors:
- Report a failure by throwing, not with an error code or sentinel value (`-1`, `""`, `false`).
- Handling errors is one thing: in a function that catches, `try` comes first, nothing follows the
  last `catch` or `finally`, and the work inside is a call to a function that does it. Only the
  line creating what `finally` releases may come before the `try`, and a `try` expression that
  turns one call's failure into a value (`try { read(uri) } catch (e: IOException) { null }`) is
  fine anywhere.
- An exception's message names the operation that failed, with the why in the message or its
  `cause`; a translated exception keeps the original as its `cause`.
- Exception classes are defined by how callers handle them, not by where they come from. Wrap a
  library's exceptions in one of the app's, as `PhotoUploadException` does for authentication and
  transfer failures.
- When the caller shouldn't abort, return a special-case value that handles the case itself
  instead of throwing: an empty collection (never null for one), a default, or null when every
  caller treats the failure like absence (`ExifLocationReader.read`). Otherwise a return type is
  nullable only when absence is a normal answer, and a parameter only when null means something to
  the function.
- Handle a possible null where you get it; if it can't happen, say so with
  `checkNotNull(x) { "why" }`, not `!!`.
- When code can fail, write the `try`/`catch` first, with a test that forces the failure, and make
  the `catch` leave the program in a consistent state.

Comments (on top of the KDoc rules under Documentation):
- Explain yourself in code first: the comment you're about to write is usually the name of a
  function or `val` (`if (employee.isEligibleForFullBenefits())`), and a private function with a
  good name needs none. Don't comment bad code; clean it.
- Comments that earn their place say why the code does something non-obvious, warn of consequences
  (not thread-safe, slow), stress why a trivial-looking line matters, explain an obscure argument
  or return value of a library you can't change, or say what's left to do and why (`TODO`, or
  "until log-in is built; that work removes this"). Such a note is deleted when the work is done
  and never excuses bad code.
- A comment sits next to the code it describes, describes only that code, and is exactly right.
  When the code changes, update or delete it.
- No change logs, bylines, dates or ticket history (git has them); no comments or KDoc tags that
  repeat the code or the signature (`@param post the post`); no banners, position markers or
  closing-brace comments; no historical discussion or irrelevant detail; no HTML in KDoc, which is
  Markdown.

Order and layout:
- Files are short: typically about 200 lines, rarely over 500.
- A file reads like a newspaper article: its name says what it's about, the top tells the
  high-level story, and detail increases going down. Unless there's a reason for another order, a
  class starts with its properties, each function sits above the functions it calls, and each
  private function sits just below its first caller.
- Related code stays together: a local is declared just before its first use, lines that belong
  together aren't split by blank lines or comments, blank lines separate the steps of a function,
  and functions that are variations of one task sit side by side.
- Other languages (HTML, SQL, large JSON) stay out of Kotlin files as far as possible.

Classes and objects:
- A class is small, counted in responsibilities: it has one reason to change. If you can't name it
  concisely, or describe it in about 25 words without "if", "and", "or" or "but", it does too
  much. Many small classes beat a few large ones.
- A class is cohesive: few properties, each used by most of its functions. Functions that share
  properties the rest don't use are a class of their own.
- An object hides its data behind behavior; a data structure (a `data class` such as `Post` or a
  UI state) exposes its data and holds no business logic, though a property derived from its own
  fields (`UserProfile.avatar`) is fine. No hybrids: a ViewModel exposes read-only state (a
  `StateFlow`, or a Compose `State` property with a `private set`) and keeps the mutable state
  private. An object exposes what its data means, not how it's stored, and not a public property
  per field.
- Talk to friends, not strangers: a function uses its own class, its parameters, objects it
  creates and its properties, not objects those return (`a.b().c().doSomething()`). Tell the
  collaborator to do the job instead. Exempt: data structures (`post.avatar.initials`,
  `MaterialTheme.colorScheme.primary`), chains that return a new value of the same kind
  (`Modifier`, `Flow` operators, builders), and a ViewModel factory's
  `application.container.postRepository`.
- Kotlin declarations are public by default; make each one as private as it can be. Widen one for
  a test only as a last resort, to `internal` with `@VisibleForTesting`.
- Declare the most general type that works (`List`, not `ArrayList` or `MutableList`).
- Where you can, add features by adding code, such as a new implementation of an interface, rather
  than by changing what working classes already do. Depend on an interface the app owns
  (`PostRepository`) where a test needs a fake or a second implementation exists.
- Objects with polymorphic functions make new types easy; data structures with functions over them
  (an `enum` or `sealed` type and `when`) make new operations easy. Choose by the change you
  expect. New types are the usual one, so prefer polymorphism to `when` on a type, except for UI
  state and other data structures, which the code using them maps with `when`.
- Keep at most one `when` per kind of selection, ideally where the objects are created (for UI
  state, in its screen). Make it exhaustive over the `enum` or `sealed` type, with no `else`, so a
  new case doesn't compile until it's handled.
- A fixed set of values is an `enum class` (like `FeedSortOrder`) or `sealed` type, not `Int` or
  `String` constants; enums can have properties and functions.
- No magic numbers or strings: a value whose meaning isn't obvious gets a named constant
  (`SECONDS_PER_DAY`), in tests too. A value that reads clearly where it is (`radius * 2`, or a
  size passed as a named argument like `padding(horizontal = 16.dp)`) can stay, unless it repeats
  elsewhere with the same meaning.

Where code goes:
- Don't repeat yourself: identical code becomes a function, and similar algorithms share one
  function that takes the step that differs as a parameter.
- Code goes where a reader would look for it: a constant with the code that uses it, a computation
  in the function whose name promises it. A general enum, constant or helper doesn't live in a
  specific class for convenience, and a nested class is only for helpers of its outer class.
- A function that mostly uses another class's data belongs in that class, unless that would tie
  the class to an unrelated concern (formatting a post for display stays in the UI).
- An interface holds only what every implementation can honor (`PostRepository` declares nothing
  only Firestore could do). A base type never refers to its subtypes, except a `sealed` one.
- Ask a collaborator for what you rely on (`formatter.maxPageSize`) instead of assuming it. A
  default or configuration value that higher-level code knows about or sets is defined there and
  passed down; one only its user cares about stays with that code.
- A top-level or `object` function is for logic with one implementation (`max`, `initialsOf`).
  When it needs a second, it becomes a member of a class the caller is given.

Construction and boundaries:
- Building objects is separate from using them. A class gets its dependencies as constructor
  parameters; only `AppContainer` and the ViewModel factories that read it create them. No
  `FakePostRepository()` or `if (x == null) x = Foo()` inside a ViewModel, composable or
  repository.
- A concern that cuts across classes, such as caching, lives in one place (say, a
  `PostRepository` that wraps another and is wired in `AppContainer`), not in a copy inside each
  class.
- Few places know about a data or service library (Firebase, Play Services location,
  ExifInterface): it sits behind classes or interfaces the app owns, shaped by what the app needs,
  and its types and exceptions stay inside the classes that wrap it.
- When the other side of a boundary isn't built yet, define the interface you wish you had and
  code against a fake of it, as `FakePostRepository` stood in for `PostRepository` until
  `FirestorePostRepository` was built.
- Before relying on an unfamiliar library, write learning tests that call it the way the app will,
  and keep them; they show what changed when the library is upgraded. One that needs a device, the
  network or Firebase is an instrumented test in `app/src/androidTest`, which `./gradlew build`
  doesn't run.

Tests:
- Test code meets the same standards as production code, KDoc aside. It may trade efficiency for
  readability, never cleanliness.
- A test builds its data, acts, then checks the result, with blank lines between the three (see
  `addPost_putsNewPostFirst` in `FakePostRepositoryTest`); an act that is one call can sit inside
  the assert. It covers one concept with as few asserts as it can. Setup noise moves into helpers
  named in the domain's terms.
- Tests are fast, independent of each other and of run order, repeatable anywhere without network
  or Firebase (use fakes), and pass or fail on their own without anyone reading output.
- Test everything that could break, including trivial cases and every boundary condition. For a
  bug, first write a test that reproduces it, then test that function thoroughly: bugs cluster.
- Use a coverage tool (Android Studio's Run with Coverage) to find untested code. Which tests fail
  together, and which lines never run, point at causes.

Concurrency:
- Keep concurrency (scopes, dispatchers, `launch`) apart from the logic it runs. Get that logic
  right and tested as plain functions first.
- Share as little mutable state as possible: immutable data classes changed with `copy()`, and
  coroutines that take their inputs as parameters and return results.
- Two calls that are each safe aren't safe in sequence: a check followed by an update of state that
  another thread can change, or with a suspension point between them, is a race. Update in one
  atomic step (`_uiState.update { it.copy(...) }`), or give the owner one function that does the
  whole operation under its `Mutex`. Callers never lock.
- Keep locked sections small, with no slow work like I/O inside, and never take a `Mutex` again
  inside its own locked section (it isn't reentrant). If two locks can't be avoided, always take
  them in the same order.
- Plan shutdown from the start: launch in a scope tied to a lifecycle (`viewModelScope`) and keep
  suspending code cancellable.
- Inject dispatchers and scopes so tests can run concurrent code on one thread or many, with fast
  or slow fakes, many times over.
- A test that fails once and then passes has found a real bug; suspect a race first. Find it
  instead of rerunning until it passes.
