#!/bin/bash

# Скрипт для тестирования OpenAI API интеграции
# Этот скрипт отправляет тестовый запрос к API для проверки работы OpenAI провайдера

API_URL="http://localhost:8080"

echo "================================================"
echo "Тестирование OpenAI API интеграции"
echo "================================================"
echo ""

# 1. Получение списка всех провайдеров
echo "1. Получение списка доступных провайдеров..."
curl -s "${API_URL}/api/v2/providers" | jq '.'
echo ""
echo ""

# 2. Получение моделей OpenAI
echo "2. Получение списка моделей OpenAI..."
curl -s "${API_URL}/api/v2/providers/openai/models" | jq '.'
echo ""
echo ""

# 3. Отправка тестового сообщения через OpenAI
echo "3. Отправка тестового сообщения через OpenAI..."
curl -X POST "${API_URL}/api/v2/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "openai",
    "messages": [
      {
        "role": "user",
        "content": "Привет! Скажи короткую фразу о том, что ты работаешь."
      }
    ],
    "model": "gpt-4-turbo",
    "parameters": {
      "temperature": 0.7,
      "maxTokens": 100
    }
  }' | jq '.'
echo ""
echo ""

# 4. Проверка конфигурации OpenAI
echo "4. Проверка конфигурации OpenAI..."
curl -s "${API_URL}/api/v2/providers/openai/config" | jq '.'
echo ""
echo ""

echo "================================================"
echo "Тестирование завершено"
echo "================================================"
