package io.github.thesobiaakbar.adroit.internal.banner

import io.github.thesobiaakbar.adroit.BannerSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerSizeResolverTest {
    @Test
    fun `adaptive width comes from the container, in dp`() {
        assertEquals(
            BannerSizeSpec(BannerSize.AnchoredAdaptive, 360),
            BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 1_080, 3f, 2_000),
        )
        assertEquals(
            BannerSizeSpec(BannerSize.InlineAdaptive(90), 411),
            BannerSizeResolver.resolve(BannerSize.InlineAdaptive(90), 1_234, 3f, 2_000),
        )
    }

    @Test
    fun `dp are rounded down, so the ad never exceeds the container`() {
        assertEquals(393, BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 1_180, 3f, 0).widthDp)
        assertEquals(400, BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 1_050, 2.625f, 0).widthDp)
    }

    @Test
    fun `no container width - falls back to the window width`() {
        assertEquals(480, BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 0, 2f, 960).widthDp)
        assertEquals(0, BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 0, 2f, 0).widthDp)
        assertEquals(0, BannerSizeResolver.resolve(BannerSize.AnchoredAdaptive, 100, 0f, 960).widthDp)
    }

    @Test
    fun `fixed sizes ignore the widths`() {
        assertEquals(320, BannerSizeResolver.resolve(BannerSize.Standard, 1_080, 3f, 0).widthDp)
        assertEquals(300, BannerSizeResolver.resolve(BannerSize.MediumRectangle, 0, 1f, 0).widthDp)
    }

    @Test
    fun `reload threshold - 8 dp or more, adaptive only`() {
        val anchored = BannerSizeSpec(BannerSize.AnchoredAdaptive, 400)
        assertFalse(BannerSizeResolver.needsReload(anchored, 400))
        assertFalse(BannerSizeResolver.needsReload(anchored, 407))
        assertFalse(BannerSizeResolver.needsReload(anchored, 393))
        assertTrue(BannerSizeResolver.needsReload(anchored, 408))
        assertTrue(BannerSizeResolver.needsReload(anchored, 392))
        assertTrue(BannerSizeResolver.needsReload(BannerSizeSpec(BannerSize.InlineAdaptive(60), 400), 600))
        assertFalse("width unknown", BannerSizeResolver.needsReload(anchored, 0))
        assertFalse(BannerSizeResolver.needsReload(BannerSizeSpec(BannerSize.Standard, 320), 600))
        assertFalse(BannerSizeResolver.needsReload(BannerSizeSpec(BannerSize.MediumRectangle, 300), 600))
    }
}
