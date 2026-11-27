package com.example.zenwall.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogDecisionTest {

    @Test
    fun `running firewall needs nothing`() {
        assertEquals(WatchdogDecision.NOTHING, WatchdogDecision.decide(isRunning = true, hasVpnConsent = true))
    }

    @Test
    fun `dead firewall with consent is restarted`() {
        assertEquals(WatchdogDecision.RESTART, WatchdogDecision.decide(isRunning = false, hasVpnConsent = true))
    }

    @Test
    fun `dead firewall without consent alerts the user instead of restarting`() {
        assertEquals(WatchdogDecision.ALERT_AND_GIVE_UP, WatchdogDecision.decide(isRunning = false, hasVpnConsent = false))
    }

    @Test
    fun `running firewall is left alone even if consent check fails`() {
        assertEquals(WatchdogDecision.NOTHING, WatchdogDecision.decide(isRunning = true, hasVpnConsent = false))
    }
}
