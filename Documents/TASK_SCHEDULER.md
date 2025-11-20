# Task Scheduler Architecture

Complete technical documentation for the ResearchAI Task Scheduler feature.

**Generated:** 2025-11-20
**Branch:** scheduler
**Status:** Implemented and tested

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [API Endpoints](#api-endpoints)
5. [Frontend Integration](#frontend-integration)
6. [Persistence Layer](#persistence-layer)
7. [Lifecycle Management](#lifecycle-management)
8. [Error Handling](#error-handling)
9. [Configuration](#configuration)
10. [Usage Examples](#usage-examples)
11. [Security Considerations](#security-considerations)
12. [Future Improvements](#future-improvements)

---

## Overview

### What is Task Scheduler?

The Task Scheduler is a comprehensive feature that enables automated, recurring AI conversations in ResearchAI. It allows users to create scheduled tasks that automatically send messages to AI providers at configurable intervals.

### Key Features

- **Recurring Execution**: Tasks run at user-defined intervals (minimum 10 seconds)
- **Immediate Execution**: Optional first execution on task creation
- **Hybrid Configuration**: Global provider/model settings with per-task overrides
- **Persistent Storage**: JSON-based task persistence with atomic writes
- **Graceful Error Handling**: Errors displayed in chat without stopping scheduler
- **Session Integration**: Automatic session creation and management
- **Full Lifecycle Management**: Start, stop, delete operations
- **REST API**: Complete CRUD operations via HTTP endpoints
- **UI Integration**: Modal-based task creation with validation

### Use Cases

1. **Periodic Research**: Automated market analysis, news monitoring
2. **Scheduled Reminders**: Daily/weekly AI-generated summaries
3. **Data Collection**: Regular data gathering and analysis
4. **Monitoring**: System health checks, status updates
5. **Testing**: Automated API testing and validation

---

## Architecture

### Design Patterns

The Task Scheduler follows several established design patterns:

1. **Strategy Pattern**: Abstract `TaskScheduler<T>` allows different task implementations
2. **Template Method Pattern**: `onTaskExecution()` and `onTaskError()` hooks
3. **Factory Pattern**: `SchedulerManager` creates and manages scheduler instances
4. **Repository Pattern**: `ScheduledTaskStorage` abstracts persistence
5. **Dependency Injection**: Constructor-based DI throughout

### Architectural Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     Presentation Layer                      │
│  ┌──────────────────┐           ┌──────────────────┐       │
│  │ SchedulerRoutes  │           │ SchedulerModal   │       │
│  │   (REST API)     │           │  (Frontend UI)   │       │
│  └──────────────────┘           └──────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              SchedulerManager                        │  │
│  │  - Task lifecycle management                         │  │
│  │  - Scheduler instance creation                       │  │
│  │  - Auto-loading from storage                         │  │
│  │  - Graceful shutdown                                 │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                           │
│  ┌────────────────┐          ┌─────────────────────┐       │
│  │ TaskScheduler  │◄─────────│ ChatTaskScheduler   │       │
│  │  (Abstract)    │          │   (Concrete)        │       │
│  └────────────────┘          └─────────────────────┘       │
│  ┌────────────────┐          ┌─────────────────────┐       │
│  │ ScheduledTask  │◄─────────│ ScheduledChatTask   │       │
│  │  (Interface)   │          │   (Data Class)      │       │
│  └────────────────┘          └─────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Persistence Layer                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          ScheduledTaskStorage                        │  │
│  │  - JSON file storage                                 │  │
│  │  - Atomic writes                                     │  │
│  │  - Directory: data/scheduled_tasks/                  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Relationships

```
┌──────────────────┐
│  SchedulerRoutes │
└────────┬─────────┘
         │
         │ depends on
         ▼
┌──────────────────────┐      creates      ┌──────────────────┐
│  SchedulerManager    │──────────────────►│ ChatTaskScheduler│
└──────────┬───────────┘                   └────────┬─────────┘
           │                                        │
           │ uses                                   │ uses
           ▼                                        │
┌──────────────────────┐                           │
│ScheduledTaskStorage  │                           │
└──────────────────────┘                           │
                                                    ▼
                                          ┌──────────────────┐
                                          │SendMessageUseCase│
                                          └──────────────────┘
                                          ┌──────────────────┐
                                          │ChatSessionManager│
                                          └──────────────────┘
```

---

## Core Components

### 1. ScheduledTask Interface

**Location:** `com.researchai.scheduler.ScheduledTask`

Base interface for all scheduled task types. Enables extensibility for future task implementations beyond chat.

```kotlin
interface ScheduledTask {
    val id: String                    // Unique task identifier
    val intervalSeconds: Long         // Execution interval
    val executeImmediately: Boolean   // Execute on creation?
    val createdAt: Long               // Creation timestamp
}
```

**Design Notes:**
- Simple, minimal interface for maximum flexibility
- No business logic, only data contract
- Supports generic task scheduling beyond chat

---

### 2. TaskScheduler<T> Abstract Class

**Location:** `com.researchai.scheduler.TaskScheduler`

Abstract scheduler with coroutine-based timing mechanism. Provides lifecycle management and execution hooks.

**Key Features:**
- Thread-safe state management with `AtomicBoolean`
- Coroutine-based async execution with `SupervisorJob`
- Automatic interval timing with `delay()`
- Hook methods for execution and error handling
- Time tracking for next execution

**Public API:**
```kotlin
abstract class TaskScheduler<T : ScheduledTask>(val task: T) {
    // Lifecycle
    fun start()                          // Start scheduler
    fun stop()                           // Stop scheduler (pausable)
    suspend fun shutdown()               // Graceful shutdown

    // State
    fun isRunning(): Boolean             // Check if running
    fun getSecondsUntilNextExecution(): Long  // Time until next run

    // Hooks (to be implemented by subclasses)
    protected abstract suspend fun onTaskExecution()
    protected open suspend fun onTaskError(error: Exception)
}
```

**Implementation Details:**

```kotlin
// Thread-safe state
private val isRunning = AtomicBoolean(false)
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var job: Job? = null
private var lastExecutionTime: Long = 0
private var nextExecutionTime: Long = 0

// Execution loop
fun start() {
    if (!isRunning.compareAndSet(false, true)) return

    job = scope.launch {
        if (task.executeImmediately) {
            executeTask()
        }

        while (isActive && isRunning.get()) {
            nextExecutionTime = System.currentTimeMillis() + (task.intervalSeconds * 1000)
            delay(task.intervalSeconds * 1000)

            if (isActive && isRunning.get()) {
                executeTask()
            }
        }
    }
}
```

**Error Handling:**
- Exceptions in `onTaskExecution()` are caught
- Calls `onTaskError()` for custom error handling
- Scheduler continues running after errors
- No automatic task termination

---

### 3. ScheduledChatTask Data Class

**Location:** `com.researchai.scheduler.ScheduledChatTask`

Concrete implementation for chat-specific scheduled tasks with hybrid provider/model configuration.

```kotlin
@Serializable
data class ScheduledChatTask(
    override val id: String = UUID.randomUUID().toString(),
    val title: String? = null,                  // Optional task title
    val taskRequest: String,                    // Message to send
    override val intervalSeconds: Long,         // Execution interval
    override val executeImmediately: Boolean,   // Immediate execution flag

    // Hybrid configuration
    @Serializable(with = ProviderTypeSerializer::class)
    val providerId: ProviderType? = null,       // null = use global
    val model: String? = null,                  // null = use global

    override val createdAt: Long = System.currentTimeMillis(),
    var sessionId: String? = null               // Linked session ID
) : ScheduledTask
```

**Configuration Strategy:**
- `providerId = null` → Uses global provider from settings
- `providerId = CLAUDE` → Uses Claude regardless of global setting
- `model = null` → Uses global model for the provider
- `model = "gpt-4"` → Uses specified model regardless of global setting

**Serialization:**
- Uses kotlinx.serialization
- Custom `ProviderTypeSerializer` for enum handling
- Stored as JSON files

---

### 4. ChatTaskScheduler Class

**Location:** `com.researchai.scheduler.ChatTaskScheduler`

Concrete scheduler implementation for automated chat message execution.

**Constructor:**
```kotlin
class ChatTaskScheduler(
    task: ScheduledChatTask,
    private val sessionManager: ChatSessionManager,
    private val sendMessageUseCase: SendMessageUseCase
) : TaskScheduler<ScheduledChatTask>(task)
```

**Lifecycle:**

```kotlin
// 1. Initialize (called once on creation)
suspend fun initialize() {
    // Create session linked to this task
    val sessionId = sessionManager.createSession(scheduledTaskId = task.id)
    task.sessionId = sessionId

    // Add informational message
    val initialMessage = """
        Я планировщик задач.
        Каждые ${formatInterval(task.intervalSeconds)} я буду выполнять задачу
        Моя задача - ${task.taskRequest}
    """.trimIndent()

    sessionManager.addMessageToSession(sessionId, MessageRole.ASSISTANT, initialMessage)

    // Set session title
    val title = task.title ?: "Задача: ${task.taskRequest.take(50)}..."
    sessionManager.updateSessionTitle(sessionId, title)
}

// 2. Execute (called on each interval)
override suspend fun onTaskExecution() {
    val sessionId = task.sessionId ?: return

    // Determine provider and model
    val providerId = task.providerId ?: ProviderType.CLAUDE
    val model = task.model

    // Send message
    val result = sendMessageUseCase(
        message = task.taskRequest,
        sessionId = sessionId,
        providerId = providerId,
        model = model,
        parameters = RequestParameters()
    )

    result.onFailure { error ->
        throw error  // Handled by onTaskError
    }
}

// 3. Handle errors
override suspend fun onTaskError(error: Exception) {
    val sessionId = task.sessionId ?: return

    val errorMessage = """
        ⚠️ Ошибка выполнения задачи:
        ${error.message ?: "Неизвестная ошибка"}

        Следующая попытка будет выполнена через ${formatInterval(task.intervalSeconds)}
    """.trimIndent()

    sessionManager.addMessageToSession(sessionId, MessageRole.ASSISTANT, errorMessage)
}
```

**Execution Flow:**
1. User creates task → `initialize()` called
2. Session created with `scheduledTaskId` link
3. Initial informational message posted
4. If `executeImmediately = true`, first execution happens immediately
5. Scheduler starts interval loop
6. On each interval: `onTaskExecution()` → sends message
7. AI response received → added to session
8. On error: `onTaskError()` → error message posted to chat

---

### 5. SchedulerManager Service

**Location:** `com.researchai.services.SchedulerManager`

Central manager for all task schedulers. Handles lifecycle, persistence, and CRUD operations.

**Constructor:**
```kotlin
class SchedulerManager(
    private val sessionManager: ChatSessionManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val storage: ScheduledTaskStorage
)
```

**Internal State:**
```kotlin
private val schedulers = ConcurrentHashMap<String, ChatTaskScheduler>()
```

**Initialization:**
```kotlin
init {
    runBlocking {
        loadAllTasks()
    }
}

private suspend fun loadAllTasks() {
    storage.loadAllTasks().onSuccess { tasks ->
        tasks.forEach { task ->
            try {
                val scheduler = ChatTaskScheduler(task, sessionManager, sendMessageUseCase)
                // Note: Don't call initialize() - session already exists
                scheduler.start()
                schedulers[task.id] = scheduler
                logger.info("Restored and started task: ${task.id}")
            } catch (e: Exception) {
                logger.error("Failed to restore task ${task.id}", e)
            }
        }
    }
}
```

**Public API:**

```kotlin
// Create new task
suspend fun createTask(task: ScheduledChatTask): Result<String> {
    // Validation
    if (task.intervalSeconds < 10) {
        return Result.failure(IllegalArgumentException("Interval must be at least 10 seconds"))
    }

    // Initialize and start
    val scheduler = ChatTaskScheduler(task, sessionManager, sendMessageUseCase)
    scheduler.initialize()
    scheduler.start()

    // Store
    schedulers[task.id] = scheduler
    storage.saveTask(task)

    return Result.success(task.id)
}

// Get task details
fun getTask(taskId: String): ScheduledChatTask?

// Get scheduler instance
fun getScheduler(taskId: String): ChatTaskScheduler?

// Get all tasks
fun getAllTasks(): List<ScheduledChatTask>

// Stop task (pausable)
suspend fun stopTask(taskId: String): Result<Unit> {
    val scheduler = schedulers[taskId] ?: return Result.failure(...)
    scheduler.stop()
    storage.saveTask(scheduler.task)
    return Result.success(Unit)
}

// Start task (resume)
suspend fun startTask(taskId: String): Result<Unit> {
    val scheduler = schedulers[taskId] ?: return Result.failure(...)
    scheduler.start()
    return Result.success(Unit)
}

// Delete task permanently
suspend fun deleteTask(taskId: String): Result<Unit> {
    val scheduler = schedulers[taskId] ?: return Result.failure(...)

    // Stop and cleanup
    scheduler.stop()
    scheduler.shutdown()

    // Delete session if exists
    scheduler.task.sessionId?.let { sessionId ->
        sessionManager.deleteSession(sessionId)
    }

    // Remove from storage
    storage.deleteTask(taskId)
    schedulers.remove(taskId)

    return Result.success(Unit)
}

// Graceful shutdown
suspend fun shutdown() {
    schedulers.values.forEach { scheduler ->
        scheduler.stop()
        scheduler.shutdown()
        storage.saveTask(scheduler.task)
    }
    schedulers.clear()
}
```

**Lifecycle Management:**
- Auto-loads tasks on initialization
- Starts schedulers without calling `initialize()` (sessions exist)
- Saves state on shutdown
- Deletes associated sessions when task is deleted

---

### 6. ScheduledTaskStorage

**Location:** `com.researchai.persistence.ScheduledTaskStorage`

JSON-based persistence with atomic writes to prevent data corruption.

**Storage Details:**
- **Directory:** `data/scheduled_tasks/`
- **Format:** One JSON file per task: `{taskId}.json`
- **Write Strategy:** Atomic writes (temp file + move)

**Public API:**

```kotlin
class ScheduledTaskStorage(
    private val storagePath: String = "data/scheduled_tasks"
) {
    suspend fun saveTask(task: ScheduledChatTask): Result<Unit>
    suspend fun loadTask(taskId: String): Result<ScheduledChatTask>
    suspend fun loadAllTasks(): Result<List<ScheduledChatTask>>
    suspend fun deleteTask(taskId: String): Result<Unit>
}
```

**Atomic Write Implementation:**
```kotlin
suspend fun saveTask(task: ScheduledChatTask): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val filePath = getTaskFilePath(task.id)
        val jsonContent = json.encodeToString(task)

        // Write to temp file first
        val tempFile = File("$filePath.tmp")
        tempFile.writeText(jsonContent)

        // Atomic move to final location
        Files.move(
            tempFile.toPath(),
            Path(filePath),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )

        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to save task ${task.id}", e)
        Result.failure(e)
    }
}
```

**Why Atomic Writes?**
- Prevents file corruption during crashes
- Ensures either old or new data is present, never partial
- Critical for task restoration on restart

---

## API Endpoints

### REST API Routes

**Base Path:** `/scheduler`

All endpoints are defined in `SchedulerRoutes.kt`.

---

#### 1. Create Task

**Endpoint:** `POST /scheduler/tasks`

**Request Body:**
```json
{
  "title": "Daily Market Summary",
  "taskRequest": "Provide a summary of today's market trends",
  "intervalSeconds": 86400,
  "executeImmediately": true,
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5"
}
```

**Request Fields:**
- `title` (optional): Task display name
- `taskRequest` (required): Message to send to AI
- `intervalSeconds` (required): Execution interval (minimum 10)
- `executeImmediately` (required): Execute on creation?
- `providerId` (optional): Provider override (CLAUDE, OPENAI, HUGGINGFACE)
- `model` (optional): Model override

**Success Response (201 Created):**
```json
{
  "taskId": "uuid-here",
  "sessionId": "session-uuid",
  "message": "Task created and started successfully"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid parameters
  ```json
  {
    "success": false,
    "message": "Interval must be at least 10 seconds"
  }
  ```
- `400 Bad Request`: Invalid provider
  ```json
  {
    "success": false,
    "message": "Invalid provider: INVALID. Valid providers: CLAUDE, OPENAI, HUGGINGFACE, GEMINI, CUSTOM"
  }
  ```
- `500 Internal Server Error`: Creation failed

**Example cURL:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Hourly News",
    "taskRequest": "What are the top tech news in the last hour?",
    "intervalSeconds": 3600,
    "executeImmediately": true
  }'
```

---

#### 2. List All Tasks

**Endpoint:** `GET /scheduler/tasks`

**Success Response (200 OK):**
```json
{
  "tasks": [
    {
      "id": "task-uuid",
      "title": "Daily Market Summary",
      "taskRequest": "Provide market summary",
      "intervalSeconds": 86400,
      "executeImmediately": true,
      "sessionId": "session-uuid",
      "createdAt": 1700000000000,
      "isRunning": true,
      "secondsUntilNext": 3600,
      "providerId": "CLAUDE",
      "model": "claude-sonnet-4-5"
    }
  ]
}
```

**Response Fields:**
- `isRunning`: Current execution state
- `secondsUntilNext`: Time until next execution (0 if stopped)

**Example cURL:**
```bash
curl http://localhost:8080/scheduler/tasks
```

---

#### 3. Get Task Details

**Endpoint:** `GET /scheduler/tasks/{id}`

**Path Parameters:**
- `id`: Task UUID

**Success Response (200 OK):**
```json
{
  "id": "task-uuid",
  "title": "Daily Market Summary",
  "taskRequest": "Provide market summary",
  "intervalSeconds": 86400,
  "executeImmediately": true,
  "sessionId": "session-uuid",
  "createdAt": 1700000000000,
  "isRunning": true,
  "secondsUntilNext": 3600,
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5"
}
```

**Error Response:**
- `404 Not Found`: Task not found
  ```json
  {
    "success": false,
    "message": "Task not found: task-uuid"
  }
  ```

**Example cURL:**
```bash
curl http://localhost:8080/scheduler/tasks/task-uuid
```

---

#### 4. Stop Task

**Endpoint:** `POST /scheduler/tasks/{id}/stop`

Pauses task execution without deleting it. Can be resumed with `/start`.

**Path Parameters:**
- `id`: Task UUID

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Task stopped successfully"
}
```

**Error Response:**
- `404 Not Found`: Task not found

**Example cURL:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks/task-uuid/stop
```

---

#### 5. Start Task

**Endpoint:** `POST /scheduler/tasks/{id}/start`

Resumes a stopped task.

**Path Parameters:**
- `id`: Task UUID

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Task started successfully"
}
```

**Error Response:**
- `404 Not Found`: Task not found

**Example cURL:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks/task-uuid/start
```

---

#### 6. Delete Task

**Endpoint:** `DELETE /scheduler/tasks/{id}`

Permanently deletes task and associated session.

**Path Parameters:**
- `id`: Task UUID

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Task deleted successfully"
}
```

**Side Effects:**
- Stops scheduler
- Deletes task from storage
- Deletes associated chat session
- Removes scheduler instance from memory

**Error Response:**
- `404 Not Found`: Task not found

**Example cURL:**
```bash
curl -X DELETE http://localhost:8080/scheduler/tasks/task-uuid
```

---

## Frontend Integration

### UI Components

#### 1. Scheduler Button (Sidebar)

**Location:** `index.html` (sidebar section)

```html
<button id="schedulerButton" class="scheduler-button-sidebar"
        title="Создать задачу планировщика">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <!-- Calendar icon with clock -->
    </svg>
    Новая задача
</button>
```

**Styles:** `styles/components/buttons.css` - `.scheduler-button-sidebar`

---

#### 2. Scheduler Modal

**Location:** `index.html` (modals section)

**Structure:**
```html
<div id="schedulerModal" class="modal">
    <div class="modal-content">
        <div class="modal-header">
            <h3>Создать запланированную задачу</h3>
            <button id="closeSchedulerModal" class="close-button">&times;</button>
        </div>

        <div class="scheduler-content">
            <form id="schedulerForm" class="scheduler-form">
                <!-- Form fields -->
            </form>
        </div>
    </div>
</div>
```

**Form Fields:**
1. **Task Title** (optional)
   ```html
   <input type="text" id="schedulerTitle" class="form-input">
   ```

2. **Task Description** (required)
   ```html
   <textarea id="schedulerRequest" class="form-textarea" rows="4" required></textarea>
   ```

3. **Interval** (required, min 10 seconds)
   ```html
   <input type="number" id="schedulerInterval" class="form-input"
          min="10" value="60" required>
   ```

4. **Execute Immediately** (checkbox)
   ```html
   <input type="checkbox" id="schedulerExecuteImmediately" checked>
   ```

5. **Provider Override** (optional)
   ```html
   <select id="schedulerProvider" class="form-select">
       <option value="">Использовать глобальные настройки</option>
       <option value="CLAUDE">Claude</option>
       <option value="OPENAI">OpenAI</option>
       <option value="HUGGINGFACE">HuggingFace</option>
   </select>
   ```

6. **Model Override** (optional)
   ```html
   <input type="text" id="schedulerModel" class="form-input">
   ```

**Styles:** `styles/components/forms.css` - `.scheduler-form`, `.form-group`, etc.

---

#### 3. Tasks Category Filter

**Location:** `index.html` (sessions-categories section)

```html
<button class="category-item" data-category="tasks">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <!-- Calendar icon -->
    </svg>
    <span class="category-label">Задачи</span>
    <span class="category-count" id="tasksCount">0</span>
</button>
```

**Functionality:**
- Filters session list to show only scheduled task sessions
- Count badge shows number of active task sessions
- Synchronized with session list updates

---

### JavaScript Modules

#### 1. schedulerApi.js

**Location:** `static/js/api/schedulerApi.js`

**Exports:** `schedulerApi` object with methods

**Methods:**

```javascript
export const schedulerApi = {
    // Create new task
    async createTask(taskData) {
        const response = await fetch('/scheduler/tasks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title: taskData.title || null,
                taskRequest: taskData.taskRequest,
                intervalSeconds: parseInt(taskData.intervalSeconds),
                executeImmediately: taskData.executeImmediately,
                providerId: taskData.providerId || null,
                model: taskData.model || null
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to create task');
        }

        return await response.json();
    },

    // Get all tasks
    async getAllTasks() { /* ... */ },

    // Get task details
    async getTask(taskId) { /* ... */ },

    // Stop task
    async stopTask(taskId) { /* ... */ },

    // Start task
    async startTask(taskId) { /* ... */ },

    // Delete task
    async deleteTask(taskId) { /* ... */ }
};
```

**Error Handling:**
- Throws exceptions on HTTP errors
- Parses error messages from response body
- Propagates to UI layer for user feedback

---

#### 2. schedulerModal.js

**Location:** `static/js/ui/schedulerModal.js`

**Exports:** `SchedulerModal` class

**Class Structure:**

```javascript
export class SchedulerModal {
    constructor() {
        this.modal = document.getElementById('schedulerModal');
        this.form = document.getElementById('schedulerForm');
        this.closeButton = document.getElementById('closeSchedulerModal');
        this.cancelButton = document.getElementById('cancelSchedulerButton');

        this.initializeEventListeners();
    }

    initializeEventListeners() {
        // Open modal
        const schedulerButton = document.getElementById('schedulerButton');
        schedulerButton.addEventListener('click', () => this.open());

        // Close modal
        this.closeButton.addEventListener('click', () => this.close());
        this.cancelButton.addEventListener('click', () => this.close());

        // Close on background click
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) this.close();
        });

        // Form submission
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));
    }

    open() {
        this.modal.classList.add('active');
        this.form.reset();
        document.getElementById('schedulerInterval').value = 60;
        document.getElementById('schedulerExecuteImmediately').checked = true;
    }

    close() {
        this.modal.classList.remove('active');
    }

    async handleSubmit(e) {
        e.preventDefault();

        // Collect form data
        const formData = {
            title: document.getElementById('schedulerTitle').value.trim() || null,
            taskRequest: document.getElementById('schedulerRequest').value.trim(),
            intervalSeconds: document.getElementById('schedulerInterval').value,
            executeImmediately: document.getElementById('schedulerExecuteImmediately').checked,
            providerId: document.getElementById('schedulerProvider').value || null,
            model: document.getElementById('schedulerModel').value.trim() || null
        };

        // Validation
        if (!formData.taskRequest) {
            alert('Пожалуйста, введите описание задачи');
            return;
        }

        if (formData.intervalSeconds < 10) {
            alert('Интервал должен быть не менее 10 секунд');
            return;
        }

        try {
            // Show loading state
            const submitButton = this.form.querySelector('button[type="submit"]');
            submitButton.disabled = true;
            submitButton.textContent = 'Создание...';

            // Create task
            const result = await schedulerApi.createTask(formData);

            // Close modal
            this.close();

            // Reload sessions
            await sessionService.loadSessions();

            // Switch to new session
            if (result.sessionId) {
                await sessionService.switchSession(result.sessionId);
            }

            // Show success notification
            alert(`Задача создана! ${formData.executeImmediately ?
                   'Первое выполнение начато.' :
                   'Задача будет выполнена через ' + formData.intervalSeconds + ' секунд.'}`);

        } catch (error) {
            console.error('Failed to create task:', error);
            alert('Ошибка при создании задачи: ' + error.message);
        } finally {
            // Restore button
            const submitButton = this.form.querySelector('button[type="submit"]');
            submitButton.disabled = false;
            submitButton.textContent = 'Создать задачу';
        }
    }
}
```

**Initialization:** Instantiated in `main.js`:

```javascript
import { SchedulerModal } from './ui/schedulerModal.js';

async function initApp() {
    // ... other initialization
    const schedulerModal = new SchedulerModal();
    // ...
}
```

---

### Session Integration

**Session Linkage:**

Sessions are linked to tasks via `scheduledTaskId` field:

```kotlin
// ChatSession.kt
data class ChatSession(
    val id: String,
    val assistantId: String? = null,
    val scheduledTaskId: String? = null,  // Links to task
    // ... other fields
)
```

**Mutual Exclusivity:**
- A session can have EITHER `assistantId` OR `scheduledTaskId` (or neither)
- Not both at the same time
- Enforced by business logic, not database constraints

**Session List Response:**

```kotlin
// SessionListItem includes scheduledTaskId
@Serializable
data class SessionListItem(
    val id: String,
    val title: String?,
    val messageCount: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val assistantId: String? = null,
    val scheduledTaskId: String? = null
)
```

**Frontend Filtering:**

Sessions with `scheduledTaskId` are shown when "Задачи" category is selected:

```javascript
// sessionsUI.js
filterSessions(category, sessions) {
    if (category === 'tasks') {
        return sessions.filter(s => s.scheduledTaskId != null);
    }
    // ... other categories
}
```

---

## Persistence Layer

### Storage Architecture

**Directory Structure:**
```
data/
├── sessions/              # Chat sessions
│   ├── session-1.json
│   └── session-2.json
└── scheduled_tasks/       # Scheduled tasks
    ├── task-1.json
    └── task-2.json
```

### Task JSON Format

**Example:** `data/scheduled_tasks/abc-123.json`

```json
{
  "id": "abc-123",
  "title": "Daily Market Summary",
  "taskRequest": "Provide a summary of today's market trends",
  "intervalSeconds": 86400,
  "executeImmediately": true,
  "providerId": "CLAUDE",
  "model": "claude-sonnet-4-5",
  "createdAt": 1700000000000,
  "sessionId": "session-xyz"
}
```

**Fields:**
- All fields from `ScheduledChatTask` are serialized
- `providerId` serialized as string (e.g., "CLAUDE")
- Null values are included in JSON

### Atomic Write Mechanism

**Problem:** File corruption during crashes or power loss

**Solution:** Two-phase commit

```kotlin
// Phase 1: Write to temporary file
val tempFile = File("$filePath.tmp")
tempFile.writeText(jsonContent)

// Phase 2: Atomic move to final location
Files.move(
    tempFile.toPath(),
    Path(filePath),
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE
)
```

**Guarantees:**
- At any point, either old or new file exists (never partial)
- File system ensures atomicity at OS level
- Crash-safe persistence

### Loading Strategy

**On Application Startup:**

1. `SchedulerManager` initialized by `AppModule`
2. `init` block calls `loadAllTasks()`
3. Each task file is read from `data/scheduled_tasks/`
4. Tasks deserialized to `ScheduledChatTask` objects
5. `ChatTaskScheduler` instances created
6. **Important:** `initialize()` is NOT called (session exists)
7. Schedulers started with `.start()`

**Code:**
```kotlin
private suspend fun loadAllTasks() {
    storage.loadAllTasks().onSuccess { tasks ->
        tasks.forEach { task ->
            try {
                val scheduler = ChatTaskScheduler(task, sessionManager, sendMessageUseCase)
                // Don't call initialize() - session already exists
                scheduler.start()
                schedulers[task.id] = scheduler
                logger.info("Restored and started task: ${task.id}")
            } catch (e: Exception) {
                logger.error("Failed to restore task ${task.id}", e)
            }
        }
    }
}
```

**Error Handling:**
- Failed task loads are logged but don't stop application
- Corrupted JSON files are skipped
- Application starts even if all tasks fail to load

---

## Lifecycle Management

### Application Lifecycle

```
┌──────────────────────────────────────────────────────────┐
│                    Application Start                     │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│              AppModule Initialization                    │
│  1. Create ScheduledTaskStorage                          │
│  2. Create SchedulerManager                              │
│     - SchedulerManager.init runs                         │
│     - Calls loadAllTasks()                               │
│     - Loads from data/scheduled_tasks/                   │
│     - Creates ChatTaskScheduler instances                │
│     - Starts all schedulers                              │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│                  Normal Operation                        │
│  - Schedulers run in background coroutines               │
│  - Execute tasks at specified intervals                  │
│  - Handle errors gracefully                              │
│  - Persist state on modifications                        │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│               Graceful Shutdown                          │
│  1. AppModule.close() called                             │
│  2. SchedulerManager.shutdown() executed                 │
│     - Stop all schedulers                                │
│     - Save all tasks to disk                             │
│     - Cancel coroutine scopes                            │
│     - Clear scheduler map                                │
└──────────────────────────────────────────────────────────┘
```

### Task Lifecycle

```
┌──────────────────────────────────────────────────────────┐
│                   Task Creation                          │
│  1. User fills scheduler modal                           │
│  2. POST /scheduler/tasks                                │
│  3. SchedulerManager.createTask()                        │
│  4. ChatTaskScheduler created                            │
│  5. scheduler.initialize()                               │
│     - Create session with scheduledTaskId                │
│     - Post initial message                               │
│     - Set session title                                  │
│  6. scheduler.start()                                    │
│  7. Save to storage                                      │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│                 Execution Loop                           │
│  IF executeImmediately:                                  │
│    - Execute task immediately                            │
│  LOOP while running:                                     │
│    - Calculate next execution time                       │
│    - delay(intervalSeconds * 1000)                       │
│    - Execute task                                        │
│      - Send message via SendMessageUseCase               │
│      - Response added to session                         │
│      - On error: post error message to session           │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│              Stop (Pausable)                             │
│  1. POST /scheduler/tasks/{id}/stop                      │
│  2. scheduler.stop()                                     │
│     - Set isRunning = false                              │
│     - Coroutine exits loop                               │
│  3. Save state to storage                                │
│                                                          │
│  Can be resumed with:                                    │
│  POST /scheduler/tasks/{id}/start                        │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│               Delete (Permanent)                         │
│  1. DELETE /scheduler/tasks/{id}                         │
│  2. scheduler.stop()                                     │
│  3. scheduler.shutdown()                                 │
│     - Cancel coroutine scope                             │
│     - Clean up resources                                 │
│  4. Delete associated session                            │
│  5. Delete from storage                                  │
│  6. Remove from scheduler map                            │
└──────────────────────────────────────────────────────────┘
```

### Session Lifecycle

```
┌──────────────────────────────────────────────────────────┐
│              Session Creation (for Task)                 │
│  1. sessionManager.createSession(scheduledTaskId)        │
│  2. ChatSession created with scheduledTaskId link        │
│  3. Initial message posted (info about task)             │
│  4. Session title set                                    │
│  5. Session persisted to data/sessions/                  │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│                Message Accumulation                      │
│  - Each task execution adds 2 messages:                  │
│    1. User message (task request)                        │
│    2. Assistant message (AI response)                    │
│  - Or error message if execution fails                   │
│  - Session history grows over time                       │
│  - Can be compressed using compression API               │
└────────────────────────┬─────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│              Session Deletion                            │
│  - Triggered by task deletion                            │
│  - Or user manually deletes session                      │
│  - If user deletes session:                              │
│    - Task continues running                              │
│    - BUT: sessionId becomes invalid                      │
│    - Task execution fails                                │
│    - Should delete task to avoid errors                  │
└──────────────────────────────────────────────────────────┘
```

**Important Notes:**
- Deleting a task deletes its session
- Deleting a session does NOT delete the task
- Orphaned tasks (session deleted) will fail on execution
- Future improvement: Auto-delete task when session is deleted

---

## Error Handling

### Error Categories

#### 1. Task Creation Errors

**Validation Errors:**
```kotlin
// Interval too short
if (task.intervalSeconds < 10) {
    return Result.failure(
        IllegalArgumentException("Interval must be at least 10 seconds")
    )
}

// Empty task request
if (request.taskRequest.isBlank()) {
    call.respond(HttpStatusCode.BadRequest, ...)
}

// Invalid provider
try {
    ProviderType.valueOf(providerId)
} catch (e: IllegalArgumentException) {
    call.respond(HttpStatusCode.BadRequest, "Invalid provider: $providerId")
}
```

**Response:** `400 Bad Request` with error message

---

#### 2. Execution Errors

**Scenarios:**
- Network errors (AI provider unavailable)
- Rate limit exceeded
- Invalid API key
- Model not found
- Context window exceeded

**Handling:**
```kotlin
override suspend fun onTaskError(error: Exception) {
    val sessionId = task.sessionId ?: return

    val errorMessage = """
        ⚠️ Ошибка выполнения задачи:
        ${error.message ?: "Неизвестная ошибка"}

        Следующая попытка будет выполнена через ${formatInterval(task.intervalSeconds)}
    """.trimIndent()

    sessionManager.addMessageToSession(
        sessionId,
        MessageRole.ASSISTANT,
        errorMessage
    )
}
```

**Behavior:**
- Error message posted to chat
- Scheduler continues running
- Next execution attempted after interval
- No automatic task termination

**User Experience:**
- Errors visible in chat interface
- User can see error history
- User can stop/delete task if errors persist

---

#### 3. Persistence Errors

**Scenarios:**
- Disk full
- Permission denied
- File corruption
- JSON deserialization error

**Handling:**

```kotlin
// Save errors
suspend fun saveTask(task: ScheduledChatTask): Result<Unit> {
    try {
        // ... atomic write
        Result.success(Unit)
    } catch (e: Exception) {
        logger.error("Failed to save task ${task.id}", e)
        Result.failure(e)
    }
}

// Load errors
suspend fun loadAllTasks(): Result<List<ScheduledChatTask>> {
    try {
        val tasks = dir.listFiles()?.mapNotNull { file ->
            try {
                json.decodeFromString<ScheduledChatTask>(file.readText())
            } catch (e: Exception) {
                logger.error("Failed to load task from ${file.name}", e)
                null  // Skip corrupted file
            }
        } ?: emptyList()

        Result.success(tasks)
    } catch (e: Exception) {
        logger.error("Failed to load tasks", e)
        Result.failure(e)
    }
}
```

**Behavior:**
- Save errors logged, task continues in memory
- Load errors: corrupted tasks skipped
- Application starts even if all tasks fail to load

---

#### 4. Session Management Errors

**Scenarios:**
- Session not found
- Session deleted externally
- Session creation failed

**Handling:**

```kotlin
// In ChatTaskScheduler.onTaskExecution()
override suspend fun onTaskExecution() {
    val sessionId = task.sessionId ?: run {
        logger.error("Session ID is null for task ${task.id}")
        return  // Skip execution
    }

    // ... send message
}

// In SchedulerManager.deleteTask()
suspend fun deleteTask(taskId: String): Result<Unit> {
    // ... stop scheduler

    scheduler.task.sessionId?.let { sessionId ->
        try {
            sessionManager.deleteSession(sessionId)
        } catch (e: Exception) {
            logger.warn("Failed to delete session $sessionId for task $taskId", e)
            // Continue with task deletion anyway
        }
    }

    // ...
}
```

**Behavior:**
- Missing session: execution skipped
- Session deletion errors: logged but task still deleted

---

### Error Logging

**Log Levels:**

```kotlin
// Info: Normal operations
logger.info("Created scheduled task: $taskId")
logger.info("Restored and started task: ${task.id}")

// Warn: Recoverable issues
logger.warn("Failed to delete session $sessionId for task $taskId", e)

// Error: Execution failures
logger.error("Failed to execute chat task ${task.id}", error)
logger.error("Failed to save task ${task.id}", e)
logger.error("Failed to restore task ${task.id}", e)

// Debug: Detailed tracing
logger.debug("Executing chat task ${task.id}: sending message to session $sessionId")
logger.debug("Saved task: ${task.id}")
```

**Log Format:** Uses SLF4J with configured backend (Logback)

---

## Configuration

### Global Configuration

**Provider Settings:**

Managed by `SettingsService` and configurable in Settings modal:

- Default provider (CLAUDE, OPENAI, HUGGINGFACE)
- Default model for each provider
- Temperature, max tokens

**Task-Specific Overrides:**

Tasks can override global settings:

```kotlin
// Use global provider and model
ScheduledChatTask(
    providerId = null,
    model = null,
    // ...
)

// Override provider only (use provider's global model)
ScheduledChatTask(
    providerId = ProviderType.OPENAI,
    model = null,
    // ...
)

// Override both
ScheduledChatTask(
    providerId = ProviderType.CLAUDE,
    model = "claude-opus-4",
    // ...
)
```

### Interval Constraints

**Minimum Interval:** 10 seconds

**Rationale:**
- Prevents API rate limit issues
- Avoids excessive costs
- Reduces server load

**Enforcement:**

```kotlin
// Backend validation
if (request.intervalSeconds < 10) {
    call.respond(
        HttpStatusCode.BadRequest,
        SchedulerOperationResponse(
            success = false,
            message = "Interval must be at least 10 seconds"
        )
    )
    return@post
}

// Frontend validation
<input type="number" id="schedulerInterval"
       class="form-input" min="10" value="60" required>

// Additional JavaScript validation
if (formData.intervalSeconds < 10) {
    alert('Интервал должен быть не менее 10 секунд');
    return;
}
```

**Maximum Interval:** None

Users can set arbitrarily long intervals (days, weeks, months).

### Storage Configuration

**Default Paths:**

```kotlin
// Tasks storage
ScheduledTaskStorage(storagePath = "data/scheduled_tasks")

// Sessions storage
JsonPersistenceStorage(storagePath = "data/sessions")
```

**Customization:**

Can be modified in `AppModule`:

```kotlin
val scheduledTaskStorage: ScheduledTaskStorage by lazy {
    ScheduledTaskStorage(
        storagePath = System.getenv("TASKS_STORAGE_PATH") ?: "data/scheduled_tasks"
    )
}
```

---

## Usage Examples

### Example 1: Daily Market Summary

**Scenario:** Get daily market trends every 24 hours

**Request:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Daily Market Summary",
    "taskRequest": "Provide a comprehensive summary of today'\''s market trends, including major indices, top movers, and significant news",
    "intervalSeconds": 86400,
    "executeImmediately": true,
    "providerId": "CLAUDE",
    "model": "claude-sonnet-4-5"
  }'
```

**Response:**
```json
{
  "taskId": "task-abc-123",
  "sessionId": "session-xyz-789",
  "message": "Task created and started successfully"
}
```

**Result:**
- Session created with title "Daily Market Summary"
- First execution happens immediately
- Subsequent executions every 24 hours
- All summaries accumulated in the same session

---

### Example 2: Hourly News Monitor

**Scenario:** Track tech news every hour

**Request:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Hourly Tech News",
    "taskRequest": "What are the top 5 tech news stories in the last hour? Provide brief summaries.",
    "intervalSeconds": 3600,
    "executeImmediately": false
  }'
```

**Response:**
```json
{
  "taskId": "task-def-456",
  "sessionId": "session-uvw-012",
  "message": "Task created and started successfully"
}
```

**Result:**
- Session created with title "Hourly Tech News"
- First execution after 1 hour
- Uses global provider/model settings
- Runs every hour thereafter

---

### Example 3: Testing with Short Interval

**Scenario:** Test scheduler with 30-second interval

**Request:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Task",
    "taskRequest": "What time is it?",
    "intervalSeconds": 30,
    "executeImmediately": true
  }'
```

**Result:**
- Executes every 30 seconds
- Useful for testing scheduler functionality
- Can verify timing accuracy

---

### Example 4: Stopping and Resuming

**Stop Task:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks/task-abc-123/stop
```

**Response:**
```json
{
  "success": true,
  "message": "Task stopped successfully"
}
```

**Resume Task:**
```bash
curl -X POST http://localhost:8080/scheduler/tasks/task-abc-123/start
```

**Response:**
```json
{
  "success": true,
  "message": "Task started successfully"
}
```

**Behavior:**
- Stop: Execution pauses, task remains in storage
- Start: Execution resumes from current time (not from when stopped)

---

### Example 5: Deleting Task and Session

**Delete Task:**
```bash
curl -X DELETE http://localhost:8080/scheduler/tasks/task-abc-123
```

**Response:**
```json
{
  "success": true,
  "message": "Task deleted successfully"
}
```

**Side Effects:**
- Task stopped and removed from memory
- Task file deleted from `data/scheduled_tasks/`
- Associated session deleted from `data/sessions/`
- Chat history permanently lost

---

### Example 6: List All Tasks

**Request:**
```bash
curl http://localhost:8080/scheduler/tasks
```

**Response:**
```json
{
  "tasks": [
    {
      "id": "task-abc-123",
      "title": "Daily Market Summary",
      "taskRequest": "Provide market summary...",
      "intervalSeconds": 86400,
      "executeImmediately": true,
      "sessionId": "session-xyz-789",
      "createdAt": 1700000000000,
      "isRunning": true,
      "secondsUntilNext": 43200,
      "providerId": "CLAUDE",
      "model": "claude-sonnet-4-5"
    },
    {
      "id": "task-def-456",
      "title": "Hourly Tech News",
      "taskRequest": "Top 5 tech news...",
      "intervalSeconds": 3600,
      "executeImmediately": false,
      "sessionId": "session-uvw-012",
      "createdAt": 1700003600000,
      "isRunning": true,
      "secondsUntilNext": 1800,
      "providerId": null,
      "model": null
    }
  ]
}
```

**Use Case:**
- Monitor all active tasks
- Check execution status
- See time until next execution

---

## Security Considerations

### 1. Input Validation

**Current Implementation:**

```kotlin
// Interval validation
if (request.intervalSeconds < 10) {
    return Result.failure(IllegalArgumentException(...))
}

// Provider validation
try {
    ProviderType.valueOf(providerId)
} catch (e: IllegalArgumentException) {
    // Invalid provider
}

// Empty request validation
if (request.taskRequest.isBlank()) {
    // Error
}
```

**Potential Issues:**
- No maximum interval limit (could create very long-running tasks)
- No validation on task request content
- No length limits on title or taskRequest

**Recommendations:**
- Add maximum interval limit (e.g., 7 days)
- Validate task request length (prevent extremely long messages)
- Sanitize title input

---

### 2. Authentication & Authorization

**Current State:** ❌ Not implemented

**Risks:**
- Anyone can create tasks
- Anyone can stop/delete other users' tasks
- No user isolation

**Recommendations:**

```kotlin
// Add authentication middleware
fun Route.schedulerRoutes(
    schedulerManager: SchedulerManager,
    authService: AuthService  // NEW
) {
    authenticate("jwt") {  // NEW
        route("/scheduler") {
            post("/tasks") {
                val userId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("sub")
                    ?.asString()

                // Create task with userId
                val task = ScheduledChatTask(
                    userId = userId,  // NEW
                    // ...
                )
            }
        }
    }
}
```

---

### 3. Rate Limiting

**Current State:** ❌ Not implemented

**Risks:**
- Users can create unlimited tasks
- Potential API abuse
- Cost implications

**Recommendations:**

```kotlin
// Limit tasks per user
suspend fun createTask(task: ScheduledChatTask, userId: String): Result<String> {
    val userTasks = getAllTasks().filter { it.userId == userId }

    if (userTasks.size >= MAX_TASKS_PER_USER) {
        return Result.failure(
            IllegalStateException("Maximum tasks limit reached")
        )
    }

    // ...
}
```

---

### 4. Resource Limits

**Current Implementation:**

```kotlin
// Minimum interval: 10 seconds
if (task.intervalSeconds < 10) {
    return Result.failure(...)
}
```

**Missing:**
- Maximum number of concurrent tasks
- Memory limits for task storage
- Maximum session message count

**Recommendations:**

```kotlin
companion object {
    const val MIN_INTERVAL_SECONDS = 10L
    const val MAX_INTERVAL_SECONDS = 604800L  // 7 days
    const val MAX_TOTAL_TASKS = 1000
    const val MAX_TASKS_PER_USER = 10
}
```

---

### 5. File System Security

**Current Implementation:**

```kotlin
// Atomic writes prevent corruption
Files.move(
    tempFile.toPath(),
    Path(filePath),
    StandardCopyOption.REPLACE_EXISTING,
    StandardCopyOption.ATOMIC_MOVE
)
```

**Good Practices:**
- ✅ Atomic writes
- ✅ Proper error handling

**Missing:**
- File permission checks
- Directory traversal prevention
- Disk space monitoring

**Recommendations:**

```kotlin
// Validate task ID doesn't contain path traversal
fun validateTaskId(taskId: String): Boolean {
    return taskId.matches(Regex("^[a-zA-Z0-9-]+$"))
}

// Check disk space before saving
fun hasEnoughDiskSpace(): Boolean {
    val file = File(storagePath)
    return file.usableSpace > MIN_FREE_SPACE_BYTES
}
```

---

### 6. Error Information Leakage

**Current Implementation:**

```kotlin
// Error messages exposed to user
val errorMessage = """
    ⚠️ Ошибка выполнения задачи:
    ${error.message ?: "Неизвестная ошибка"}
    ...
""".trimIndent()
```

**Potential Issues:**
- Stack traces might leak implementation details
- API keys could appear in error messages

**Recommendations:**

```kotlin
fun sanitizeErrorMessage(error: Exception): String {
    return when (error) {
        is NetworkException -> "Network error occurred"
        is AuthenticationException -> "Authentication failed"
        is RateLimitException -> "Rate limit exceeded"
        else -> "An error occurred"
    }
}
```

---

## Future Improvements

### 1. User Isolation

**Problem:** All tasks shared globally

**Solution:**
- Add `userId` field to `ScheduledChatTask`
- Filter tasks by authenticated user
- Prevent cross-user task access

**Implementation:**
```kotlin
@Serializable
data class ScheduledChatTask(
    // ... existing fields
    val userId: String,  // NEW
    // ...
)

// Filter by user
fun getUserTasks(userId: String): List<ScheduledChatTask> {
    return getAllTasks().filter { it.userId == userId }
}
```

---

### 2. Task Templates

**Problem:** Repetitive task creation

**Solution:** Pre-defined task templates

**Implementation:**
```kotlin
enum class TaskTemplate(
    val title: String,
    val taskRequest: String,
    val defaultInterval: Long
) {
    DAILY_NEWS(
        "Daily News Summary",
        "Provide top 10 news stories from today",
        86400
    ),
    MARKET_UPDATE(
        "Market Update",
        "Summarize today's market performance",
        86400
    ),
    WEATHER_FORECAST(
        "Weather Forecast",
        "What's the weather forecast for today?",
        86400
    )
}
```

**UI:**
```html
<select id="taskTemplate">
    <option value="">Custom Task</option>
    <option value="DAILY_NEWS">Daily News Summary</option>
    <option value="MARKET_UPDATE">Market Update</option>
    <option value="WEATHER_FORECAST">Weather Forecast</option>
</select>
```

---

### 3. Cron-like Scheduling

**Problem:** Only fixed intervals supported

**Solution:** Cron expressions for complex schedules

**Example:**
```kotlin
@Serializable
data class ScheduledChatTask(
    // ... existing fields
    val cronExpression: String? = null,  // "0 9 * * 1-5" (9am weekdays)
    // ...
)
```

**Library:** Use `com.cronutils:cron-utils` for parsing

---

### 4. Task History & Analytics

**Problem:** No execution history tracking

**Solution:** Store execution records

**Implementation:**
```kotlin
@Serializable
data class TaskExecution(
    val taskId: String,
    val timestamp: Long,
    val success: Boolean,
    val responseTime: Long,
    val errorMessage: String? = null
)

class TaskExecutionStorage {
    suspend fun saveExecution(execution: TaskExecution)
    suspend fun getTaskHistory(taskId: String): List<TaskExecution>
    suspend fun getTaskStatistics(taskId: String): TaskStatistics
}

data class TaskStatistics(
    val totalExecutions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val averageResponseTime: Long,
    val lastExecution: Long
)
```

---

### 5. Execution Retry Logic

**Problem:** Failed executions not retried

**Solution:** Exponential backoff retry

**Implementation:**
```kotlin
class ChatTaskScheduler(
    // ... existing parameters
    private val maxRetries: Int = 3,
    private val retryDelaySeconds: Long = 60
) : TaskScheduler<ScheduledChatTask>(task) {

    override suspend fun onTaskError(error: Exception) {
        var retryCount = 0

        while (retryCount < maxRetries) {
            delay(retryDelaySeconds * 1000 * (retryCount + 1))

            try {
                onTaskExecution()
                return  // Success
            } catch (e: Exception) {
                retryCount++
            }
        }

        // All retries failed
        postErrorToSession(error)
    }
}
```

---

### 6. Task Notifications

**Problem:** No notifications for task failures

**Solution:** WebSocket notifications

**Implementation:**
```kotlin
// Backend
class TaskNotificationService {
    suspend fun notifyTaskFailure(taskId: String, error: String) {
        val notification = Notification(
            type = "TASK_FAILURE",
            taskId = taskId,
            message = error
        )
        websocketManager.broadcast(notification)
    }
}

// Frontend
const ws = new WebSocket('ws://localhost:8080/notifications');
ws.onmessage = (event) => {
    const notification = JSON.parse(event.data);
    if (notification.type === 'TASK_FAILURE') {
        showNotification(`Task ${notification.taskId} failed: ${notification.message}`);
    }
};
```

---

### 7. Task Chaining

**Problem:** No task dependencies

**Solution:** Allow tasks to trigger other tasks

**Implementation:**
```kotlin
@Serializable
data class ScheduledChatTask(
    // ... existing fields
    val nextTaskId: String? = null,  // Execute this task after completion
    val onSuccessTaskId: String? = null,
    val onFailureTaskId: String? = null
)
```

---

### 8. Database Persistence

**Problem:** File-based storage doesn't scale

**Solution:** Migrate to database (PostgreSQL)

**Schema:**
```sql
CREATE TABLE scheduled_tasks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255),
    task_request TEXT NOT NULL,
    interval_seconds BIGINT NOT NULL,
    execute_immediately BOOLEAN NOT NULL,
    provider_id VARCHAR(50),
    model VARCHAR(100),
    session_id VARCHAR(36),
    created_at BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT true,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX idx_tasks_user_id ON scheduled_tasks(user_id);
CREATE INDEX idx_tasks_active ON scheduled_tasks(is_active);
```

**Repository:**
```kotlin
interface TaskRepository {
    suspend fun save(task: ScheduledChatTask)
    suspend fun findById(id: String): ScheduledChatTask?
    suspend fun findByUserId(userId: String): List<ScheduledChatTask>
    suspend fun findAllActive(): List<ScheduledChatTask>
    suspend fun delete(id: String)
}
```

---

### 9. Task Export/Import

**Problem:** No way to backup/share tasks

**Solution:** Export/import functionality

**API:**
```kotlin
// Export
GET /scheduler/tasks/export
Response: JSON array of all tasks

// Import
POST /scheduler/tasks/import
Request: JSON array of tasks
Response: { imported: 5, failed: 0, errors: [] }
```

---

### 10. Advanced Error Handling

**Problem:** Limited error recovery

**Solution:** Circuit breaker pattern

**Implementation:**
```kotlin
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutSeconds: Long = 300
) {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var state = State.CLOSED

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun execute(block: suspend () -> Unit) {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeoutSeconds * 1000) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            State.HALF_OPEN -> {
                try {
                    block()
                    state = State.CLOSED
                    failureCount = 0
                } catch (e: Exception) {
                    state = State.OPEN
                    throw e
                }
            }
            State.CLOSED -> {
                try {
                    block()
                    failureCount = 0
                } catch (e: Exception) {
                    failureCount++
                    lastFailureTime = System.currentTimeMillis()

                    if (failureCount >= failureThreshold) {
                        state = State.OPEN
                    }

                    throw e
                }
            }
        }
    }
}
```

---

## Appendix: File Locations

### Backend Files

**Domain Layer:**
- `src/main/kotlin/com/researchai/scheduler/ScheduledTask.kt`
- `src/main/kotlin/com/researchai/scheduler/TaskScheduler.kt`
- `src/main/kotlin/com/researchai/scheduler/ScheduledChatTask.kt`
- `src/main/kotlin/com/researchai/scheduler/ChatTaskScheduler.kt`

**Service Layer:**
- `src/main/kotlin/com/researchai/services/SchedulerManager.kt`

**Persistence Layer:**
- `src/main/kotlin/com/researchai/persistence/ScheduledTaskStorage.kt`

**API Layer:**
- `src/main/kotlin/com/researchai/routes/SchedulerRoutes.kt`
- `src/main/kotlin/com/researchai/models/SchedulerResponses.kt`

**Integration:**
- `src/main/kotlin/com/researchai/di/AppModule.kt`
- `src/main/kotlin/Routing.kt`

**Session Integration:**
- `src/main/kotlin/com/researchai/models/ChatSession.kt`
- `src/main/kotlin/com/researchai/models/SessionResponses.kt`
- `src/main/kotlin/com/researchai/services/ChatSessionManager.kt`
- `src/main/kotlin/com/researchai/routes/ChatRoutes.kt`

### Frontend Files

**JavaScript:**
- `src/main/resources/static/js/api/schedulerApi.js`
- `src/main/resources/static/js/ui/schedulerModal.js`
- `src/main/resources/static/js/main.js`

**HTML:**
- `src/main/resources/static/index.html`

**CSS:**
- `src/main/resources/static/styles/components/buttons.css`
- `src/main/resources/static/styles/components/forms.css`
- `src/main/resources/static/styles/components/modals.css`

### Storage Directories

**Runtime Data:**
- `data/scheduled_tasks/` - Task persistence
- `data/sessions/` - Session persistence

---

## Glossary

**Task:** A scheduled job that executes at regular intervals
**Scheduler:** Component that manages task execution timing
**Session:** Chat conversation associated with a task
**Interval:** Time between task executions (in seconds)
**Execute Immediately:** Flag to run task on creation
**Hybrid Configuration:** Global settings with per-task overrides
**Atomic Write:** Write operation that completes fully or not at all
**Graceful Shutdown:** Proper cleanup before application exit
**Circuit Breaker:** Pattern to prevent cascading failures

---

**End of Documentation**

Generated: 2025-11-20
Version: 1.0
Status: Complete
