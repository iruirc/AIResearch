# Облачные платформы

## Введение в облачные вычисления

Облачные вычисления — это предоставление вычислительных ресурсов (серверов, хранилища, баз данных, сетей, ПО) через интернет по модели "pay-as-you-go". Три основных облачных провайдера: AWS, Google Cloud Platform (GCP) и Microsoft Azure.

## Модели обслуживания

### IaaS (Infrastructure as a Service)
Базовые вычислительные ресурсы: виртуальные машины, хранилище, сети. Максимальный контроль, но требует администрирования.

**Примеры:** AWS EC2, GCP Compute Engine, Azure Virtual Machines

### PaaS (Platform as a Service)
Платформа для разработки и развёртывания приложений. Провайдер управляет инфраструктурой.

**Примеры:** Heroku, Google App Engine, AWS Elastic Beanstalk

### SaaS (Software as a Service)
Готовое ПО, доступное через браузер. Пользователь не управляет ничем, кроме настроек приложения.

**Примеры:** Google Workspace, Salesforce, Slack

## Amazon Web Services (AWS)

AWS — крупнейший облачный провайдер с самым широким набором сервисов.

### Основные сервисы AWS

**Compute:**
- **EC2**: Виртуальные серверы с полным контролем
- **Lambda**: Serverless-функции, оплата за вызовы
- **ECS/EKS**: Контейнерные сервисы (Docker/Kubernetes)
- **Fargate**: Serverless-контейнеры

**Storage:**
- **S3**: Объектное хранилище (файлы, медиа, бэкапы)
- **EBS**: Блочное хранилище для EC2
- **EFS**: Файловая система для множества серверов
- **Glacier**: Архивное хранилище (дёшево, медленный доступ)

**Database:**
- **RDS**: Managed SQL-базы (PostgreSQL, MySQL, Aurora)
- **DynamoDB**: NoSQL key-value и документная БД
- **ElastiCache**: Managed Redis/Memcached
- **Redshift**: Data warehouse для аналитики

**Networking:**
- **VPC**: Изолированная виртуальная сеть
- **Route 53**: DNS-сервис
- **CloudFront**: CDN для доставки контента
- **API Gateway**: Управление API

### Преимущества AWS:
- Самый большой выбор сервисов
- Глобальная инфраструктура (30+ регионов)
- Зрелая экосистема и документация
- Сильные enterprise-возможности

## Google Cloud Platform (GCP)

GCP — облако от Google с сильными позициями в Big Data и Machine Learning.

### Основные сервисы GCP

**Compute:**
- **Compute Engine**: Виртуальные машины
- **Cloud Functions**: Serverless-функции
- **GKE (Google Kubernetes Engine)**: Managed Kubernetes
- **Cloud Run**: Serverless-контейнеры

**Storage:**
- **Cloud Storage**: Объектное хранилище
- **Persistent Disk**: Блочное хранилище
- **Filestore**: Managed NFS

**Database:**
- **Cloud SQL**: Managed PostgreSQL, MySQL
- **Cloud Spanner**: Глобально распределённая SQL БД
- **Firestore**: Serverless NoSQL
- **BigQuery**: Serverless data warehouse

**AI/ML:**
- **Vertex AI**: Платформа для ML
- **Vision AI, Speech-to-Text**: Готовые ML-сервисы
- **TensorFlow на TPU**: Ускоренное обучение моделей

### Преимущества GCP:
- Лидер в Big Data (BigQuery) и ML
- Отличный Kubernetes (GKE)
- Конкурентные цены
- Сильная сеть (backbone Google)

## Microsoft Azure

Azure — облако от Microsoft с глубокой интеграцией с продуктами Microsoft.

### Основные сервисы Azure

**Compute:**
- **Virtual Machines**: Виртуальные серверы
- **Azure Functions**: Serverless
- **AKS (Azure Kubernetes Service)**: Managed Kubernetes
- **Container Instances**: Простые контейнеры

**Storage:**
- **Blob Storage**: Объектное хранилище
- **Azure Files**: Managed файловые шары
- **Disk Storage**: Managed диски

**Database:**
- **Azure SQL**: Managed SQL Server
- **Cosmos DB**: Глобально распределённая NoSQL
- **Azure Database for PostgreSQL/MySQL**

**Enterprise:**
- **Active Directory**: Идентификация и доступ
- **Office 365 интеграция**
- **Power Platform**: Low-code инструменты

### Преимущества Azure:
- Лучшая интеграция с Microsoft-стеком
- Сильные enterprise и гибридные решения
- Active Directory и идентификация
- Хорош для .NET-приложений

## Сравнение провайдеров

| Критерий | AWS | GCP | Azure |
|----------|-----|-----|-------|
| Доля рынка | ~32% | ~10% | ~22% |
| Сильные стороны | Широта сервисов | Big Data, ML, K8s | Enterprise, Microsoft |
| Регионы | 30+ | 35+ | 60+ |
| Ценообразование | Сложное | Простое | Среднее |
| Бесплатный tier | 12 месяцев | Always Free + $300 | 12 месяцев + Always Free |

## Мультиоблачная стратегия

Многие компании используют несколько облаков для:
- Избежания vendor lock-in
- Использования лучших сервисов каждого провайдера
- Географического распределения
- Соответствия регуляторным требованиям

**Инструменты для мультиоблака:**
- Terraform — единый IaC для всех облаков
- Kubernetes — стандартная оркестрация везде
- Pulumi — IaC на обычных языках

## Serverless

Serverless — это модель, при которой провайдер управляет инфраструктурой, а разработчик пишет только код.

**Преимущества:**
- Нет администрирования серверов
- Автоматическое масштабирование до нуля
- Оплата только за использование
- Быстрый запуск проектов

**Ограничения:**
- Cold start задержки
- Ограничения времени выполнения
- Vendor lock-in
- Сложность отладки

**Serverless-сервисы:**
- AWS Lambda, API Gateway, DynamoDB
- GCP Cloud Functions, Cloud Run
- Azure Functions

## Рекомендации по выбору

1. **Стартап без legacy**: GCP или AWS — выбирайте по цене и нужным сервисам
2. **Enterprise с Microsoft-стеком**: Azure
3. **Big Data и ML**: GCP (BigQuery, Vertex AI)
4. **Максимум сервисов**: AWS
5. **Kubernetes-focused**: GCP (GKE — самый зрелый managed K8s)

## Заключение

Выбор облачного провайдера зависит от конкретных требований: технологического стека, бюджета, географии и compliance-требований. Все три крупных провайдера предлагают надёжную инфраструктуру и богатый набор сервисов.
