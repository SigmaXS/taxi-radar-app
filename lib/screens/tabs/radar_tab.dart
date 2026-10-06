import 'package:flutter/cupertino.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_strings.dart';
import '../../models/app_config.dart';
import '../../services/license_service.dart';
import '../../services/live_activity_service.dart';
import '../../services/order_parser_service.dart';
import '../../services/radar_alerts.dart';
import '../../ui/ds.dart';
import '../guides/price_guide_screen.dart';
import '../guides/order_price_view.dart';
import '../guides/radar_guide_screen.dart';
import '../radar_alerts_screen.dart';
import '../subscription_screen.dart';

/// Главный экран: надбавка крупно, включение радара, инструкции и поддержка.
class RadarTab extends StatefulWidget {
  final LicenseStatus? license;
  final AppConfig config;
  final Future<void> Function() onRefresh;

  const RadarTab({super.key, required this.license, required this.config, required this.onRefresh});

  @override
  State<RadarTab> createState() => _RadarTabState();
}

class _RadarTabState extends State<RadarTab> {
  bool _isMonitoring = false;
  bool _busy = false;
  String _tariffName = 'Эконом';

  @override
  void initState() {
    super.initState();
    _loadState();
  }

  Future<void> _loadState() async {
    final prefs = await SharedPreferences.getInstance();
    final s = await RadarAlertSettings.load();
    if (!mounted) return;
    setState(() {
      _isMonitoring = prefs.getBool('is_monitoring') ?? false;
      _tariffName = s.tariffName;
    });
  }


  Future<void> _toggleMonitoring() async {
    setState(() => _busy = true);
    if (!_isMonitoring) {
      LiveActivityResult res;
      try {
        res = await LiveActivityService.startMonitoring();
      } catch (e) {
        res = LiveActivityResult(success: false, errorMessage: '$e');
      }
      if (!mounted) return;
      setState(() {
        _busy = false;
        _isMonitoring = res.success;
      });
      dsToast(
        context,
        res.success
            ? AppStrings.t('Радар включён — надбавка на иконке и на экране блокировки',
                'Radarul e pornit — adaosul pe pictogramă și pe ecranul de blocare')
            : (res.errorMessage ?? AppStrings.t('Не удалось запустить радар', 'Radarul nu a pornit')),
      );
    } else {
      await LiveActivityService.stopMonitoring();
      if (!mounted) return;
      setState(() {
        _busy = false;
        _isMonitoring = false;
      });
    }
  }

  void _open(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);

  void _showNews() {
    final cfg = widget.config;
    showCupertinoDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text('Taxi Radar ${cfg.latestVersionName.isNotEmpty ? cfg.latestVersionName : '1.16'}'),
        content: Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(cfg.updateNotes.isNotEmpty
              ? cfg.updateNotes
              : AppStrings.t('Новостей пока нет — всё работает.', 'Deocamdată nu sunt noutăți — totul funcționează.')),
        ),
        actions: [CupertinoDialogAction(isDefaultAction: true, onPressed: () => Navigator.pop(ctx), child: const Text('OK'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final lic = widget.license;
    final licensed = lic?.isLicensed ?? false;
    return DSPage(
      title: AppStrings.navRadar,
      onRefresh: widget.onRefresh,
      trailing: CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: _showNews,
        child: Icon(CupertinoIcons.bell, color: DS.label(context)),
      ),
      children: [
        if (lic != null && !licensed) _subscriptionBanner(),
        _hero(),
        _order(),
        DSSection(
          header: AppStrings.t('Как это работает', 'Cum funcționează'),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.dot_radiowaves_left_right, DS.surge),
              title: AppStrings.t('Радар надбавки', 'Radarul adaosului'),
              subtitle: AppStrings.t('Где сейчас горит надбавка и как её видеть', 'Unde e acum adaos și cum îl vedeți'),
              onTap: () => dsPush(context, const RadarGuideScreen()),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.money_dollar_circle_fill, DS.success),
              title: AppStrings.t('Показ цены заказа', 'Prețul comenzii'),
              subtitle: AppStrings.t('Цена до «Принять» — одной кнопкой поверх Яндекс Про',
                  'Prețul înainte de «Acceptă» — cu un buton peste Yandex Pro'),
              onTap: () => dsPush(context, const PriceGuideScreen()),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.bell_fill, DS.danger),
              title: AppStrings.t('Уведомления радара', 'Notificările radarului'),
              subtitle: AppStrings.t('Тариф и когда сообщать', 'Tariful și când să anunțe'),
              onTap: () async {
                await dsPush(context, const RadarAlertsScreen());
                _loadState();
              },
            ),
          ],
        ),
        DSSection(
          header: AppStrings.t('Помощь', 'Ajutor'),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.paperplane_fill, DS.telegram),
              title: AppStrings.t('Группа в Telegram', 'Grupul Telegram'),
              subtitle: AppStrings.t('Новости, обновления, вопросы водителей', 'Noutăți, actualizări, întrebări'),
              onTap: () => _open(widget.config.groupUrl),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.chat_bubble_2_fill, DS.success),
              title: AppStrings.t('Написать в поддержку', 'Scrie suportului'),
              onTap: () => _open('https://t.me/${widget.config.telegram}'),
            ),
          ],
        ),
        _footer(lic),
      ],
    );
  }

  /// Подписки нет — сразу наверху, коротко, со входом в «Подписку».
  Widget _subscriptionBanner() => DSSection(
        children: [
          DSRow(
            icon: const DSIcon(CupertinoIcons.exclamationmark_triangle_fill, CupertinoColors.systemOrange),
            title: AppStrings.t('Подписка не активна', 'Abonamentul nu este activ'),
            subtitle: AppStrings.t('Введите ключ или выберите тариф', 'Introduceți cheia sau alegeți un tarif'),
            onTap: () async {
              await dsPush(context, SubscriptionScreen(config: widget.config, license: widget.license));
              widget.onRefresh();
            },
          ),
        ],
      );

  Widget _hero() {
    return DSCard(
      padding: const EdgeInsets.fromLTRB(DS.s20, DS.s16, DS.s20, DS.s20),
      child: ValueListenableBuilder<String>(
        valueListenable: LiveActivityService.currentSurgeNotifier,
        builder: (context, surge, _) {
          final hot = surge.startsWith('+') && surge != '+0';
          final noFix = surge == '📍';
          final big = !_isMonitoring
              ? '—'
              : noFix
                  ? '—'
                  : (surge == '+0' || surge.isEmpty ? '0' : surge);
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(
                    AppStrings.t('Надбавка · ', 'Adaos · ') + _tariffNameLocalized(),
                    style: DS.footnote.copyWith(color: DS.label2(context), fontWeight: FontWeight.w600),
                  ),
                  const Spacer(),
                  ValueListenableBuilder<DateTime?>(
                    valueListenable: LiveActivityService.surgeUpdatedNotifier,
                    builder: (context, at, _) => Text(
                      at == null || !_isMonitoring
                          ? ''
                          : AppStrings.t('обновлено ', 'actualizat ') + DateFormat('HH:mm').format(at),
                      style: DS.footnote.copyWith(color: DS.label3(context)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: DS.s4),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 250),
                child: Text(
                  big,
                  key: ValueKey(big),
                  style: DS.display.copyWith(
                    height: 1.1,
                    color: big == '—'
                        ? DS.label3(context)
                        : hot
                            ? DS.c(context, DS.surge)
                            : DS.label(context),
                  ),
                ),
              ),
              const SizedBox(height: DS.s4),
              Row(
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _isMonitoring ? DS.c(context, DS.success) : DS.label3(context),
                    ),
                  ),
                  const SizedBox(width: DS.s8),
                  Expanded(
                    child: Text(
                      !_isMonitoring
                          ? AppStrings.t('Радар выключен', 'Radarul este oprit')
                          : noFix
                              ? AppStrings.t('Нет геолокации — надбавку не показываем', 'Fără localizare — adaosul nu se arată')
                              : AppStrings.t('Радар включён · обновляется раз в минуту', 'Radar pornit · se actualizează la un minut'),
                      style: DS.subhead.copyWith(color: DS.label2(context)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: DS.s16),
              DSButton(
                _isMonitoring ? AppStrings.t('Выключить радар', 'Oprește radarul') : AppStrings.t('Включить радар', 'Pornește radarul'),
                icon: _isMonitoring ? CupertinoIcons.stop_fill : CupertinoIcons.play_fill,
                secondary: _isMonitoring,
                destructive: _isMonitoring,
                loading: _busy,
                onPressed: _toggleMonitoring,
              ),
            ],
          );
        },
      ),
    );
  }

  String _tariffNameLocalized() => switch (_tariffName) {
        'Комфорт' => AppStrings.t('Комфорт', 'Confort'),
        'Комфорт+' => AppStrings.t('Комфорт+', 'Confort+'),
        _ => AppStrings.t('Эконом', 'Econom'),
      };

  /// Последний заказ со скриншота.
  Widget _order() => ValueListenableBuilder<ParsedOrder?>(
        valueListenable: LiveActivityService.latestOrderNotifier,
        builder: (context, order, _) {
          if (order == null) return const SizedBox.shrink();
          return DSCard(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: OrderPriceView(order: order)),
                CupertinoButton(
                  padding: EdgeInsets.zero,
                  minimumSize: const Size(28, 28),
                  onPressed: LiveActivityService.clearCurrentOrder,
                  child: Icon(CupertinoIcons.xmark_circle_fill, color: DS.label3(context)),
                ),
              ],
            ),
          );
        },
      );

  Widget _footer(LicenseStatus? lic) {
    final parts = <String>[
      if (lic != null && lic.isLicensed)
        '${lic.isTrial ? AppStrings.licenseTrial : AppStrings.licenseActive} · ${lic.daysLeft} ${AppStrings.t('дн.', 'zile')}',
      'Taxi Radar 1.16',
    ];
    return GestureDetector(
      onTap: () => dsPush(context, SubscriptionScreen(config: widget.config, license: widget.license)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(DS.gutter, DS.s16, DS.gutter, DS.s8),
        child: Text(
          parts.join('  ·  '),
          textAlign: TextAlign.center,
          style: DS.footnote.copyWith(color: DS.label3(context)),
        ),
      ),
    );
  }
}
