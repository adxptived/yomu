# App icons

The Yomu launcher icon in four colour variants: one 1024 × 1024 drawing, also rendered as a 4096 px square PNG
by the designer. `purple-dark` is the app's own icon; all four are offered in Settings → Appearance → App icon.

| Variant | Background | Head | Book |
|---|---|---|---|
| `purple-dark` (default) | `#1C142B` | `#A477EF` | `#E9DDFB` |
| `purple-light` | `#F3EDFC` | `#9254DE` | `#352052` |
| `orange-dark` | `#152033` | `#FF852E` | `#FFF3DD` |
| `orange-light` | `#FFF7E9` | `#F56A22` | `#18283E` |

Each variant becomes an adaptive icon in `app/src/main/res`:

- `drawable/ic_launcher_<variant>_{background,foreground,monochrome}.xml` — the layers on a 108 × 108 dp canvas,
  the artwork scaled by 108 / 1024, which puts it in the 66 dp safe zone and leaves room for the launcher's masks
  and parallax. `monochrome` is the head and the book as one white silhouette for the themed icons of Android 13+.
- `mipmap-anydpi/ic_launcher_<variant>.xml` — the adaptive icon.
- `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` (the manifest's `android:icon`) — `purple-dark`.
