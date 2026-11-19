/*
 * Derived from NightscoutFoundation/xDrip IPluginDA at commit
 * 66eb3a17063a21b8dff344719e5e72a7decbc1a6. Kotlin compatibility interface.
 * Licensed under GPL-3.0-or-later. See LICENSES/xDrip-libkeks.md.
 */
package app.sugarlicious.sensor.auth.spi

interface IPluginDA {
    fun aNext(): Array<ByteArray?>?

    fun bNext(): Array<ByteArray?>

    fun cNext(): Array<ByteArray?>

    fun amConnected()

    fun bondNow(data: ByteArray): Boolean

    fun receivedResponse(data: ByteArray): Boolean

    fun receivedResponse2(data: ByteArray): Boolean

    fun receivedResponse3(data: ByteArray): Boolean

    fun receivedData(data: ByteArray): Boolean

    fun receivedData2(data: ByteArray): Boolean

    fun receivedData3(data: ByteArray): Boolean

    fun getPersistence(channel: Int): ByteArray?

    fun setPersistence(channel: Int, data: ByteArray?): Boolean

    fun getStatus(): String

    fun getName(): String
}
