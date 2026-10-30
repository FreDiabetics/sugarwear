package app.aapswear.g7watch

import android.content.Context
import android.content.Intent

internal fun g7OpenAppIntent(context: Context): Intent =
    Intent(context, G7WatchActivity::class.java)
