package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element

/** Native WFF motion; no app timer or background process is involved. */
internal fun configureOrbitSecondsMotion(face: Element, strings: Element) {
    val configs = face.need("UserConfigurations")
    if (configs.one("ListConfiguration[@id='secondsMotion']") == null) {
        val config = configs.add("ListConfiguration", "id" to "secondsMotion", "displayName" to "secondsMotion", "defaultValue" to "0")
        config.add("ListOption", "id" to "0", "displayName" to "secondsMotion_0")
        config.add("ListOption", "id" to "1", "displayName" to "secondsMotion_1")
    }
    listOf("secondsMotion" to "Zeigerlauf", "secondsMotion_0" to "Gleitend", "secondsMotion_1" to "Tickend").forEach { (name, text) ->
        if (strings.one("string[@name='$name']") == null) strings.add("string", "name" to name).textContent = text
    }
    face.need("Scene/Group[@name='hands']").all(".//SecondHand[Sweep]").forEach { hand ->
        val clock = hand.parentNode as Element
        val parent = clock.parentNode as Element
        if (parent.attr("name").startsWith("seconds-motion-")) return@forEach
        listOf(false, true).forEach { ticking ->
            val wrapper = parent.add("Group", "name" to "seconds-motion-${parent.attr("name")}-${if (ticking) "tick" else "smooth"}", "x" to 0, "y" to 0, "width" to 512, "height" to 512)
            wrapper.add("Transform", "target" to "alpha", "value" to "[CONFIGURATION.secondsMotion] == \"${if (ticking) 1 else 0}\" ? 255 : 0")
            val copy = clock.cloneNode(true) as Element
            if (ticking) {
                val second = copy.need("SecondHand")
                second.need("Sweep").remove()
                second.add("Tick", "duration" to "0.1", "strength" to "0.2")
            }
            wrapper.appendChild(copy)
            parent.insertBefore(wrapper, clock)
        }
        clock.remove()
    }
}
