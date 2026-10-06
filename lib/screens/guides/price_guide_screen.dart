import 'package:flutter/cupertino.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_strings.dart';
import '../../services/live_activity_service.dart';
import '../../services/order_parser_service.dart';
import '../../ui/ds.dart';
import 'guide_widgets.dart';
import 'order_price_view.dart';

/// Инструкция «Показ цены заказа»: быстрая команда + AssistiveTouch, со схемами и проверкой.
class PriceGuideScreen extends StatefulWidget {
  const PriceGuideScreen({super.key});

  @override
  State<PriceGuideScreen> createState() => _PriceGuideScreenState();
}

class _PriceGuideScreenState extends State<PriceGuideScreen> {
  // Так выглядит текст, распознанный со скриншота карточки Яндекс Про.
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
                  'полупрозрачной кнопки — и сверху приходит баннер с ценой, км и минутами. Снимок никуда не сохраняется.',
              'iPhone nu permite aplicațiilor să citească ecranul Yandex Pro. De aceea prețul se află din captură: o atingere '
                  'a butonului semitransparent — și sus apare un banner cu prețul, km și minute. Captura nu se salvează.',
            ),
            style: DS.callout.copyWith(color: DS.label2(context), height: 1.35),
          ),
        ),
        GuideStep(
          n: 1,
          title: t('Создайте быструю команду', 'Creați o comandă rapidă'),
          text: t(
            'Приложение «Команды» → «+» → назовите «Taxi Radar» и добавьте 4 действия по порядку. '
                'В последнем вставьте адрес и переменную «Закодированный текст».',
            'Aplicația «Comenzi» → «+» → numiți-o «Taxi Radar» și adăugați 4 acțiuni în ordine. '
                'În ultima introduceți adresa și variabila «Text codificat».',
          ),
          picture: SizedBox(
            width: 260,
            child: Column(
              children: [
                MockShortcutAction(
                    icon: CupertinoIcons.camera_viewfinder, color: CupertinoColors.systemBlue, text: t('Сделать снимок экрана', 'Fă o captură de ecran')),
                MockShortcutAction(
                    icon: CupertinoIcons.doc_text_viewfinder, color: CupertinoColors.systemOrange, text: t('Извлечь текст из [Снимок экрана]', 'Extrage textul din [Captură]')),
                MockShortcutAction(
                    icon: CupertinoIcons.link, color: CupertinoColors.systemGrey, text: t('Кодировать URL [Текст]', 'Codifică URL [Text]')),
                MockShortcutAction(
                    icon: CupertinoIcons.globe, color: CupertinoColors.systemIndigo, text: 'taxiradar://order?text=[…]'),
              ],
            ),
          ),
        ),
        DSInset(
          padding: const EdgeInsets.fromLTRB(DS.gutter, 0, DS.gutter, DS.s8),
          child: DSButton(
            t('Открыть «Команды»', 'Deschide «Comenzi»'),
            icon: CupertinoIcons.square_stack_3d_up_fill,
            secondary: true,
            onPressed: () => launchUrl(Uri.parse('shortcuts://'), mode: LaunchMode.externalApplication),
          ),
        ),
        GuideStep(
          n: 2,
          title: t('Включите AssistiveTouch', 'Activați AssistiveTouch'),
          text: t(
            'Настройки → Универсальный доступ → Касание → AssistiveTouch. «Непрозрачность в покое» — 15–20 %, '
                'чтобы точка не мешала карте.',
            'Setări → Accesibilitate → Atingere → AssistiveTouch. «Opacitate inactivă» — 15–20 %, '
                'ca punctul să nu încurce harta.',
          ),
          picture: SizedBox(
            width: 260,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: Column(
                children: [
                  MockSettingsRow(icon: CupertinoIcons.hand_point_right_fill, color: CupertinoColors.systemBlue, title: t('Касание', 'Atingere')),
                  Container(height: 0.5, color: DS.separator(context)),
                  MockSettingsRow(icon: CupertinoIcons.circle_grid_3x3_fill, color: CupertinoColors.systemGrey, title: 'AssistiveTouch', on: true),
                ],
              ),
            ),
          ),
        ),
        GuideStep(
          n: 3,
          title: t('Назначьте команду на одно касание', 'Atribuiți comanda la o atingere'),
          text: t(
            'Там же: «Настройка действий» → «Одно касание» → внизу «Быстрые команды» → «Taxi Radar».',
            'Tot acolo: «Personalizare acțiuni» → «O atingere» → jos «Comenzi rapide» → «Taxi Radar».',
          ),
        ),
        GuideStep(
          n: 4,
          title: t('Пришёл заказ — коснитесь точки', 'A venit o comandă — atingeți punctul'),
          text: t(
            'Через секунду-две сверху — цена по маршруту и надбавка. Включённый радар не нужен.',
            'În 1–2 secunde sus apare prețul pe traseu și adaosul. Radarul pornit nu e necesar.',
          ),
          picture: SizedBox(
            width: 260,
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
