import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../../ui/ds.dart';

/// Шаг инструкции: номер, заголовок, текст и (по желанию) картинка-схема.
class GuideStep extends StatelessWidget {
  final int n;
  final String title;
  final String text;
  final Widget? picture;
  const GuideStep({super.key, required this.n, required this.title, required this.text, this.picture});

  @override
  Widget build(BuildContext context) => DSCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 26,
                  height: 26,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(color: DS.c(context, DS.brand), shape: BoxShape.circle),
                  child: Text('$n', style: DS.subhead.copyWith(fontWeight: FontWeight.w700, color: Colors.black)),
                ),
                const SizedBox(width: DS.s12),
                Expanded(child: Text(title, style: DS.headline.copyWith(color: DS.label(context)))),
              ],
            ),
            const SizedBox(height: DS.s8),
            Padding(
              padding: const EdgeInsets.only(left: 38),
              child: Text(text, style: DS.subhead.copyWith(color: DS.label2(context), height: 1.35)),
            ),
            if (picture != null) ...[
              const SizedBox(height: DS.s16),
              Center(child: picture!),
            ],
          ],
        ),
      );
}

/// Рамка «экрана iPhone» для схем.
class PhoneFrame extends StatelessWidget {
  final Widget child;
  final double height;
  final Color? background;
  const PhoneFrame({super.key, required this.child, this.height = 170, this.background});

  @override
  Widget build(BuildContext context) => Container(
        width: 250,
        height: height,
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: background ?? DS.bg(context),
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: DS.separator(context), width: 1),
        ),
        child: ClipRRect(borderRadius: BorderRadius.circular(18), child: child),
      );
}

/// Схема уведомления-баннера.
class MockBanner extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String body;
  const MockBanner({super.key, required this.icon, required this.color, required this.title, required this.body});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: DS.card(context),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: DS.separator(context), width: 0.5),
        ),
        child: Row(
          children: [
            DSIcon(icon, color, size: 30),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: DS.footnote.copyWith(fontWeight: FontWeight.w600, color: DS.label(context))),
                  Text(body,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: DS.caption.copyWith(color: DS.label2(context))),
                ],
              ),
            ),
          ],
        ),
      );
}

/// Значок приложения с цифрой надбавки.
class MockAppIcon extends StatelessWidget {
  final String badge;
  const MockAppIcon({super.key, required this.badge});

  @override
  Widget build(BuildContext context) => SizedBox(
        width: 74,
        height: 84,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Column(
              children: [
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: const Color(0xFF1C1C1E),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(CupertinoIcons.dot_radiowaves_left_right, color: DS.c(context, DS.brand), size: 32),
                ),
                const SizedBox(height: 4),
                Text('Taxi Radar', style: DS.caption.copyWith(color: DS.label(context))),
              ],
            ),
            Positioned(
              right: 0,
              top: -6,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(color: DS.c(context, DS.danger), borderRadius: BorderRadius.circular(12)),
                child: Text(badge, style: DS.footnote.copyWith(color: Colors.white, fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      );
}

/// Строка «настроек iOS» для схемы.
class MockSettingsRow extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final bool? on;
  const MockSettingsRow({super.key, required this.icon, required this.color, required this.title, this.on});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        color: DS.card(context),
        child: Row(
          children: [
            DSIcon(icon, color, size: 24),
            const SizedBox(width: 10),
            Expanded(child: Text(title, style: DS.footnote.copyWith(color: DS.label(context)))),
            if (on != null)
              Transform.scale(
                scale: 0.7,
                child: CupertinoSwitch(value: on!, onChanged: null, activeTrackColor: DS.c(context, DS.success)),
              )
            else
              Icon(CupertinoIcons.chevron_right, size: 14, color: DS.label3(context)),
          ],
        ),
      );
}

/// Блок действия «Быстрых команд».
class MockShortcutAction extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String text;
  const MockShortcutAction({super.key, required this.icon, required this.color, required this.text});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(color: DS.card(context), borderRadius: BorderRadius.circular(10)),
        child: Row(
          children: [
            DSIcon(icon, color, size: 22),
            const SizedBox(width: 8),
            Expanded(child: Text(text, style: DS.caption.copyWith(color: DS.label(context)))),
          ],
        ),
      );
}
