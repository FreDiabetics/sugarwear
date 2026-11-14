package app.sugarlicious.wear

import app.sugarlicious.model.BasalDetailPresentation
import app.sugarlicious.model.BasalState
import app.sugarlicious.model.basalDetailPresentation
import java.util.Locale

internal typealias WearBasalDetails = BasalDetailPresentation

internal fun wearBasalDetails(basal: BasalState?, now: Long, locale: Locale = Locale.getDefault()): WearBasalDetails =
    basalDetailPresentation(basal, now, locale)
