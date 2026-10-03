package app.n_zik.compagnon.core.palette

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Pure-Kotlin port of the parts of `androidx.palette.graphics.Palette` (Apache-2.0) and
 * `androidx.core.graphics.ColorUtils` the phone uses to read a cover's colours
 * (`Palette.from(bitmap).maximumColorCount(8).generate()`, `dominantSwatch`, `swatches`, `get*Color`,
 * `ColorUtils.colorToHSL`). Desktop Compose has no `androidx.palette`; the algorithm (5-bit colour
 * quantization, median cut, default filter, the six default targets and their scoring) is the library's,
 * on ARGB pixels instead of an Android `Bitmap`.
 */

/** Port of `androidx.core.graphics.ColorUtils` (the HSL conversions the phone uses). */
object ColorUtils {

    /** `ColorUtils.RGBToHSL`. */
    fun rgbToHsl(r: Int, g: Int, b: Int, outHsl: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = max(rf, max(gf, bf))
        val min = min(rf, min(gf, bf))
        val deltaMaxMin = max - min

        var h: Float
        val s: Float
        val l = (max + min) / 2f

        if (max == min) {
            // Monochromatic
            h = 0f
            s = 0f
        } else {
            h = when (max) {
                rf -> ((gf - bf) / deltaMaxMin) % 6f
                gf -> ((bf - rf) / deltaMaxMin) + 2f
                else -> ((rf - gf) / deltaMaxMin) + 4f
            }
            s = deltaMaxMin / (1f - abs(2f * l - 1f))
        }

        h = (h * 60f) % 360f
        if (h < 0) h += 360f

        outHsl[0] = h.coerceIn(0f, 360f)
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
    }

    /** `ColorUtils.colorToHSL`. */
    fun colorToHSL(color: Int, outHsl: FloatArray) = rgbToHsl(red(color), green(color), blue(color), outHsl)

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun rgb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

/** Port of `Palette.Target`: the lightness / saturation window and weights of a swatch role. */
class Target private constructor(
    val minSaturation: Float, val targetSaturation: Float, val maxSaturation: Float,
    val minLightness: Float, val targetLightness: Float, val maxLightness: Float,
) {
    private val weights = floatArrayOf(WEIGHT_SATURATION, WEIGHT_LUMA, WEIGHT_POPULATION)
    val isExclusive: Boolean = true

    val saturationWeight: Float get() = weights[0]
    val lightnessWeight: Float get() = weights[1]
    val populationWeight: Float get() = weights[2]

    internal fun normalizeWeights() {
        var sum = 0f
        for (weight in weights) if (weight > 0) sum += weight
        if (sum != 0f) {
            for (i in weights.indices) if (weights[i] > 0) weights[i] /= sum
        }
    }

    companion object {
        private const val TARGET_DARK_LUMA = 0.26f
        private const val MAX_DARK_LUMA = 0.45f
        private const val MIN_LIGHT_LUMA = 0.55f
        private const val TARGET_LIGHT_LUMA = 0.74f
        private const val MIN_NORMAL_LUMA = 0.3f
        private const val TARGET_NORMAL_LUMA = 0.5f
        private const val MAX_NORMAL_LUMA = 0.7f
        private const val TARGET_MUTED_SATURATION = 0.3f
        private const val MAX_MUTED_SATURATION = 0.4f
        private const val TARGET_VIBRANT_SATURATION = 1f
        private const val MIN_VIBRANT_SATURATION = 0.35f
        private const val WEIGHT_SATURATION = 0.24f
        private const val WEIGHT_LUMA = 0.52f
        private const val WEIGHT_POPULATION = 0.24f

        private fun light(minSat: Float, targetSat: Float, maxSat: Float) =
            Target(minSat, targetSat, maxSat, MIN_LIGHT_LUMA, TARGET_LIGHT_LUMA, 1f)

        private fun normal(minSat: Float, targetSat: Float, maxSat: Float) =
            Target(minSat, targetSat, maxSat, MIN_NORMAL_LUMA, TARGET_NORMAL_LUMA, MAX_NORMAL_LUMA)

        private fun dark(minSat: Float, targetSat: Float, maxSat: Float) =
            Target(minSat, targetSat, maxSat, 0f, TARGET_DARK_LUMA, MAX_DARK_LUMA)

        val LIGHT_VIBRANT = light(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f)
        val VIBRANT = normal(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f)
        val DARK_VIBRANT = dark(MIN_VIBRANT_SATURATION, TARGET_VIBRANT_SATURATION, 1f)
        val LIGHT_MUTED = light(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION)
        val MUTED = normal(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION)
        val DARK_MUTED = dark(0f, TARGET_MUTED_SATURATION, MAX_MUTED_SATURATION)
    }
}

/**
 * Port of `androidx.palette.graphics.Palette`: [Palette.from] takes the ARGB pixels of an image
 * (row-major, [width] × [height]), [Builder.generate] runs the library's extraction.
 */
class Palette private constructor(val swatches: List<Swatch>) {

    /** Port of `Palette.Swatch`: a colour ([rgb], opaque ARGB) and how many pixels it stands for. */
    class Swatch(rgb: Int, val population: Int) {
        val rgb: Int = rgb or (0xFF shl 24)
        val hsl: FloatArray by lazy { FloatArray(3).also { ColorUtils.colorToHSL(this.rgb, it) } }
    }

    private val targets = listOf(
        Target.LIGHT_VIBRANT, Target.VIBRANT, Target.DARK_VIBRANT,
        Target.LIGHT_MUTED, Target.MUTED, Target.DARK_MUTED,
    )
    private val selectedSwatches = HashMap<Target, Swatch?>()
    private val usedColors = HashSet<Int>()

    /** The swatch with the most pixels, `null` when the image gave no colour. */
    val dominantSwatch: Swatch? = swatches.maxByOrNull { it.population }

    private fun generate() {
        for (target in targets) {
            target.normalizeWeights()
            selectedSwatches[target] = generateScoredTarget(target)
        }
        usedColors.clear()
    }

    private fun generateScoredTarget(target: Target): Swatch? {
        val maxScoreSwatch = getMaxScoredSwatchForTarget(target)
        if (maxScoreSwatch != null && target.isExclusive) usedColors.add(maxScoreSwatch.rgb)
        return maxScoreSwatch
    }

    private fun getMaxScoredSwatchForTarget(target: Target): Swatch? {
        var maxScore = 0f
        var maxScoreSwatch: Swatch? = null
        for (swatch in swatches) {
            if (shouldBeScoredForTarget(swatch, target)) {
                val score = generateScore(swatch, target)
                if (maxScoreSwatch == null || score > maxScore) {
                    maxScoreSwatch = swatch
                    maxScore = score
                }
            }
        }
        return maxScoreSwatch
    }

    private fun shouldBeScoredForTarget(swatch: Swatch, target: Target): Boolean {
        val hsl = swatch.hsl
        return hsl[1] >= target.minSaturation && hsl[1] <= target.maxSaturation &&
            hsl[2] >= target.minLightness && hsl[2] <= target.maxLightness &&
            swatch.rgb !in usedColors
    }

    private fun generateScore(swatch: Swatch, target: Target): Float {
        val hsl = swatch.hsl
        val maxPopulation = dominantSwatch?.population ?: 1
        val saturationScore = if (target.saturationWeight > 0) {
            target.saturationWeight * (1f - abs(hsl[1] - target.targetSaturation))
        } else 0f
        val luminanceScore = if (target.lightnessWeight > 0) {
            target.lightnessWeight * (1f - abs(hsl[2] - target.targetLightness))
        } else 0f
        val populationScore = if (target.populationWeight > 0) {
            target.populationWeight * (swatch.population / maxPopulation.toFloat())
        } else 0f
        return saturationScore + luminanceScore + populationScore
    }

    private fun colorFor(target: Target, defaultColor: Int): Int = selectedSwatches[target]?.rgb ?: defaultColor

    fun getDominantColor(defaultColor: Int): Int = dominantSwatch?.rgb ?: defaultColor
    fun getVibrantColor(defaultColor: Int): Int = colorFor(Target.VIBRANT, defaultColor)
    fun getLightVibrantColor(defaultColor: Int): Int = colorFor(Target.LIGHT_VIBRANT, defaultColor)
    fun getDarkVibrantColor(defaultColor: Int): Int = colorFor(Target.DARK_VIBRANT, defaultColor)
    fun getMutedColor(defaultColor: Int): Int = colorFor(Target.MUTED, defaultColor)
    fun getLightMutedColor(defaultColor: Int): Int = colorFor(Target.LIGHT_MUTED, defaultColor)
    fun getDarkMutedColor(defaultColor: Int): Int = colorFor(Target.DARK_MUTED, defaultColor)

    /** Port of `Palette.Builder` (`maximumColorCount`, default resize area of 112 × 112, default filter). */
    class Builder internal constructor(private val pixels: IntArray, private val width: Int, private val height: Int) {
        private var maxColors = DEFAULT_CALCULATE_NUMBER_COLORS

        fun maximumColorCount(colors: Int): Builder = apply { maxColors = colors }

        fun generate(): Palette {
            val scaled = scaleBitmapDown(pixels, width, height)
            val quantizer = ColorCutQuantizer(scaled, maxColors)
            return Palette(quantizer.quantizedColors).also { it.generate() }
        }
    }

    companion object {
        internal const val DEFAULT_RESIZE_BITMAP_AREA = 112 * 112
        internal const val DEFAULT_CALCULATE_NUMBER_COLORS = 16

        fun from(pixels: IntArray, width: Int, height: Int): Builder {
            require(width > 0 && height > 0 && pixels.size >= width * height) { "Invalid image" }
            return Builder(pixels, width, height)
        }

        /**
         * `Palette.Builder.scaleBitmapDown`: above 112 × 112 pixels the image is scaled down to that area
         * (`Bitmap.createScaledBitmap(…, filter = false)`, a nearest-neighbour sampling).
         */
        internal fun scaleBitmapDown(pixels: IntArray, width: Int, height: Int): IntArray {
            val bitmapArea = width * height
            if (bitmapArea <= DEFAULT_RESIZE_BITMAP_AREA) return pixels.copyOf(bitmapArea)
            val scaleRatio = sqrt(DEFAULT_RESIZE_BITMAP_AREA / bitmapArea.toDouble())
            val newWidth = max(1, ceil(width * scaleRatio).toInt())
            val newHeight = max(1, ceil(height * scaleRatio).toInt())
            return IntArray(newWidth * newHeight) { index ->
                val x = index % newWidth
                val y = index / newWidth
                val sourceX = min(width - 1, (x * width) / newWidth)
                val sourceY = min(height - 1, (y * height) / newHeight)
                pixels[sourceY * width + sourceX]
            }
        }
    }
}

/** Port of `androidx.palette.graphics.ColorCutQuantizer`: 5-bit histogram then median cut. */
internal class ColorCutQuantizer(pixels: IntArray, maxColors: Int) {
    private val histogram = IntArray(1 shl (QUANTIZE_WORD_WIDTH * 3))
    private val colors: IntArray
    val quantizedColors: List<Palette.Swatch>
    private val tempHsl = FloatArray(3)

    init {
        for (i in pixels.indices) {
            val quantizedColor = quantizeFromRgb888(pixels[i])
            pixels[i] = quantizedColor
            histogram[quantizedColor]++
        }

        var distinctColorCount = 0
        for (color in histogram.indices) {
            if (histogram[color] > 0 && shouldIgnoreColor(color)) histogram[color] = 0
            if (histogram[color] > 0) distinctColorCount++
        }

        colors = IntArray(distinctColorCount)
        var distinctColorIndex = 0
        for (color in histogram.indices) {
            if (histogram[color] > 0) colors[distinctColorIndex++] = color
        }

        quantizedColors = if (distinctColorCount <= maxColors) {
            colors.map { Palette.Swatch(approximateToRgb888(it), histogram[it]) }
        } else {
            quantizePixels(maxColors)
        }
    }

    private fun quantizePixels(maxColors: Int): List<Palette.Swatch> {
        val pq = PriorityQueue<Vbox>(maxColors) { lhs, rhs -> rhs.volume - lhs.volume }
        pq.offer(Vbox(0, colors.size - 1))
        splitBoxes(pq, maxColors)
        return generateAverageColors(pq)
    }

    private fun splitBoxes(queue: PriorityQueue<Vbox>, maxSize: Int) {
        while (queue.size < maxSize) {
            val vbox = queue.poll()
            if (vbox != null && vbox.canSplit()) {
                queue.offer(vbox.splitBox())
                queue.offer(vbox)
            } else {
                return
            }
        }
    }

    private fun generateAverageColors(vboxes: Collection<Vbox>): List<Palette.Swatch> =
        vboxes.map { it.averageColor }.filterNot(::shouldIgnoreColor)

    private inner class Vbox(private val lowerIndex: Int, private var upperIndex: Int) {
        private var population = 0
        private var minRed = 0
        private var maxRed = 0
        private var minGreen = 0
        private var maxGreen = 0
        private var minBlue = 0
        private var maxBlue = 0

        init {
            fitBox()
        }

        val volume: Int
            get() = (maxRed - minRed + 1) * (maxGreen - minGreen + 1) * (maxBlue - minBlue + 1)

        fun canSplit(): Boolean = colorCount > 1

        val colorCount: Int get() = 1 + upperIndex - lowerIndex

        fun fitBox() {
            var minR = Int.MAX_VALUE
            var minG = Int.MAX_VALUE
            var minB = Int.MAX_VALUE
            var maxR = Int.MIN_VALUE
            var maxG = Int.MIN_VALUE
            var maxB = Int.MIN_VALUE
            var count = 0
            for (i in lowerIndex..upperIndex) {
                val color = colors[i]
                count += histogram[color]
                val r = quantizedRed(color)
                val g = quantizedGreen(color)
                val b = quantizedBlue(color)
                if (r > maxR) maxR = r
                if (r < minR) minR = r
                if (g > maxG) maxG = g
                if (g < minG) minG = g
                if (b > maxB) maxB = b
                if (b < minB) minB = b
            }
            minRed = minR
            maxRed = maxR
            minGreen = minG
            maxGreen = maxG
            minBlue = minB
            maxBlue = maxB
            population = count
        }

        fun splitBox(): Vbox {
            check(canSplit()) { "Can not split a box with only 1 color" }
            val splitPoint = findSplitPoint()
            val newBox = Vbox(splitPoint + 1, upperIndex)
            upperIndex = splitPoint
            fitBox()
            return newBox
        }

        private val longestColorDimension: Int
            get() {
                val redLength = maxRed - minRed
                val greenLength = maxGreen - minGreen
                val blueLength = maxBlue - minBlue
                return if (redLength >= greenLength && redLength >= blueLength) {
                    COMPONENT_RED
                } else if (greenLength >= redLength && greenLength >= blueLength) {
                    COMPONENT_GREEN
                } else {
                    COMPONENT_BLUE
                }
            }

        private fun findSplitPoint(): Int {
            val longestDimension = longestColorDimension
            modifySignificantOctet(colors, longestDimension, lowerIndex, upperIndex)
            colors.sort(lowerIndex, upperIndex + 1)
            modifySignificantOctet(colors, longestDimension, lowerIndex, upperIndex)

            val midPoint = population / 2
            var count = 0
            for (i in lowerIndex..upperIndex) {
                count += histogram[colors[i]]
                if (count >= midPoint) return min(upperIndex - 1, i)
            }
            return lowerIndex
        }

        val averageColor: Palette.Swatch
            get() {
                var redSum = 0
                var greenSum = 0
                var blueSum = 0
                var totalPopulation = 0
                for (i in lowerIndex..upperIndex) {
                    val color = colors[i]
                    val colorPopulation = histogram[color]
                    totalPopulation += colorPopulation
                    redSum += colorPopulation * quantizedRed(color)
                    greenSum += colorPopulation * quantizedGreen(color)
                    blueSum += colorPopulation * quantizedBlue(color)
                }
                val redMean = (redSum / totalPopulation.toFloat()).roundToInt()
                val greenMean = (greenSum / totalPopulation.toFloat()).roundToInt()
                val blueMean = (blueSum / totalPopulation.toFloat()).roundToInt()
                return Palette.Swatch(approximateToRgb888(redMean, greenMean, blueMean), totalPopulation)
            }
    }

    private fun shouldIgnoreColor(color565: Int): Boolean {
        val rgb = approximateToRgb888(color565)
        ColorUtils.colorToHSL(rgb, tempHsl)
        return shouldIgnoreColor(tempHsl)
    }

    private fun shouldIgnoreColor(swatch: Palette.Swatch): Boolean = shouldIgnoreColor(swatch.hsl)

    /** `Palette.DEFAULT_FILTER`: no near-black, no near-white, no colour near the red I line. */
    private fun shouldIgnoreColor(hsl: FloatArray): Boolean {
        val isBlack = hsl[2] <= BLACK_MAX_LIGHTNESS
        val isWhite = hsl[2] >= WHITE_MIN_LIGHTNESS
        val isNearRedILine = hsl[0] in 10f..37f && hsl[1] <= 0.82f
        return isBlack || isWhite || isNearRedILine
    }

    companion object {
        const val COMPONENT_RED = -3
        const val COMPONENT_GREEN = -2
        const val COMPONENT_BLUE = -1
        private const val QUANTIZE_WORD_WIDTH = 5
        private const val QUANTIZE_WORD_MASK = (1 shl QUANTIZE_WORD_WIDTH) - 1
        private const val BLACK_MAX_LIGHTNESS = 0.05f
        private const val WHITE_MIN_LIGHTNESS = 0.95f

        fun modifySignificantOctet(a: IntArray, dimension: Int, lower: Int, upper: Int) {
            when (dimension) {
                COMPONENT_RED -> Unit
                COMPONENT_GREEN -> for (i in lower..upper) {
                    val color = a[i]
                    a[i] = (quantizedGreen(color) shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or
                        (quantizedRed(color) shl QUANTIZE_WORD_WIDTH) or quantizedBlue(color)
                }
                COMPONENT_BLUE -> for (i in lower..upper) {
                    val color = a[i]
                    a[i] = (quantizedBlue(color) shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or
                        (quantizedGreen(color) shl QUANTIZE_WORD_WIDTH) or quantizedRed(color)
                }
            }
        }

        fun quantizeFromRgb888(color: Int): Int {
            val r = modifyWordWidth(ColorUtils.red(color), 8, QUANTIZE_WORD_WIDTH)
            val g = modifyWordWidth(ColorUtils.green(color), 8, QUANTIZE_WORD_WIDTH)
            val b = modifyWordWidth(ColorUtils.blue(color), 8, QUANTIZE_WORD_WIDTH)
            return (r shl (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) or (g shl QUANTIZE_WORD_WIDTH) or b
        }

        fun approximateToRgb888(r: Int, g: Int, b: Int): Int = ColorUtils.rgb(
            modifyWordWidth(r, QUANTIZE_WORD_WIDTH, 8),
            modifyWordWidth(g, QUANTIZE_WORD_WIDTH, 8),
            modifyWordWidth(b, QUANTIZE_WORD_WIDTH, 8),
        )

        fun approximateToRgb888(color: Int): Int =
            approximateToRgb888(quantizedRed(color), quantizedGreen(color), quantizedBlue(color))

        fun quantizedRed(color: Int): Int = (color shr (QUANTIZE_WORD_WIDTH + QUANTIZE_WORD_WIDTH)) and QUANTIZE_WORD_MASK
        fun quantizedGreen(color: Int): Int = (color shr QUANTIZE_WORD_WIDTH) and QUANTIZE_WORD_MASK
        fun quantizedBlue(color: Int): Int = color and QUANTIZE_WORD_MASK

        private fun modifyWordWidth(value: Int, currentWidth: Int, targetWidth: Int): Int {
            val newValue = if (targetWidth > currentWidth) {
                value shl (targetWidth - currentWidth)
            } else {
                value shr (currentWidth - targetWidth)
            }
            return newValue and ((1 shl targetWidth) - 1)
        }
    }
}
