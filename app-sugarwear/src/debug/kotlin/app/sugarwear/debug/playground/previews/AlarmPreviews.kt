package app.sugarwear.debug.playground.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.sugarwear.CgmAlarmScreenContent
import app.sugarwear.SugarWearAlarmType
import app.sugarwear.debug.playground.demo.DemoAlarmFactory

@Preview(name = "Alarm Screen - Low", device = "id:wearos_small_round")
@Composable
fun PreviewAlarmLow() {
    val state = DemoAlarmFactory.createTestAlarmState(SugarWearAlarmType.LOW, glucoseMgDl = 58.0)
    CgmAlarmScreenContent(
        state = state,
        onAcknowledge = {},
        onSnooze = {},
        onAdjustSnooze = {},
        onMute = {},
        onVibrate = {},
    )
}

@Preview(name = "Alarm Screen - High", device = "id:wearos_large_round")
@Composable
fun PreviewAlarmHigh() {
    val state = DemoAlarmFactory.createTestAlarmState(SugarWearAlarmType.HIGH, glucoseMgDl = 250.0)
    CgmAlarmScreenContent(
        state = state,
        onAcknowledge = {},
        onSnooze = {},
        onAdjustSnooze = {},
        onMute = {},
        onVibrate = {},
    )
}

@Preview(name = "Alarm Screen - Technical Warning", device = "id:wearos_small_round")
@Composable
fun PreviewAlarmTechnicalWarning() {
    val state = DemoAlarmFactory.createTestAlarmState(SugarWearAlarmType.TECHNICAL_WARNING, glucoseMgDl = 110.0)
    CgmAlarmScreenContent(
        state = state,
        onAcknowledge = {},
        onSnooze = {},
        onAdjustSnooze = {},
        onMute = {},
        onVibrate = {},
    )
}
