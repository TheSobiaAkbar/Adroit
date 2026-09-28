package com.company.admob.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeStyle
import kotlin.math.max

/**
 * The loading placeholder of a banner or native ad (PROMPT 29C): grey blocks shaped like the ad that will appear,
 * with a highlight sweeping across them. [showBanner] sizes it like a banner of a given height; [showNative] like a
 * native template. Colors come from `company_admob_shimmer_base` and `company_admob_shimmer_highlight` (with night
 * variants), which an app can override. The animation runs only while the view is attached and visible. For the
 * library's own views and composables; main thread only.
 */
@InternalCompanyAdMobApi
public class AdShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private sealed interface Shape {
        data class Banner(val heightPx: Int) : Shape
        data class Native(val style: NativeStyle) : Shape
    }

    private var shape: Shape = Shape.Native(NativeStyle.MEDIUM)
    private val density = resources.displayMetrics.density
    private val bones = mutableListOf<RectF>()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.company_admob_shimmer_base)
    }
    private val highlightColor = context.getColor(R.color.company_admob_shimmer_highlight)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shineMatrix = Matrix()
    private var progress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = SWEEP_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    /** Shapes it like a banner [heightPx] tall. Does nothing if it already is. */
    public fun showBanner(heightPx: Int) {
        if (shape == Shape.Banner(heightPx)) return
        shape = Shape.Banner(heightPx)
        requestLayout()
        invalidate()
    }

    /** Shapes it like the native template of [style]. Does nothing if it already is. */
    public fun showNative(style: NativeStyle) {
        if (shape == Shape.Native(style)) return
        shape = Shape.Native(style)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = when (val s = shape) {
            is Shape.Banner -> s.heightPx
            is Shape.Native -> dp(nativeHeightDp(s.style))
        }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutBones(w.toFloat(), h.toFloat())
        shinePaint.shader = LinearGradient(
            0f, 0f, w * SHINE_WIDTH, 0f,
            intArrayOf(0x00FFFFFF and highlightColor, highlightColor, 0x00FFFFFF and highlightColor),
            null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val radius = dp(4).toFloat()
        bones.forEach { canvas.drawRoundRect(it, radius, radius, basePaint) }
        // The highlight enters from the left edge and leaves past the right one.
        shineMatrix.setTranslate(-width * SHINE_WIDTH + progress * width * (1 + SHINE_WIDTH), 0f)
        shinePaint.shader?.setLocalMatrix(shineMatrix)
        bones.forEach { canvas.drawRoundRect(it, radius, radius, shinePaint) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        if (isAttachedToWindow && isShown) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    private fun layoutBones(w: Float, h: Float) {
        bones.clear()
        val pad = dp(8).toFloat()
        when (val s = shape) {
            is Shape.Banner -> when {
                h <= dp(64) -> compactRow(w, h, pad)
                h < dp(200) -> textBanner(w, h, pad)
                else -> tallBanner(w, h, pad)
            }
            is Shape.Native -> when (s.style) {
                NativeStyle.SMALL -> compactRow(w, h, pad)
                NativeStyle.MEDIUM -> {
                    var y = header(w, pad, pad, dp(40).toFloat())
                    bones += RectF(pad, y, w * 0.9f, y + dp(12)); y += dp(12) + pad
                    bones += RectF(pad, y, w - pad, y + dp(180)); y += dp(180) + pad
                    bones += RectF(pad, y, w - pad, y + dp(40))
                }
                NativeStyle.LARGE -> {
                    var y = pad
                    bones += RectF(pad, y, w - pad, y + dp(220)); y += dp(220) + pad
                    y = header(w, pad, y, dp(48).toFloat())
                    bones += RectF(pad, y, w * 0.9f, y + dp(12)); y += dp(12) + dp(6)
                    bones += RectF(pad, y, w * 0.7f, y + dp(12)); y += dp(12) + pad
                    bones += RectF(pad, y, w - pad, y + dp(40))
                }
            }
        }
    }

    /** Icon, two text lines and a button in one row: small native ads and ordinary banners. */
    private fun compactRow(w: Float, h: Float, pad: Float) {
        val icon = max(h - 2 * pad, 0f)
        val button = dp(64).toFloat()
        bones += RectF(pad, pad, pad + icon, pad + icon)
        val textStart = pad + icon + pad
        val textEnd = w - pad - button - pad
        val line = dp(12).toFloat()
        val top = h / 2 - line - dp(2)
        bones += RectF(textStart, top, textStart + (textEnd - textStart) * 0.8f, top + line)
        bones += RectF(textStart, top + line + dp(4), textStart + (textEnd - textStart) * 0.5f, top + 2 * line + dp(4))
        val buttonHeight = minOf(dp(32).toFloat(), h - 2 * pad)
        bones += RectF(w - pad - button, (h - buttonHeight) / 2, w - pad, (h + buttonHeight) / 2)
    }

    /** A large adaptive banner: a headline, two text lines and a button, like the ad itself. */
    private fun textBanner(w: Float, h: Float, pad: Float) {
        val button = dp(96).toFloat()
        val buttonHeight = dp(40).toFloat()
        val textEnd = w - pad - button - 2 * pad
        val block = dp(16) + dp(10) + dp(12) + dp(6) + dp(12)
        var y = (h - block) / 2
        bones += RectF(pad, y, pad + (textEnd - pad) * 0.8f, y + dp(16)); y += dp(16) + dp(10)
        bones += RectF(pad, y, textEnd, y + dp(12)); y += dp(12) + dp(6)
        bones += RectF(pad, y, pad + (textEnd - pad) * 0.6f, y + dp(12))
        bones += RectF(w - pad - button, (h - buttonHeight) / 2, w - pad, (h + buttonHeight) / 2)
    }

    /** A medium rectangle or inline banner: media, a text line and a button. */
    private fun tallBanner(w: Float, h: Float, pad: Float) {
        val button = dp(32).toFloat()
        val line = dp(12).toFloat()
        val mediaBottom = h - pad - button - pad - line - pad
        bones += RectF(pad, pad, w - pad, mediaBottom)
        bones += RectF(pad, mediaBottom + pad, w * 0.7f, mediaBottom + pad + line)
        bones += RectF(pad, h - pad - button, w - pad, h - pad)
    }

    /** An icon and two text lines from [top]; returns the y below them. */
    private fun header(w: Float, pad: Float, top: Float, icon: Float): Float {
        bones += RectF(pad, top, pad + icon, top + icon)
        val start = pad + icon + pad
        bones += RectF(start, top + dp(4), start + (w - start - pad) * 0.7f, top + dp(16))
        bones += RectF(start, top + dp(22), start + (w - start - pad) * 0.4f, top + dp(32))
        return top + icon + pad
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    /** Sizes for the library's views. */
    @InternalCompanyAdMobApi
    public companion object {
        private const val SWEEP_MILLIS = 1_200L

        /** A banner's usual height before its real one is known (adaptive banners need their width first), in dp. */
        public const val DEFAULT_BANNER_HEIGHT_DP: Int = 56

        /** The highlight's width, as a share of the view's. */
        private const val SHINE_WIDTH = 0.4f

        private fun nativeHeightDp(style: NativeStyle): Int = when (style) {
            NativeStyle.SMALL -> 72
            NativeStyle.MEDIUM -> 312
            NativeStyle.LARGE -> 378
        }
    }
}
