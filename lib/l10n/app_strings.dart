class AppStrings {
  static String lang = 'RU'; // 'RU' or 'RO'

  static bool get isRu => lang == 'RU';

  static String get appName => 'Taxi Radar';
  static String get appSubtitle => isRu ? 'Радар тарифов и дорожной обстановки' : 'Radar de tarife și situație rutieră';

  // Вкладки
  static String get navRadar => isRu ? 'Радар' : 'Radar';
  static String get navMap => isRu ? 'Карта' : 'Hartă';
  static String get navUseful => isRu ? 'Полезное' : 'Utile';
  static String get navProfile => isRu ? 'Профиль' : 'Profil';

  // Лицензия / Подписка
  static String get licenseActive => isRu ? 'Подписка активна' : 'Abonament activ';
  static String get licenseTrial => isRu ? 'Пробный доступ' : 'Acces de probă';
  static String get licenseExpired => isRu ? 'Срок действия истёк' : 'Abonament expirat';
  static String get licenseDaysLeft => isRu ? 'осталось дней: ' : 'zile rămase: ';
  static String get licenseKeyHint => isRu ? 'Введите ключ активации' : 'Introduceți cheia de activare';
  static String get licenseActivate => isRu ? 'Активировать' : 'Activează';
  static String get licenseChecking => isRu ? 'Проверка…' : 'Verificare…';
  static String get licenseRenew => isRu ? 'Продлить' : 'Prelungește';

  // Радар
  static String get radarStart => isRu ? 'Запустить мониторинг' : 'Pornește monitorizarea';
  static String get radarStop => isRu ? 'Остановить мониторинг' : 'Oprește monitorizarea';
  static String get radarRunning => isRu ? 'Мониторинг активен' : 'Monitorizare activă';
  static String get tariffsTitle => isRu ? 'Спрос и тарифы' : 'Cerere și tarife';
  static String get tariffsCaption => isRu ? 'Для каких тарифов отслеживать надбавку' : 'Pentru ce tarife se urmărește adaosul';

  // Карта
  static String get mapIAmHere => isRu ? 'Я здесь' : 'Sunt aici';
  static String get mapAddReport => isRu ? '+ Метка' : '+ Marcaj';
  static String get mapRoadAlerts => isRu ? '🔔 Предупреждать в дороге' : '🔔 Alerte în drum';
  static String get mapChecking => isRu ? 'Проверяю надбавку…' : 'Verific adaosul…';
  static String get mapLayers => isRu ? 'Слои' : 'Straturi';
  static String get mapLayerRoad => isRu ? 'Дорога' : 'Drum';
  static String get mapLayerAddr => isRu ? 'Адреса' : 'Adrese';
  static String get mapStillHere => isRu ? 'Ещё здесь' : 'Mai este aici';
  static String get mapNotHere => isRu ? 'Уже нет' : 'Nu mai este';

  // Полезное
  static String get tileChat => isRu ? 'Чат водителей' : 'Chat șoferi';
  static String get tileChatSub => isRu ? 'Общение со всеми водителями радара' : 'Comunicare cu toți șoferii radarului';
  static String get tileClients => isRu ? 'Клиенты' : 'Clienți';
  static String get tileClientsSub => isRu ? 'Проверка номера и отметки' : 'Verificare număr și recenzii';
  static String get tileAirport => isRu ? 'Аэропорт' : 'Aeroport';
  static String get tileAirportSub => isRu ? 'Прилёты и очередь такси' : 'Sosiri și rând taxi';
  static String get tileRides => isRu ? 'Попутчики' : 'Curse și pasageri';
  static String get tileRidesSub => isRu ? 'Лента заявок из групп и поиск' : 'Cereri pasageri și mașini din grupuri';

  // Аэропорт
  static String get airportQueueCaption => isRu ? 'водителей Taxi Radar в очереди' : 'șoferi Taxi Radar în rând';
  static String get airportLandedTitle => isRu ? 'СЕЛИ' : 'AU ATERIZAT';
  static String get airportNextTitle => isRu ? 'БЛИЖАЙШИЕ' : 'URMĂTOARELE';
  static String get airportLaterTitle => isRu ? 'ПОЗЖЕ СЕГОДНЯ' : 'MAI TÂRZIU AZI';
  static String get airportOfficial => isRu ? 'Официальное табло airport.md' : 'Panou oficial airport.md';
  static String get airportNoFlights => isRu ? 'Рейсов пока нет' : 'Nu sunt zboruri deocamdată';

  // Чат
  static String get chatNickTitle => isRu ? 'Ваш ник в чате' : 'Pseudonim în chat';
  static String get chatMessageHint => isRu ? 'Сообщение…' : 'Mesaj…';
  static String get chatSend => isRu ? 'Отправить' : 'Trimite';

  // Клиенты
  static String get clientsCheckTitle => isRu ? 'Проверить номер клиента' : 'Verifică numărul clientului';
  static String get clientsPhoneHint => isRu ? 'Номер телефона (например 078123456)' : 'Număr de telefon (ex. 078123456)';
  static String get clientsCheck => isRu ? 'Проверить' : 'Verifică';
  static String get clientsTagTitle => isRu ? 'Отметить клиента' : 'Adaugă etichetă';
  static String get clientsReviewHint => isRu ? 'Отзыв о поездке…' : 'Recenzie despre cursă…';

  // Профиль / Настройки
  static String get netTitle => isRu ? 'Расчёт «Чистыми»' : 'Calcul «Net»';
  static String get netConsumption => isRu ? 'Расход (л / 100 км)' : 'Consum (l / 100 km)';
  static String get netFuelPrice => isRu ? 'Цена топлива (L / литр)' : 'Preț combustibil (L / litru)';
  static String get netCommission => isRu ? 'Комиссия агрегатора (%)' : 'Comision agregator (%)';
  static String get refTitle => isRu ? 'Пригласи друга' : 'Invită un prieten';
  static String get refShare => isRu ? 'Поделиться кодом' : 'Distribuie codul';
  static String get refEnterCode => isRu ? 'Ввести код друга' : 'Introdu codul prietenului';
  static String get contactTitle => isRu ? 'Связаться с нами' : 'Contactează-ne';
  static String get groupTitle => isRu ? 'Группа в Telegram' : 'Grup Telegram';
}
