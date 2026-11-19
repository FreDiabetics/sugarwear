package app.sugarlicious.sensor.auth.engine

import app.sugarlicious.sensor.auth.engine.message.AuthChallengeTxMessage
import app.sugarlicious.sensor.auth.engine.message.AuthRequestTxMessage2
import app.sugarlicious.sensor.auth.engine.message.AuthStatusRxMessage
import app.sugarlicious.sensor.auth.engine.message.CertInfoRxMessage
import app.sugarlicious.sensor.auth.engine.message.CertInfoTxMessage
import app.sugarlicious.sensor.auth.engine.message.SignChallengeTxMessage
import app.sugarlicious.sensor.auth.engine.util.Util
import app.sugarlicious.sensor.auth.spi.IPluginDA
import app.sugarlicious.sensor.protocol.AuthBytes
import app.sugarlicious.sensor.protocol.AuthChannel
import app.sugarlicious.sensor.protocol.AuthError
import app.sugarlicious.sensor.protocol.AuthStep
import app.sugarlicious.sensor.protocol.AuthWrite
import java.security.InvalidParameterException
import java.util.Arrays

/** JamOrHam / xDrip libkeks state machine, translated to Kotlin. See LICENSES/xDrip-libkeks.md. */
class Plugin() : IPluginDA {
    val context =
        Context().apply {
            alice = Config.Get.ALICE.bytes
            bob = Config.Get.BOB.bytes
            keyA = KeyPair()
            KeyB = KeyPair()
        }

    @Volatile private var lastAuthTx2: AuthRequestTxMessage2? = null

    @Volatile private var phase = Phase.INIT

    @Volatile private var accumulator = byteArrayOf()

    @Volatile private var newFwchal: ByteArray? = byteArrayOf()

    @Volatile private var keyToTestAgainst: ByteArray? = null
    private var expectedSize = 0
    private var response: ByteArray? = null
    private var alternate = false

    constructor(password: String) : this() {
        context.password = password
        context.getPasswordBytes()
    }

    @Synchronized
    private fun changeState(next: Phase) {
        if (next !in dontClearAccumulator) accumulator = EMPTY
        phase = next
    }

    override fun amConnected() {
        context.resetIfNotReady()
        changeState(Phase.ROUND_START)
    }

    override fun bondNow(data: ByteArray): Boolean =
        Arrays.equals(data, Config.Get.TIME_EXTENDED.bytes) ||
            Arrays.equals(data, Config.Get.TIME_EXTENDED2.bytes) ||
            Arrays.equals(data, Config.Get.TIME_EXTENDED3.bytes)

    private fun positionFromState(): Int =
        when (phase) {
            Phase.ROUND_1 -> 1
            Phase.ROUND_2 -> 2
            Phase.ROUND_3 -> 3
            else -> 0
        }

    private fun parameterFromState(): Byte =
        when (phase) {
            Phase.ROUND_1 -> 0
            Phase.ROUND_2 -> 1
            Phase.ROUND_3 -> 2
            else -> -1
        }

    private fun expectedBytesForState(): Int =
        when (phase) {
            Phase.ROUND_1, Phase.ROUND_2, Phase.ROUND_3 -> Curve.PACKET_SIZE
            Phase.CERTIFICATE_1, Phase.CERTIFICATE_1_OUT, Phase.CERTIFICATE_2, Phase.CERTIFICATE_2_OUT ->
                if (expectedSize > 0) expectedSize else UNSPECIFIED_CERTIFICATE_BYTES
            Phase.KEY_CHALLENGE -> 64
            else -> UNSPECIFIED_RESPONSE_BYTES
        }

    private fun fill(data: ByteArray): Boolean {
        accumulator = Util.arrayAppend(accumulator, data)
        return accumulator.size >= expectedBytesForState()
    }

    override fun receivedResponse(data: ByteArray): Boolean {
        when (phase) {
            Phase.REQUEST_AUTH -> {
                if (!verifyChallenge(data)) {
                    context.reset()
                    if (context.sequence > 1) throw AuthenticationMismatchException()
                    return false
                }
                context.challenge = ByteArray(8)
                System.arraycopy(data, 9, context.challenge, 0, 8)
                return true
            }
            Phase.CHALLENGE_REPLY -> {
                val status = AuthStatusRxMessage(data)
                if (status.needsRefresh()) context.reset()
                if (!status.isAuthenticated) {
                    context.reset()
                    changeState(if (!status.isBonded) Phase.BOND_FAILURE else Phase.UNKNOWN)
                } else if (status.isBonded) {
                    changeState(if (context.passwordBytes!!.size > 4) Phase.GET_DATA else Phase.GET_DATA_2)
                } else {
                    expectedSize = 0
                    response = null
                    if (context.passwordBytes!!.size > 4) {
                        changeState(Phase.PAIRING)
                    } else if (context.validateParts()) {
                        changeState(Phase.CERTIFICATE_0)
                    } else {
                        throw InvalidParameterException("Missing QR code")
                    }
                }
                return true
            }
            Phase.PAIRING -> return true
            Phase.CERTIFICATE_1, Phase.CERTIFICATE_2 -> {
                val reply = CertInfoRxMessage(data)
                if (!reply.valid()) throw InvalidParameterException(if (phase == Phase.CERTIFICATE_1) "Invalid QR code 1" else "Invalid QR code 2")
                expectedSize = reply.size
                changeState(phase)
                return true
            }
            Phase.KEY_CHALLENGE -> {
                if (data.size > 2 && data[1] != 0.toByte()) throw InvalidParameterException("Invalid QR code 3")
                response = Calc.challenger(context.partC, data)
                return true
            }
            Phase.KEY_CHALLENGE_OUT -> return true
            else -> return false
        }
    }

    private fun authRequest(): ByteArray {
        val request = AuthRequestTxMessage2(8, alternate, newFwchal!!)
        lastAuthTx2 = request
        return request.byteSequence!!
    }

    /** Canonical transition API. Transport arrays exist only in aNext's compatibility adapter. */
    fun nextStep(): AuthStep =
        when (phase) {
            Phase.ROUND_START -> {
                if (context.getRound3Packet() != null || context.savedKey != null) {
                    phase = Phase.REQUEST_AUTH
                    send(authRequest(), null)
                } else {
                    phase = Phase.ROUND_1
                    sequencePacket(null)
                }
            }
            Phase.ROUND_1 -> {
                phase = Phase.ROUND_2
                sequencePacket(Calc.getRound1Packet(context).output())
            }
            Phase.ROUND_2 -> {
                phase = Phase.ROUND_3
                sequencePacket(Calc.getRound2Packet(context).output())
            }
            Phase.ROUND_3 -> {
                phase = Phase.REQUEST_AUTH
                // Evaluate auth token generation before the round-3 proof, exactly as Java did.
                send(authRequest(), Calc.getRound3Packet(context).output())
            }
            Phase.REQUEST_AUTH -> {
                phase = Phase.CHALLENGE_REPLY
                val hash = Calc.calculateHash(context)
                if (hash == null) AuthStep.Failed(AuthError.LegacyEngineUnavailable) else send(AuthChallengeTxMessage(hash).byteSequence, null)
            }
            Phase.CHALLENGE_REPLY -> {
                phase = Phase.UNKNOWN
                send(Config.Get.TIME_EXTENDED.bytes, null)
            }
            Phase.CERTIFICATE_0 -> {
                phase = Phase.CERTIFICATE_1
                send(CertInfoTxMessage.expectMyCert1(this), null)
            }
            Phase.CERTIFICATE_1 -> {
                phase = Phase.CERTIFICATE_1_OUT
                send(null, context.partA)
            }
            Phase.CERTIFICATE_1_OUT -> {
                phase = Phase.CERTIFICATE_2
                send(CertInfoTxMessage.expectMyCert2(this), null)
            }
            Phase.CERTIFICATE_2 -> {
                phase = Phase.CERTIFICATE_2_OUT
                send(null, context.partB)
            }
            Phase.CERTIFICATE_2_OUT -> {
                phase = Phase.KEY_CHALLENGE
                send(SignChallengeTxMessage().byteSequence, null)
            }
            Phase.KEY_CHALLENGE -> {
                phase = Phase.KEY_CHALLENGE_OUT
                send(Config.Get.CHALLENGE_OUT.bytes, response)
            }
            Phase.KEY_CHALLENGE_OUT, Phase.PAIRING -> {
                phase = Phase.GET_DATA
                send(Config.Get.TIME_EXTENDED.bytes, null)
            }
            Phase.GET_DATA -> {
                phase = Phase.UNKNOWN
                AuthStep.Complete(AuthBytes(Config.Get.GETDATA.bytes))
            }
            Phase.GET_DATA_2 -> {
                phase = Phase.UNKNOWN
                AuthStep.Complete(AuthBytes(Config.Get.GETDATA2.bytes))
            }
            Phase.BOND_FAILURE -> {
                phase = Phase.UNKNOWN
                AuthStep.BondRequired(AuthBytes(Config.Get.GETDATA.bytes))
            }
            Phase.INIT, Phase.UNKNOWN -> AuthStep.Failed(AuthError.LegacyEngineUnavailable)
        }

    override fun aNext(): Array<ByteArray?>? =
        when (val step = nextStep()) {
            is AuthStep.Send ->
                arrayOf(
                    step.writes
                        .firstOrNull { it.channel == AuthChannel.AUTHENTICATION }
                        ?.payload
                        ?.copyBytes(),
                    step.writes
                        .firstOrNull { it.channel == AuthChannel.EXTRA }
                        ?.payload
                        ?.copyBytes(),
                )
            is AuthStep.Complete -> arrayOf(step.command.copyBytes())
            is AuthStep.BondRequired -> arrayOf(step.command.copyBytes(), null, null)
            is AuthStep.Failed -> null
        }

    override fun receivedData(data: ByteArray): Boolean {
        val full = fill(data)
        if (full) {
            context.packet[positionFromState()] = Packet.parse(accumulator)
            accumulator = EMPTY
            if (!validate()) {
                context.packet[positionFromState()] = null
                return false
            }
        }
        return full
    }

    override fun getPersistence(channel: Int): ByteArray? =
        when (channel) {
            1 -> getSharedKey() ?: byteArrayOf()
            3 -> if (keyToTestAgainst != null && getSharedKey() != null && Arrays.equals(getSharedKey(), keyToTestAgainst)) byteArrayOf() else null
            else -> byteArrayOf()
        }

    override fun setPersistence(channel: Int, data: ByteArray?): Boolean {
        if (channel == 1) {
            keyToTestAgainst = data
            return true
        }
        if (channel == 2) {
            if (data != null && data.size != 16) return false
            if (context.savedKey != null) return false
            context.savedKey = data
            return true
        }
        if (channel == 3 || channel == 4) instance = null
        if (channel == 6) {
            alternate = Arrays.equals(data, Config.Get.SPARAM.bytes)
            newFwchal = data
        }
        if (channel == 7) dontClearAccumulator.clear()
        if (channel == 8) context.partA = data
        if (channel == 9) context.partB = data
        if (channel == 10) context.partC = data
        return false
    }

    private fun validate(): Boolean {
        if (context.packet[positionFromState()] == null) return false
        return when (phase) {
            Phase.ROUND_1 -> Calc.validateRound1Packet(context)
            Phase.ROUND_2 -> Calc.validateRound2Packet(context)
            Phase.ROUND_3 -> Calc.validateRound3Packet(context)
            Phase.CERTIFICATE_1_OUT, Phase.CERTIFICATE_2_OUT -> true
            else -> false
        }
    }

    private fun verifyChallenge(data: ByteArray): Boolean {
        if (context.savedKey != null) return true
        context.challenge = lastAuthTx2!!.singleUseToken
        val hash = Calc.calculateHash(context) ?: return false
        for (index in 0 until 8) if (hash[index] != data[index + 1]) return false
        return true
    }

    private fun sequencePacket(extra: ByteArray?): AuthStep {
        context.sequence++
        val parameter = parameterFromState()
        return send(if (parameter < 0) null else byteArrayOf(Config.Get.KEYCMD.bytes[0], parameter), extra)
    }

    private fun getSharedKey(): ByteArray? {
        if (context.savedKey == null) context.savedKey = Calc.getShortSharedKey(context)
        return context.savedKey
    }

    override fun bNext(): Array<ByteArray?> = emptyArray()

    override fun cNext(): Array<ByteArray?> = emptyArray()

    override fun receivedResponse2(data: ByteArray): Boolean = false

    override fun receivedResponse3(data: ByteArray): Boolean = false

    override fun receivedData2(data: ByteArray): Boolean = false

    override fun receivedData3(data: ByteArray): Boolean = false

    override fun getStatus(): String = ""

    override fun getName(): String = "keks"

    private enum class Phase {
        INIT,
        UNKNOWN,
        ROUND_START,
        PAIRING,
        ROUND_1,
        ROUND_2,
        ROUND_3,
        BOND_FAILURE,
        REQUEST_AUTH,
        CHALLENGE_REPLY,
        CERTIFICATE_0,
        CERTIFICATE_1,
        CERTIFICATE_2,
        CERTIFICATE_1_OUT,
        CERTIFICATE_2_OUT,
        KEY_CHALLENGE,
        KEY_CHALLENGE_OUT,
        GET_DATA,
        GET_DATA_2,
    }

    companion object {
        private const val UNSPECIFIED_CERTIFICATE_BYTES = 0x179005
        private const val UNSPECIFIED_RESPONSE_BYTES = 0x602910
        private val EMPTY = byteArrayOf()

        // Preserve legacy persistence channel 7's shared clear semantics in this parity migration.
        private val dontClearAccumulator = hashSetOf(Phase.CERTIFICATE_1, Phase.CERTIFICATE_1_OUT, Phase.CERTIFICATE_2, Phase.CERTIFICATE_2_OUT)

        @Volatile private var instance: Plugin? = null

        @JvmStatic
        fun getInstance(password: String): Plugin =
            synchronized(Plugin::class.java) {
                if (instance == null || instance!!.context.password!! != password) instance = Plugin(password)
                instance!!
            }

        private fun send(command: ByteArray?, extra: ByteArray?): AuthStep {
            val writes =
                buildList {
                    if (extra != null) add(AuthWrite(AuthChannel.EXTRA, AuthBytes(extra)))
                    if (command != null) add(AuthWrite(AuthChannel.AUTHENTICATION, AuthBytes(command)))
                }
            return if (writes.isEmpty()) AuthStep.Failed(AuthError.UnexpectedOutputShape) else AuthStep.Send(writes)
        }
    }
}
