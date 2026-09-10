// A colour ramp is named numbers; that is the whole file.
@file:Suppress("MagicNumber")

package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The four tones each accent palette contributes to the `*Fixed` roles.
 *
 * Declared once and shared by both schemes because a `*Fixed` role holds the same value in light
 * and dark — that is what "fixed" means, and making it structural is better than asserting it.
 *
 * Every one of these tones is already in use elsewhere in the schemes below: T90 is the light
 * container, T80 the dark accent, T10 the light on-container, T30 the dark container. So the
 * `*Fixed` roles introduce no new colour — they name tones the seed already produced. See
 * `docs/adr/0008`.
 */
private val primaryTone90 = Color(0xFFDFE0FF)
private val primaryTone80 = Color(0xFFBCC2FF)
private val primaryTone30 = Color(0xFF00429A)
private val primaryTone10 = Color(0xFF001944)
private val secondaryTone90 = Color(0xFFE1E1F1)
private val secondaryTone80 = Color(0xFFC5C5D4)
private val secondaryTone30 = Color(0xFF454652)
private val secondaryTone10 = Color(0xFF1A1B26)
private val tertiaryTone90 = Color(0xFFFFD8E8)
private val tertiaryTone80 = Color(0xFFFFAFD3)
private val tertiaryTone30 = Color(0xFF742D52)
private val tertiaryTone10 = Color(0xFF3D0024)

/**
 * The light palette, and the fallback used on every platform that cannot supply a dynamic one and
 * on Android below 12.
 *
 * Grown from the seed `#2D6BE4`. The tone declarations above are that seed's only committed
 * record, so changing them is an architecture decision — record it under `docs/adr/`, as
 * ADR-0008 records the `*Fixed` roles — rather than an edit. The tones are generated rather than picked:
 * each role sits at the Material 3 tone its slot calls for, on the CIELAB L* axis that Material's
 * HCT "tone" is defined as, holding the seed's hue and clipping chroma to the sRGB gamut.
 * `ColorSchemeTest` proves every text-bearing pair clears WCAG AA, which is the property that
 * actually matters — the generator is an implementation detail, the contrast is the contract.
 *
 * The error roles are Material 3's baseline error palette verbatim; a hand-derived red buys nothing
 * and risks a less legible one.
 *
 * Every role the factory accepts is set explicitly except `surfaceTint`, which Material derives
 * from the `primary` passed in. The `*Fixed` roles are set because Material does *not* derive them
 * — left alone they stay on the baseline lavender `#EADDFF`, which would draw a purple hero surface
 * in a blue app. `ColorSchemeTest` pins both facts.
 */
public val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF0058C9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = primaryTone90,
        onPrimaryContainer = primaryTone10,
        secondary = Color(0xFF5D5D6A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = secondaryTone90,
        onSecondaryContainer = secondaryTone10,
        tertiary = Color(0xFF8F466A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = tertiaryTone90,
        onTertiaryContainer = tertiaryTone10,
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF1A1B23),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF1A1B23),
        surfaceVariant = Color(0xFFE0E1F8),
        onSurfaceVariant = Color(0xFF444558),
        outline = Color(0xFF74768A),
        outlineVariant = Color(0xFFC4C5DB),
        scrim = Color(0xFF000004),
        inverseSurface = Color(0xFF2F3039),
        inverseOnSurface = Color(0xFFEFF0FB),
        inversePrimary = primaryTone80,
        surfaceDim = Color(0xFFD9D9E5),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2F3FE),
        surfaceContainer = Color(0xFFEDEDF9),
        surfaceContainerHigh = Color(0xFFE7E7F3),
        surfaceContainerHighest = Color(0xFFE1E2ED),
        primaryFixed = primaryTone90,
        primaryFixedDim = primaryTone80,
        onPrimaryFixed = primaryTone10,
        onPrimaryFixedVariant = primaryTone30,
        secondaryFixed = secondaryTone90,
        secondaryFixedDim = secondaryTone80,
        onSecondaryFixed = secondaryTone10,
        onSecondaryFixedVariant = secondaryTone30,
        tertiaryFixed = tertiaryTone90,
        tertiaryFixedDim = tertiaryTone80,
        onTertiaryFixed = tertiaryTone10,
        onTertiaryFixedVariant = tertiaryTone30,
    )

/** The dark counterpart of [LightColorScheme], grown from the same seed. */
public val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = primaryTone80,
        onPrimary = Color(0xFF002D6E),
        primaryContainer = primaryTone30,
        onPrimaryContainer = primaryTone90,
        secondary = secondaryTone80,
        onSecondary = Color(0xFF2F303B),
        secondaryContainer = secondaryTone30,
        onSecondaryContainer = secondaryTone90,
        tertiary = tertiaryTone80,
        onTertiary = Color(0xFF5A143B),
        tertiaryContainer = tertiaryTone30,
        onTertiaryContainer = tertiaryTone90,
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF12131B),
        onBackground = Color(0xFFE1E2ED),
        surface = Color(0xFF12131B),
        onSurface = Color(0xFFE1E2ED),
        surfaceVariant = Color(0xFF444558),
        onSurfaceVariant = Color(0xFFC4C5DB),
        outline = Color(0xFF8E8FA4),
        outlineVariant = Color(0xFF444558),
        scrim = Color(0xFF000004),
        inverseSurface = Color(0xFFE1E2ED),
        inverseOnSurface = Color(0xFF2F3039),
        inversePrimary = Color(0xFF0058C9),
        surfaceDim = Color(0xFF12131B),
        surfaceBright = Color(0xFF383841),
        surfaceContainerLowest = Color(0xFF0D0D17),
        surfaceContainerLow = Color(0xFF1A1B23),
        surfaceContainer = Color(0xFF1E1F27),
        surfaceContainerHigh = Color(0xFF292932),
        surfaceContainerHighest = Color(0xFF34343D),
        primaryFixed = primaryTone90,
        primaryFixedDim = primaryTone80,
        onPrimaryFixed = primaryTone10,
        onPrimaryFixedVariant = primaryTone30,
        secondaryFixed = secondaryTone90,
        secondaryFixedDim = secondaryTone80,
        onSecondaryFixed = secondaryTone10,
        onSecondaryFixedVariant = secondaryTone30,
        tertiaryFixed = tertiaryTone90,
        tertiaryFixedDim = tertiaryTone80,
        onTertiaryFixed = tertiaryTone10,
        onTertiaryFixedVariant = tertiaryTone30,
    )
