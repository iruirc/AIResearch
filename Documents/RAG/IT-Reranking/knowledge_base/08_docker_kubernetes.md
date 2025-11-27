# Docker и Kubernetes

## Docker

### Что такое Docker

Docker — это платформа для разработки, доставки и запуска приложений в контейнерах. Контейнер — это стандартизированная единица ПО, которая упаковывает код и все его зависимости.

### Основные концепции

**Image (Образ):**
Неизменяемый шаблон для создания контейнеров. Содержит ОС, зависимости и приложение. Образы строятся слоями — каждая инструкция в Dockerfile создаёт слой.

**Container (Контейнер):**
Запущенный экземпляр образа. Изолированный процесс с собственным файловой системой, сетью и пространством процессов.

**Dockerfile:**
Текстовый файл с инструкциями для сборки образа.

**Registry:**
Хранилище образов. Docker Hub — публичный реестр. Можно использовать приватные (AWS ECR, GCR).

### Пример Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### Оптимизация образов

**Multi-stage builds:**
Использование нескольких FROM для уменьшения размера финального образа.

```dockerfile
# Build stage
FROM node:18 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
```

**Лучшие практики:**
- Используйте slim/alpine образы
- Минимизируйте количество слоёв
- Помещайте редко меняющиеся инструкции в начало
- Используйте .dockerignore
- Не запускайте от root

### Docker Compose

Инструмент для определения и запуска multi-container приложений.

```yaml
version: '3.8'
services:
  web:
    build: .
    ports:
      - "8000:8000"
    depends_on:
      - db
    environment:
      - DATABASE_URL=postgresql://user:pass@db/mydb

  db:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      - POSTGRES_PASSWORD=pass

volumes:
  postgres_data:
```

### Networking в Docker

**Bridge (по умолчанию):**
Изолированная сеть для контейнеров на одном хосте.

**Host:**
Контейнер использует сеть хоста напрямую.

**Overlay:**
Сеть для связи контейнеров на разных хостах (Docker Swarm, Kubernetes).

## Kubernetes

### Что такое Kubernetes

Kubernetes (K8s) — это платформа для автоматизации развёртывания, масштабирования и управления контейнеризированными приложениями. Разработан Google, сейчас поддерживается CNCF.

### Основные компоненты

**Control Plane:**
- **API Server**: Центральная точка управления
- **etcd**: Хранилище состояния кластера
- **Scheduler**: Распределение подов по нодам
- **Controller Manager**: Управление состоянием ресурсов

**Worker Nodes:**
- **Kubelet**: Агент на каждой ноде
- **Container Runtime**: Docker, containerd
- **Kube-proxy**: Сетевые правила

### Основные ресурсы Kubernetes

**Pod:**
Минимальная единица развёртывания. Один или несколько контейнеров с общими ресурсами.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: nginx
spec:
  containers:
    - name: nginx
      image: nginx:1.25
      ports:
        - containerPort: 80
```

**Deployment:**
Декларативное управление подами. Обеспечивает rolling updates и rollbacks.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: web
          image: myapp:v1
          ports:
            - containerPort: 8000
```

**Service:**
Абстракция для доступа к набору подов. Обеспечивает load balancing и service discovery.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: web-service
spec:
  selector:
    app: web
  ports:
    - port: 80
      targetPort: 8000
  type: ClusterIP
```

**Типы Service:**
- **ClusterIP**: Доступ только внутри кластера
- **NodePort**: Открывает порт на каждой ноде
- **LoadBalancer**: Создаёт облачный балансировщик

**Ingress:**
Управление внешним HTTP/HTTPS доступом. Маршрутизация на основе хоста и пути.

**ConfigMap и Secret:**
Хранение конфигурации и секретов отдельно от образов.

### Масштабирование

**Manual scaling:**
```bash
kubectl scale deployment web --replicas=5
```

**Horizontal Pod Autoscaler (HPA):**
Автоматическое масштабирование на основе метрик (CPU, memory, custom metrics).

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: web-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: web
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Стратегии деплоя

**Rolling Update (по умолчанию):**
Постепенная замена подов. Настраивается maxSurge и maxUnavailable.

**Recreate:**
Удаление всех старых подов перед созданием новых. Простой, но есть downtime.

### Helm

Пакетный менеджер для Kubernetes. Charts — это пакеты с шаблонами K8s ресурсов.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install my-redis bitnami/redis
```

### Операционные практики

**Resource Limits:**
Всегда указывайте requests и limits для CPU и памяти.

**Health Checks:**
Используйте liveness и readiness probes.

**Namespaces:**
Изолируйте окружения и команды.

**RBAC:**
Настройте права доступа через Role и ClusterRole.

## Заключение

Docker и Kubernetes стали стандартом для развёртывания современных приложений. Docker обеспечивает консистентность сред разработки, а Kubernetes — надёжное масштабируемое развёртывание в продакшене.
