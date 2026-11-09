package com.rakdatak.core.training

enum class WorkoutOwner { PHONE, WATCH }

data class WorkoutCommand(
    val action: String,
    val sessionId: String,
    val owner: WorkoutOwner,
    val origin: WorkoutOwner,
    val sentAt: Long,
    val planIndex: Int,
    val elapsed: Int,
    val gps: Boolean = false,
    val paused: Boolean = false,
) {
    fun encode(): String = listOf("2", action, sessionId, owner.name, origin.name,
        sentAt, planIndex, elapsed, gps, paused).joinToString("|")
}

/** Pure validation shared by both endpoints. Never interpret legacy/missing IDs as a start. */
object WorkoutSync {
    const val MAX_AGE_MS = 15_000L
    fun fresh(timestamp: Long, now: Long): Boolean = timestamp > 0 && timestamp <= now + 2_000 &&
        now - timestamp <= MAX_AGE_MS

    fun parse(raw: String): WorkoutCommand? = runCatching {
        val p = raw.split('|')
        require(p.size == 10 && p[0] == "2")
        require(p[1] in setOf("START", "PAUSE", "RESUME", "STOP"))
        require(p[2].matches(Regex("[a-zA-Z0-9-]{1,64}")))
        WorkoutCommand(p[1], p[2], WorkoutOwner.valueOf(p[3]), WorkoutOwner.valueOf(p[4]),
            p[5].toLong(), p[6].toInt(), p[7].toInt(), p[8].toBooleanStrict(), p[9].toBooleanStrict())
            .also { require(it.planIndex >= 0 && it.elapsed >= 0 && it.sentAt > 0) }
    }.getOrNull()

    fun accepts(c: WorkoutCommand, sessionId: String?, owner: WorkoutOwner?,
        receiver: WorkoutOwner, now: Long, lastCommandAt: Long = 0): Boolean {
        if (c.origin == receiver || !fresh(c.sentAt, now) || c.sentAt <= lastCommandAt) return false
        if (c.action == "START") return receiver == WorkoutOwner.WATCH &&
            c.owner == WorkoutOwner.PHONE && sessionId == null
        return c.sessionId == sessionId && c.owner == owner
    }
}
