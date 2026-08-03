package ru.notiprint.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class NotificationKind(val title: String) {
    SMS("СМС"),
    MISSED_CALL("Пропущенный звонок"),
    CALENDAR("Календарь"),
}

enum class PrintStatus {
    PENDING,
    PRINTING,
    PRINTED,
    RETRY,
}

class RoomConverters {
    @TypeConverter
    fun notificationKindToString(value: NotificationKind): String = value.name

    @TypeConverter
    fun stringToNotificationKind(value: String): NotificationKind = NotificationKind.valueOf(value)

    @TypeConverter
    fun printStatusToString(value: PrintStatus): String = value.name

    @TypeConverter
    fun stringToPrintStatus(value: String): PrintStatus = PrintStatus.valueOf(value)
}

@Entity(
    tableName = "print_jobs",
    indices = [
        Index(value = ["notificationKey"], unique = true),
        Index(value = ["status", "receivedAt"]),
    ],
)
data class PrintJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val packageName: String,
    val postedAt: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val status: PrintStatus = PrintStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
)
