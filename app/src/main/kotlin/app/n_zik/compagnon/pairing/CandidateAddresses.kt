package app.n_zik.compagnon.pairing

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.logging.Logger

/** Snapshot of one network interface, enough for the contract §4.3 filter. */
data class NetInterface(
    val name: String,
    val displayName: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val ipv4Addresses: List<String>,
)

/** Where interfaces come from; the real one reads the OS, tests pass a fixed list. */
interface NetworkInterfaceSource {
    fun interfaces(): List<NetInterface>

    /** Local IPv4 the OS would use for the default route, i.e. the address of the default-gateway interface. */
    fun defaultRouteAddress(): String?
}

/** IP candidates written into the pairing QR (contract §4.3, PC side). */
object CandidateAddresses {

    /** Name or description fragments of virtual adapters, case-insensitive (contract §4.3 step 2). */
    val VIRTUAL_ADAPTER_MARKERS = listOf(
        "vEthernet", "WSL", "Hyper-V", "VirtualBox", "VMware", "TAP", "TUN",
        "WireGuard", "Tailscale", "ZeroTier", "Npcap", "Loopback",
    )

    /**
     * 1. active, non-loopback interfaces with an RFC 1918 IPv4; 2. virtual adapters excluded;
     * 3. the default-gateway interface first; 4. at most 4 addresses. Empty → manual pairing only.
     */
    fun select(interfaces: List<NetInterface>, defaultRouteAddress: String?): List<String> {
        val route = defaultRouteAddress
        val kept = interfaces
            .filter { it.isUp && !it.isLoopback && !isVirtual(it) }
            .map { it.ipv4Addresses.filter(PairingRules::isPrivateIpv4) }
            .filter { it.isNotEmpty() }
        val (gateway, others) = kept.partition { route != null && route in it }
        return (gateway + others)
            .flatMap { ips -> if (route != null && route in ips) listOf(route) + (ips - route) else ips }
            .distinct()
            .take(BridgeContract.MAX_CANDIDATE_IPS)
    }

    fun select(source: NetworkInterfaceSource): List<String> = select(source.interfaces(), source.defaultRouteAddress())

    fun isVirtual(networkInterface: NetInterface): Boolean = VIRTUAL_ADAPTER_MARKERS.any { marker ->
        networkInterface.name.contains(marker, ignoreCase = true) ||
            networkInterface.displayName.contains(marker, ignoreCase = true)
    }
}

/** Reads the machine's interfaces through `java.net.NetworkInterface`. */
object SystemNetworkInterfaceSource : NetworkInterfaceSource {
    private val log = Logger.getLogger("CandidateAddresses")

    /** Any routable address: a UDP "connect" sends nothing, it only asks the OS which source address it would use. */
    private const val ROUTE_PROBE_HOST = "192.0.2.1"
    private const val ROUTE_PROBE_PORT = 9

    override fun interfaces(): List<NetInterface> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().mapNotNull { nif ->
            runCatching {
                NetInterface(
                    name = nif.name.orEmpty(),
                    displayName = nif.displayName.orEmpty(),
                    isUp = nif.isUp,
                    isLoopback = nif.isLoopback,
                    ipv4Addresses = nif.inetAddresses.toList().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                )
            }.getOrNull()
        }
    }.onFailure { log.warning("Could not list network interfaces: ${it.message}") }.getOrDefault(emptyList())

    override fun defaultRouteAddress(): String? = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(ROUTE_PROBE_HOST), ROUTE_PROBE_PORT)
            (socket.localAddress as? Inet4Address)?.hostAddress?.takeUnless { socket.localAddress.isAnyLocalAddress }
        }
    }.getOrNull()
}
