package com.example.zenwall.vpn

import com.example.zenwall.data.FirewallRules

/**
 * Translates user rules into what the VPN builder must be told.
 *
 * Apps routed into the tunnel are blocked, because nothing ever reads the tunnel.
 * - [Mode.CAPTURE_ONLY]: only [packages] go into the tunnel (addAllowedApplication).
 * - [Mode.CAPTURE_ALL_EXCEPT]: every app goes into the tunnel except [packages] (addDisallowedApplication).
 *
 * Safety invariant: in blacklist mode the list handed to addAllowedApplication is never empty.
 * An empty allow-list means "every app uses the VPN", which would block the whole phone. ZenWall itself
 * is always included; it needs no internet, so the tunnel stays up and blocks nothing else.
 */
data class FirewallPlan(val mode: Mode, val packages: Set<String>) {
    enum class Mode { CAPTURE_ONLY, CAPTURE_ALL_EXCEPT }

    companion object {
        fun from(rules: FirewallRules, ownPackage: String, isInstalled: (String) -> Boolean): FirewallPlan {
            val installed = rules.packages.filter(isInstalled).toSet()
            return if (rules.whitelistMode) {
                FirewallPlan(Mode.CAPTURE_ALL_EXCEPT, installed - ownPackage)
            } else {
                FirewallPlan(Mode.CAPTURE_ONLY, installed + ownPackage)
            }
        }
    }
}
