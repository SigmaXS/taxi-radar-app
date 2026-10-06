import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
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
                  'полупрозрачной кнопки — и сверху приходит баннер с ценой, км и минутами. Снимок нигде не сохраняется. '
                  'Настраивается один раз, минут за пять.',
              'iPhone nu permite aplicațiilor să citească ecranul Yandex Pro. De aceea prețul se află din captură: o atingere '
                  'a butonului semitransparent — și sus apare un banner cu prețul, km și minute. Captura nu se salvează. '
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
                'Внизу в «Поиске действий» вводите название и нажимайте на действие. Нужно 4 действия по порядку:',
            'Deschideți «Comenzi» → «+» sus în dreapta → apăsați pe nume → «Redenumește» → «Taxi Radar». '
                'Jos în «Căutare acțiuni» scrieți numele și apăsați pe acțiune. Sunt necesare 4 acțiuni în ordine:',
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
                    icon: CupertinoIcons.doc_on_clipboard_fill,
                    color: CupertinoColors.systemGrey,
                    text: t('3. «Скопировать в буфер обмена» — само возьмёт текст',
                        '3. «Copiază în clipboard» — ia singur textul')),
                MockShortcutAction(
                    icon: CupertinoIcons.globe,
                    color: CupertinoColors.systemTeal,
                    text: t('4. «Открыть URL-адреса» → вставьте taxiradar://order', '4. «Deschide URL-urile» → lipiți taxiradar://order')),
              ],
            ),
          ),
        ),
        _buttons([
          DSButton(
            t('Скопировать taxiradar://order', 'Copiază taxiradar://order'),
            icon: CupertinoIcons.doc_on_doc,
            secondary: true,
            onPressed: () {
              Clipboard.setData(const ClipboardData(text: 'taxiradar://order'));
              dsToast(context, t('Скопировано — вставьте в 4-е действие', 'Copiat — lipiți în acțiunea 4'));
            },
          ),
          DSButton(
            t('Открыть «Команды»', 'Deschide «Comenzi»'),
            icon: CupertinoIcons.square_stack_3d_up_fill,
            secondary: true,
            onPressed: () => _open(['shortcuts://create-shortcut', 'shortcuts://']),
          ),
        ]),

        // ---------- 2. Вставка из буфера ----------
        GuideStep(
          n: 2,
          title: t('Разрешите вставку', 'Permiteți lipirea'),
          text: t(
            'Чтобы iPhone не спрашивал «Разрешить вставку?» при каждом заказе: Настройки → Taxi Radar → '
                '«Вставка из других приложений» → «Разрешить».',
            'Ca iPhone să nu întrebe «Permiteți lipirea?» la fiecare comandă: Setări → Taxi Radar → '
                '«Lipire din alte aplicații» → «Permite».',
          ),
          picture: _rows([
            MockSettingsRow(icon: CupertinoIcons.dot_radiowaves_left_right, color: const Color(0xFF1C1C1E), title: 'Taxi Radar'),
            MockSettingsRow(
                icon: CupertinoIcons.doc_on_clipboard_fill,
                color: CupertinoColors.systemGrey,
                title: t('Вставка из других приложений: Разрешить', 'Lipire din alte aplicații: Permite')),
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
          title: t('Включите кнопку AssistiveTouch', 'Activați butonul AssistiveTouch'),
          text: t(
            'Настройки → Универсальный доступ → Касание → AssistiveTouch → включите. '
                '«Непрозрачность в покое» — 15–20 %, чтобы точка не мешала карте.',
            'Setări → Accesibilitate → Atingere → AssistiveTouch → activați. '
                '«Opacitate inactivă» — 15–20 %, ca punctul să nu încurce harta.',
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
            'Там же, в AssistiveTouch: «Настройка действий» → «Одно касание» → пролистайте вниз до «Быстрые команды» → «Taxi Radar».',
            'Tot în AssistiveTouch: «Personalizare acțiuni» → «O atingere» → derulați jos la «Comenzi rapide» → «Taxi Radar».',
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
            'Откроется Taxi Radar и сверху придёт баннер с ценой по маршруту. Вернитесь в Яндекс Про — '
                'баннер останется в шторке.',
            'Se deschide Taxi Radar și sus apare un banner cu prețul pe traseu. Reveniți în Yandex Pro — '
                'bannerul rămâne în centrul de notificări.',
          ),
          picture: SizedBox(
            width: 280,
            child: MockBanner(
              icon: CupertinoIcons.car_fill,
              color: DS.success,
              title: '~109 L',
              body: t('Эконом · 9.4 км · 15 мин', 'Econom · 9.4 km · 15 min'),
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
