package app.n_zik.compagnon.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone's reference density (story 11c decision): the phone's code hardcodes some values in physical
 * px (the wave and the scrubber of `SeekBarWaved`, among others), which are only right at its ~3× density.
 * On the PC they are converted with this density into dp, so they keep the phone's proportions exactly:
 * 15 px → 5 dp, 10 px → 3.33 dp, 5 px → 1.67 dp. No other scaling is applied.
 */
const val PHONE_REFERENCE_DENSITY = 3f

/** A phone value in physical px, in dp at [PHONE_REFERENCE_DENSITY]. */
fun phonePx(px: Float): Dp = (px / PHONE_REFERENCE_DENSITY).dp
