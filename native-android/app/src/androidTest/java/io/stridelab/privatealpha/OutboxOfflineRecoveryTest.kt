package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Existing production SQLite v1 outbox: durability tests, no remote replay assumed. */
@RunWith(AndroidJUnit4::class)
class OutboxOfflineRecoveryTest {
    private fun withDb(action:(android.content.Context,String)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="stridelab-outbox-isolated-"+System.nanoTime()+".db"
        context.deleteDatabase(name)
        try { action(context,name) } finally { context.deleteDatabase(name) }
    }

    @Test fun committedLocalEditAndOperationSurviveCloseAndReopen() = withDb { context,name ->
        TrainingStore(context,name).use { store ->
            store.enqueue("athlete-a","plan","training","stable-op-a",
                "{\"revision\":1}",null,100)
            assertEquals("{\"revision\":1}",store.get("athlete-a","plan","training")!!.payload)
            assertEquals(listOf("stable-op-a"),store.ready("athlete-a",50).map { it.operationId })
        }
        TrainingStore(context,name).use { reopened ->
            assertEquals("{\"revision\":1}",reopened.get("athlete-a","plan","training")!!.payload)
            assertEquals("stable-op-a",reopened.ready("athlete-a",50).single().operationId)
            assertNull(reopened.get("athlete-b","plan","training"))
            assertTrue(reopened.ready("athlete-b",50).isEmpty())
        }
    }

    @Test fun interruptedRemoteAckMustNotEraseLaterLocalEdits() = withDb { context,name ->
        TrainingStore(context,name).use { store ->
            store.enqueue("athlete-a","plan","training","op-1",
                "{\"revision\":1}",null,100)
            store.enqueue("athlete-a","plan","training","op-2",
                "{\"revision\":2}",null,101)
            assertEquals(listOf("op-1"),store.ready("athlete-a",10).map { it.operationId })
        }
        TrainingStore(context,name).use { reopened ->
            // Mimic a confirmed server reply from an explicitly mocked endpoint,
            // NOT a real Supabase write.
            assertTrue(reopened.acknowledge("athlete-a","op-1",
                "{\"revision\":1}","remote-v1",120))
            assertEquals("{\"revision\":2}",
                reopened.get("athlete-a","plan","training")!!.payload)
            assertEquals("op-2",reopened.ready("athlete-a",10).single().operationId)
            assertEquals("remote-v1",reopened.ready("athlete-a",10).single().baseVersion)
        }
        TrainingStore(context,name).use { reopenedAgain ->
            assertEquals("op-2",reopenedAgain.ready("athlete-a",10).single().operationId)
            assertFalse(reopenedAgain.acknowledge("athlete-a","op-1",
                "{\"revision\":1}","remote-v1",130))
        }
    }

    @Test fun wrongAccountCannotAcknowledgePendingOperation() = withDb { context,name ->
        TrainingStore(context,name).use { store ->
            store.enqueue("athlete-a","plan","training","private-op",
                "{\"revision\":5}",null,100)
            assertFalse(store.acknowledge("athlete-b","private-op",
                "{\"revision\":5}","server-v5",110))
            assertEquals("private-op",store.ready("athlete-a",10).single().operationId)
            assertTrue(store.ready("athlete-b",10).isEmpty())
            assertNull(store.get("athlete-b","plan","training"))
        }
    }

    @Test fun failedWriteNeverProducesVisibleOrQueuedRecord() = withDb { context,name ->
        TrainingStore(context,name).use { store ->
            store.enqueue("athlete-a","plan","training","immutable-op",
                "{\"revision\":1}",null,100)
            assertThrows(IllegalArgumentException::class.java) {
                store.enqueue("athlete-a","plan","other","immutable-op",
                    "{\"revision\":2}",null,101)
            }
            assertNull(store.get("athlete-a","plan","other"))
            assertEquals(1,store.ready("athlete-a",50).size)
        }
    }
}
