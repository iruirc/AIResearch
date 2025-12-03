# GitHub Action для Автоматического PR Review

Это руководство объясняет как настроить и использовать GitHub Action для автоматического ревью pull request с помощью ResearchAI.

## Обзор

GitHub Action workflow автоматически запускается при:
- Открытии нового PR (`opened`)
- Обновлении PR новыми коммитами (`synchronize`)
- Повторном открытии PR (`reopened`)

Workflow выполняет следующие действия:
1. Собирает ResearchAI CLI из исходников
2. Запускает AI-ревью PR с использованием RAG
3. Публикует результаты ревью как комментарий в PR
4. Проверяет quality gate (порог = 50)
5. Фейлит если score < 50

## Требуемые Настройки

### 1. GitHub Secrets

Необходимо добавить следующие secrets в настройки репозитория:

**Обязательные:**
- `RESEARCHAI_SERVER_URL` - URL вашего ResearchAI сервера (например, `https://researchai.example.com`)

**Автоматически предоставляемые GitHub:**
- `GITHUB_TOKEN` - токен для доступа к GitHub API (предоставляется автоматически)

#### Как добавить secrets:

1. Перейдите в ваш GitHub репозиторий
2. Откройте `Settings` → `Secrets and variables` → `Actions`
3. Нажмите `New repository secret`
4. Добавьте:
   - **Name:** `RESEARCHAI_SERVER_URL`
   - **Value:** URL вашего сервера ResearchAI

### 2. ResearchAI Server

Убедитесь что ваш ResearchAI сервер:
- Доступен из GitHub Actions (публичный URL или VPN)
- Запущен и работает (`/health` endpoint отвечает)
- Имеет настроенный GitHub MCP server
- Имеет загруженную RAG базу данных (если используете RAG)

### 3. GitHub Permissions

Workflow требует следующих прав:
- `contents: read` - для чтения кода репозитория
- `pull-requests: write` - для публикации комментариев в PR

Эти права предоставляются автоматически через `GITHUB_TOKEN`.

## Конфигурация Workflow

Файл workflow находится в `.github/workflows/pr-review.yml`.

### Основные параметры

#### Review Mode

По умолчанию используется `--mode standard`. Доступные режимы:

```yaml
--mode quick      # Быстрое ревью - только summary
--mode standard   # Стандартное ревью - баланс детализации
--mode thorough   # Детальное ревью - построчный анализ
```

Чтобы изменить режим, отредактируйте обе команды `java -jar` в workflow:

```yaml
java -jar researchai-cli/build/libs/researchai-cli-*-all.jar \
  review ${{ github.event.pull_request.html_url }} \
  --mode thorough \  # <-- Измените здесь
  --use-rag \
  --output json > review.json
```

#### Focus Areas

Добавьте `--focus` для указания конкретных областей проверки:

```yaml
--focus security,performance,architecture
```

Доступные focus areas:
- `SECURITY` - уязвимости безопасности
- `PERFORMANCE` - проблемы производительности
- `CODE_STYLE` - стиль кода
- `ARCHITECTURE` - архитектурные проблемы
- `TESTING` - покрытие тестами
- `DOCUMENTATION` - документация
- `ERROR_HANDLING` - обработка ошибок
- `KOTLIN_IDIOMS` - идиоматичность Kotlin

#### Quality Gate Threshold

По умолчанию порог = 50. PR с score < 50 будут отклонены.

Чтобы изменить порог, отредактируйте шаг "Check Quality Gate":

```yaml
- name: Check Quality Gate
  run: |
    SCORE=${{ steps.review.outputs.score }}
    THRESHOLD=70  # <-- Измените здесь

    if [ "$SCORE" -lt "$THRESHOLD" ]; then
      echo "❌ PR review score ($SCORE) is below threshold ($THRESHOLD)"
      exit 1
    else
      echo "✅ PR review score ($SCORE) meets threshold ($THRESHOLD)"
    fi
```

#### Отключение RAG

Если не хотите использовать RAG контекст, удалите флаг `--use-rag`:

```yaml
java -jar researchai-cli/build/libs/researchai-cli-*-all.jar \
  review ${{ github.event.pull_request.html_url }} \
  --mode standard \
  --output json > review.json
```

## Как Это Работает

### 1. Trigger Events

Workflow запускается автоматически при:

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened]
```

- `opened` - новый PR создан
- `synchronize` - новые коммиты добавлены в PR
- `reopened` - закрытый PR открыт снова

### 2. Build CLI

```yaml
- name: Build ResearchAI CLI
  run: |
    chmod +x ./gradlew
    ./gradlew :researchai-cli:build
    ./gradlew :researchai-cli:buildFatJar
```

Собирает CLI из исходников в fat JAR.

### 3. Run Review

```yaml
- name: Run AI Review
  id: review
  env:
    RESEARCHAI_SERVER_URL: ${{ secrets.RESEARCHAI_SERVER_URL }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    java -jar researchai-cli/build/libs/researchai-cli-*-all.jar \
      review ${{ github.event.pull_request.html_url }} \
      --mode standard \
      --use-rag \
      --output json > review.json

    SCORE=$(jq '.overallScore' review.json)
    echo "score=$SCORE" >> $GITHUB_OUTPUT
```

Выполняет ревью и сохраняет score для следующих шагов.

### 4. Post Comment

```yaml
- name: Post Review Comment
  if: always()
  env:
    RESEARCHAI_SERVER_URL: ${{ secrets.RESEARCHAI_SERVER_URL }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    java -jar researchai-cli/build/libs/researchai-cli-*-all.jar \
      review ${{ github.event.pull_request.html_url }} \
      --mode standard \
      --use-rag \
      --post-comment
```

Публикует результаты как комментарий. `if: always()` гарантирует выполнение даже при ошибках.

### 5. Quality Gate

```yaml
- name: Check Quality Gate
  run: |
    SCORE=${{ steps.review.outputs.score }}
    THRESHOLD=50

    if [ "$SCORE" -lt "$THRESHOLD" ]; then
      echo "❌ PR review score ($SCORE) is below threshold ($THRESHOLD)"
      exit 1
    else
      echo "✅ PR review score ($SCORE) meets threshold ($THRESHOLD)"
    fi
```

Проверяет score и фейлит workflow если ниже порога.

## Примеры Использования

### Пример 1: Базовая настройка

Минимальная конфигурация с настройками по умолчанию:

1. Добавьте `RESEARCHAI_SERVER_URL` в secrets
2. Закоммитьте `.github/workflows/pr-review.yml`
3. Создайте PR
4. Workflow запустится автоматически

### Пример 2: Фокус на безопасности

Проверяйте только security и error handling:

```yaml
java -jar researchai-cli/build/libs/researchai-cli-*-all.jar \
  review ${{ github.event.pull_request.html_url }} \
  --mode thorough \
  --focus security,error_handling \
  --use-rag \
  --output json > review.json
```

### Пример 3: Высокий порог качества

Требуйте score >= 80 для слияния:

```yaml
- name: Check Quality Gate
  run: |
    SCORE=${{ steps.review.outputs.score }}
    THRESHOLD=80  # Высокий порог

    if [ "$SCORE" -lt "$THRESHOLD" ]; then
      echo "❌ PR review score ($SCORE) is below threshold ($THRESHOLD)"
      exit 1
    fi
```

### Пример 4: Отключение автокомментариев

Сохраняйте результаты только в artifacts, не публикуйте в PR:

Удалите или закомментируйте шаг "Post Review Comment":

```yaml
# - name: Post Review Comment
#   if: always()
#   ...
```

Добавьте шаг сохранения artifact:

```yaml
- name: Upload Review Results
  uses: actions/upload-artifact@v4
  with:
    name: pr-review-results
    path: review.json
```

## Интеграция с Branch Protection

Вы можете сделать PR review обязательным требованием для слияния:

1. Перейдите в `Settings` → `Branches`
2. Добавьте branch protection rule для `main`
3. Включите `Require status checks to pass before merging`
4. Выберите `ai-review` job из списка

Теперь PR нельзя будет слить если AI review не пройдет quality gate.

## Troubleshooting

### Workflow не запускается

**Проблема:** Workflow не появляется во вкладке Actions

**Решение:**
- Убедитесь что файл `.github/workflows/pr-review.yml` закоммичен в main ветку
- Проверьте что YAML синтаксис корректен
- Проверьте что GitHub Actions включены в настройках репозитория

### Ошибка "RESEARCHAI_SERVER_URL not set"

**Проблема:** Secret не найден

**Решение:**
- Убедитесь что secret добавлен в Settings → Secrets and variables → Actions
- Имя должно быть точно `RESEARCHAI_SERVER_URL` (case-sensitive)
- Проверьте что secret добавлен на уровне репозитория, а не организации

### Ошибка подключения к серверу

**Проблема:** CLI не может подключиться к ResearchAI серверу

**Решение:**
- Проверьте что сервер доступен публично или через VPN
- Проверьте что URL корректен (включая http:// или https://)
- Проверьте что сервер запущен (`curl $RESEARCHAI_SERVER_URL/health`)
- Проверьте firewall и network security groups

### GitHub MCP не подключен

**Проблема:** Ошибка "GitHub MCP server not connected"

**Решение:**
- Запустите сервер ResearchAI с правильными настройками GitHub MCP
- Убедитесь что GitHub token в server config корректен
- Проверьте логи сервера для деталей ошибки MCP

### Build fails

**Проблема:** Gradle build не проходит

**Решение:**
- Убедитесь что Java 17 установлена
- Проверьте что все зависимости доступны
- Попробуйте локально: `./gradlew :researchai-cli:build`

### Review занимает слишком много времени

**Проблема:** Workflow timeout (default 6 hours)

**Решение:**
- Используйте `--mode quick` для больших PR
- Отключите RAG (уберите `--use-rag`)
- Добавьте timeout для job:

```yaml
jobs:
  ai-review:
    runs-on: ubuntu-latest
    timeout-minutes: 10  # Ограничьте время
```

## Стоимость

Каждый запуск workflow:
- **GitHub Actions:** Free для публичных репозиториев, ~$0.008/мин для приватных
- **AI Provider:** Зависит от модели и размера PR (~$0.05-$0.20 за PR)
- **Total:** ~$0.05-$0.25 за PR ревью

Для минимизации стоимости:
- Используйте `--mode quick` где возможно
- Настройте triggers только для важных событий
- Используйте дешевые модели (например, Claude Haiku)

## Дополнительные Возможности

### Scheduled Reviews

Запускайте ревью по расписанию:

```yaml
on:
  schedule:
    - cron: '0 9 * * 1'  # Каждый понедельник в 9:00 UTC
  pull_request:
    types: [opened, synchronize, reopened]
```

### Multiple Review Modes

Запускайте quick и thorough параллельно:

```yaml
jobs:
  quick-review:
    runs-on: ubuntu-latest
    steps:
      # ... quick review

  thorough-review:
    runs-on: ubuntu-latest
    steps:
      # ... thorough review
```

### Custom Notifications

Отправляйте результаты в Slack/Discord:

```yaml
- name: Notify Slack
  if: steps.review.outputs.score < 50
  uses: slackapi/slack-github-action@v1
  with:
    webhook: ${{ secrets.SLACK_WEBHOOK }}
    payload: |
      {
        "text": "PR Review failed with score ${{ steps.review.outputs.score }}"
      }
```

## Ссылки

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [ResearchAI CLI Documentation](../CLAUDE.md#pr-review)
- [PR Review API Documentation](./PR_REVIEW_API.md)
