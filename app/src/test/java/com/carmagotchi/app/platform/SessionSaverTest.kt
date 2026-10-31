package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Snapshot
import com.carmagotchi.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException

class SessionSaverTest {
    @get:Rule val tmp = TemporaryFolder()
    private val clock = clockAt("2026-10-25T09:00:00")
    private fun session() = PetSession(Snapshot(newPet(clock)), StateEngine(clock), clock).also { it.changed() }

    @Test fun failedSaveRetriesLatestStateAfterBackoff() {
        var calls = 0
        var saved: Snapshot? = null
        val saver = SessionSaver { if (++calls == 1) throw IOException("disk full") else saved = it }
        val s = session()
        saver.persist(s, clock.now)
        assertTrue(saver.failed)
        s.replace(s.pet.copy(name = "새이름"))
        saver.persist(s, clock.now.plusSeconds(29))
        assertEquals(1, calls)
        saver.persist(s, clock.now.plusSeconds(30))
        assertEquals("새이름", saved!!.pet.name)
        assertFalse(saver.failed)
        assertFalse(s.takeDirty())
    }

    @Test fun userCanRetryImmediatelyAndRepeatedFailureKeepsDirty() {
        var fail = true
        val saver = SessionSaver { if (fail) throw IOException() }
        val s = session()
        saver.persist(s, clock.now)
        saver.persist(s, clock.now, force = true)
        assertTrue(saver.failed)
        fail = false
        saver.persist(s, clock.now, force = true)
        assertFalse(saver.failed)
        assertFalse(s.takeDirty())
    }

    @Test fun unchangedSessionDoesNotWriteAndDeletionClearsFailure() {
        var calls = 0
        val saver = SessionSaver { calls++; throw IOException() }
        val s = session()
        s.takeDirty()
        saver.persist(s, clock.now)
        assertEquals(0, calls)
        s.changed()
        saver.persist(s, clock.now)
        assertTrue(saver.failed)
        saver.persist(null, clock.now)
        assertFalse(saver.failed)
    }

    @Test fun realFileWriteFailureKeepsPreviousSaveAndCanRecover() {
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = com.carmagotchi.app.data.PetStore(tmp.root, com.carmagotchi.app.data.AesGcmCrypto({ key }))
        val saver = SessionSaver(store::save)
        val s = session()
        saver.persist(s, clock.now)
        val before = store.load()
        val blocked = tmp.root.resolve("core.bin.tmp")
        assertTrue(blocked.mkdir())
        s.replace(s.pet.copy(name = "새이름"))
        saver.persist(s, clock.now)
        assertTrue(saver.failed)
        assertEquals(before, store.load())
        assertTrue(blocked.delete())
        saver.persist(s, clock.now, force = true)
        assertFalse(saver.failed)
        assertEquals(s.snapshot(), store.load())
    }
}
