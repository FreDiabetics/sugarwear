package app.sugarwear

import org.junit.Assert.*
import org.junit.Test

class SugarWearAlarmStateMachineTest {

    @Test
    fun `transition from inactive to active for immediate alarm`() {
        val rule = SugarWearAlarmDefaults.defaultRule(SugarWearAlarmType.LOW)
        val now = 1_000_000L

        val updated = SugarWearAlarmStateMachine.update(
            previous = null,
            type = SugarWearAlarmType.LOW,
            rule = rule,
            conditionMet = true,
            glucoseMgDl = 65.0,
            sensorId = "s1",
            sessionId = "ss1",
            source = "WATCH",
            measuredAtEpochMs = now,
            receivedAtEpochMs = now,
            technicalErrorSignature = null,
            nowEpochMs = now,
        )

        assertEquals(SugarWearAlarmState.ACTIVE, updated.state)
        assertEquals(now, updated.triggeredAtEpochMs)
        assertEquals(65.0, updated.triggerGlucoseMgDl!!, 0.0)
    }

    @Test
    fun `transition from active to snoozed and back to active upon snooze expiration`() {
        val rule = SugarWearAlarmDefaults.defaultRule(SugarWearAlarmType.LOW).copy(snoozeMinutes = 10)
        val now = 1_000_000L

        val active = SugarWearAlarmInstance(
            type = SugarWearAlarmType.LOW,
            state = SugarWearAlarmState.ACTIVE,
            triggeredAtEpochMs = now,
        )

        val snoozed = active.copy(
            state = SugarWearAlarmState.SNOOZED,
            snoozedUntilEpochMs = now + 10 * 60_000L,
        )

        // 9 min later condition still met -> stays snoozed
        val step1 = SugarWearAlarmStateMachine.update(
            previous = snoozed,
            type = SugarWearAlarmType.LOW,
            rule = rule,
            conditionMet = true,
            glucoseMgDl = 65.0,
            sensorId = "s1",
            sessionId = "ss1",
            source = "WATCH",
            measuredAtEpochMs = now + 9 * 60_000L,
            receivedAtEpochMs = now + 9 * 60_000L,
            technicalErrorSignature = null,
            nowEpochMs = now + 9 * 60_000L,
        )
        assertEquals(SugarWearAlarmState.SNOOZED, step1.state)

        // 10 min later condition still met -> ACTIVE
        val step2 = SugarWearAlarmStateMachine.update(
            previous = step1,
            type = SugarWearAlarmType.LOW,
            rule = rule,
            conditionMet = true,
            glucoseMgDl = 65.0,
            sensorId = "s1",
            sessionId = "ss1",
            source = "WATCH",
            measuredAtEpochMs = now + 10 * 60_000L,
            receivedAtEpochMs = now + 10 * 60_000L,
            technicalErrorSignature = null,
            nowEpochMs = now + 10 * 60_000L,
        )
        assertEquals(SugarWearAlarmState.ACTIVE, step2.state)
    }
}
