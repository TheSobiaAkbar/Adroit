package io.github.thesobiaakbar.adroit

import org.junit.Assert.assertTrue
import org.junit.Test

class AdroitVersionTest {
    @Test
    fun `version name is non-blank semver`() {
        val name = AdroitVersion.NAME
        assertTrue("Version must not be blank", name.isNotBlank())
        assertTrue("Version '$name' is not SemVer", Regex("""\d+\.\d+\.\d+(-rc\d+)?(-SNAPSHOT)?""").matches(name))
    }
}
