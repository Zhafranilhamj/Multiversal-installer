package app.pwhs.universalinstaller.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.Progress
import java.util.UUID

/** Process-scoped session UI state; Ackpine owns durable installation sessions. */
class SessionDataRepositoryImpl : SessionDataRepository {
    private val sessionState = MutableStateFlow<List<SessionData>>(emptyList())
    private val progressState = MutableStateFlow<List<SessionProgress>>(emptyList())
    override val sessions = sessionState.asStateFlow()
    override val sessionsProgress = progressState.asStateFlow()
    private var _sessions: List<SessionData>
        get() = sessionState.value
        set(value) { sessionState.value = value }
    private var _sessionsProgress: List<SessionProgress>
        get() = progressState.value
        set(value) { progressState.value = value }

    override fun addSessionData(sessionData: SessionData) {
        _sessions = listOf(sessionData) + _sessions
        _sessionsProgress = listOf(SessionProgress(sessionData.id, Progress())) + _sessionsProgress
    }

    override fun removeSessionData(id: UUID) {
        val sessions = _sessions.toMutableList()
        sessions.removeAll { it.id == id }
        _sessions = sessions
        val sessionsProgress = _sessionsProgress.toMutableList()
        sessionsProgress.removeAll { it.id == id }
        _sessionsProgress = sessionsProgress
    }

    override fun updateSessionProgress(id: UUID, progress: Progress) {
        val sessionsProgress = _sessionsProgress.toMutableList()
        val sessionProgressIndex = sessionsProgress.indexOfFirst { it.id == id }
        if (sessionProgressIndex != -1) {
            sessionsProgress[sessionProgressIndex] = SessionProgress(id, progress)
        }
        _sessionsProgress = sessionsProgress
    }

    override fun updateSessionIsCancellable(id: UUID, isCancellable: Boolean) {
        val sessionDataIndex = _sessions.indexOfFirst { it.id == id }
        if (sessionDataIndex == -1) {
            return
        }
        val sessionData = _sessions[sessionDataIndex]
        if (sessionData.isCancellable == isCancellable) {
            return
        }
        val sessions = _sessions.toMutableList()
        sessions[sessionDataIndex] = sessionData.copy(isCancellable = isCancellable)
        _sessions = sessions
    }

    override fun setError(id: UUID, error: ResolvableString) {
        val sessions = _sessions.toMutableList()
        val sessionDataIndex = sessions.indexOfFirst { it.id == id }
        if (sessionDataIndex != -1) {
            val sessionData = sessions[sessionDataIndex]
            sessions[sessionDataIndex] = sessionData.copy(error = error)
        }
        _sessions = sessions
    }
}