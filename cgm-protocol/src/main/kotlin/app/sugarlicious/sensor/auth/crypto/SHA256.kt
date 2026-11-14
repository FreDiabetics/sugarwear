package app.sugarlicious.sensor.auth.crypto

/*
 * MIT License
 * <p>
 * Copyright (c) 2017 Fabian Meyer
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.nio.ByteBuffer
import java.nio.IntBuffer

/** Kotlin translation retaining the existing algorithm and class-level working-array lock. */
class SHA256 private constructor() {
    companion object {
        private val constants =
            intArrayOf(
                0x428a2f98,
                0x71374491,
                0xb5c0fbcf.toInt(),
                0xe9b5dba5.toInt(),
                0x3956c25b,
                0x59f111f1,
                0x923f82a4.toInt(),
                0xab1c5ed5.toInt(),
                0xd807aa98.toInt(),
                0x12835b01,
                0x243185be,
                0x550c7dc3,
                0x72be5d74,
                0x80deb1fe.toInt(),
                0x9bdc06a7.toInt(),
                0xc19bf174.toInt(),
                0xe49b69c1.toInt(),
                0xefbe4786.toInt(),
                0x0fc19dc6,
                0x240ca1cc,
                0x2de92c6f,
                0x4a7484aa,
                0x5cb0a9dc,
                0x76f988da,
                0x983e5152.toInt(),
                0xa831c66d.toInt(),
                0xb00327c8.toInt(),
                0xbf597fc7.toInt(),
                0xc6e00bf3.toInt(),
                0xd5a79147.toInt(),
                0x06ca6351,
                0x14292967,
                0x27b70a85,
                0x2e1b2138,
                0x4d2c6dfc,
                0x53380d13,
                0x650a7354,
                0x766a0abb,
                0x81c2c92e.toInt(),
                0x92722c85.toInt(),
                0xa2bfe8a1.toInt(),
                0xa81a664b.toInt(),
                0xc24b8b70.toInt(),
                0xc76c51a3.toInt(),
                0xd192e819.toInt(),
                0xd6990624.toInt(),
                0xf40e3585.toInt(),
                0x106aa070,
                0x19a4c116,
                0x1e376c08,
                0x2748774c,
                0x34b0bcb5,
                0x391c0cb3,
                0x4ed8aa4a,
                0x5b9cca4f,
                0x682e6ff3,
                0x748f82ee,
                0x78a5636f,
                0x84c87814.toInt(),
                0x8cc70208.toInt(),
                0x90befffa.toInt(),
                0xa4506ceb.toInt(),
                0xbef9a3f7.toInt(),
                0xc67178f2.toInt(),
                0x6a09e667,
                0xbb67ae85.toInt(),
                0x3c6ef372,
                0xa54ff53a.toInt(),
                0x510e527f,
                0x9b05688c.toInt(),
                0x1f83d9ab,
                0x5be0cd19,
            )
        private const val BLOCK_BITS = 512
        private const val BLOCK_BYTES = BLOCK_BITS / 8
        private val schedule = IntArray(64)
        private val hashState = IntArray(8)

        @JvmStatic
        fun hash(message: ByteArray): ByteArray =
            synchronized(SHA256::class.java) {
                val temporary = IntArray(8)
                System.arraycopy(constants, constants.size - 8, hashState, 0, hashState.size)
                val words = pad(message)
                for (block in 0 until words.size / 16) {
                    System.arraycopy(words, block * 16, schedule, 0, 16)
                    for (round in 16 until schedule.size) {
                        schedule[round] = smallSig1(schedule[round - 2]) + schedule[round - 7] +
                            smallSig0(schedule[round - 15]) + schedule[round - 16]
                    }
                    System.arraycopy(hashState, 0, temporary, 0, hashState.size)
                    for (round in schedule.indices) {
                        val t1 =
                            temporary[7] + bigSig1(temporary[4]) + ch(temporary[4], temporary[5], temporary[6]) +
                                constants[round] + schedule[round]
                        val t2 = bigSig0(temporary[0]) + maj(temporary[0], temporary[1], temporary[2])
                        System.arraycopy(temporary, 0, temporary, 1, temporary.size - 1)
                        temporary[4] += t1
                        temporary[0] = t1 + t2
                    }
                    for (index in hashState.indices) hashState[index] += temporary[index]
                }
                toByteArray(hashState)
            }

        private fun pad(message: ByteArray): IntArray {
            val finalBlockLength = message.size % BLOCK_BYTES
            val blockCount = message.size / BLOCK_BYTES + if (finalBlockLength + 1 + 8 > BLOCK_BYTES) 2 else 1
            val result = IntBuffer.allocate(blockCount * (BLOCK_BYTES / 4))
            val input = ByteBuffer.wrap(message)
            for (index in 0 until message.size / 4) result.put(input.int)
            val remainder = ByteBuffer.allocate(4)
            remainder.put(input).put(0x80.toByte()).rewind()
            result.put(remainder.int)
            result.position(result.capacity() - 2)
            val messageLength = message.size * 8L
            result.put((messageLength ushr 32).toInt())
            result.put(messageLength.toInt())
            return result.array()
        }

        private fun toByteArray(values: IntArray): ByteArray {
            val output = ByteBuffer.allocate(values.size * Int.SIZE_BYTES)
            for (value in values) output.putInt(value)
            return output.array()
        }

        private fun ch(x: Int, y: Int, z: Int): Int = (x and y) or (x.inv() and z)

        private fun maj(x: Int, y: Int, z: Int): Int = (x and y) or (x and z) or (y and z)

        private fun rotateRight(value: Int, distance: Int): Int = (value ushr distance) or (value shl -distance)

        private fun bigSig0(value: Int): Int = rotateRight(value, 2) xor rotateRight(value, 13) xor rotateRight(value, 22)

        private fun bigSig1(value: Int): Int = rotateRight(value, 6) xor rotateRight(value, 11) xor rotateRight(value, 25)

        private fun smallSig0(value: Int): Int = rotateRight(value, 7) xor rotateRight(value, 18) xor (value ushr 3)

        private fun smallSig1(value: Int): Int = rotateRight(value, 17) xor rotateRight(value, 19) xor (value ushr 10)
    }
}
