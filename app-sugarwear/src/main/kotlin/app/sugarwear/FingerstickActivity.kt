package app.sugarwear

import android.app.Application
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import org.json.JSONObject

internal class FingerstickModel(
    application: Application,
    private val state: SavedStateHandle,
) : AndroidViewModel(application) {
    private val id = state.get<String>("id") ?: UUID.randomUUID().toString().also { state["id"] = it }
    var measuredAt: Long
        get() = checkNotNull(state["measuredAt"])
        set(value) { state["measuredAt"] = value }

    init {
        if (!state.contains("measuredAt")) measuredAt = System.currentTimeMillis()
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
                val store = getApplication<Application>().getSharedPreferences("fingerstick_events", 0)
                val entry = JSONObject().put("value", value).put("unit", unit).put("measuredAt", time)
                store.contains(id) || store.edit().putString(id, entry.toString()).commit()
            }
            status.value = if (saved) Status.SAVED else Status.ERROR
        }
    }
}

class FingerstickActivity : ComponentActivity() {
    private val model by viewModels<FingerstickModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val inset = (resources.displayMetrics.widthPixels * 0.14f).toInt()
            setPadding(inset, inset, inset, inset)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(ScrollView(this).apply { addView(panel) })
        panel.addView(TextView(this).apply { setText(R.string.fingerstick_log) })
        val input = EditText(this).apply {
            id = R.id.fingerstick_input
            setHint(R.string.fingerstick_value)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
        }
        panel.addView(input)
        val unit = Button(this).apply {
            text = savedInstanceState?.getString("unit") ?: "mg/dl"
            setOnClickListener { text = if (text == "mg/dl") "mmol/l" else "mg/dl" }
            tag = "fingerstick-unit"
        }
        panel.addView(unit)
        val time = Button(this)
        fun updateTime() {
            time.text = getString(R.string.fingerstick_time, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(model.measuredAt)))
        }
        updateTime()
        time.setOnClickListener {
            val clock = Calendar.getInstance().apply { timeInMillis = model.measuredAt }
            TimePickerDialog(this, { _, hour, minute ->
                clock.set(Calendar.HOUR_OF_DAY, hour)
                clock.set(Calendar.MINUTE, minute)
                clock.set(Calendar.SECOND, 0)
                clock.set(Calendar.MILLISECOND, 0)
                if (clock.timeInMillis > System.currentTimeMillis()) clock.add(Calendar.DATE, -1)
                model.measuredAt = clock.timeInMillis
                updateTime()
            }, clock.get(Calendar.HOUR_OF_DAY), clock.get(Calendar.MINUTE), true).show()
        }
        panel.addView(time)
        val save = Button(this).apply {
            setText(R.string.fingerstick_save)
            setTextColor(0xFF22EB60.toInt())
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
