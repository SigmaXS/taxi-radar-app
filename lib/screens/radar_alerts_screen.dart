import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../l10n/app_strings.dart';
import '../services/radar_alerts.dart';
import '../ui/ds.dart';

/// «Уведомления радара»: водитель сам решает, что и когда сообщать.
class RadarAlertsScreen extends StatefulWidget {
  const RadarAlertsScreen({super.key});

  @override
  State<RadarAlertsScreen> createState() => _RadarAlertsScreenState();
}

class _RadarAlertsScreenState extends State<RadarAlertsScreen> {
  RadarAlertSettings? _s;

  @override
  void initState() {
    super.initState();
    RadarAlertSettings.load().then((s) => setState(() => _s = s));
  }

  void _update(void Function(RadarAlertSettings s) change) {
    final s = _s;
    if (s == null) return;
    setState(() => change(s));
    s.save();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    final s = _s;
    if (s == null) {
      return Scaffold(backgroundColor: DS.bg(context), body: const Center(child: CupertinoActivityIndicator()));
    }
    const tariffs = {'econom': ('Эконом', 'Econom'), 'comfort': ('Комфорт', 'Confort'), 'comfortplus': ('Комфорт+', 'Confort+')};
    return DSSubpage(
      title: t('Уведомления', 'Notificări'),
      children: [
        DSSection(
          header: t('Тариф', 'Tarif'),
          footer: t('Надбавку показываем по одному тарифу — как на кнопке «Принять».',
              'Adaosul se arată pentru un tarif — ca pe butonul «Acceptă».'),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: SizedBox(
                width: double.infinity,
                child: CupertinoSlidingSegmentedControl<String>(
                  groupValue: s.tariff,
                  children: {for (final e in tariffs.entries) e.key: Text(t(e.value.$1, e.value.$2))},
                  onValueChanged: (v) {
                    if (v == null) return;
                    DS.tap();
                    _update((x) => x.tariff = v);
                  },
                ),
              ),
            ),
          ],
        ),
        DSSection(
          header: t('Всегда на виду', 'Mereu la vedere'),
          children: [
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.app_badge_fill, DS.danger),
              title: t('Цифра на иконке', 'Cifra pe pictogramă'),
              subtitle: t('Надбавка на значке Taxi Radar', 'Adaosul pe pictograma Taxi Radar'),
              value: s.badge,
              onChanged: (v) => _update((x) => x.badge = v),
            ),
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.lock_fill, CupertinoColors.systemIndigo),
              title: t('На экране блокировки', 'Pe ecranul de blocare'),
              subtitle: t('Тихо, раз в минуту', 'Discret, o dată pe minut'),
              value: s.lockStatus,
              onChanged: (v) => _update((x) => x.lockStatus = v),
            ),
          ],
        ),
        DSSection(
          header: t('Баннер, когда надбавка меняется', 'Banner când adaosul se schimbă'),
          children: [
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.arrow_up_right, DS.surge),
              title: t('Выросла или появилась', 'A crescut sau a apărut'),
              value: s.onUp,
              onChanged: (v) => _update((x) => x.onUp = v),
            ),
            if (s.onUp)
              Padding(
                padding: const EdgeInsets.fromLTRB(DS.s16, DS.s8, DS.s16, DS.s12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(t('Сообщать от', 'Anunță de la'), style: DS.footnote.copyWith(color: DS.label2(context))),
                    const SizedBox(height: DS.s8),
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoSlidingSegmentedControl<int>(
                        groupValue: s.minSurge,
                        children: {for (final v in const [0, 15, 35, 55]) v: Text(v == 0 ? t('любой', 'oricare') : '+$v')},
                        onValueChanged: (v) {
                          if (v == null) return;
                          DS.tap();
                          _update((x) => x.minSurge = v);
                        },
                      ),
                    ),
                  ],
                ),
              ),
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.arrow_down_right, CupertinoColors.systemOrange),
              title: t('Упала', 'A scăzut'),
              value: s.onDown,
              onChanged: (v) => _update((x) => x.onDown = v),
            ),
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.moon_fill, CupertinoColors.systemGrey),
              title: t('Пропала', 'A dispărut'),
              value: s.onGone,
              onChanged: (v) => _update((x) => x.onGone = v),
            ),
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.speaker_2_fill, CupertinoColors.systemPink),
              title: t('Со звуком', 'Cu sunet'),
              value: s.sound,
              onChanged: (v) => _update((x) => x.sound = v),
            ),
          ],
        ),
        DSSection(
          header: t('Заказ', 'Comandă'),
          footer: t(
            'После снимка карточки: «~109 L · 9.4 km · 15 min» и адреса.',
            'După captura cardului: «~109 L · 9.4 km · 15 min» și adresele.',
          ),
          children: [
            DSSwitchRow(
              icon: const DSIcon(CupertinoIcons.car_fill, DS.success),
              title: t('Баннер с ценой заказа', 'Banner cu prețul comenzii'),
              value: s.orderBanner,
              onChanged: (v) => _update((x) => x.orderBanner = v),
            ),
          ],
        ),
      ],
    );
  }
}
