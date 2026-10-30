package app.aapswear.g7watch

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.test.core.app.ApplicationProvider
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7CompanionBondAccessTest {
    @Test fun `successful sensor bond does not require an extra companion association`() {
        val backend = Backend().apply { bonded.add(first) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 36)
        var ready = 0
        access.ensureForAuthenticatedSensor(first, { ready++ }, { error(it) })
        assertEquals(1, ready)
        assertTrue(backend.requested.isEmpty())
    }

    @Test fun `unbonded sensor cannot complete Bluetooth access check`() {
        val backend = Backend()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 36)
        var ready = 0
        var failed = 0
        access.ensureForAuthenticatedSensor(first, { ready++ }, { failed++ })
        assertEquals(0, ready)
        assertEquals(1, failed)
        assertTrue(backend.requested.isEmpty())
    }

    private val first = "AA:BB:CC:DD:EE:01"
    private val second = "AA:BB:CC:DD:EE:02"

    private class Backend : G7CompanionAssociationBackend {
        val owned = mutableListOf<G7OwnedCompanionAssociation>()
        val bonded = mutableSetOf<String>()
        val requested = mutableListOf<String>()
        var created: ((String?) -> Unit)? = null
        var failed: (() -> Unit)? = null
        var cancelled = 0

        override fun ownedAssociations() = owned.toList()

        override fun isBonded(address: String) = address in bonded

        override fun associate(address: String, pending: (IntentSender) -> Unit, created: (String?) -> Unit, failed: () -> Unit) {
            requested.add(address)
            this.created = created
            this.failed = failed
        }

        override fun cancelPending() {
            cancelled++
        }
    }

    @Test fun `legacy platform needs no association and never requests consent`() {
        val backend = Backend()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var ready = 0
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 35, unlinkAddresses = { error("must not read Bluetooth") })

        access.ensureForUnlink({ ready++ }, { error(it) })

        assertEquals(1, ready)
        assertTrue(backend.requested.isEmpty())
    }

    @Test fun `old bonded peers require sequential exact associations and skip existing ownership`() {
        val backend =
            Backend().apply {
                bonded.addAll(listOf(first, second))
                owned.add(G7OwnedCompanionAssociation(7, first.lowercase()))
            }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 36, unlinkAddresses = { linkedSetOf(first, second) })
        var ready = 0

        access.ensureForUnlink({ ready++ }, { error(it) })
        assertEquals(listOf(second), backend.requested)
        assertEquals(0, ready)
        backend.created!!.invoke(second.lowercase())

        assertEquals(1, ready)
        assertEquals(setOf(first, second), backend.bonded)
    }

    @Test fun `mismatched association cannot authorize unlink`() {
        val backend = Backend().apply { bonded.add(first) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 36, unlinkAddresses = { setOf(first) })
        var ready = 0
        var failures = 0

        access.ensureForUnlink({ ready++ }, { failures++ })
        backend.created!!.invoke(second)
        backend.created!!.invoke(first)

        assertEquals(0, ready)
        assertEquals(1, failures)
    }

    @Test fun `cancelled consent ignores late callbacks and fails exactly once`() {
        val backend = Backend().apply { bonded.add(first) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val access = G7CompanionBondAccess(activity, backend, sdkInt = 36, unlinkAddresses = { setOf(first) })
        var ready = 0
        var failures = 0
        access.ensureForUnlink({ ready++ }, { failures++ })
        val lateCreated = backend.created!!
        assertTrue(access.onActivityResult(G7CompanionBondAccess.REQUEST_ASSOCIATION, Activity.RESULT_CANCELED))
        access.cancel()
        lateCreated(first)
        backend.failed!!.invoke()

        assertEquals(0, ready)
        assertEquals(1, failures)
        assertEquals(1, backend.cancelled)
        assertTrue(backend.bonded.contains(first))
        assertFalse(access.onActivityResult(17, Activity.RESULT_CANCELED))
    }

    @Test fun `public removal disassociates only matching owned records after confirmed bond disappearance`() {
        val associations = listOf(G7OwnedCompanionAssociation(1, first), G7OwnedCompanionAssociation(2, second), G7OwnedCompanionAssociation(3, first.lowercase()))
        val events = mutableListOf<String>()

        val result =
            removeConfirmedG7CompanionBond(
                first,
                associations,
                {
                    events.add("remove:$it")
                    true
                },
                {
                    events.add("confirmed")
                    true
                },
                { events.add("disassociate:$it") },
            )

        assertEquals(G7BondRemovalStatus.REMOVED, result)
        assertEquals(listOf("remove:1", "confirmed", "disassociate:1", "disassociate:3"), events)
    }

    @Test fun `declined or unconfirmed public removal preserves associations`() {
        for (requested in listOf(false, true)) {
            var removed = 0
            assertEquals(
                G7BondRemovalStatus.FAILED,
                removeConfirmedG7CompanionBond(first, listOf(G7OwnedCompanionAssociation(1, first)), { requested }, { false }, { removed++ }),
            )
            assertEquals(0, removed)
        }
    }

    @Test fun `already gone bond cleans only matching associations without another removal request`() {
        val removed = mutableListOf<Int>()
        val result =
            removeConfirmedG7CompanionBond(
                first,
                listOf(G7OwnedCompanionAssociation(1, first), G7OwnedCompanionAssociation(2, second)),
                { error("bond is already gone") },
                { error("no further bond wait needed") },
                { removed.add(it) },
                alreadyGone = true,
            )

        assertEquals(G7BondRemovalStatus.REMOVED, result)
        assertEquals(listOf(1), removed)
        assertEquals(
            G7BondRemovalStatus.REMOVED,
            removeConfirmedG7CompanionBond(first, emptyList(), { error("no association") }, { false }, {}, alreadyGone = true),
        )
    }

    @Test fun `association cleanup failure reports failed even when bond is already gone`() {
        assertEquals(
            G7BondRemovalStatus.FAILED,
            removeConfirmedG7CompanionBond(
                first,
                listOf(G7OwnedCompanionAssociation(1, first)),
                { error("bond is already gone") },
                { false },
                { throw SecurityException("cleanup denied") },
                alreadyGone = true,
            ),
        )
    }

    @Test fun `unlink candidates retain orphaned shared address without opening encrypted credentials`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        G7SensorStateStore(context).save(G7PersistedState(sensor = G7Sensor("configured", deviceAddress = first)))
        context
            .getSharedPreferences("g7_credentials", Context.MODE_PRIVATE)
            .edit()
            .putString("shared_address", second)
            .commit()

        assertEquals(setOf(first, second), g7SensorUnlinkAddresses(context, bondedAddresses = { emptySet() }))
    }
}
