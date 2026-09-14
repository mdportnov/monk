package com.mdportnov.monk.shared

import com.mdportnov.monk.shared.data.InMemoryStore
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.model.BlockMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UninstallArchiveTest {
    @Test
    fun uninstallKeepsSettingsAndMarksTheEntry() {
        val store = MonkStore(InMemoryStore())
        store.addApp("com.example.ig", "Instagram")
        store.updateConfig { c -> c.copy(apps = c.apps.map { it.copy(mode = BlockMode.BLOCK, dailyLimit = 3) }) }

        assertTrue(store.archiveIfWatched("com.example.ig"))
        val gone = store.config.value.archived("com.example.ig")
        assertNotNull(gone)
        assertNotNull(gone.uninstalledAt)
        assertEquals(BlockMode.BLOCK, gone.mode)
        assertEquals("Instagram", gone.label)
        assertTrue(store.config.value.apps.isEmpty())
    }

    @Test
    fun reinstallRestoresSettingsAndClearsTheMark() {
        val store = MonkStore(InMemoryStore())
        store.addApp("com.example.ig", "Instagram")
        store.updateConfig { c -> c.copy(apps = c.apps.map { it.copy(dailyLimit = 3) }) }
        store.archiveIfWatched("com.example.ig")

        assertTrue(store.restoreIfArchived("com.example.ig"))
        val back = store.config.value.app("com.example.ig")
        assertNotNull(back)
        assertEquals(3, back.dailyLimit)
        assertNull(back.uninstalledAt)
        assertNull(store.config.value.archived("com.example.ig"))
    }

    @Test
    fun manualRemovalIsNotShownAsUninstalled() {
        val store = MonkStore(InMemoryStore())
        store.addApp("com.example.ig", "Instagram")
        store.removeApp("com.example.ig")
        assertNull(store.config.value.archived("com.example.ig")?.uninstalledAt)
    }
}
