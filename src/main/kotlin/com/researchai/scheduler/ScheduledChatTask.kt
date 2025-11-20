package com.researchai.scheduler

import com.researchai.domain.models.ProviderType
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

/**
 * Запланированная задача для чата
 * Отправляет повторяющиеся сообщения в AI чат с заданным интервалом
 */
@Serializable
data class ScheduledChatTask(
    override val id: String = UUID.randomUUID().toString(),

    /**
     * Название задачи (опционально)
     */
    val title: String? = null,

    /**
     * Текст запроса, который будет отправляться при каждом тике
     */
    val taskRequest: String,

    override val intervalSeconds: Long,

    override val executeImmediately: Boolean,

    /**
     * ID провайдера для этой задачи
     * null = использовать глобальные настройки
     */
    @Serializable(with = ProviderTypeSerializer::class)
    val providerId: ProviderType? = null,

    /**
     * Модель для этой задачи
     * null = использовать глобальные настройки
     */
    val model: String? = null,

    override val createdAt: Long = System.currentTimeMillis(),

    /**
     * ID сессии чата, связанной с этой задачей
     */
    var sessionId: String? = null
) : ScheduledTask

/**
 * Serializer для ProviderType enum
 */
object ProviderTypeSerializer : KSerializer<ProviderType> {
    override val descriptor = PrimitiveSerialDescriptor(
        "ProviderType",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: ProviderType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ProviderType {
        return ProviderType.valueOf(decoder.decodeString())
    }
}
