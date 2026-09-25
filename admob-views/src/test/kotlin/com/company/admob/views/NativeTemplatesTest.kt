package com.company.admob.views

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.company.admob.NativeAdLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NativeTemplatesTest {
    private val templates = mapOf(
        "small" to NativeTemplates.Small,
        "medium" to NativeTemplates.Medium,
        "large" to NativeTemplates.Large,
    )

    private fun NativeAdLayout.assetIds(): List<Int> = listOf(
        headlineId, bodyId, callToActionId, iconId, mediaContainerId, advertiserId, starRatingId, priceId, storeId,
        attributionId,
    ).filter { it != 0 }

    private fun inflate(context: Context, layout: NativeAdLayout): View =
        LayoutInflater.from(context).inflate(layout.layoutRes, FrameLayout(context), false)

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `each template inflates and has every id its NativeAdLayout declares`() {
        for ((name, layout) in templates) {
            val root = inflate(activity(), layout)
            for (id in layout.assetIds()) {
                assertNotNull("$name: ${root.resources.getResourceEntryName(id)}", root.findViewById<View>(id))
            }
        }
        assertEquals(4, NativeTemplates.Small.assetIds().size)
        assertEquals(10, NativeTemplates.Medium.assetIds().size)
        assertEquals(10, NativeTemplates.Large.assetIds().size)
    }

    @Test
    fun `light and dark framework themes both inflate every template`() {
        for (theme in listOf(android.R.style.Theme_Material_Light, android.R.style.Theme_Material)) {
            val context = ContextThemeWrapper(activity(), theme)
            for ((_, layout) in templates) inflate(context, layout)
        }
    }

    @Test
    fun `RTL-safe - the templates use start and end, never left or right`() {
        val layouts = File("src/main/res/layout").listFiles { file -> file.name.startsWith("company_admob_native_") }!!
        assertEquals(3, layouts.size)
        // paddingLeft=, layout_marginRight=, layout_alignParentLeft=, gravity="left", gravity="center|right", ...
        val sided = Regex("""(Left|Right)[A-Za-z]*="|"(left|right)"|\|(left|right)\b|\b(left|right)\|""")
        for (file in layouts) {
            val offending = file.readLines().filter { sided.containsMatchIn(it) }
            assertTrue("${file.name}: $offending", offending.isEmpty())
        }
    }

    @Test
    fun `attribution says Ad, the CTA is a Button, the icon is decorative, media is described`() {
        for ((name, layout) in templates) {
            val root = inflate(activity(), layout)
            assertEquals(name, "Ad", root.findViewById<TextView>(layout.attributionId).text.toString())
            assertTrue(name, root.findViewById<View>(layout.callToActionId) is Button)
            assertEquals(name, View.IMPORTANT_FOR_ACCESSIBILITY_NO, root.findViewById<View>(layout.iconId).importantForAccessibility)
            if (layout.mediaContainerId != 0) {
                assertEquals(name, "Ad media", root.findViewById<View>(layout.mediaContainerId).contentDescription)
            }
        }
    }
}
