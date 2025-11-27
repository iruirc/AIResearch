# Практики тестирования ПО

## Введение

Тестирование — неотъемлемая часть разработки качественного программного обеспечения. Автоматизированные тесты позволяют быстро находить регрессии, документировать поведение системы и уверенно вносить изменения.

## Пирамида тестирования

Классическая модель распределения тестов по типам:

```
        /\
       /  \      E2E Tests (немного)
      /----\
     /      \    Integration Tests (средне)
    /--------\
   /          \  Unit Tests (много)
  /------------\
```

**Unit Tests** (основа):
- Тестируют отдельные функции/классы
- Быстрые, изолированные
- Легко поддерживать
- 70-80% от общего числа тестов

**Integration Tests** (середина):
- Тестируют взаимодействие компонентов
- Включают БД, API, внешние сервисы
- Медленнее unit-тестов
- 15-25% от общего числа

**E2E Tests** (вершина):
- Тестируют всю систему целиком
- Имитируют пользовательские сценарии
- Самые медленные и хрупкие
- 5-10% от общего числа

## Unit-тестирование

### Принципы хороших unit-тестов

**F.I.R.S.T.:**
- **Fast**: Быстрое выполнение
- **Independent**: Независимость от других тестов
- **Repeatable**: Повторяемый результат
- **Self-validating**: Чёткий pass/fail
- **Timely**: Пишутся вовремя (до или во время разработки)

### Структура теста (AAA)

**Arrange** — подготовка данных и зависимостей
**Act** — выполнение тестируемого действия
**Assert** — проверка результата

```python
def test_calculate_total_with_discount():
    # Arrange
    cart = ShoppingCart()
    cart.add_item(Product("Book", 100))
    cart.add_item(Product("Pen", 50))
    discount = PercentageDiscount(10)

    # Act
    total = cart.calculate_total(discount)

    # Assert
    assert total == 135  # 150 - 10%
```

### Mocking и Stubbing

**Stub** — предоставляет заранее определённые ответы
**Mock** — записывает вызовы для последующей проверки

```python
# Stub
def test_get_user_returns_user():
    user_repo = Mock()
    user_repo.find_by_id.return_value = User(id=1, name="John")

    service = UserService(user_repo)
    user = service.get_user(1)

    assert user.name == "John"

# Mock verification
def test_user_creation_sends_email():
    email_service = Mock()
    user_service = UserService(email_service=email_service)

    user_service.create_user("john@example.com")

    email_service.send_welcome_email.assert_called_once_with("john@example.com")
```

## Integration-тестирование

### Тестирование с базой данных

**Подходы:**
1. **In-memory DB** (H2, SQLite): Быстро, но может отличаться от production
2. **Docker containers**: Реальная БД в контейнере
3. **Testcontainers**: Автоматическое управление контейнерами

```python
@pytest.fixture
def db_session():
    # Создаём тестовую БД
    engine = create_engine("postgresql://test:test@localhost/test")
    Session = sessionmaker(bind=engine)
    session = Session()

    yield session

    # Очистка после теста
    session.rollback()
    session.close()
```

### Тестирование API

```python
def test_create_user_endpoint():
    response = client.post("/api/users", json={
        "name": "John",
        "email": "john@example.com"
    })

    assert response.status_code == 201
    assert response.json()["name"] == "John"
```

## E2E-тестирование

### Инструменты

**Cypress**: Современный инструмент для веб-приложений
```javascript
describe('Login Flow', () => {
  it('should login successfully', () => {
    cy.visit('/login')
    cy.get('[data-testid="email"]').type('user@example.com')
    cy.get('[data-testid="password"]').type('password123')
    cy.get('[data-testid="submit"]').click()
    cy.url().should('include', '/dashboard')
  })
})
```

**Playwright**: От Microsoft, поддержка всех браузеров

**Selenium**: Классический инструмент, но более громоздкий

### Best Practices для E2E

- Используйте data-testid для селекторов
- Тестируйте критические пути (happy path)
- Минимизируйте количество E2E тестов
- Запускайте на CI, не локально

## Test-Driven Development (TDD)

### Цикл Red-Green-Refactor

1. **Red**: Напишите падающий тест
2. **Green**: Напишите минимальный код для прохождения
3. **Refactor**: Улучшите код, сохраняя тесты зелёными

### Преимущества TDD
- Лучший дизайн кода
- Полное покрытие тестами
- Документация через тесты
- Уверенность при рефакторинге

## Behavior-Driven Development (BDD)

Тесты в формате Gherkin, понятном бизнесу:

```gherkin
Feature: Shopping Cart
  Scenario: Add item to cart
    Given I have an empty cart
    When I add a "Book" priced at $20
    Then the cart total should be $20
    And the cart should contain 1 item
```

**Инструменты:** Cucumber, Behave, SpecFlow

## Тестирование в CI/CD

### Стратегия

1. **Pre-commit**: Линтеры, быстрые unit-тесты
2. **PR checks**: Все unit + integration тесты
3. **Main branch**: Полный набор включая E2E
4. **Nightly**: Нагрузочные тесты, security scans

### Параллелизация

- Разделение тестов по файлам/модулям
- Параллельные runners в CI
- Шардирование E2E тестов

## Метрики тестирования

### Code Coverage

Процент кода, покрытого тестами. 80% — хорошая цель, но не самоцель.

**Виды:**
- Line coverage — покрытие строк
- Branch coverage — покрытие ветвлений
- Function coverage — покрытие функций

### Mutation Testing

Автоматическое внесение мутаций в код и проверка, что тесты их ловят. Показывает качество тестов.

## Property-Based Testing

Вместо конкретных примеров — свойства, которые должны выполняться:

```python
from hypothesis import given, strategies as st

@given(st.lists(st.integers()))
def test_sort_preserves_length(lst):
    assert len(sorted(lst)) == len(lst)

@given(st.lists(st.integers()))
def test_sort_is_idempotent(lst):
    assert sorted(sorted(lst)) == sorted(lst)
```

## Тестирование legacy-кода

1. **Характеризационные тесты**: Фиксируют текущее поведение
2. **Seams**: Точки для внедрения тестов
3. **Постепенный рефакторинг**: Под защитой тестов

## Заключение

Хорошая стратегия тестирования — это баланс между покрытием, скоростью и стоимостью поддержки. Unit-тесты должны составлять основу, интеграционные — проверять критичные взаимодействия, а E2E — только ключевые пользовательские сценарии.
