package app.sugarlicious.uishared

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import java.util.Locale

/** Direct-value entry with platform-appropriate layout and shared validation. */
object SharedNumberEditor {
    fun show(
        activity: Activity,
        title: String,
        current: Int,
        min: Int,
        max: Int,
        onValue: (Int) -> Unit,
    ) = showText(
        activity = activity,
        title = title,
        current = String.format(Locale.ROOT, "%d", current),
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED,
        validate = { raw -> if (raw.trim().toIntOrNull()?.let { it in min..max } == true) null else "$min bis $max" },
        onValue = { onValue(it.trim().toInt()) },
    )

    fun showText(
        activity: Activity,
        title: String,
        current: String,
        inputType: Int,
        validate: (String) -> String?,
        onValue: (String) -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            WearNumberEditor.show(activity, title, current, inputType, validate, onValue, onDismiss)
            return
        }
        val input =
            EditText(activity).apply {
                this.inputType = inputType
                setText(current)
                setSelection(length())
                setSelectAllOnFocus(true)
            }
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen", null)
                .create()
        dialog.setOnDismissListener { onDismiss() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString()
                val error = validate(raw)
                if (error != null) {
                    input.error = error
                } else {
                    onValue(raw)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }
}
