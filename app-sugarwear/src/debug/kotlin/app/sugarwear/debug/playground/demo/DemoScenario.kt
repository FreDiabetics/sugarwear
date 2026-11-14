package app.sugarwear.debug.playground.demo

/**
 * Deterministic synthetic glucose scenarios for playground previews and playback.
 */
enum class DemoScenario(
    val id: String,
    val title: String,
    val description: String,
    val startGlucoseMgDl: Double,
    val deltaPerIntervalMgDl: Double,
    val minGlucoseMgDl: Double = 40.0,
    val maxGlucoseMgDl: Double = 400.0,
) {
    STABLE("scenario_stable", "Stabile Werte", "Normale Glukosewerte um 110 mg/dL", 110.0, 0.0),
    SLOW_RISE("scenario_slow_rise", "Langsamer Anstieg", "Glukose steigt leicht an", 120.0, 2.5),
    FAST_RISE("scenario_fast_rise", "Schneller Anstieg", "Starker Glukoseanstieg über 180 mg/dL", 140.0, 6.0),
    SLOW_FALL("scenario_slow_fall", "Langsamer Abfall", "Glukose fällt leicht ab", 110.0, -2.5),
    FAST_FALL("scenario_fast_fall", "Schneller Abfall", "Starker Glukoseabfall unter 80 mg/dL", 130.0, -6.0),
    HYPOGLYCEMIA("scenario_hypo", "Hypoglykämie (Tief)", "Kritisch niedriger Wert um 55 mg/dL", 65.0, -2.0, minGlucoseMgDl = 45.0),
    HYPERGLYCEMIA("scenario_hyper", "Hyperglykämie (Hoch)", "Kritisch hoher Wert um 260 mg/dL", 220.0, 4.0, maxGlucoseMgDl = 350.0),
    FLUCTUATING("scenario_fluctuating", "Schwankender Verlauf", "Starke Schwankungen im Verlauf", 110.0, 8.0),
    MISSING_VALUES("scenario_missing", "Datenlücken", "Verlauf mit fehlenden Messwerten", 120.0, 1.0),
    STALE_DATA("scenario_stale", "Veraltete Daten", "Letzter Wert liegt 20 Minuten zurück", 105.0, 0.0),
    DISCONNECTED("scenario_disconnected", "Keine Verbindung", "Keine aktuellen Messwerte verfügbar", 0.0, 0.0),
}
