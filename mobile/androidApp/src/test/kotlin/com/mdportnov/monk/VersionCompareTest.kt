package com.mdportnov.monk

import com.mdportnov.monk.update.GitHubUpdater
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {
    @Test
    fun numericNotLexicographic() {
        assertTrue(GitHubUpdater.isNewer("1.10.0", "1.9.0"))
        assertTrue(GitHubUpdater.isNewer("2.0.0", "1.99.99"))
        assertFalse(GitHubUpdater.isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun devSuffixSortsBelowRelease() {
        assertTrue(GitHubUpdater.isNewer("1.2.3", "1.2.3-dev"))
        assertTrue(GitHubUpdater.isNewer("1.0.0", "0.0.0-dev"))
        assertFalse(GitHubUpdater.isNewer("1.2.3-dev", "1.2.3"))
    }

    @Test
    fun missingPartsAreZero() {
        assertFalse(GitHubUpdater.isNewer("1.2", "1.2.0"))
        assertTrue(GitHubUpdater.isNewer("1.2.1", "1.2"))
        assertTrue(GitHubUpdater.isNewer("0.1", ""))
    }
}
