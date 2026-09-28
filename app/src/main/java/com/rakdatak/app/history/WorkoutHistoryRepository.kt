package com.rakdatak.app.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A compact offline-first history store for recent workouts. */
data class WorkoutHistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val elapsedSeconds: Int,
    val distanceMeters: Double,
    val completionRatio: Double,
) {
    val completed: Boolean
        get() = completionRatio >= 0.999
}

class WorkoutHistoryRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): List<WorkoutHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        WorkoutHistoryEntry(
                            id = item.optString("id"),
                            timestampMillis = item.optLong("timestampMillis"),
                            elapsedSeconds = item.optInt("elapsedSeconds"),
                            distanceMeters = item.optDouble("distanceMeters").coerceAtLeast(0.0),
                            completionRatio = item.optDouble("completionRatio")
                                .coerceIn(0.0, 1.0),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
            .sortedByDescending { it.timestampMillis }
    }

    fun recordWorkout(
        elapsedSeconds: Int,
        distanceMeters: Double,
        completionRatio: Double,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val updated = buildList {
            add(
                WorkoutHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = timestampMillis,
                    elapsedSeconds = elapsedSeconds.coerceAtLeast(0),
                    distanceMeters = distanceMeters.coerceAtLeast(0.0),
                    completionRatio = completionRatio.coerceIn(0.0, 1.0),
                )
            )
            addAll(load())
        }.take(MAX_ENTRIES)

        persist(updated)
    }

    /**
     * Fills the distance of the newest workout only when it was originally saved without distance.
     * Treadmill sessions use this after the runner reads the final distance from the machine.
     */
    fun fillLatestMissingDistance(distanceMeters: Double): Boolean {
        val existing = load()
        val latest = existing.firstOrNull() ?: return false
        if (latest.distanceMeters > 0.0) return false

        val safeDistance = distanceMeters.coerceAtLeast(0.0)
        if (safeDistance <= 0.0) return false

        val updated = existing.mapIndexed { index, entry ->
            if (index == 0) entry.copy(distanceMeters = safeDistance) else entry
        }
        persist(updated)
        return true
    }

    private fun persist(entries: List<WorkoutHistoryEntry>) {
        val array = JSONArray()
        entries.take(MAX_ENTRIES).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestampMillis", entry.timestampMillis)
                    put("elapsedSeconds", entry.elapsedSeconds)
                    put("distanceMeters", entry.distanceMeters)
                    put("completionRatio", entry.completionRatio)
                }
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "workout_history"
        const val KEY_HISTORY = "entries"
        const val MAX_ENTRIES = 60
    }
}
