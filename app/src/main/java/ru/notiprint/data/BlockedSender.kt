package ru.notiprint.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/** A local rule that prevents a sender's SMS and missed calls from being printed. */
@Entity(
    tableName = "blocked_senders",
    indices = [Index(value = ["normalized"], unique = true)],
)
data class BlockedSender(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalized: String,
    val label: String,
)

object SenderIdentifier {
    const val DEFAULT_BLOCKED_SENDER_LABEL = "+79089148402"
    const val DEFAULT_BLOCKED_SENDER_NORMALIZED = "79089148402"

    fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        if (isNumericAddress(trimmed)) {
            val digits = trimmed.filter(Char::isDigit)
            // Treat the usual Russian domestic and international forms as one number.
            return if (digits.length == 11 && digits.startsWith('8')) "7${digits.drop(1)}" else digits
        }

        return trimmed.lowercase(Locale.ROOT)
    }

    fun isNumericAddress(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.any(Char::isDigit) && trimmed.all { character ->
            character.isDigit() || character in "+-() "
        }
    }
}
