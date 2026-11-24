package app.sugarwear

import android.app.Application
import android.widget.TimePicker
import app.sugarlicious.uishared.WearSettingsUi
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FingerstickModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {
    private val journal = FingerstickJournal(application)
    private val id = state.get<String>("id") ?: UUID.randomUUID().toString().also { state["id"] = it }
    var measuredAt: Long
        get() = checkNotNull(state["measuredAt"])
        set(value) { state["measuredAt"] = value }

    init {
        if (!state.contains("measuredAt")) measuredAt = System.currentTimeMillis()
    }

    override fun onCleared() {
        journal.close()
    }

    enum class Status { READY, SAVING, SAVED, INVALID, ERROR }

    val status = MutableStateFlow(Status.READY)

    fun save(raw: String, unit: String) {
        if (status.value == Status.SAVING || status.value == Status.SAVED) return
        val value = raw.trim().replace(',', '.')
        if (!Regex("[0-9]+([.][0-9]+)?").matches(value) || value.toBigDecimalOrNull()?.signum() != 1 || measuredAt > System.currentTimeMillis()) {
            status.value = Status.INVALID
            return
        }
        val time = measuredAt
        status.value = Status.SAVING
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    journal.record(id, value, unit, time)
                    true
                } catch (_: android.database.SQLException) {
                    false
                }
            }
            status.value = if (saved) Status.SAVED else Status.ERROR
        }
    }
}

class FingerstickActivity : ComponentActivity() {
    private val model by viewModels<FingerstickModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = CgmAppearanceStore(this).load()
        val surface = palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE)
        val foreground = palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY)
        val accent = palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            WearSettingsUi.pagePadding(this)
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(WearSettingsUi.pageHeader(context, getString(R.string.fingerstick_log), foreground))
        }
        setContentView(CgmEdgeFadeScrollView(this).apply { isFillViewport = true; addView(panel) }.applyCgmEdgeFade())
        val input = EditText(this).apply {
            id = R.id.fingerstick_input
            setTextColor(foreground)
            setHintTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY))
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
            setHint(R.string.fingerstick_value)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
        }
        panel.addView(input)
        val unit = Button(this).apply {
            text = savedInstanceState?.getString("unit") ?: "mg/dl"
            setOnClickListener { text = if (text == "mg/dl") "mmol/l" else "mg/dl" }
            WearSettingsUi.actionButton(this, surface, foreground)
            tag = "fingerstick-unit"
        }
        panel.addView(unit)
        val time = Button(this).apply { WearSettingsUi.actionButton(this, surface, foreground) }
        fun updateTime() {
            time.text = getString(R.string.fingerstick_time, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(model.measuredAt)))
        }
        updateTime()
        time.setOnClickListener {
            val clock = Calendar.getInstance().apply { timeInMillis = model.measuredAt }
            val picker = TimePicker(android.view.ContextThemeWrapper(this, android.R.style.Theme_Material)).apply {
                setIs24HourView(true)
                hour = clock.get(Calendar.HOUR_OF_DAY)
                minute = clock.get(Calendar.MINUTE)
            }
            showCgmSettingsDialog(getString(R.string.fingerstick_measurement_time), picker, confirm = getString(android.R.string.ok)) { dialog ->
                picker.clearFocus()
                clock.set(Calendar.HOUR_OF_DAY, picker.hour)
                clock.set(Calendar.MINUTE, picker.minute)
                clock.set(Calendar.SECOND, 0)
                clock.set(Calendar.MILLISECOND, 0)
                if (clock.timeInMillis > System.currentTimeMillis()) clock.add(Calendar.DATE, -1)
                model.measuredAt = clock.timeInMillis
                updateTime()
                dialog.dismiss()
            }
        }
        panel.addView(time)
        val save = Button(this).apply {
            setText(R.string.fingerstick_save)
            WearSettingsUi.actionButton(this, surface, accent)
            setOnClickListener { model.save(input.text.toString(), unit.text.toString()) }
        }
        panel.addView(save)
        lifecycleScope.launch {
            model.status.collect { status ->
                save.isEnabled = status != FingerstickModel.Status.SAVING
                when (status) {
                    FingerstickModel.Status.SAVED -> {
                        Toast.makeText(this@FingerstickActivity, R.string.fingerstick_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    FingerstickModel.Status.INVALID -> input.error = getString(R.string.fingerstick_invalid)
                    FingerstickModel.Status.ERROR -> input.error = getString(R.string.fingerstick_error)
                    else -> Unit
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val unit = (window.decorView as android.view.ViewGroup).findViewWithTag<Button>("fingerstick-unit")
        outState.putString("unit", unit.text.toString())
    }
}
