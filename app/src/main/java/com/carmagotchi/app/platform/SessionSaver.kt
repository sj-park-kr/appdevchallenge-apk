package com.carmagotchi.app.platform

import com.carmagotchi.app.data.Snapshot
import java.time.Instant

/** 실패한 변경은 보존한다. 차량 신호마다 쓰기를 반복하지 않고 30초 뒤 또는 사용자 요청으로 재시도한다. */
class SessionSaver(private val write: (Snapshot) -> Unit) {
    var failed = false; private set
    private var retryAt = Instant.MIN

    fun persist(session: PetSession?, now: Instant, force: Boolean = false) {
        if (session == null) { failed = false; retryAt = Instant.MIN; return }
        if (!force && failed && now.isBefore(retryAt)) return
        if (!session.takeDirty() && !force) return
        try {
            write(session.snapshot())
            failed = false
        } catch (e: Exception) {
            session.changed()
            failed = true
            retryAt = now.plusSeconds(30)
        }
    }
}
