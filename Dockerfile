# Multi-stage build для оптимизации размера образа

# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Копируем только файлы для загрузки зависимостей (для кэширования слоев)
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .
COPY gradle/libs.versions.toml gradle/

# Загружаем зависимости (этот слой будет закэширован)
RUN ./gradlew dependencies --no-daemon

# Копируем исходный код
COPY src src

# Собираем приложение
RUN ./gradlew shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Устанавливаем curl для healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Создаем пользователя для запуска приложения (безопасность)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Копируем JAR из builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Меняем владельца файлов
RUN chown -R appuser:appgroup /app

# Переключаемся на непривилегированного пользователя
USER appuser

# Открываем порт
EXPOSE 8080

# Настройка JVM для контейнера
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
