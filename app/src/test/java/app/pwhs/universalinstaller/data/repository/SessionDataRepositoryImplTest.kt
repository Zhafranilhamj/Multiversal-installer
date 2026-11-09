package app.pwhs.universalinstaller.data.repository

import app.pwhs.universalinstaller.domain.model.SessionData
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SessionDataRepositoryImplTest {
    @Test fun coldConstructionNeedsNoSavedStateOwner() {
        val repository = SessionDataRepositoryImpl()
        assertTrue(repository.sessions.value.isEmpty())
        assertTrue(repository.sessionsProgress.value.isEmpty())
    }

    @Test fun addUpdateAndRemoveStayConsistent() {
        val repository = SessionDataRepositoryImpl()
        val id = UUID.randomUUID()
        repository.addSessionData(SessionData(id = id, name = "test.apk"))
        assertEquals(id, repository.sessions.value.single().id)
        assertEquals(id, repository.sessionsProgress.value.single().id)
        repository.updateSessionIsCancellable(id, false)
        assertFalse(repository.sessions.value.single().isCancellable)
        repository.removeSessionData(id)
        assertTrue(repository.sessions.value.isEmpty())
        assertTrue(repository.sessionsProgress.value.isEmpty())
    }
}
