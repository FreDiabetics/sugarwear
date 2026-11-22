package app.sugarwear

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/** Manual entry is available only in the privately installed debug build. */
@SuppressLint("WearPasswordInput")
internal fun manualSharePasswordField(context: Context): EditText? =
    EditText(context).apply {
        hint = "Dexcom-Passwort"
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        isSaveEnabled = false
        textSize = 14f
    }
