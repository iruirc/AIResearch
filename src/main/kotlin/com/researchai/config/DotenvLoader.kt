package com.researchai.config

import java.io.File

/**
 * Загрузчик переменных окружения из .env файла
 */
object DotenvLoader {

    /**
     * Загружает переменные из .env файла в System properties
     * Если файл не найден - игнорирует (используются системные переменные окружения)
     */
    fun load(envFilePath: String = ".env") {
        val envFile = File(envFilePath)

        if (!envFile.exists()) {
            println("⚠️  .env файл не найден по пути: ${envFile.absolutePath}")
            println("   Используются системные переменные окружения")
            return
        }

        println("✅ Загружаем переменные из .env файла: ${envFile.absolutePath}")

        envFile.readLines()
            .filter { line -> line.isNotBlank() && !line.trim().startsWith("#") }
            .forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                        .removePrefix("\"")
                        .removeSuffix("\"")
                        .removePrefix("'")
                        .removeSuffix("'")

                    // Устанавливаем переменную окружения только если она еще не установлена
                    if (System.getenv(key) == null) {
                        // Используем reflection для установки переменной окружения
                        setEnv(key, value)
                        println("   Загружена переменная: $key")
                    } else {
                        println("   Пропущена переменная: $key (уже установлена в системе)")
                    }
                }
            }
    }

    /**
     * Устанавливает переменную окружения через reflection
     * (работает на большинстве JVM)
     */
    private fun setEnv(key: String, value: String) {
        try {
            val env = System.getenv()
            val cl = env.javaClass
            val field = cl.getDeclaredField("m")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val writableEnv = field.get(env) as MutableMap<String, String>
            writableEnv[key] = value
        } catch (e: Exception) {
            // Если не удалось установить через reflection, используем System.setProperty
            System.setProperty(key, value)
            println("   ⚠️  Использован fallback для $key (System.setProperty)")
        }
    }
}
