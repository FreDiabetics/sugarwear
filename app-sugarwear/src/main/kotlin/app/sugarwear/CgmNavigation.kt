package app.sugarwear

import android.content.Context
import android.content.Intent

internal fun cgmOpenAppIntent(context: Context): Intent =
    Intent(context, CgmWatchActivity::class.java)
