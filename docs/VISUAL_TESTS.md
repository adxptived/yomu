# Screenshot tests

The Robolectric screen tests capture PNGs into `app/build/outputs/roborazzi`. Those files are **review artifacts**: they
prove that a screen still renders and let a reviewer look at it, but comparing them with nothing cannot fail a build.

## Approved images (goldens)

`app/src/test/screenshots/` holds the approved images. They are copied into `app/build/outputs/roborazzi` before
verification, so `-Proborazzi.test.verify=true` compares the new render with the committed PNG at the same path.

The CI "Snapshot tests" step runs only when at least one golden is committed; until then it is skipped and only the
review artifacts are produced. After the goldens are added it becomes a gate: a broken spacing, a clipped label or an
overlapping button fails the job and the differing images are uploaded as `screenshot-diffs-*`.

### Recording the goldens

Record them **on the CI runner (Linux)**, not on a laptop: fonts and antialiasing of the rendering JVM differ between
Windows, macOS and Linux, and a golden recorded on another OS produces a diff on every run.

1. Run a workflow with the normal "Unit and Robolectric tests (records review screenshots)" step.
2. Download the `reports-java21` artifact and take `app/build/outputs/roborazzi/`.
3. Copy that tree to `app/src/test/screenshots/` and commit it.
4. Re-run CI: "Snapshot tests" now verifies the committed images.

To update a golden intentionally, change the screen, record again the same way and commit the new PNG in the same pull
request that changes the UI — the diff is then part of the review.

### What stays independent of the images

Behaviour is checked by assertions (semantics nodes, counts, navigation), not by the pictures. A screenshot failure
means "the pixels moved", not "the feature broke"; both kinds of check are kept separate on purpose.

The environment is pinned by the tests themselves (`@Config(sdk = [35])`, Robolectric's default density and locale), so
a golden only has to match the same SDK and the same Compose theme. Change the SDK or the default locale in a test and
its golden must be recorded again.
