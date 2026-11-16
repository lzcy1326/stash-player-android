package gomeng.dev.stashplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneBulkDeleteConfirmationStateTest {
    @Test fun `delete request snapshots targets and defaults to deleting files`() {
        val selected = mutableListOf("a", "b", "a", "")
        val state = SceneBulkDeleteConfirmationState.Hidden.request(selected)
        selected.clear()

        assertEquals(listOf("a", "b"), state.sceneIds)
        assertTrue(state.canDelete)
        assertTrue(state.deleteOptions.deleteFile)
        assertTrue(state.deleteOptions.deleteGenerated)
        assertEquals(state, state.request(listOf("other")))
        assertFalse(state.deleting().canDelete)
    }

    @Test fun `empty requests cannot delete and original files can be kept`() {
        assertFalse(SceneBulkDeleteConfirmationState.Hidden.request(listOf(" ")).canDelete)
        val state = SceneBulkDeleteConfirmationState.Hidden.request(listOf("a"))
            .withDeleteFile(false)
        assertFalse(state.deleteOptions.deleteFile)
        assertTrue(state.deleteOptions.deleteGenerated)
        assertFalse(state.dismiss().canDelete)
    }
}
