package com.rakdatak.app.schedule

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rakdatak.core.training.TrainingSlot
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trainingScheduleDataStore by preferencesDataStore(name = "training_schedule")

data class UserTrainingSchedule(
    val slots: List<TrainingSlot> = emptyList(),
) {
    val isConfigured: Boolean get() = slots.size == 3
}

class TrainingScheduleRepository(private val context: Context) {
    val schedule: Flow<UserTrainingSchedule> = context.trainingScheduleDataStore.data.map { preferences ->
        UserTrainingSchedule(
            slots = decode(preferences[SLOTS]),
        )
    }

    suspend fun saveSlots(slots: List<TrainingSlot>) {
        require(slots.size == 3) { "Exactly three training times are required" }
        require(slots.map { it.dayOfWeek }.distinct().size == 3) {
            "Training days must be three distinct days"
        }

        val normalized = slots.sortedBy { it.dayOfWeek.value }
        context.trainingScheduleDataStore.edit { preferences ->
            preferences[SLOTS] = encode(normalized)
        }
    }

    private fun encode(slots: List<TrainingSlot>): String = slots.joinToString(";") { slot ->
        "${slot.dayOfWeek.name},${slot.time.hour},${slot.time.minute}"
    }

    private fun decode(raw: String?): List<TrainingSlot> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { item ->
            val parts = item.split(',')
            if (parts.size != 3) return@mapNotNull null
            runCatching {
                TrainingSlot(
                    dayOfWeek = DayOfWeek.valueOf(parts[0]),
                    time = LocalTime.of(parts[1].toInt(), parts[2].toInt()),
                )
            }.getOrNull()
        }.takeIf { it.size == 3 } ?: emptyList()
    }

    private companion object {
        val SLOTS = stringPreferencesKey("weekly_training_slots")
    }
}
