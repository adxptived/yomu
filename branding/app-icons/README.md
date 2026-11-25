# App icon

The Yomu launcher icon: one 1254 ? 1254 drawing, also kept as the source `yomu.png`.

It becomes one adaptive icon in `app/src/main/res`:

- `drawable/ic_launcher_background.xml` - the near-black canvas under the artwork.
- `drawable-nodpi/ic_launcher_foreground.png` - the drawing on a 108 ? 108 dp layer; the launcher's mask keeps the
  72 dp safe zone, so the open book stays inside it.
- `drawable-nodpi/ic_launcher_monochrome.png` - the book and the glow as one white silhouette for the themed icons of
  Android 13+.
- `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` (the manifest's `android:icon`) - the adaptive icon.
