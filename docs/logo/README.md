# Logo: shop awning + check mark

No letters and no currency sign. Colours: green `#0F7B4F`, white, pale stripes `#DDF3E8`, gold `#F2B84B`.

| File | What |
|---|---|
| `shop-logo.svg` | The source drawing, 512 x 512 canvas (rounded square, radius 112, is only for the Play preview). |
| `play-store-512.png` | The 512 x 512 Google Play icon, rendered from `shop-logo.svg` with resvg (a PC-only tool, not part of the app). |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Adaptive icon, background layer: solid `#0F7B4F`. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Foreground layer and the Android 12+ splash mark: the same paths as the SVG, scaled by 0.17 and centred in the 66dp safe zone. |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon layer: awning with the stripes cut out, valance, body as an outline, check mark. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` | The adaptive icon (round and normal) joining the three layers. |

## Geometry (512 canvas)

- Awning: white polygon (92,176) (128,100) (384,100) (420,176); stripes (164,100) (228,100) (212,176) (148,176) and
  (284,100) (348,100) (364,176) (300,176); white bar x 92..420, y 176..192.
- Valance: four half-circles r = 41 centred at x = 133, 215, 297, 379 on y = 192.
- Shop body: white rounded rectangle x 124, y 236, 264 x 180, radius 18.
- Check mark: stroke `#0F7B4F`, width 28, round caps and joins: (200,330) to (242,372) to (318,284).

To change the logo: edit `shop-logo.svg`, copy the same numbers into the two drawables (the group scale/translate
there maps the 512 canvas to the 108dp icon), and re-render the PNG.
