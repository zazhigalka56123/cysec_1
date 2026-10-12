 # Лабораторная работа №1

REST API личных заметок на Kotlin и Spring Boot. Пользователь входит по логину и паролю, получает JWT, создаёт и читает свои заметки.

Стек: Kotlin 2.2.21, Spring Boot 4.0.8, Java 21, JDBC, H2. База работает в памяти и очищается после остановки приложения.

## API

### Запуск

Нужны JDK 21 и OpenSSL для генерации секретов. Команды выполняются из корня проекта:

```bash
export APP_USERNAME=student
export APP_PASSWORD='Lab-demo-password-2026'
export JWT_SECRET="$(openssl rand -base64 32)"
export DB_PASSWORD="$(openssl rand -base64 24)"
./gradlew --quiet bootRun
```

При старте создаётся пользователь с заданными логином и паролем. Пароль в примере используется только для локальной демонстрации.

### Ручки

Адрес: `http://localhost:8080` (локально на этом компьютере). Тела запросов и ответов передаются в JSON.

| Метод | Ручка | Тело запроса | Успешный ответ | Доступ |
|---|---|---|---|---|
| POST | `/auth/login` | `username`, `password` | `200`: `accessToken`, `tokenType`, `expiresIn` | Без токена |
| POST | `/api/data` | `title`, `content` | `201`: `id`, `title`, `content` | Bearer JWT |
| GET | `/api/data` | Нет | `200`: массив своих заметок | Bearer JWT |

`title`: 1–120 символов, `content`: 1–2000, оба поля не могут состоять из пробелов. Ошибки валидации: `400`; неверные учётные данные или токен: `401`.

### curl

В другом терминале:

```bash
# Вход
curl -i http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student","password":"Lab-demo-password-2026"}'

# Вставить accessToken из ответа
TOKEN='<accessToken>'

# Создание заметки
curl -i http://localhost:8080/api/data \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Покупки","content":"Купить молоко"}'

# Получение заметок
curl -i http://localhost:8080/api/data \
  -H "Authorization: Bearer $TOKEN"
```

## Меры защиты

### SQL-инъекции

В `NoteRepository` значения передаются через `.param()`. `JdbcClient` выполняет подготовленные запросы: SQL и пользовательские данные передаются отдельно.

```kotlin
jdbcClient.sql("SELECT id, title, content FROM notes WHERE username = :username ORDER BY id")
    .param("username", username)
```

Так же устроен `INSERT`: `id`, `username`, `title` и `content` связаны с параметрами запроса. Пользовательский ввод нигде не склеивается с SQL. Поэтому кавычки и SQL-команды в заметке сохраняются как текст и не выполняются.

За запросы к таблицам пользователей отвечает `JdbcUserDetailsManager`, который также использует подготовленные запросы. Логин `' OR '1'='1' --` будет искаться целиком как имя пользователя и не изменит условие выборки.

### XSS

В `NoteController.toResponse` оба пользовательских поля экранируются перед отправкой:

```kotlin
title = HtmlUtils.htmlEscape(note.title, "UTF-8")
content = HtmlUtils.htmlEscape(note.content, "UTF-8")
```

`<script>alert(1)</script>` превращается в `&lt;script&gt;alert(1)&lt;/script&gt;`. При выводе в текстовый HTML-контекст браузер покажет текст, не создавая исполняемый тег.

Обработка применяется и к ответу после создания заметки, и к списку заметок. В БД остаётся исходное значение; при каждом чтении экранирование выполняется один раз.

Ответы имеют формат JSON. Заголовок `X-Content-Type-Options: nosniff`, добавляемый Spring Security, запрещает браузеру угадывать другой тип содержимого. HTML-экранирование не защищает вставку строки внутрь JavaScript, CSS или URL.

### Пароли и аутентификация

- `CreateDefaultUser` сохраняет в БД хэш через `BCryptPasswordEncoder(12)`. Случайная соль защищает от готовых таблиц хэшей, вычислительная стоимость замедляет перебор при утечке БД.
- При входе `DaoAuthenticationProvider` получает пользователя из БД и проверяет пароль через `PasswordEncoder.matches`. JWT выдаётся только после успешного `authenticate`.
- Неверный пароль и неизвестный логин возвращают одинаковый ответ `401 Invalid credentials`, чтобы не раскрывать наличие учётной записи.
- `AuthController` отклоняет пароли длиннее 72 байт UTF-8, чтобы исключить проверку по обрезанному bcrypt паролю.

### JWT и доступ к данным

- `JwtConfiguration` задаёт HS256 для подписи и проверки. `NimbusJwtDecoder` проверяет подпись, издателя `secure-notes` и срок действия. Подмена логина внутри JWT нарушает подпись и приводит к `401`.
- Срок токена составляет 15 минут с допуском на расхождение часов при проверке. Это ограничивает время использования украденного токена; индивидуального отзыва токенов нет.
- Ключ берётся из `JWT_SECRET`, декодируется из Base64 и проверяется на длину от 32 байт. В инструкции запуска он генерируется случайно через OpenSSL.
- `SecurityConfiguration` требует проверенный Bearer-токен для `GET` и `POST /api/data`. Публичен только `POST /auth/login`, остальные маршруты закрыты через `denyAll()`.
- Владелец заметки берётся из `jwt.subject`. При чтении запрос фильтрует записи по этому же логину через `WHERE username = :username`. Передать чужого владельца в теле запроса нельзя.
- Ответ с JWT содержит `Cache-Control: no-store`, чтобы запретить его кэширование.

### Дополнительные меры

`@Valid`, `@NotBlank` и `@Size` отклоняют пустые поля и превышение длины с кодом `400`. Пароли и ключ подписи задаются через переменные окружения. В ответах об ошибках отключены stack trace и внутренние сообщения сервера.

API не использует сессии и cookie-аутентификацию. CSRF отключён: токен передаётся явно в заголовке `Authorization`. Сервис доступен локально; для внешнего доступа нужен HTTPS, чтобы пароль и токен не передавались открытым текстом.

## Отчёты SAST/SCA

[GitHub Actions](https://github.com/zazhigalka56123/cysec_1/actions) запускает проверки при push и pull request. Конфигурация находится в `.github/workflows/ci.yml`.

| Проверка | Инструмент | Условие ошибки | Артефакт |
|---|---|---|---|
| SAST | CodeQL, набор `security-extended` для Kotlin | Есть находки или анализ завершился ошибкой | `sast-reports`, SARIF |
| SCA | OWASP Dependency-Check | Найдена уязвимость с CVSS ≥ 7 или сканирование завершилось ошибкой | `sca-reports`, HTML и JSON |

Отдельная задача собирает проект и запускает интеграционные тесты. Для загрузки базы уязвимостей в workflow предусмотрен repository secret `NVD_API_KEY`.
