package com.frankov_dev.khmlight

import com.frankov_dev.khmlight.data.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.2", "1.1.1"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.2.0", "1.1.1"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.1.2", "1.1.1"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.10.0", "1.2.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0", "1.9.9"))

        assertFalse(AppUpdateChecker.isNewerVersion("1.1.1", "1.1.1"))
        assertFalse(AppUpdateChecker.isNewerVersion("v1.1.1", "1.1.1"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.0", "1.1"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.0", "1.1.1"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.0", "1.1.1"))
    }

    @Test
    fun stripsPreReleaseSuffixesWhenComparing() {
        assertTrue(AppUpdateChecker.isNewerVersion("v1.2.0-beta1", "1.1.1"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.1.1-hotfix", "1.1.1"))
    }

    @Test
    fun formatsApkFileSizeHumanReadably() {
        assertEquals("", AppUpdateChecker.formatFileSize(null))
        assertEquals("", AppUpdateChecker.formatFileSize(0L))
        assertEquals("512 КБ", AppUpdateChecker.formatFileSize(512L * 1024L))
        assertEquals("4.5 МБ", AppUpdateChecker.formatFileSize((4.5 * 1024 * 1024).toLong()))
    }
}
