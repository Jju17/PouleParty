package dev.rahier.pouleparty

import org.junit.Assert.*
import org.junit.Test

class MigrationManagerTest {

    // MARK: - Version comparison (via reflection on private method)

    @Test
    fun `compareVersions equal versions`() {
        assertEquals(0, compareVersions("1.4.0", "1.4.0"))
    }

    @Test
    fun `compareVersions lesser version`() {
        assertTrue(compareVersions("1.3.0", "1.4.0") < 0)
    }

    @Test
    fun `compareVersions greater version`() {
        assertTrue(compareVersions("1.5.0", "1.4.0") > 0)
    }

    @Test
    fun `compareVersions minor difference`() {
        assertTrue(compareVersions("1.3.9", "1.4.0") < 0)
    }

    @Test
    fun `compareVersions patch difference`() {
        assertTrue(compareVersions("1.4.0", "1.4.1") < 0)
    }

    @Test
    fun `compareVersions major difference`() {
        assertTrue(compareVersions("0.9.9", "1.0.0") < 0)
    }

    @Test
    fun `compareVersions different length shorter first`() {
        assertTrue(compareVersions("1.3", "1.3.1") < 0)
    }

    @Test
    fun `compareVersions different length shorter second`() {
        assertTrue(compareVersions("1.3.1", "1.3") > 0)
    }

    @Test
    fun `compareVersions same with different length`() {
        assertEquals(0, compareVersions("1.3", "1.3.0"))
    }

    @Test
    fun `compareVersions empty vs version`() {
        assertTrue(compareVersions("", "1.4.0") < 0)
    }

    @Test
    fun `compareVersions version vs empty`() {
        assertTrue(compareVersions("1.4.0", "") > 0)
    }

    @Test
    fun `compareVersions both empty`() {
        assertEquals(0, compareVersions("", ""))
    }

    @Test
    fun `compareVersions malformed string treated as zero`() {
        assertTrue(compareVersions("abc", "1.4.0") < 0)
    }

    @Test
    fun `compareVersions partially malformed`() {
        assertTrue(compareVersions("1.abc.0", "1.4.0") < 0)
    }

    @Test
    fun `compareVersions large version numbers`() {
        assertTrue(compareVersions("10.0.0", "9.99.99") > 0)
    }

    @Test
    fun `compareVersions zero vs non-zero patch`() {
        assertTrue(compareVersions("1.4.0", "1.4.1") < 0)
    }

    @Test
    fun `1_3_0 is less than 1_4_0`() {
        assertTrue(compareVersions("1.3.0", "1.4.0") < 0)
    }

    @Test
    fun `1_4_0 is not less than 1_4_0`() {
        assertFalse(compareVersions("1.4.0", "1.4.0") < 0)
    }

    @Test
    fun `2_0_0 is not less than 1_4_0`() {
        assertFalse(compareVersions("2.0.0", "1.4.0") < 0)
    }

    @Test
    fun `0_0_0 default is less than 1_4_0`() {
        assertTrue(compareVersions("0.0.0", "1.4.0") < 0)
    }

    private fun compareVersions(a: String, b: String): Int =
        MigrationManager.compareVersions(a, b)
}
