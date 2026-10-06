---
name: release
description: Выпуск новой версии Taxi Radar для Android — поднять версию, собрать подписанный APK, проверить подпись, положить в TaxiRadar-keys, закоммитить. Только по команде пользователя.
disable-model-invocation: true
argument-hint: "<versionName, например 1.17> [описание изменений]"
---

# /release

Аргументы: `$ARGUMENTS` — первая часть новая versionName (например `1.17`), остальное — что вошло в релиз.

1. Убедись, что ветка `main` и всё нужное закоммичено (`git status`). Если версия не указана — спроси.
2. Запусти скрипт (он сам поднимет versionCode на 1, поставит versionName, соберёт, проверит подпись и скопирует APK):
   ```bash
   bash .claude/skills/release/release.sh <versionName>
   ```
   Если скрипт упал — показать ошибку пользователю, ничего не коммитить.
3. Закоммить: `Release <versionName>: <кратко что нового>` (build.gradle.kts и изменённые файлы). Push в `origin main` — да, это приватный репозиторий приложения.
4. Сообщи пользователю: путь к APK в `TaxiRadar-keys`, размер, SHA-256, versionCode.
5. **Не** менять LATEST_VERSION/min_version_code на сервере и не готовить уведомление, пока пользователь не скажет «объявляем» / «кидай уведомление».
   Когда скажет: в `taxi-radar-license/server.js` обновить `latest_version_code`/`latest_version_name` по умолчанию и `update_notes`, `node --check server.js`, commit, push.
6. На телефон через adb ставить только с разрешения.
