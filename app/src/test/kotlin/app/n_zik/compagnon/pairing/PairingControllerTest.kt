package app.n_zik.compagnon.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PairingControllerTest {

    @TempDir
    lateinit var dir: Path

    private val token = "T".repeat(43)

    private class FakeListener : OfferListener {
        val flow = MutableStateFlow<PairingRequest?>(null)
        override val request: StateFlow<PairingRequest?> = flow
        var onOffer: ((PairingOffer) -> Unit)? = null
        var closed = false
        override suspend fun start(onOffer: (PairingOffer) -> Unit): Int {
            this.onOffer = onOffer
            flow.value = PairingRequest("rid-1", 0)
            return 53817
        }
        override fun close() {
            closed = true
            flow.value = null
        }
    }

    private inner class FakeApi : BridgeApi {
        var metaAnswer: (ServerAddress) -> MetaResult = { MetaResult.Ok(MetaResponse("1.1", "Pixel 8")) }
        var validateAnswer: (ValidateRequest) -> ValidateResult = {
            ValidateResult.Ok(ValidateResponse(token, "Ab3dE5fG7hI", "Pixel 8", 42420))
        }
        val probes = ArrayDeque<ProbeResult>()
        val calls = mutableListOf<String>()
        val validateRequests = mutableListOf<ValidateRequest>()
        override suspend fun meta(address: ServerAddress): MetaResult {
            calls += "meta ${address.ip}:${address.port}"
            return metaAnswer(address)
        }
        override suspend fun validate(address: ServerAddress, request: ValidateRequest): ValidateResult {
            calls += "validate ${address.ip}:${address.port}"
            validateRequests += request
            return validateAnswer(request)
        }
        override suspend fun probe(address: ServerAddress, deviceToken: String): ProbeResult {
            calls += "probe"
            return probes.removeFirstOrNull() ?: ProbeResult.Ok
        }
    }

    private val api = FakeApi()
    private val secrets = InMemorySecretStore()
    private val listeners = mutableListOf<FakeListener>()
    private var candidates = listOf("192.168.1.20")

    private fun store() = CredentialStore(dir.resolve("pairing.json"), secrets)

    private fun TestScope.controller() = PairingController(
        scope = backgroundScope,
        api = api,
        store = store(),
        listenerFactory = { FakeListener().also { listeners += it } },
        candidateProvider = { candidates },
        defaultDeviceName = "PC-SALON",
    )

    private fun offer(requestId: String = "rid-1") =
        PairingOffer(1, requestId, "K7M2QX", listOf("192.168.1.14"), 42420, "Pixel 8")

    private fun savePairing() {
        store().save(
            StoredPairing(PairingRecord(listOf("192.168.1.14"), 42420, "Pixel 8", "Ab3dE5fG7hI", "PC-SALON"), token),
        )
    }

    @Test
    fun `without credential the QR is shown with the computer name`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        val state = controller.state.value as PairingState.Unpaired
        assertEquals(PairingMode.Qr, state.mode)
        val qr = state.qrPayload!!
        assertEquals(1, qr.v)
        assertEquals("nzik-pair", qr.type)
        assertEquals("rid-1", qr.requestId)
        assertEquals("PC-SALON", qr.deviceName)
        assertEquals(53817, qr.port)
        assertEquals(listOf("192.168.1.20"), qr.ips)
        assertEquals(
            """{"v":1,"type":"nzik-pair","requestId":"rid-1","deviceName":"PC-SALON","port":53817,"ips":["192.168.1.20"]}""",
            qr.toJson(),
        )
    }

    @Test
    fun `QR offer then meta then validate leads to paired and persisted`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        listeners.single().onOffer!!(offer())
        runCurrent()

        assertTrue(listeners.single().closed)
        assertEquals(listOf("meta 192.168.1.14:42420", "validate 192.168.1.14:42420"), api.calls)
        assertEquals(ValidateRequest("K7M2QX", "rid-1", "PC-SALON"), api.validateRequests.single())
        val paired = controller.state.value as PairingState.Paired
        assertEquals(PairedStatus.Ok, paired.status)
        assertEquals("Pixel 8", paired.record.serverName)
        assertEquals(token, secrets.secret)
        assertFalse(Files.readString(dir.resolve("pairing.json")).contains(token))
    }

    @Test
    fun `restart with a stored pairing goes straight to paired`() = runTest {
        savePairing()
        val controller = controller()
        controller.start()
        runCurrent()
        assertEquals(PairedStatus.Ok, (controller.state.value as PairingState.Paired).status)
        assertTrue(listeners.isEmpty())
    }

    @Test
    fun `60 s without offer switches to manual with the explicit message, QR kept`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        advanceTimeBy(BridgeContract.LISTENER_UNREACHABLE_MS - 1)
        runCurrent()
        assertEquals(PairingMode.Qr, (controller.state.value as PairingState.Unpaired).mode)
        advanceTimeBy(2)
        runCurrent()
        val state = controller.state.value as PairingState.Unpaired
        assertEquals(PairingMode.Manual, state.mode)
        assertTrue(state.listenerUnreachable)
        assertNotNull(state.qrPayload)
        assertFalse(listeners.single().closed)
    }

    @Test
    fun `no candidate address goes straight to manual without listener`() = runTest {
        candidates = emptyList()
        val controller = controller()
        controller.start()
        runCurrent()
        val state = controller.state.value as PairingState.Unpaired
        assertEquals(PairingMode.Manual, state.mode)
        assertTrue(state.noCandidates)
        assertNull(state.qrPayload)
        assertTrue(listeners.isEmpty())
    }

    @Test
    fun `manual pairing normalises the code and validates with a null requestId`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        controller.showManual()
        controller.updateManual(ManualForm(ip = "192.168.1.14", port = "42420", code = "abc def"))
        controller.submitManual()
        runCurrent()
        assertEquals(ValidateRequest("ABCDEF", null, "PC-SALON"), api.validateRequests.single())
        assertTrue(controller.state.value is PairingState.Paired)
        assertTrue(listeners.single().closed)
    }

    @Test
    fun `rejected code keeps the manual form with the error`() = runTest {
        api.validateAnswer = { ValidateResult.Rejected }
        val controller = controller()
        controller.start()
        runCurrent()
        controller.showManual()
        val form = ManualForm(ip = "192.168.1.14", port = "42420", code = "ABC-DEF")
        controller.updateManual(form)
        controller.submitManual()
        runCurrent()
        val state = controller.state.value as PairingState.Unpaired
        assertEquals(PairingError.CodeRejected, state.error)
        assertEquals(PairingMode.Manual, state.mode)
        assertEquals(form, state.manual)
        assertNull(secrets.secret)
    }

    @Test
    fun `rate limit is reported with the wait`() = runTest {
        api.validateAnswer = { ValidateResult.RateLimited(42_000) }
        val controller = controller()
        controller.start()
        runCurrent()
        listeners.single().onOffer!!(offer())
        runCurrent()
        assertEquals(PairingError.RateLimited(42_000), (controller.state.value as PairingState.Unpaired).error)
    }

    @Test
    fun `meta 2_0 is incompatible and nothing is persisted or validated`() = runTest {
        api.metaAnswer = { MetaResult.Incompatible("2.0") }
        val controller = controller()
        controller.start()
        runCurrent()
        listeners.single().onOffer!!(offer())
        runCurrent()
        val state = controller.state.value as PairingState.Unpaired
        assertEquals(PairingError.IncompatibleVersion("2.0"), state.error)
        assertTrue(api.validateRequests.isEmpty())
        assertNull(secrets.secret)
        assertFalse(Files.exists(dir.resolve("pairing.json")))
        // A new listener and QR are offered again.
        assertEquals(2, listeners.size)
        assertNotNull(state.qrPayload)
    }

    @Test
    fun `unreachable phone at start-up keeps the credential and Edit IP keeps token and port`() = runTest {
        savePairing()
        api.metaAnswer = { MetaResult.Unreachable }
        val controller = controller()
        controller.start()
        runCurrent()
        assertEquals(PairedStatus.Unreachable, (controller.state.value as PairingState.Paired).status)
        assertEquals(token, secrets.secret)

        api.metaAnswer = { MetaResult.Ok(MetaResponse("1.1", "Pixel 8")) }
        controller.editIp("192.168.1.99")
        runCurrent()
        val paired = controller.state.value as PairingState.Paired
        assertEquals(PairedStatus.Ok, paired.status)
        assertEquals(listOf("192.168.1.99"), paired.record.serverIps)
        assertEquals(42420, paired.record.serverPort)
        assertEquals("meta 192.168.1.99:42420", api.calls.filter { it.startsWith("meta") }.last())
        assertEquals(token, secrets.secret)
    }

    @Test
    fun `revoked twice erases the credential and shows the re-pairing screen`() = runTest {
        savePairing()
        api.probes += listOf(ProbeResult.Revoked, ProbeResult.Revoked)
        val controller = controller()
        controller.start()
        runCurrent()
        assertTrue(controller.state.value is PairingState.Paired)
        advanceTimeBy(BridgeContract.REVOCATION_CONFIRM_DELAY_MS + 1)
        runCurrent()
        assertEquals(PairingState.Revoked, controller.state.value)
        assertNull(secrets.secret)
        assertFalse(Files.exists(dir.resolve("pairing.json")))

        controller.pairAgain()
        runCurrent()
        assertTrue(controller.state.value is PairingState.Unpaired)
    }

    @Test
    fun `revoked once then ok erases nothing`() = runTest {
        savePairing()
        api.probes += listOf(ProbeResult.Revoked, ProbeResult.Ok)
        val controller = controller()
        controller.start()
        advanceTimeBy(BridgeContract.REVOCATION_CONFIRM_DELAY_MS + 1)
        runCurrent()
        assertEquals(PairedStatus.Ok, (controller.state.value as PairingState.Paired).status)
        assertEquals(token, secrets.secret)
    }

    @Test
    fun `other active PC is shown without erasing or retrying automatically`() = runTest {
        savePairing()
        api.probes += ProbeResult.OtherActive("PC-BUREAU")
        val controller = controller()
        controller.start()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(PairedStatus.OtherActive("PC-BUREAU"), (controller.state.value as PairingState.Paired).status)
        assertEquals(1, api.calls.count { it == "probe" })
        assertEquals(token, secrets.secret)
    }

    @Test
    fun `another 401 erases nothing`() = runTest {
        savePairing()
        api.probes += ProbeResult.Failed(401, BridgeErrorCode.UNAUTHORIZED)
        val controller = controller()
        controller.start()
        runCurrent()
        assertEquals(PairedStatus.Error(401, "UNAUTHORIZED"), (controller.state.value as PairingState.Paired).status)
        assertEquals(token, secrets.secret)
    }

    @Test
    fun `forget erases both halves and reopens the QR`() = runTest {
        savePairing()
        val controller = controller()
        controller.start()
        runCurrent()
        controller.forget()
        runCurrent()
        assertNull(secrets.secret)
        assertFalse(Files.exists(dir.resolve("pairing.json")))
        assertEquals(PairingMode.Qr, (controller.state.value as PairingState.Unpaired).mode)
    }

    @Test
    fun `closing the controller closes the listener`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        controller.close()
        assertTrue(listeners.single().closed)
    }

    @Test
    fun `a fresh manual form has the default bridge port and no IP`() = runTest {
        val controller = controller()
        controller.start()
        runCurrent()
        val form = (controller.state.value as PairingState.Unpaired).manual
        assertEquals("", form.ip)
        assertEquals("42420", form.port)
    }

    @Test
    fun `after a revocation the manual form is prefilled with the last phone address`() = runTest {
        savePairing()
        api.probes.addAll(listOf(ProbeResult.Revoked, ProbeResult.Revoked))
        val controller = controller()
        controller.start()
        runCurrent()
        advanceTimeBy(BridgeContract.REVOCATION_CONFIRM_DELAY_MS + 1)
        runCurrent()
        assertEquals(PairingState.Revoked, controller.state.value)

        controller.pairAgain()
        runCurrent()
        val form = (controller.state.value as PairingState.Unpaired).manual
        assertEquals("192.168.1.14", form.ip)
        assertEquals("42420", form.port)
        assertFalse(Files.readString(dir.resolve("pairing.json").takeIf { Files.exists(it) } ?: return@runTest).contains("192.168.1.14"))
    }
}