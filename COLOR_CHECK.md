# Color and Visibility Check (theme level)

Method: `ensureContrast()` in `core/theme/ColorMath.kt` pushes every text/icon colour until it reaches at least 4.7:1 on the
worst (brightest or darkest) surface it can sit on. The same algorithm was run in Python for all 30 combinations
(Light, Dark, AMOLED x 10 accents) with a 4.6 minimum as safety margin for rounding.

Result: 0 failures across 30 combinations for
onSurface, onSurfaceVariant, primary, secondary, tertiary, error (on all 7 surface levels), onPrimary/primary, onPrimaryContainer/primaryContainer,
onSecondary/secondary, onSecondaryContainer/secondaryContainer, onTertiary/tertiary, onTertiaryContainer/tertiaryContainer,
onError/error, onErrorContainer/errorContainer; outline (borders) 3:1 on all surfaces; primary fill vs card 3:1.

Lowest measured ratios: onPrimaryContainer 4.67 (dark Gold), all others higher.

Notes
- Divider colour (`outlineVariant`) is decorative and intentionally lighter than 3:1; component borders use `outline` (3:1 or more).
- Disabled controls use Material's standard reduced emphasis (WCAG exempts disabled controls).
- Selected containers in Light Gold / Orange / Cyan differ from unselected cards mostly by tint, not brightness; selected items also get a 2dp primary border or a filled primary background.
- Platform tags use white text on deep brand colours (all above 4.5:1) or the theme "on" colour on grey.
- Browser start tiles: the fallback letter uses the better of black or white for each brand colour.
- "Made by Anas": gradient stops are checked against the card behind them (4.5:1) in every theme.

## Added in v2.1 (checked with the same Python port)
- Announcement banner (5 colours): the fill is darkened until white text reaches 4.6:1, results 4.76 to 7.04 (amber becomes a deeper orange-brown to keep white text readable).
- Online status text (green): 4.76 (Dark) to 4.9 (Light) on the highest surface; the offline text uses the theme `onSurfaceVariant`.
- "Continue with Google" button: Google's own colours, 16.5:1 (light) and 14.5:1 (dark); border 5.8:1.
