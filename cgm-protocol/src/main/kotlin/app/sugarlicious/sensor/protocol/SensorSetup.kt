package app.sugarlicious.sensor.protocol

import app.sugarlicious.sensor.auth.engine.Plugin
import kotlinx.serialization.Serializable

@Serializable
data class CgmSetupPayload(
    val pairingCode: String,
    val sensorSerial: String? = null,
    val gtin: String? = null,
    val rawBarcode: String? = null,
) {
    init {
        require(CgmSetupParser.isPairingCode(pairingCode))
    }
}

data class CgmGKeyParts(
    val certificateAuthority: ByteArray,
    val certificate: ByteArray,
    val privateKey: ByteArray,
) {
    fun isComplete(): Boolean = certificateAuthority.isNotEmpty() && certificate.isNotEmpty() && privateKey.isNotEmpty()
}

/** Public one-time G-Key setup material published by the xDrip project. */
object CgmDefaultGKey {
    val parts =
        CgmGKeyParts(
            certificateAuthority = P1.hexBytes(),
            certificate = P2.hexBytes(),
            privateKey = P3.hexBytes(),
        )

    private const val P1 = "308201ea3082018fa00302010202142f3c52b6eb08701046d45d78ce81784c9dfe5240300a06082a8648ce3d04030230133111300f06035504030c084445583030504731301e170d3230313033303135353930345a170d3335313032373135353930345a30133111300f06035504030c0844455830335047313059301306072a8648ce3d020106082a8648ce3d03010703420004fb1aca21d8aeec9a4eb51f85304953d977a1ad569799250ff863987f42a3cd9fa4ff571eb568bc6c396277c3dcb51dedaee85513c80a5c4435538a19f5a96348a381c03081bd300f0603551d130101ff040530030101ff301f0603551d230418301680149e0f1e36f3f276a701fe8e883a6e26a635bd6afc305a0603551d1f04533051304fa034a0328630687474703a2f2f63726c2e64702e736161732e7072696d656b65792e636f6d2f63726c2f44455830305047312e63726ca217a41530133111300f06035504030c084445583030504731301d0603551d0e0416041488f61e81bc4b17f05c6b1be2991d60087ccedd79300e0603551d0f0101ff040403020186300a06082a8648ce3d0403020349003046022100aa69cd897ec663af5f9e158187df6851ff0756f00c401624564f81a19f5a0785022100daebb9fdb163b731eb0661f1c0a1932871a50e399ad1c6f519eabd4c9e7ba013"
    private const val P2 = "308201cd30820174a003020102021419052fcc17530bfa56e49dcafcdacf853ce5ba73300a06082a8648ce3d04030230133111300f06035504030c084445583033504731301e170d3233303431343130323831345a170d3235303431333130323831335a303a3138303606035504030c2f30312c303030302c303330304c514543437a4142417741412c63696f69653356625132686c5a4d6a64556d357267413059301306072a8648ce3d020106082a8648ce3d030107034200045118c35e9e41e7e0654fee801c52a9c5dfc510ef09597d5cca8461e4af9c666714834f2bc903f16fabfc45755b0183f1a09745cdffcb4e2f799e50bed9a6b58ca37f307d300c0603551d130101ff04023000301f0603551d2304183016801488f61e81bc4b17f05c6b1be2991d60087ccedd79301d0603551d250416301406082b0601050507030206082b06010505070301301d0603551d0e04160414d309e75c0725412d7a7922e3aacfb27f7ebd6be0300e0603551d0f0101ff0404030205a0300a06082a8648ce3d0403020347003044022048d4868cf393d9044101b6f07fd68d7f0642805f85da74e2fe9de8dd3507f02702201cd1bf7c6c7edd59435e324925fcf0ebb3cae2110d79407c77aa3b93b7bc04cb"
    private const val P3 = "308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420007cfbd596f6e74477b8c0e9f6f7a174275e101ef6bf7d18caf01181d127b579a144034200045118c35e9e41e7e0654fee801c52a9c5dfc510ef09597d5cca8461e4af9c666714834f2bc903f16fabfc45755b0183f1a09745cdffcb4e2f799e50bed9a6b58c"
}

enum class CgmReceiverSlot {
    PRIMARY,
    WATCH_ALTERNATE,
}

internal fun CgmReceiverSlot.keksPersistence6(): ByteArray =
    when (this) {
        CgmReceiverSlot.PRIMARY -> byteArrayOf(0x00)
        CgmReceiverSlot.WATCH_ALTERNATE -> byteArrayOf(0x02)
    }

class CgmAuthenticationSession(
    pairingCode: String,
    gKey: CgmGKeyParts = CgmDefaultGKey.parts,
    persistedKey: ByteArray? = null,
    receiverSlot: CgmReceiverSlot = CgmReceiverSlot.WATCH_ALTERNATE,
) {
    private val plugin = Plugin(pairingCode)

    init {
        require(CgmSetupParser.isPairingCode(pairingCode))
        require(gKey.isComplete())
        plugin.setPersistence(8, gKey.certificateAuthority)
        plugin.setPersistence(9, gKey.certificate)
        plugin.setPersistence(10, gKey.privateKey)
        persistedKey?.takeIf { it.size == 16 }?.let { plugin.setPersistence(2, it) }
        // KEKS channel 6 selects the receiver auth slot. A coexisting Wear collector uses
        // the alternate slot so it does not contend with the phone-side Manufacturer connection.
        plugin.setPersistence(6, receiverSlot.keksPersistence6())
    }

    fun connected() = plugin.amConnected()

    fun next(): Array<ByteArray?>? = plugin.aNext()

    fun nextStep(): AuthStep = plugin.nextStep()

    fun onAuthenticationData(data: ByteArray): Boolean = plugin.receivedResponse(data)

    fun onExtraData(data: ByteArray): Boolean = plugin.receivedData(data)

    fun shouldBond(data: ByteArray): Boolean = plugin.bondNow(data)

    fun sharedKey(): ByteArray? = plugin.getPersistence(1)?.takeIf { it.size == 16 }
}

object CgmSetupParser {
    fun isPairingCode(value: String): Boolean = value.length == 4 && value.all(Char::isDigit)

    fun parse(input: String): CgmSetupPayload? {
        val raw = input.trim()
        if (isPairingCode(raw)) return CgmSetupPayload(raw)
        val normalized = raw.removePrefix("]d2").replace("^]", "\u001d")
        val fields = parseGs1(normalized)
        val pin =
            fields["240"]?.take(4)
                ?: Regex("(?:^|[()\\u001d])240[)]?([0-9]{4})(?:$|\\u001d)").find(normalized)?.groupValues?.get(1)
                ?: return null
        if (!isPairingCode(pin)) return null
        val gtin = fields["01"]
        if (gtin != null && (gtin.length != 14 || gtin.substring(1, 8) != "0386270")) return null
        return CgmSetupPayload(pin, fields["21"], gtin, raw)
    }

    private fun parseGs1(value: String): Map<String, String> {
        val fixed = mapOf("01" to 14, "11" to 6, "17" to 6)
        val variable = listOf("240", "250", "10", "21")
        val clean = value.trimStart('\u001d')
        var offset = 0
        val result = linkedMapOf<String, String>()
        while (offset < clean.length) {
            if (clean[offset] == '\u001d') {
                offset++
                continue
            }
            val ai = (variable + fixed.keys).firstOrNull { clean.startsWith(it, offset) } ?: break
            offset += ai.length
            val fixedLength = fixed[ai]
            val end =
                if (fixedLength !=
                    null
                ) {
                    (offset + fixedLength).coerceAtMost(clean.length)
                } else {
                    clean.indexOf('\u001d', offset).takeIf { it >= 0 }
                        ?: clean.length
                }
            result[ai] = clean.substring(offset, end)
            offset = end
        }
        return result
    }
}

private fun String.hexBytes(): ByteArray {
    require(length % 2 == 0)
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
