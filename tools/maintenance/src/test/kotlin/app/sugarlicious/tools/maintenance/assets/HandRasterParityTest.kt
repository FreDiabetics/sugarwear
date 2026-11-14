package app.sugarlicious.tools.maintenance.assets

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.*

/** Golden decoded RGBA hashes captured from original Pillow 11.3 render_orbit_hands.
 * PNG compression and metadata are deliberately outside the visual contract. */
class HandRasterParityTest {
    @Test fun everyHandAssetMatchesOriginalRgbaPixels() {
        val root = Path.of(System.getProperty("maintenance.sharedRoot"))
        val output = Files.createTempDirectory("native-hand-parity")
        handTool(listOf("--output", output.toString()), root)
        val expected =
            mapOf(
                "orbit_hand_preview_0.png" to "e69befba616255d705015097c2360cdd07867fb725381bd3cae8c7daedd7b0f7",
                "orbit_hand_preview_1.png" to "217ae2af2814b2cead4289120ea233f5c7bd0c2cdf338f6404f3098face00e7a",
                "orbit_hand_preview_10.png" to "4734a2f2514017dc02bc088f987b09101935123121294cab8a1dcadec3cf7ab2",
                "orbit_hand_preview_11.png" to "c9b9cafbc7053f72bfc13b1f8d5f9f83164f0973ba5cb2eb458484f5d2f01294",
                "orbit_hand_preview_12.png" to "8b5221c07bcea82180baa150506b2754e392370af54e508d4500893dd0649303",
                "orbit_hand_preview_13.png" to "4a98703d1b879313ae614d89e8ff194e6b85a40de0a2459779bec2f33f29ebfe",
                "orbit_hand_preview_14.png" to "a97e5c294e1ea8327b143432e96d5f9bd1576e9c949a152e58dd70d12c837b9d",
                "orbit_hand_preview_15.png" to "df75fc7f2c7efcb8ede486e4c9e5b37339c3b5c44408dd65c05dcef483a3cf94",
                "orbit_hand_preview_16.png" to "907f689d7571a28a6c1e6480863c932f98f8ed596bf9fb5729ae44b396c8980c",
                "orbit_hand_preview_17.png" to "61b7c290ae90b9f382e8d4d15716cd1e2785df8a9137d8ab7950aa5a3bb74628",
                "orbit_hand_preview_2.png" to "a27a1c957df585b3ee0e37c5f2ddd130102fa201466e1adb53aeda4b38852903",
                "orbit_hand_preview_3.png" to "cc0e77b80e8c0e4da1223d1d8e8cffc66d503bd5b062fb5c9e9c16377f7e8a04",
                "orbit_hand_preview_4.png" to "5aa8eb2e1d114c16b8898fef5bce65e232308b90804296db58c589dc7cc302a0",
                "orbit_hand_preview_5.png" to "7840d008fc38b47bfdb51e038d23e6a18dfe1db4092cfe862b9ce52f427dc9dd",
                "orbit_hand_preview_6.png" to "214bc93f83d279b30ec6a604d94f9f73c2591560d7567133351f96540ba1343d",
                "orbit_hand_preview_7.png" to "8218b67f9f122470c7e55dc31b18c4babda60d502af08c345ff5ac28403f651c",
                "orbit_hand_preview_8.png" to "f104b7c3cc8f6037dede490858fb06f05422a905bb7a0b7591b2950fd4a872ea",
                "orbit_hand_preview_9.png" to "9ca3c24680b112a18d6edfeed08ea7e6b1057b5f387fe4655b72cb1e18bf3e52",
                "orbit_hour_0.png" to "04a60055463ba7b7f57b58a2659d50cd01633325dfe02f66585bd5426790a67c",
                "orbit_hour_1.png" to "c38a15619a7fe368a1549cd543538c6659abf33a00de4fe2aea04240bd5cfdf9",
                "orbit_hour_10.png" to "26170697769d2782d3ca40b40240f3342e8ce9c431ae764f5331b5111291d704",
                "orbit_hour_11.png" to "0d8fa2cc2d44c98e1b7145d4c1919450b76747ee425aff7caa4c5d015504fc26",
                "orbit_hour_12.png" to "9dfa732ca92251c216a5777642f7cd9bd1486901d062bb8ef519f4dc4ce39964",
                "orbit_hour_13.png" to "ab71ab8b5eaed1619f5a87d30d9496d6dfec3ab932d013c29bb9e47d234d6bf5",
                "orbit_hour_14.png" to "411dcd5d163fe2b71897ba7d114c0df8252898f7d7ea87713ab374a4ed6a20d3",
                "orbit_hour_14_accent.png" to "886d4bd19998b7279fa6352f7ce1578a4ee79e557caeace3fc3324492d671831",
                "orbit_hour_2.png" to "2d9f01885f616267cd8d6b0710bc89fdc7e4529338d616aaeaf7c968d006b54a",
                "orbit_hour_3.png" to "168a9eb1fa3ba9ac822eee0be0789e8dc87486ec49f86361501b050f0264b705",
                "orbit_hour_4.png" to "2351cac396e02c4a8eecc415ffebd324af2869755b2834080405fa5f07cae032",
                "orbit_hour_5.png" to "b548d8d927ef616a256f0d1a4e494b90897c45dabb05dc24c525d3f0ea57713e",
                "orbit_hour_6.png" to "9c4ee3f3594f9a64dffbc077640c9a8ffca7a20f6363deab8e482ae51b6bbb94",
                "orbit_hour_6_accent.png" to "6802d03c573f3290205242285515a39154ab9da80dda8f3f0837f8a62bb488c2",
                "orbit_hour_7.png" to "8f4e6c152a2e95b78f10005ccb16229f29501292c8f12ffdabd0504718ec0b11",
                "orbit_hour_8.png" to "1e610d3fb573ee0750fbdf69d666f3ebababd7d46542492b579b05d2c767c69d",
                "orbit_hour_9.png" to "3967a16d52248cf2f78bc89a8d6df03dce9b2d8573e7191c22c8f77452f684da",
                "orbit_minute_0.png" to "9b9fb63d9725597417e186e935da71d8fafea272f16f135866d7328fdb717655",
                "orbit_minute_1.png" to "534bebf15a2675921602f662de8710299373080996a8e26881cfaf927031abd4",
                "orbit_minute_10.png" to "9f4d475901a1b515c0a90122e69e5821915e1077dbafb7a766637bcafc5523bd",
                "orbit_minute_11.png" to "204cf9f25d79c6b2130b0798f47e69228dddb0252870421ce020a57515eb1be9",
                "orbit_minute_12.png" to "010e779630f8263a819bd6ed3d9d55178974c85caafcc8f204067dba7366670e",
                "orbit_minute_13.png" to "7308e73f61f09ae5929477a376879213b77aa898b6d0faa3b553d7d3c47cbabf",
                "orbit_minute_14.png" to "fbed3732ee65b656679c0e0e3451003535a2ee96812cf303042bdc3165fee824",
                "orbit_minute_14_accent.png" to "479ae4f925aa3cffe25a276a4f93b7dbe5daf3020d9b05c4e434f08e7922b133",
                "orbit_minute_2.png" to "199e6bf9a994175774d191f74b75e7e890411ca9ba8bbda3e9ad24f8c1698fe9",
                "orbit_minute_3.png" to "db26337af8cbd2de832877b4cef7b1a084f6643ea43beb9bbc3bd6fdf4678266",
                "orbit_minute_4.png" to "a9faf78f85741928e1fef3bbb7d295a108f3767e50c0360616bddecf2bece9f5",
                "orbit_minute_5.png" to "460ed6d6ede4f5f29ffa01b87262fceb414887303ad44f8066d7235c55214eb6",
                "orbit_minute_6.png" to "0877fbfbde6eeff9ee92e86806e62107f5a1a07b591e681c7d61768ff3bf8e6c",
                "orbit_minute_6_accent.png" to "d33db1700b8c2a57f4f83c79b618c82fb27c17805329e6f7d0d32e29d18937ec",
                "orbit_minute_7.png" to "1427dbc88bb04125f702450dd391a919ee65aac7a103823d25d58b212ba9e33d",
                "orbit_minute_8.png" to "7a028cf5e49a7fcece92ee93800104b8df0a0cbd283cb5a7c45d4350d3755f58",
                "orbit_minute_9.png" to "2e61c59ae82172b0c53f18359371b9ac699a1f5a84e399d768a63b75b16f6132",
                "orbit_second_hand.png" to "4a9240466ba590be94e8639ddc8270976c127ee14954e01af2376d68d4f780e5",
            )
        val actual = Files.list(output).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() }
        assertEquals(expected.keys, actual)
        val mismatches = expected.filter { (name, digest) -> digest != rgbaHash(ImageIO.read(output.resolve(name).toFile())) }
        assertEquals(emptySet(), mismatches.keys, "Decoded RGBA mismatches; output: $output")
    }

    private fun rgbaHash(image: BufferedImage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                for (shift in intArrayOf(16, 8, 0, 24)) digest.update((pixel ushr shift).toByte())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
