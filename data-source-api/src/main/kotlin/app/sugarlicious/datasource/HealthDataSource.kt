package app.sugarlicious.datasource
import app.sugarlicious.model.TherapyDisplayState

fun interface HealthDataSource {
    fun latest(): TherapyDisplayState?
}
