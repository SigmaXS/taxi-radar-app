import 'package:flutter/material.dart';

import '../services/radar_alerts.dart';

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

  Widget _section(String title) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 6),
        child: Text(title.toUpperCase(),
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: Colors.white54, letterSpacing: 0.8)),
      );

  Widget _switch(String title, String subtitle, bool value, void Function(bool) onChanged) => SwitchListTile(
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.white60)),
        value: value,
        activeThumbColor: const Color(0xFFFFCC00),
        onChanged: onChanged,
      );

  @override
  Widget build(BuildContext context) {
    final s = _s;
    return Scaffold(
      appBar: AppBar(title: const Text('Уведомления радара')),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                _section('Тариф'),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Wrap(
                    spacing: 8,
                    children: RadarAlertSettings.tariffNames.entries
                        .map((e) => ChoiceChip(
                              label: Text(e.value),
                              selected: s.tariff == e.key,
                              onSelected: (_) => _update((x) => x.tariff = e.key),
                            ))
                        .toList(),
                  ),
                ),
                _section('Всегда на виду'),
                _switch('Цифра на иконке', 'Надбавка красным кружком на значке Taxi Radar', s.badge,
                    (v) => _update((x) => x.badge = v)),
                _switch('Статус на экране блокировки', 'Одно тихое уведомление, обновляется раз в минуту без звука',
                    s.lockStatus, (v) => _update((x) => x.lockStatus = v)),
                _section('Баннер, когда надбавка меняется'),
                _switch('Выросла или появилась', 'Например, 0 → +15 или +15 → +35', s.onUp,
                    (v) => _update((x) => x.onUp = v)),
                if (s.onUp)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                    child: Row(
                      children: [
                        const Text('Сообщать от', style: TextStyle(color: Colors.white70)),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Wrap(
                            spacing: 6,
                            children: const [0, 15, 35, 55]
                                .map((v) => ChoiceChip(
                                      label: Text(v == 0 ? 'любой' : '+$v'),
                                      selected: s.minSurge == v,
                                      onSelected: (_) => _update((x) => x.minSurge = v),
                                    ))
                                .toList(),
                          ),
                        ),
                      ],
                    ),
                  ),
                _switch('Упала', 'Например, +55 → +35', s.onDown, (v) => _update((x) => x.onDown = v)),
                _switch('Пропала', 'Надбавка стала 0', s.onGone, (v) => _update((x) => x.onGone = v)),
                _switch('Со звуком', 'Выключите — баннер придёт молча', s.sound, (v) => _update((x) => x.sound = v)),
                _section('Заказ'),
                _switch('Баннер с ценой заказа', 'После скриншота карточки: «~109 L · 9,4 км · 15 мин» и адреса',
                    s.orderBanner, (v) => _update((x) => x.orderBanner = v)),
                const Padding(
                  padding: EdgeInsets.fromLTRB(16, 16, 16, 24),
                  child: Text(
                    'Пока радар включён, он следит за местом в фоне (синяя стрелка вверху экрана) — так iPhone '
                    'не усыпляет его, и надбавка обновляется раз в минуту. Батарея садится немного быстрее.',
                    style: TextStyle(fontSize: 12, color: Colors.white54),
                  ),
                ),
              ],
            ),
    );
  }
}
