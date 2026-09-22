# ZapretDroid

Android-порт [Flowseal/zapret-discord-youtube](https://github.com/Flowseal/zapret-discord-youtube) и [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy).  
Обход DPI-блокировок YouTube, Discord и Telegram на Android. **Root не требуется.**

> ### ФЕЙКИ
>
> Это единственный официальный репозиторий проекта.  
> Если вы встретили что-то похожее в других местах — **ФЕЙК**.

> ### РАЗРЕШЕНИЕ НА VPN
>
> При первом запуске Android запросит разрешение на создание VPN-соединения. Это стандартный системный запрос — приложение использует Android VpnService API (аналог WinDivert/iptables на Windows/Linux). Само соединение локальное, трафик не уходит на сторонние серверы.

## ⚙️ Использование

1. Скачайте APK со [страницы последнего релиза](https://github.com/ezxidze/ZapretDroid/releases/latest)
2. Разрешите установку из неизвестных источников и установите APK
3. Откройте приложение → дайте разрешение на VPN
4. Выберите стратегию из списка (начните с **General**)
5. Нажмите **Запустить**
6. Проверьте работу YouTube / Discord. Если не работает — пробуйте другие стратегии (**ALT 1, 2, 3...**)

## ℹ️ Стратегии

Работоспособность стратегии зависит от провайдера и региона. **Пробуйте разные стратегии, пока не найдёте рабочую.**

| Стратегия | Техника | Для чего |
|---|---|---|
| **General** | Fake QUIC, repeats=6 | YouTube, Discord, Google |
| **ALT 1** | Multisplit seqovl=681 | YouTube, Google |
| **ALT 2** | Fake + Multisplit seqovl=652 | YouTube, Discord |
| **ALT 3** | Hostfakesplit + BadSeq | YouTube, Discord |
| **ALT 4** | Syndata + Multidisorder | Все сервисы |
| **ALT 5** | BadSeq increment=1000 | YouTube, Discord |
| **ALT 6** | Fake + Fakedsplit seqovl=664 | YouTube, Discord |
| **ALT 7** | Multisplit seqovl=568 + TS fooling | YouTube |
| **ALT 8** | Fake seqovl=679, L7=Discord | Discord media |
| **ALT 9** | Any-protocol + cutoff=n2 | Игры, Discord |
| **ALT 10** | Multisplit midsld | YouTube, Google |
| **ALT 11** | Fake TLS MAX.RU | YouTube, Discord |
| **ALT 12** | Fake + Multisplit sniext+1 | YouTube, Discord |
| **Fake TLS AUTO** | Рандомный TLS ClientHello | Против pattern-DPI |
| **Fake TLS AUTO DupSID** | Дублирование Session ID | Против pattern-DPI |
| **Simple Fake** | Только fake-пакет | Fallback |
| **Simple Fake UDP** | Fake для UDP/QUIC | Discord, игры |
| **Google IP-ID Zero** | Multisplit + IP ID=0 | YouTube/Google |

## ☑️ Распространённые вопросы и проблемы

### Не работает YouTube

- Убедитесь, что включён Secure DNS в браузере или системных настройках Android
- Попробуйте все стратегии по порядку — ALT 1, 2, 3...
- Попробуйте стратегии с пометкой "Fake TLS AUTO"

### Не работает Discord

- Сначала найдите стратегию, на которой работает YouTube, и запустите её
- Для голосовых звонков и стримов попробуйте **ALT 8** — он оптимизирован под Discord media-серверы
- Проверьте Discord в браузере на телефоне — если там работает, значит стратегия подходящая

### Не работает Telegram

- Включите переключатель **"Telegram обход"** в приложении
- Нажмите кнопку **"Подключить"** — Telegram откроется автоматически с уже заполненными настройками прокси
- Если кнопка не появляется — сначала нажмите **Запустить**

### Обход перестал работать

> Стратегии со временем могут переставать работать из-за обновления DPI на стороне провайдера. Если ни одна стратегия не помогает — создайте [issue](https://github.com/ezxidze/ZapretDroid/issues).

- Попробуйте другие стратегии
- Перезапустите приложение
- Проверьте, включён ли Secure DNS

### Не работают игры с включённым обходом

- Попробуйте стратегию **ALT 9** (any-protocol режим, оптимизирован для UDP)
- Если игра не работает — добавьте её домены в `list-general.txt` в папке assets и пересоберите APK

### Не нашли своей проблемы

- Создайте её [тут](https://github.com/ezxidze/ZapretDroid/issues)

## 🗒️ Добавление своих доменов

Список доменов для обхода находится в `app/src/main/assets/lists/`:

- **`list-general.txt`** — Discord, CDN, Telegram и др.
- **`list-google.txt`** — YouTube, Google
- **`list-exclude.txt`** — домены, которые не нужно фильтровать

Поддомены учитываются автоматически.

## ⭐ Поддержка проекта

Поставьте ⭐ этому репозиторию, если приложение вам помогло.

Также поддержите оригинального разработчика zapret [тут](https://github.com/bol-van/zapret?tab=readme-ov-file#поддержать-разработчика).

## ⚖️ Лицензирование

Проект распространяется на условиях лицензии [MIT](LICENSE)

## 🩷 Благодарность

💖 Отдельная благодарность [Flowseal](https://github.com/Flowseal) за стратегии и tg-ws-proxy  

