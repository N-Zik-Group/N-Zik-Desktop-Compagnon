package app.n_zik.compagnon.core.network

import app.n_zik.compagnon.bridge.pairing.PairingRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CandidateAddressesTest {

    private fun nif(
        name: String,
        vararg ips: String,
        displayName: String = name,
        up: Boolean = true,
        loopback: Boolean = false,
    ) = NetInterface(name, displayName, up, loopback, ips.toList())

    @Test
    fun `keeps only active non-loopback RFC 1918 addresses`() {
        val result = CandidateAddresses.select(
            listOf(
                nif("lo", "127.0.0.1", loopback = true),
                nif("eth0", "192.168.1.20", "8.8.4.4"),
                nif("eth1", "10.0.0.5", up = false),
                nif("eth2", "172.16.3.4", "172.32.0.1"),
                nif("eth3", "100.64.0.1"),
            ),
            defaultRouteAddress = null,
        )
        assertEquals(listOf("192.168.1.20", "172.16.3.4"), result)
    }

    @Test
    fun `excludes virtual adapters by name or description, ignoring case`() {
        val result = CandidateAddresses.select(
            listOf(
                nif("eth5", "172.20.0.1", displayName = "vEthernet (WSL)"),
                nif("eth6", "192.168.56.1", displayName = "VirtualBox Host-Only Ethernet Adapter"),
                nif("eth7", "10.8.0.2", displayName = "TAP-Windows Adapter V9"),
                nif("tailscale0", "10.9.0.2", displayName = "Tailscale Tunnel"),
                nif("wlan0", "192.168.1.30", displayName = "Intel(R) Wi-Fi 6 AX201"),
                nif("eth8", "10.1.1.1", displayName = "npcap loopback adapter"),
            ),
            defaultRouteAddress = null,
        )
        assertEquals(listOf("192.168.1.30"), result)
    }

    @Test
    fun `default gateway interface comes first`() {
        val result = CandidateAddresses.select(
            listOf(
                nif("eth0", "10.0.0.5"),
                nif("wlan0", "192.168.1.42", "192.168.1.43"),
            ),
            defaultRouteAddress = "192.168.1.43",
        )
        assertEquals(listOf("192.168.1.43", "192.168.1.42", "10.0.0.5"), result)
    }

    @Test
    fun `at most four addresses`() {
        val result = CandidateAddresses.select(
            (1..6).map { nif("eth$it", "192.168.$it.10") },
            defaultRouteAddress = "192.168.6.10",
        )
        assertEquals(listOf("192.168.6.10", "192.168.1.10", "192.168.2.10", "192.168.3.10"), result)
    }

    @Test
    fun `nothing left means no candidate`() {
        val result = CandidateAddresses.select(
            listOf(nif("vEthernet (Default Switch)", "172.17.0.1"), nif("eth0", "203.0.113.4")),
            defaultRouteAddress = "203.0.113.4",
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `private IPv4 ranges`() {
        assertTrue(PairingRules.isPrivateIpv4("10.255.0.1"))
        assertTrue(PairingRules.isPrivateIpv4("172.31.255.255"))
        assertTrue(PairingRules.isPrivateIpv4("192.168.0.1"))
        assertFalse(PairingRules.isPrivateIpv4("172.15.0.1"))
        assertFalse(PairingRules.isPrivateIpv4("192.169.0.1"))
        assertFalse(PairingRules.isPrivateIpv4("256.1.1.1"))
        assertFalse(PairingRules.isPrivateIpv4("fe80::1"))
    }
}
