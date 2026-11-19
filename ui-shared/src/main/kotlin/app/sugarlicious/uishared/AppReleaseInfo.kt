package app.sugarlicious.uishared

import android.content.Context

/** About pages use the installed package version, not a shared or hardcoded suite version. */
object AppReleaseInfo {
    enum class Product { MOBILE, WEAR, COLLECTOR }

    fun version(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unbekannt"

    fun changes(product: Product): List<String> =
        when (product) {
            Product.MOBILE -> listOf("Kompakte Untermenüs mit begrenztem Scrollbereich.", "Systemskalierung für Widgets und Vorschauen.", "Kleinere Complication-Vorschautexte im Einstellungsstil.", "Übertragungsprotokoll unter Datenverwaltung.")
            Product.WEAR -> listOf("Übersicht passt sich größerer Systemschrift an.", "Einheitliche Regler und scrollsichere Schalter.", "Graph-Texte in Tiles folgen der Systemschriftgröße.", "Aktualisierte Watchface-Vorlagen und Vorschauen.")
            Product.COLLECTOR -> listOf("Einheitliche Regler und scrollsichere Schalter.", "Besser lesbare Auswahlmenüs.", "Vigil mit überarbeiteten Minutenmarkierungen und einheitlicher Vorschau-Zeigerstellung.", "Stabile Vorschau im nativen Watchface-Auswahlmenü.")
        }

    fun summary(context: Context, product: Product): String =
        buildString {
            append("Version ").append(version(context))
            val changes = changes(product)
            if (changes.isNotEmpty()) append("\n\nNeu\n").append(changes.joinToString("\n") { "• $it" })
        }
}
