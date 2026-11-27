package com.example.zenwall.vpn

import com.example.zenwall.data.FirewallRules
import org.junit.Assert.assertEquals
import org.junit.Test

class FirewallPlanTest {
    private val own = "com.example.zenwall"

    private fun plan(whitelist: Boolean, pkgs: Set<String>, installed: Set<String>) =
        FirewallPlan.from(FirewallRules(whitelist, pkgs), own) { it in installed }

    @Test
    fun `blacklist captures only installed selected apps plus ZenWall itself`() {
        val result = plan(false, setOf("a", "b", "gone"), setOf("a", "b"))

        assertEquals(FirewallPlan.Mode.CAPTURE_ONLY, result.mode)
        assertEquals(setOf("a", "b", own), result.packages)
    }

    @Test
    fun `blacklist with every selected app uninstalled never captures the whole phone`() {
        val result = plan(false, setOf("gone1", "gone2"), emptySet())

        assertEquals(FirewallPlan.Mode.CAPTURE_ONLY, result.mode)
        assertEquals(setOf(own), result.packages)
    }

    @Test
    fun `empty blacklist keeps the tunnel armed but blocks nothing else`() {
        val result = plan(false, emptySet(), emptySet())

        assertEquals(FirewallPlan.Mode.CAPTURE_ONLY, result.mode)
        assertEquals(setOf(own), result.packages)
    }

    @Test
    fun `whitelist exempts installed selected apps and captures everything else`() {
        val result = plan(true, setOf("a", "gone"), setOf("a"))

        assertEquals(FirewallPlan.Mode.CAPTURE_ALL_EXCEPT, result.mode)
        assertEquals(setOf("a"), result.packages)
    }

    @Test
    fun `whitelist never exempts ZenWall itself`() {
        val result = plan(true, setOf("a", own), setOf("a", own))

        assertEquals(setOf("a"), result.packages)
    }

    @Test
    fun `empty whitelist blocks every app`() {
        val result = plan(true, emptySet(), emptySet())

        assertEquals(FirewallPlan.Mode.CAPTURE_ALL_EXCEPT, result.mode)
        assertEquals(emptySet<String>(), result.packages)
    }
}
