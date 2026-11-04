package app.aapswear.g7watch

import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.UUID

/** Every launch owns its result identity; stale results cannot be relabelled as a newer request. */
internal class G7CompanionConsentLauncher(
    private val registry: ActivityResultRegistry,
    private val result: (request: Int, result: Int) -> Unit,
) {
    constructor(activity: ComponentActivity, result: (request: Int, result: Int) -> Unit) : this(activity.activityResultRegistry, result) {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = close()
            },
        )
    }

    // A replacement window never adopts obsolete keys; the VM reissues pending consent.
    private val windowKey = UUID.randomUUID().toString()
    private var sequence = 0L
    private var closed = false
    private val launches = mutableMapOf<String, ActivityResultLauncher<IntentSenderRequest>>()

    fun launch(sender: IntentSender, request: Int) {
        check(!closed) { "Consent window has been destroyed" }
        val key = "g7-companion:$windowKey:${++sequence}"
        val launcher =
            registry.register(key, ActivityResultContracts.StartIntentSenderForResult()) { response ->
                launches.remove(key)?.unregister()
                if (!closed) result(request, response.resultCode)
            }
        launches[key] = launcher
        try {
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        } catch (error: Exception) {
            launches.remove(key)?.unregister()
            throw error
        }
    }

    fun close() {
        closed = true
        launches.values.toList().forEach { it.unregister() }
        launches.clear()
    }
}
