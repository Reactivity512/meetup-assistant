# Meetup Assistant

Локальное веб-приложение для работы с записями рабочих встреч:

1. загружаете аудиофайл митинга;
2. Vosk распознаёт речь **локально** и сохраняет транскрипт в базу;
3. ассистент на Spring AI + Ollama отвечает на вопросы **по тексту выбранной встречи** и сохраняет историю ответов;
4. аудио хранится на диске и удаляется по политике хранения, транскрипты и ответы остаются в БД.

Интерфейс — серверный рендеринг Thymeleaf + Bootstrap 5, на русском языке. Аутентификации нет (MVP для локального использования).

---

## Содержание

- [Стек](#стек)
- [Как это работает](#как-это-работает)
- [Требования](#требования)
- [Быстрый старт](#быстрый-старт)
- [Модель Vosk](#модель-vosk)
- [Ollama](#ollama)
- [Конфигурация](#конфигурация)
- [База данных](#база-данных)
- [Веб-интерфейс и API](#веб-интерфейс-и-api)
- [Жизненный цикл обработки](#жизненный-цикл-обработки)
- [Хранение и очистка аудио](#хранение-и-очистка-аудио)
- [Длинные транскрипты](#длинные-транскрипты)
- [Тесты](#тесты)
- [Сборка и запуск jar](#сборка-и-запуск-jar)
- [Структура проекта](#структура-проекта)
- [Диагностика](#диагностика)
- [Ограничения MVP](#ограничения-mvp)

---

## Стек

| Слой | Технология |
|------|------------|
| Backend | Java 17, Spring Boot 3.5, Spring MVC, Spring Data JPA, Bean Validation |
| Frontend | Thymeleaf, Bootstrap 5.3, Bootstrap Icons (CDN), немного ванильного JS |
| Распознавание речи | Vosk Java API (`com.alphacephei:vosk:0.3.45`), нативные библиотеки внутри jar |
| Подготовка аудио | ffmpeg (16 кГц, mono, PCM WAV) |
| LLM | Spring AI 1.1.8 + Ollama (по умолчанию `qwen3.5:4b`) |
| БД | H2 в файле (dev), PostgreSQL (профиль `postgres`) |
| Тесты | JUnit 5, Mockito, Spring Boot Test, MockMvc, Awaitility |
| Сборка | Maven, Spring Boot Maven Plugin |

---

## Как это работает

```
Браузер
  │  POST /meetups (multipart)
  ▼
MeetupController ──► StorageService ──► data/audio/<uuid>.<ext>
  │                      (оригинал)
  ├──► Meetup (status=QUEUED) в БД
  └──► TranscriptionCoordinator ──► пул потоков (1–2 воркера, очередь 10)
                                        │
                                        ▼
                              AudioNormalizer (ffmpeg)
                                        │  16 кГц mono WAV во временный файл
                                        ▼
                              VoskTranscriptionService
                                        │  модель загружается лениво и живёт в памяти
                                        ▼
                              Meetup (status=READY, transcript)
                                        │
Браузер ──► POST /meetups/{id}/assistant
                                        ▼
                              AssistantService
                                ├─ короткий транскрипт → один запрос в LLM
                                └─ длинный → нарезка + извлечение фактов + финальный ответ
                                        ▼
                              Spring AI ChatClient ──► Ollama (localhost:11434)
                                        ▼
                              LlmAnswer сохраняется в БД
```

Особенности:

- распознавание выполняется **в процессе JVM**, без запуска отдельного сервера Vosk;
- статус обработки обновляется на странице через `GET /api/meetups/{id}/status`;
- при перезапуске приложения незавершённые задачи (`QUEUED`/`PROCESSING`) ставятся в очередь заново;
- ошибки распознавания сохраняются в БД и показываются пользователю с понятным текстом.

---

## Требования

- **JDK 17+** (проверено на JDK 17 и JDK 27);
- **Maven 3.9+**;
- **ffmpeg** в `PATH` либо путь в `FFMPEG_PATH` / `app.speech.ffmpeg-command`;
- **Ollama** с загруженной моделью (по умолчанию `qwen3.5:4b`);
- распакованная **модель Vosk** (см. ниже);
- ~2 ГБ свободной RAM для небольшой модели Vosk + LLM (LLM работает в отдельном процессе Ollama).

Проверка окружения:

```powershell
java -version
mvn -version
ffmpeg -version
ollama list
```

---

## Быстрый старт

```powershell
cd C:\Users\Greybyte\java\project2

# 1. Модель распознавания (≈46 МБ)
powershell -ExecutionPolicy Bypass -File .\scripts\download-vosk-model.ps1

# 2. Модель для ответов на вопросы
ollama pull qwen3.5:4b

# 3. Запуск
mvn spring-boot:run
```

Откройте <http://localhost:8080> — произойдёт редирект на `/meetups`.

Проверка:

```powershell
curl.exe http://localhost:8080/actuator/health
# {"status":"UP"}
```

Остановка — `Ctrl+C` в консоли Maven.

---

## Модель Vosk

Приложение ожидает распакованную модель в:

```
./models/vosk-model-small-ru-0.22
```

Скрипт `scripts/download-vosk-model.ps1` скачивает и распаковывает её автоматически. Для русской модели он сначала использует зеркало в GitHub-репозитории Alphacep (официальный сайт часто отдаёт файл крайне медленно), затем официальный URL как запасной вариант:

```powershell
# русская модель (по умолчанию)
.\scripts\download-vosk-model.ps1

# английская модель
.\scripts\download-vosk-model.ps1 -Language en
```

Ручная установка:

1. Скачайте zip с <https://alphacephei.com/vosk/models> (например, `vosk-model-small-ru-0.22.zip`).
2. Распакуйте в `models/` так, чтобы внутри `models/vosk-model-small-ru-0.22/` были каталоги `am`, `conf`, `graph`, `ivector`.
3. При необходимости укажите другой путь:

```powershell
$env:VOSK_MODEL_PATH = "D:\models\vosk-model-ru-0.42"
mvn spring-boot:run
```

Другие модели: `vosk-model-small-en-us-0.15` для английского, большие модели (`vosk-model-ru-0.42`) точнее, но требуют больше RAM и времени. Язык распознавания определяется только выбранной моделью — отдельной настройки языка нет.

> Если модель не найдена, приложение всё равно стартует, а загрузка аудио завершится статусом «Ошибка» с подсказкой, что скачать и куда распаковать.

---

## Ollama

```powershell
ollama serve          # если сервис ещё не запущен
ollama pull qwen3.5:4b
ollama list
```

Модель задаётся в `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3.5:4b
          temperature: 0.2
```

Можно быстро переопределить без правки файла:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.ai.ollama.chat.options.model=gemma4:12b"
```

Подойдёт любая instruct-модель из `ollama list`. Для русских транскриптов лучше выбирать модели с хорошим русским (например, `qwen3.5:4b`, `gemma4:12b`).

Проверить связку Spring AI ↔ Ollama можно отдельным тестом (по умолчанию он пропускается):

```powershell
$env:RUN_OLLAMA_SMOKE = "true"
mvn -Dtest=OllamaSmokeTest test
Remove-Item Env:RUN_OLLAMA_SMOKE
```

---

## Конфигурация

Все настройки — в `src/main/resources/application.yml`. Значения можно переопределять переменными окружения или аргументами Spring Boot.

### Основные параметры

| Параметр | По умолчанию | Назначение |
|----------|--------------|------------|
| `server.port` | `8080` | HTTP-порт |
| `spring.servlet.multipart.max-file-size` | `500MB` | Максимальный размер одного файла |
| `spring.servlet.multipart.max-request-size` | `520MB` | Максимальный размер запроса |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Адрес Ollama |
| `spring.ai.ollama.chat.options.model` | `qwen3.5:4b` | Модель для ответов |
| `spring.ai.ollama.chat.options.temperature` | `0.2` | Температура генерации |
| `app.speech.model-path` | `./models/vosk-model-small-ru-0.22` | Каталог модели Vosk (`VOSK_MODEL_PATH`) |
| `app.speech.sample-rate` | `16000` | Частота, ожидаемая моделью |
| `app.speech.ffmpeg-command` | `ffmpeg` | Исполняемый файл ffmpeg (`FFMPEG_PATH`) |
| `app.speech.ffmpeg-enabled` | `true` | `false` — принимать только готовые WAV 16 кГц mono |
| `app.speech.timeout` | `30m` | Лимит на конвертацию и распознавание |
| `app.storage.dir` | `./data` | Корневой каталог данных (`DATA_DIR`) |
| `app.storage.retention` | `30d` | Возраст аудио, после которого оно удаляется |
| `app.storage.cleanup-enabled` | `true` | Выключить фоновую очистку |
| `app.storage.cleanup-cron` | `0 15 * * * *` | Расписание очистки (каждый час в :15) |
| `app.assistant.chunk-size` | `6000` | Размер фрагмента транскрипта, символов |
| `app.assistant.max-chunks` | `20` | Предохранитель для очень длинных записей |
| `app.assistant.max-question-length` | `1000` | Максимальная длина вопроса |
| `app.assistant.history-limit` | `50` | Сколько ответов показывать на странице |

Примеры:

```powershell
# другой порт и каталог данных
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090 --app.storage.dir=D:\meetup-data"

# путь к ffmpeg, если его нет в PATH
$env:FFMPEG_PATH = "C:\ffmpeg\bin\ffmpeg.exe"

# хранить аудио 7 дней
mvn spring-boot:run -Dspring-boot.run.arguments="--app.storage.retention=7d"
```

Поддерживаемые форматы загрузки: `wav`, `mp3`, `m4a`, `aac`, `ogg`, `oga`, `opus`, `flac`, `webm`, `mp4`, `wma`. Видеодорожка отбрасывается, извлекается только звук.

---

## База данных

### H2 (по умолчанию)

```
jdbc:h2:file:./data/meetups;DB_CLOSE_ON_EXIT=FALSE
```

Файлы БД лежат в `data/meetups.mv.db`. Схема обновляется Hibernate (`ddl-auto: update`). H2-консоль отключена.

Полный сброс dev-данных:

```powershell
# остановить приложение, затем:
Remove-Item data\meetups.mv.db
Remove-Item data\audio\* -Force
```

Одновременно с одной БД H2 может работать только один экземпляр приложения — при запуске второй копии появится ошибка блокировки файла.

### PostgreSQL

Профиль `postgres` использует переменные окружения:

```powershell
# пример создания БД
psql -U postgres -c "CREATE USER meetups WITH PASSWORD 'meetups';"
psql -U postgres -c "CREATE DATABASE meetups OWNER meetups;"

$env:DB_URL = "jdbc:postgresql://localhost:5432/meetups"
$env:DB_USERNAME = "meetups"
$env:DB_PASSWORD = "meetups"

mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Для jar:

```powershell
java -jar target\meetup-assistant-1.0.0.jar --spring.profiles.active=postgres
```

---

## Веб-интерфейс и API

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/` | Редирект на `/meetups` |
| `GET` | `/meetups` | Список митингов, статусы, кнопки удаления |
| `GET` | `/meetups/new` | Форма загрузки |
| `POST` | `/meetups` | Загрузка аудио (`multipart/form-data`: `title`, `file`) |
| `GET` | `/meetups/{id}` | Карточка митинга: плеер, транскрипт, ассистент, история |
| `GET` | `/meetups/{id}/audio` | Аудио с поддержкой HTTP Range (перемотка в плеере) |
| `POST` | `/meetups/{id}/assistant` | Вопрос по транскрипту (`question`) |
| `POST` | `/meetups/{id}/retry` | Повторное распознавание |
| `POST` | `/meetups/{id}/delete` | Удаление митинга, аудио и истории |
| `GET` | `/api/meetups/{id}/status` | JSON-статус для автообновления страницы |
| `GET` | `/actuator/health`, `/actuator/info` | Actuator |

Пример статуса:

```json
{
  "id": 1,
  "status": "FAILED",
  "statusLabel": "Ошибка",
  "badgeClass": "danger",
  "busy": false,
  "transcriptAvailable": false,
  "transcriptChars": 0,
  "processingError": "Модель Vosk не найдена: ..."
}
```

Ошибки API возвращаются в формате `application/problem+json`, ошибки страниц — HTML-шаблон `error.html`. Все ответы и сообщения — на русском.

---

## Жизненный цикл обработки

```
QUEUED ──► PROCESSING ──► READY
   │            │
   └────────────┴──────► FAILED ──► (retry) ──► QUEUED
```

- `QUEUED` — файл сохранён, задача в очереди;
- `PROCESSING` — идёт конвертация/распознавание (страница опрашивает статус и перезагружается сама);
- `READY` — транскрипт сохранён, ассистент доступен;
- `FAILED` — текст ошибки сохранён в `processingError`, доступна кнопка «Распознать заново» (если аудио ещё не удалено).

Очередь: 1 основной поток, максимум 2, очередь на 10 задач. При переполнении загрузка помечается ошибкой с просьбой повторить позже — это защита от неконтролируемого роста памяти.

---

## Хранение и очистка аудио

- Оригиналы: `data/audio/<uuid>.<ext>` (имя на диске не совпадает с исходным — защита от path traversal).
- Временные WAV и логи ffmpeg: `data/tmp/`, удаляются после обработки; старая «временка» (>24 ч) вычищается планировщиком.
- Каждый час задача `AudioCleanupJob` удаляет аудио старше `app.storage.retention` (по умолчанию 30 дней). Поле `audioPath` обнуляется, **транскрипт и ответы остаются**.
- В UI для такого митинга показывается плашка «Аудиофайл удалён по политике хранения», плеер и кнопка повторного распознавания скрываются.
- Удаление митинга вручную удаляет и запись в БД, и файл, и историю вопросов.

Чтобы хранить аудио бессрочно:

```yaml
app:
  storage:
    cleanup-enabled: false
```

---

## Длинные транскрипты

Контекстное окно локальной LLM ограничено, поэтому `AssistantService` работает так:

1. транскрипт делится на фрагменты по `app.assistant.chunk-size` (по умолчанию 6000 символов, не более `max-chunks`);
2. если фрагмент один — сразу формируется ответ;
3. если фрагментов несколько — для каждого выполняется запрос «найди факты по вопросу», пустые ответы (`НЕТ_ДАННЫХ`) отбрасываются;
4. найденные выдержки собираются в контекст, и финальный запрос формирует структурированный ответ по-русски.

Ответ сохраняется в `llm_answer` вместе с вопросом, именем модели, временем создания и задержкой. История показывается на странице митинга (последние `history-limit` записей).

Промпты явно запрещают выдумывать имена, сроки и цифры и требуют честно сообщать, если тема не обсуждалась.

---

## Тесты

```powershell
mvn test
```

Что покрыто:

- `TranscriptChunkerTest` — нарезка длинного текста;
- `WavDataExtractorTest` — разбор WAV и поиск data-чанка;
- `MeetupServiceTest` — создание, переходы статусов, удаление, очистка файла при ошибке БД;
- `AssistantServiceTest` — валидация вопроса, одиночный и чанкованный сценарии, ошибки LLM;
- `TranscriptionCoordinatorTest` — успех/ошибка асинхронной обработки;
- `MeetupControllerTest`, `MeetupApiControllerTest`, `HomeControllerTest` — MockMvc: страницы, валидация, 404, JSON-статус;
- `MeetupFlowIntegrationTest` — полный поток: загрузка → (замоканный Vosk) транскрипт в БД → (замоканный LLM) ответ → страница/аудио/статус;
- `OllamaSmokeTest` — опциональная проверка реальной Ollama, по умолчанию пропускается.

Ожидаемый результат: **38 тестов, 0 ошибок, 1 пропущен** (Ollama smoke). Интеграционные тесты используют H2 в памяти и временные каталоги в `target/`, поэтому не трогают рабочие данные.

---

## Сборка и запуск jar

```powershell
mvn clean package
java -jar target\meetup-assistant-1.0.0.jar
```

С дополнительными параметрами:

```powershell
java -jar target\meetup-assistant-1.0.0.jar `
  --server.port=9090 `
  --app.storage.retention=14d `
  --spring.ai.ollama.chat.options.model=gemma4:12b
```

Пути `./models` и `./data` считаются от рабочего каталога запуска. Если запускаете jar из другого каталога, задайте абсолютные пути через `VOSK_MODEL_PATH` и `--app.storage.dir`.

---

## Структура проекта

```
src/main/java/com/example/meetup/
├── MeetupAssistantApplication.java
├── assistant/          # AssistantService, LlmClient, Spring AI-адаптер, нарезка текста
├── config/             # @ConfigurationProperties: storage, speech, assistant; пул потоков
├── domain/             # Meetup, LlmAnswer, MeetupStatus, MeetupSummary
├── repository/         # Spring Data JPA
├── service/            # MeetupService, TranscriptionCoordinator, исключения
├── speech/             # Vosk, ffmpeg-нормализация, разбор WAV
├── storage/            # локальное хранение, планировщик очистки
└── web/                # контроллеры, формы, обработчики ошибок, восстановление после рестарта

src/main/resources/
├── application.yml
├── application-postgres.yml
├── static/             # css/js
└── templates/          # layout, список, загрузка, карточка митинга, error

src/test/java/...       # unit + MockMvc + интеграционный поток
scripts/download-vosk-model.ps1
docs/project.md         # исходные требования и архитектурные заметки
```

---

## Диагностика

### «Модель Vosk не найдена»

Скачайте и распакуйте модель, проверьте путь:

```powershell
Test-Path .\models\vosk-model-small-ru-0.22\conf
```

Внутри каталога модели должны быть `am`, `conf`, `graph` (и обычно `ivector`). После установки нажмите «Распознать заново» — повторная загрузка приложения не нужна, модель подхватывается лениво.

### «Не удалось запустить ffmpeg»

```powershell
ffmpeg -version
# либо
$env:FFMPEG_PATH = "C:\ffmpeg\bin\ffmpeg.exe"
```

Убедитесь, что путь указывает на исполняемый файл, а не на каталог. Для теста конвертации:

```powershell
ffmpeg -i input.m4a -vn -ac 1 -ar 16000 -c:a pcm_s16le out.wav
```

### Ollama: connection refused / модель не найдена

```powershell
ollama list
ollama pull qwen3.5:4b
curl.exe http://localhost:11434/api/tags
```

Если Ollama на другом хосте/порту — измените `spring.ai.ollama.base-url`. Первый ответ после запуска модели может занять десятки секунд (загрузка весов в память).

### «Транскрипт ещё не готов»

Вопросы принимаются только при статусе `READY`. Дождитесь окончания распознавания или повторите его после устранения ошибки.

### Vosk не распознал речь

- язык записи не совпадает с языком модели;
- аудио слишком тихое или содержит только музыку/шум;
- запись пустая либо повреждена (проверьте воспроизведение в плеере на странице).

### В транскрипте «кракозябры» вместо русских букв

Приложение принудительно выставляет `jna.encoding=UTF-8` при старте, потому что Vosk возвращает JSON в UTF-8. Если вы запускаете Vosk-код в обход `MeetupAssistantApplication` (свой `main`, другой лаунчер), задайте вручную:

```powershell
java -Djna.encoding=UTF-8 -jar target\meetup-assistant-1.0.0.jar
```

Не переопределяйте `jna.encoding` на `windows-1251` / `cp1251`.

### Ошибка блокировки H2

Закройте второй экземпляр приложения. Файловая H2 не поддерживает одновременный доступ нескольких процессов.

### Нативная библиотека Vosk не загружается

Проверьте разрядность JVM (нужна x86-64) и права на временный каталог. В jar `vosk` уже включены библиотеки для Windows/Linux/macOS; отдельная установка Vosk не требуется.

### Файл слишком большой

Увеличьте лимиты:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 1GB
      max-request-size: 1100MB
```

Но помните: длинные записи дольше распознаются и сильнее нагружают RAM.

---

## Ограничения MVP

- нет аутентификации и разграничения доступа — не публикуйте приложение в открытую сеть;
- очередь обработки живёт в памяти одного узла; горизонтальное масштабирование потребует внешней очереди;
- H2 подходит для локальной работы; для нескольких пользователей используйте PostgreSQL;
- миграции схемы выполняет Hibernate (`ddl-auto`), Flyway/Liquibase не подключены;
- качество транскрипта зависит от модели Vosk и качества записи, качество ответов — от выбранной LLM;
- аудио и тексты не покидают машину: Vosk работает локально, Ollama — тоже (при локальном `base-url`).
