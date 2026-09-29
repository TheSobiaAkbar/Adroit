package com.company.admob.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdLayout
import com.company.admob.NativeStyle
import kotlin.math.max
import kotlin.math.min

/**
 * The loading placeholder of a banner or native ad: the whole ad area as a card, with grey blocks shaped like the ad
 * that will appear, and a highlight sweeping across both.
 *
 * - [showLayout] shapes it like a native layout, any layout: a library template or an app's own. It inflates the
 *   layout itself, never draws it, and draws a block over each of its views: one per line of text, one per image,
 *   button, badge and media area, at their real positions and sizes. So the shimmer is the ad's exact size, and a
 *   new layout needs nothing more.
 * - [showBanner] shapes it like a banner of a given height.
 *
 * Colors come from `company_admob_shimmer_card` (the card), `company_admob_shimmer_base` (the blocks) and
 * `company_admob_shimmer_highlight` (the sweep), with night variants, which an app can override. The animation runs
 * only while the view is attached and shown. For the library's own views and composables; main thread only.
 */
@InternalCompanyAdMobApi
public class AdShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private sealed interface Shape {
        data class Banner(val heightPx: Int) : Shape
        data class Layout(val layout: NativeAdLayout) : Shape
    }

    private class Bone(val rect: RectF, val radius: Float)

    private var shape: Shape? = null

    /** The inflated native layout: measured and laid out for its views' positions, never drawn. */
    private var skeleton: View? = null
    private val density = resources.displayMetrics.density

    /** One "dp" of the shimmer's own geometry: an eighth of its padding, which is 8dp on a 360dp phone. */
    private val sdpUnit = resources.getDimension(R.dimen.company_admob_shimmer_padding) / 8f

    /** The corners of the blocks. */
    private val radius = resources.getDimension(R.dimen.company_admob_shimmer_corner_radius)
    private val bones = mutableListOf<Bone>()
    private val card = RectF()
    private var cardRadius = 0f
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.company_admob_shimmer_card)
    }
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

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** Shapes it like a banner [heightPx] tall. Does nothing if it already is. */
    public fun showBanner(heightPx: Int) {
        if (shape == Shape.Banner(heightPx)) return
        dropSkeleton()
        shape = Shape.Banner(heightPx)
        requestLayout()
        invalidate()
    }

    /** Shapes it like the native template of [style]. Does nothing if it already is. */
    public fun showNative(style: NativeStyle) {
        showLayout(NativeTemplates.forStyle(style))
    }

    /**
     * Shapes it like [layout], a library template or an app's own: blocks over its views, at their real positions.
     * Does nothing if it already is.
     */
    public fun showLayout(layout: NativeAdLayout) {
        if (shape == Shape.Layout(layout)) return
        dropSkeleton()
        shape = Shape.Layout(layout)
        val view = try {
            LayoutInflater.from(context).inflate(layout.layoutRes, this, false)
        } catch (e: RuntimeException) {
            // A layout that can't inflate here (a custom view needing more): a plain card of a usual height.
            null
        }
        if (view != null) {
            fillPlaceholders(view, layout)
            addView(view)
            skeleton = view
        }
        requestLayout()
        invalidate()
    }

    private fun dropSkeleton() {
        skeleton?.let(::removeView)
        skeleton = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = shape
        if (s is Shape.Layout && skeleton != null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val height = when (s) {
            is Shape.Banner -> s.heightPx
            else -> sdp(FALLBACK_NATIVE_HEIGHT_DP)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutBones()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shinePaint.shader = LinearGradient(
            0f, 0f, w * SHINE_WIDTH, 0f,
            intArrayOf(0x00FFFFFF and highlightColor, highlightColor, 0x00FFFFFF and highlightColor),
            null, Shader.TileMode.CLAMP,
        )
    }

    // The skeleton's own drawing is skipped: only the card and the blocks show.
    override fun dispatchDraw(canvas: Canvas) {
        if (card.isEmpty) return
        canvas.drawRoundRect(card, cardRadius, cardRadius, cardPaint)
        bones.forEach { canvas.drawRoundRect(it.rect, it.radius, it.radius, basePaint) }
        // The highlight enters from the left edge and leaves past the right one, over the card and its blocks.
        shineMatrix.setTranslate(-width * SHINE_WIDTH + progress * width * (1 + SHINE_WIDTH), 0f)
        shinePaint.shader?.setLocalMatrix(shineMatrix)
        canvas.drawRoundRect(card, cardRadius, cardRadius, shinePaint)
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

    private fun layoutBones() {
        bones.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = resources.getDimension(R.dimen.company_admob_shimmer_padding)
        when (val s = shape) {
            is Shape.Banner -> {
                card.set(0f, 0f, w, h)
                cardRadius = 0f
                when {
                    h <= dp(64) -> compactRow(w, h, pad)
                    h < dp(200) -> textBanner(w, h, pad)
                    else -> tallBanner(w, h, pad)
                }
            }
            is Shape.Layout -> {
                cardRadius = resources.getDimension(R.dimen.company_admob_native_card_corner_radius)
                val root = skeleton
                if (root == null) {
                    card.set(0f, 0f, w, h)
                    return
                }
                card.set(root.left.toFloat(), root.top.toFloat(), root.right.toFloat(), root.bottom.toFloat())
                collectBones(root, s.layout)
            }
            null -> card.setEmpty()
        }
    }

    /** A block per visible view of [view]'s tree: per text line, or over the whole view. */
    private fun collectBones(view: View, layout: NativeAdLayout) {
        if (view.visibility != VISIBLE || view.width == 0 || view.height == 0) return
        val id = view.id
        when {
            id != NO_ID && id == layout.mediaContainerId -> addBone(view, radius * 1.5f)
            view is ViewGroup -> for (i in 0 until view.childCount) collectBones(view.getChildAt(i), layout)
            view is TextView && !isSolid(view, layout) -> addTextLines(view)
            else -> addBone(view, min(radius * 1.5f, view.height / 2f))
        }
    }

    /** A button, badge or any text on a background of its own: one block over it all. */
    private fun isSolid(view: TextView, layout: NativeAdLayout): Boolean =
        view.id == layout.callToActionId || view.id == layout.attributionId || view.background != null

    private fun addBone(view: View, radius: Float) {
        val r = Rect(0, 0, view.width, view.height)
        offsetDescendantRectToMyCoords(view, r)
        bones += Bone(RectF(r), radius)
    }

    /** One block per laid-out line of [view], as wide as its text and a little shorter than the line. */
    private fun addTextLines(view: TextView) {
        val text = view.layout
        if (text == null || text.lineCount == 0) {
            addBone(view, radius)
            return
        }
        val origin = Rect(0, 0, 0, 0)
        offsetDescendantRectToMyCoords(view, origin)
        val left = origin.left + view.compoundPaddingLeft
        val top = origin.top + view.totalPaddingTop
        val maxRight = origin.left + view.width - view.compoundPaddingRight
        for (line in 0 until text.lineCount) {
            val lineTop = top + text.getLineTop(line)
            val lineBottom = top + text.getLineBottom(line)
            val inset = (lineBottom - lineTop) * 0.2f
            val start = left + text.getLineLeft(line)
            val end = min(left + max(text.getLineRight(line), text.getLineLeft(line) + sdp(24)), maxRight.toFloat())
            bones += Bone(RectF(start, lineTop + inset, end, lineBottom - inset), radius)
        }
    }

    /** Icon, two text lines and a button in one row: ordinary banners. */
    private fun compactRow(w: Float, h: Float, pad: Float) {
        val icon = max(h - 2 * pad, 0f)
        val button = sdp(64).toFloat()
        bones += Bone(RectF(pad, pad, pad + icon, pad + icon), radius)
        val textStart = pad + icon + pad
        val textEnd = w - pad - button - pad
        val line = sdp(12).toFloat()
        val top = h / 2 - line - sdp(2)
        bones += Bone(RectF(textStart, top, textStart + (textEnd - textStart) * 0.8f, top + line), radius)
        bones += Bone(
            RectF(textStart, top + line + sdp(4), textStart + (textEnd - textStart) * 0.5f, top + 2 * line + sdp(4)),
            radius,
        )
        val buttonHeight = minOf(sdp(32).toFloat(), h - 2 * pad)
        bones += Bone(RectF(w - pad - button, (h - buttonHeight) / 2, w - pad, (h + buttonHeight) / 2), radius)
    }

    /** A large adaptive banner: a headline, two text lines and a button, like the ad itself. */
    private fun textBanner(w: Float, h: Float, pad: Float) {
        val button = sdp(96).toFloat()
        val buttonHeight = sdp(40).toFloat()
        val textEnd = w - pad - button - 2 * pad
        val block = sdp(16) + sdp(10) + sdp(12) + sdp(6) + sdp(12)
        var y = (h - block) / 2
        val r = radius
        bones += Bone(RectF(pad, y, pad + (textEnd - pad) * 0.8f, y + sdp(16)), r); y += sdp(16) + sdp(10)
        bones += Bone(RectF(pad, y, textEnd, y + sdp(12)), r); y += sdp(12) + sdp(6)
        bones += Bone(RectF(pad, y, pad + (textEnd - pad) * 0.6f, y + sdp(12)), r)
        bones += Bone(RectF(w - pad - button, (h - buttonHeight) / 2, w - pad, (h + buttonHeight) / 2), r)
    }

    /** A medium rectangle or inline banner: media, a text line and a button. */
    private fun tallBanner(w: Float, h: Float, pad: Float) {
        val button = sdp(32).toFloat()
        val line = sdp(12).toFloat()
        val mediaBottom = h - pad - button - pad - line - pad
        val r = radius
        bones += Bone(RectF(pad, pad, w - pad, mediaBottom), r)
        bones += Bone(RectF(pad, mediaBottom + pad, w * 0.7f, mediaBottom + pad + line), r)
        bones += Bone(RectF(pad, h - pad - button, w - pad, h - pad), r)
    }

    /** [value] in real dp: for banner heights, which the SDK sets in dp. */
    private fun dp(value: Int): Int = (value * density).toInt()

    /**
     * [value] "dp" scaled like the templates' sdp sizes (and like an app's override of the shimmer padding), so the
     * blocks keep their proportions on every screen width.
     */
    private fun sdp(value: Int): Int = (value * sdpUnit).toInt()

    /**
     * Gives the skeleton the content a typical ad has, so it lays out like one: a one-line headline, a two-line body,
     * a button label, an icon and media of a usual size. Assets most ads lack (advertiser, stars, price, store) are
     * left out.
     */
    private fun fillPlaceholders(root: View, layout: NativeAdLayout) {
        fun <T : View> find(id: Int): T? = if (id == 0) null else root.findViewById(id)
        find<TextView>(layout.headlineId)?.text = PLACEHOLDER_HEADLINE
        find<TextView>(layout.bodyId)?.text = PLACEHOLDER_BODY
        find<TextView>(layout.callToActionId)?.let { if (it.text.isNullOrEmpty()) it.text = PLACEHOLDER_CTA }
        find<View>(layout.iconId)?.let { icon ->
            val lp = icon.layoutParams
            if (lp != null && (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT || lp.height == ViewGroup.LayoutParams.WRAP_CONTENT)) {
                icon.minimumWidth = sdp(40)
                icon.minimumHeight = sdp(40)
                (icon as? ImageView)?.adjustViewBounds = false
            }
        }
        find<View>(layout.mediaContainerId)?.let { media ->
            if (media.layoutParams?.height == ViewGroup.LayoutParams.WRAP_CONTENT && media.minimumHeight == 0) {
                media.minimumHeight = sdp(FALLBACK_MEDIA_HEIGHT_DP)
            }
        }
        for (id in listOf(layout.advertiserId, layout.starRatingId, layout.priceId, layout.storeId)) {
            find<View>(id)?.visibility = GONE
        }
    }

    /** Sizes for the library's views. */
    @InternalCompanyAdMobApi
    public companion object {
        private const val SWEEP_MILLIS = 1_200L

        /** A banner's usual height before its real one is known (adaptive banners need their width first), in dp. */
        public const val DEFAULT_BANNER_HEIGHT_DP: Int = 56

        /** The highlight's width, as a share of the view's. */
        private const val SHINE_WIDTH = 0.4f

        private const val FALLBACK_NATIVE_HEIGHT_DP = 120
        private const val FALLBACK_MEDIA_HEIGHT_DP = 160
        private const val PLACEHOLDER_HEADLINE = "Sponsored app headline"
        private const val PLACEHOLDER_BODY =
            "A short description of the advertised app or product, long enough for two lines of text in most layouts."
        private const val PLACEHOLDER_CTA = "Install"
    }
}
