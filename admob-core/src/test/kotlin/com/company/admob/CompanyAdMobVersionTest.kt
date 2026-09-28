package com.company.admob

import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyAdMobVersionTest {
    @Test
    fun `version name is non-blank semver`() {
        val name = CompanyAdMobVersion.NAME
        assertTrue("Version must not be blank", name.isNotBlank())
        assertTrue("Version '$name' is not SemVer", Regex("""\d+\.\d+\.\d+(-rc\d+)?(-SNAPSHOT)?""").matches(name))
    }
}
