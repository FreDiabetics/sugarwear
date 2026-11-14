package app.sugarlicious.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RevisionedStateRepositoryTest {
    @Test
    fun `reconcile repairs the older copy and write advances from the persisted maximum`() =
        runBlocking {
            val backend = FakeBackend(primary = Versioned("phone", 5), secondary = Versioned("display", 4))
            val repository =
                RevisionedStateRepository(
                    backend = backend,
                    revisionOf = Versioned::revision,
                    withRevision = { value, revision -> value.copy(revision = revision) },
                )

            assertEquals(Versioned("phone", 5), repository.read())
            assertEquals(Versioned("phone", 5), backend.secondary)

            val written = repository.write(Versioned("incoming", 1))
            assertEquals(Versioned("incoming", 6), written)
            assertEquals(written, backend.primary)
            assertEquals(written, backend.secondary)
        }

    private data class Versioned(
        val value: String,
        val revision: Long,
    )

    private class FakeBackend(
        var primary: Versioned?,
        var secondary: Versioned?,
    ) : RevisionedStateBackend<Versioned> {
        override suspend fun readPrimary() = primary

        override suspend fun readSecondary() = secondary

        override suspend fun writePrimary(value: Versioned) {
            primary = value
        }

        override suspend fun writeSecondary(value: Versioned) {
            secondary = value
        }
    }
}
