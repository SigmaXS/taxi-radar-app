import 'package:flutter/cupertino.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_strings.dart';
import '../../services/live_activity_service.dart';
import '../../services/order_parser_service.dart';
import '../../ui/ds.dart';
import 'guide_widgets.dart';
import 'order_price_view.dart';

/// Инструкция «Показ цены заказа»: быстрая команда + AssistiveTouch, по шагам,
/// со схемами и кнопками, которые открывают нужное место.
class PriceGuideScreen extends StatefulWidget {
  const PriceGuideScreen({super.key});

  @override
  State<PriceGuideScreen> createState() => _PriceGuideScreenState();
}

class _PriceGuideScreenState extends State<PriceGuideScreen> {
  // Так выглядит текст, распознанный со снимка карточки Яндекс Про.
  final _sample = TextEditingController(
    text: 'Omite\nPrioritate: -4\nstr. ISMAIL\n1,5 km · 5 min.\nPreluare apropiată\nA\nstrada Calea Basarabiei, 8\n'
        'B\nstrada Alecu Russo, 63/2, entrance 1\nPasager\npoarta 3\n+35 L\nAcceptă',
  );
  ParsedOrder? _result;
  bool _busy = false;

  Future<void> _test() async {
    final text = _sample.text.trim();
    if (text.isEmpty) return;
    final order = OrderParserService.parse(text);
    setState(() {
      _result = order;
      _busy = true;
    });
    LiveActivityService.processScannedOrder(order);
    final priced = await OrderParserService.enrich(order);
    if (!mounted) return;
    setState(() {
      _result = priced;
      _busy = false;
    });
    LiveActivityService.processScannedOrder(priced);
  }

  Future<void> _open(List<String> urls) async {
    for (final u in urls) {
      try {
        if (await launchUrl(Uri.parse(u), mode: LaunchMode.externalApplication)) return;
      } catch (_) {}
    }
  }

  /// Нужное место в Настройках. Apple не всегда даёт открыть подраздел из приложения —
  /// тогда откроются просто Настройки, дальше по схеме.
  void _openTouchSettings() => _open([
        'App-prefs:ACCESSIBILITY&path=TOUCH_REACHABLE_TITLE/ASSISTIVE_TOUCH_SWITCH',
        'App-prefs:ACCESSIBILITY&path=TOUCH_REACHABLE_TITLE',
        'App-prefs:ACCESSIBILITY',
        'App-prefs:',
        'app-settings:',
      ]);

  Widget _rows(List<Widget> rows) => SizedBox(
        width: 280,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Column(
            children: [
              for (var i = 0; i < rows.length; i++) ...[
                if (i > 0) Container(height: 0.5, color: DS.separator(context)),
                rows[i],
              ],
            ],
          ),
        ),
      );

  Widget _buttons(List<Widget> buttons) => DSInset(
        padding: const EdgeInsets.fromLTRB(DS.gutter, 0, DS.gutter, DS.s8),
        child: Column(
          children: [
            for (var i = 0; i < buttons.length; i++) ...[if (i > 0) const SizedBox(height: DS.s8), buttons[i]],
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    return DSSubpage(
      title: t('Показ цены', 'Prețul comenzii'),
      children: [
        DSInset(
          child: Text(
            t(
              'iPhone не даёт приложениям читать экран Яндекс Про. Поэтому цену узнаём по снимку: одно касание '
                  'полупрозрачной точки — и через секунду-две сверху приходит баннер с ценой. Taxi Radar при этом '
                  'не открывается, вы остаётесь в Яндекс Про. На экране звонка та же точка покажет отметки о клиенте. '
                  'Настраивается один раз, минут за пять.',
              'iPhone nu permite aplicațiilor să citească ecranul Yandex Pro. De aceea prețul se află din captură: o atingere '
                  'a punctului semitransparent — și în 1–2 secunde sus apare un banner cu prețul. Taxi Radar nu se '
                  'deschide, rămâneți în Yandex Pro. Pe ecranul apelului, același punct arată etichetele clientului. '
                  'Se configurează o singură dată, în cinci minute.',
            ),
            style: DS.callout.copyWith(color: DS.label2(context), height: 1.35),
          ),
        ),

        // ---------- 1. Быстрая команда ----------
        GuideStep(
          n: 1,
          title: t('Создайте быструю команду', 'Creați o comandă rapidă'),
          text: t(
            'Откройте «Команды» → «+» вверху справа → нажмите на название → «Переименовать» → «Taxi Radar». '
                'Внизу в «Поиске действий» вводите название и нажимайте на действие. Нужно 3 действия по порядку. '
                'Если «Цена заказа» не находится — откройте Taxi Radar один раз и вернитесь.',
            'Deschideți «Comenzi» → «+» sus în dreapta → apăsați pe nume → «Redenumește» → «Taxi Radar». '
                'Jos în «Căutare acțiuni» scrieți numele și apăsați pe acțiune. Sunt necesare 3 acțiuni în ordine. '
                'Dacă «Prețul comenzii» nu se găsește — deschideți Taxi Radar o dată și reveniți.',
          ),
          picture: SizedBox(
            width: 280,
            child: Column(
              children: [
                MockShortcutAction(
                    icon: CupertinoIcons.camera_viewfinder,
                    color: CupertinoColors.systemBlue,
                    text: t('1. «Сделать снимок экрана»', '1. «Fă o captură de ecran»')),
                MockShortcutAction(
                    icon: CupertinoIcons.doc_text_viewfinder,
                    color: CupertinoColors.systemOrange,
                    text: t('2. «Извлечь текст из изображения» — само возьмёт снимок',
                        '2. «Extrage textul din imagine» — ia singur captura')),
                MockShortcutAction(
                    icon: CupertinoIcons.dot_radiowaves_left_right,
                    color: const Color(0xFF1C1C1E),
                    text: t('3. Введите «Taxi Radar» → «Цена заказа» — само возьмёт текст',
                        '3. Scrieți «Taxi Radar» → «Prețul comenzii» — ia singur textul')),
              ],
            ),
          ),
        ),
        _buttons([
          DSButton(
            t('Открыть «Команды»', 'Deschide «Comenzi»'),
            icon: CupertinoIcons.square_stack_3d_up_fill,
            secondary: true,
            onPressed: () => _open(['shortcuts://create-shortcut', 'shortcuts://']),
          ),
        ]),

        // ---------- 2. Уведомления ----------
        GuideStep(
          n: 2,
          title: t('Разрешите уведомления', 'Permiteți notificările'),
          text: t(
            'Цена приходит уведомлением. Настройки → Taxi Radar → «Уведомления» → включите «Допуск уведомлений» '
                'и выберите стиль баннера «Постоянно» — тогда баннер не исчезнет, пока вы его не смахнёте.',
            'Prețul vine ca notificare. Setări → Taxi Radar → «Notificări» → activați «Permite notificări» '
                'și alegeți stilul bannerului «Persistent» — bannerul nu dispare până nu-l glisați.',
          ),
          picture: _rows([
            MockSettingsRow(icon: CupertinoIcons.dot_radiowaves_left_right, color: const Color(0xFF1C1C1E), title: 'Taxi Radar'),
            MockSettingsRow(icon: CupertinoIcons.bell_fill, color: CupertinoColors.systemRed, title: t('Уведомления', 'Notificări')),
            MockSettingsRow(icon: CupertinoIcons.bell_fill, color: CupertinoColors.systemRed, title: t('Допуск уведомлений', 'Permite notificări'), on: true),
            MockSettingsRow(icon: CupertinoIcons.rectangle_fill_on_rectangle_fill, color: CupertinoColors.systemGrey, title: t('Стиль баннеров: Постоянно', 'Stil banner: Persistent')),
          ]),
        ),
        _buttons([
          DSButton(
            t('Открыть настройки Taxi Radar', 'Deschide setările Taxi Radar'),
            icon: CupertinoIcons.gear_alt_fill,
            secondary: true,
            onPressed: () => _open(['app-settings:']),
          ),
        ]),

        // ---------- 3. AssistiveTouch ----------
        GuideStep(
          n: 3,
          title: t('Включите точку AssistiveTouch', 'Activați punctul AssistiveTouch'),
          text: t(
            'Это кнопка, которой вы запускаете команду поверх любого приложения. Настройки → Универсальный доступ → '
                'Касание → AssistiveTouch → включите. «Непрозрачность в покое» — 15–20 %, чтобы точка не мешала карте.',
            'E butonul cu care porniți comanda peste orice aplicație. Setări → Accesibilitate → Atingere → '
                'AssistiveTouch → activați. «Opacitate inactivă» — 15–20 %, ca punctul să nu încurce harta.',
          ),
          picture: _rows([
            MockSettingsRow(
                icon: CupertinoIcons.person_crop_circle_fill_badge_checkmark,
                color: CupertinoColors.systemBlue,
                title: t('Универсальный доступ', 'Accesibilitate')),
            MockSettingsRow(icon: CupertinoIcons.hand_point_right_fill, color: CupertinoColors.systemBlue, title: t('Касание', 'Atingere')),
            MockSettingsRow(icon: CupertinoIcons.circle_grid_3x3_fill, color: CupertinoColors.systemGrey, title: 'AssistiveTouch', on: true),
            MockSettingsRow(
                icon: CupertinoIcons.circle_lefthalf_fill,
                color: CupertinoColors.systemGrey,
                title: t('Непрозрачность в покое: 20 %', 'Opacitate inactivă: 20 %')),
          ]),
        ),
        _buttons([
          DSButton(
            t('Открыть Настройки', 'Deschide Setările'),
            icon: CupertinoIcons.settings,
            secondary: true,
            onPressed: _openTouchSettings,
          ),
        ]),

        // ---------- 4. Одно касание → команда ----------
        GuideStep(
          n: 4,
          title: t('Назначьте команду на одно касание', 'Atribuiți comanda la o atingere'),
          text: t(
            'Там же, в AssistiveTouch: «Настройка действий» → «Одно касание» → пролистайте вниз до «Быстрые команды» → «Taxi Radar». '
                'Вместо точки можно использовать двойной стук по задней крышке: Касание → «Касание задней панели» → «Двойное касание» → «Taxi Radar».',
            'Tot în AssistiveTouch: «Personalizare acțiuni» → «O atingere» → derulați jos la «Comenzi rapide» → «Taxi Radar». '
                'În loc de punct puteți folosi dubla atingere pe spate: Atingere → «Atingere spate» → «Atingere dublă» → «Taxi Radar».',
          ),
          picture: _rows([
            MockSettingsRow(icon: CupertinoIcons.slider_horizontal_3, color: CupertinoColors.systemGrey, title: t('Настройка действий', 'Personalizare acțiuni')),
            MockSettingsRow(icon: CupertinoIcons.hand_draw_fill, color: CupertinoColors.systemBlue, title: t('Одно касание', 'O atingere')),
            MockSettingsRow(icon: CupertinoIcons.square_stack_3d_up_fill, color: CupertinoColors.systemIndigo, title: 'Taxi Radar  ✓'),
          ]),
        ),

        // ---------- 5. Пользуемся ----------
        GuideStep(
          n: 5,
          title: t('Пришёл заказ — коснитесь точки', 'A venit o comandă — atingeți punctul'),
          text: t(
            'Через секунду-две сверху — цена по маршруту и надбавка. Taxi Radar не открывается, можно сразу нажать «Принять».',
            'În 1–2 secunde sus apare prețul pe traseu și adaosul. Taxi Radar nu se deschide, puteți apăsa imediat «Acceptă».',
          ),
          picture: SizedBox(
            width: 280,
            child: MockBanner(
              icon: CupertinoIcons.car_fill,
              color: DS.success,
              title: '~109 L',
              body: t('Эконом · 9.4 км · 15 мин · надбавка +15', 'Econom · 9.4 km · 15 min · adaos +15'),
            ),
          ),
        ),
        GuideStep(
          n: 6,
          title: t('Звоните клиенту — коснитесь точки', 'Sunați clientul — atingeți punctul'),
          text: t(
            'На экране звонка видно номер — придёт баннер с отметками других водителей. '
                'Номер сохранится: в «Полезное → Клиенты» он уже будет подставлен, чтобы после поездки отметить клиента.',
            'Pe ecranul apelului se vede numărul — vine un banner cu etichetele altor șoferi. '
                'Numărul se salvează: în «Utile → Clienți» va fi deja completat, ca după cursă să marcați clientul.',
          ),
          picture: SizedBox(
            width: 280,
            child: MockBanner(
              icon: CupertinoIcons.person_crop_circle_fill,
              color: DS.surge,
              title: t('Клиент +373 78 123 456', 'Client +373 78 123 456'),
              body: t('Не вышел ×2 · Всё ок ×3', 'Nu a ieșit ×2 · Totul ok ×3'),
            ),
          ),
        ),

        DSSection(
          header: t('Проверка распознавания', 'Verificarea recunoașterii'),
          footer: t(
            'Пример текста со снимка. Нажмите «Посчитать» — цена появится здесь и баннером.',
            'Exemplu de text din captură. Apăsați «Calculează» — prețul apare aici și ca banner.',
          ),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: Column(
                children: [
                  DSField(controller: _sample, placeholder: t('Текст карточки заказа', 'Textul cardului comenzii'), maxLines: 6),
                  const SizedBox(height: DS.s12),
                  DSButton(t('Посчитать', 'Calculează'), icon: CupertinoIcons.play_fill, loading: _busy, onPressed: _test),
                ],
              ),
            ),
            if (_result != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(DS.s16, 0, DS.s16, DS.s16),
                child: OrderPriceView(order: _result!),
              ),
          ],
        ),
      ],
    );
  }
}
