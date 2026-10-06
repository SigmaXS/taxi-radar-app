import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_strings.dart';
import '../../models/app_config.dart';
import '../../services/license_service.dart';
import '../../services/live_activity_service.dart';
import '../../ui/ds.dart';
import '../subscription_screen.dart';
import '../yandex_key_screen.dart';

class ProfileTab extends StatefulWidget {
  final AppConfig config;
  final LicenseStatus? license;
  final Function(String) onLanguageChanged;
  final Future<void> Function() onRefresh;

  const ProfileTab({
    super.key,
    required this.config,
    required this.license,
    required this.onLanguageChanged,
    required this.onRefresh,
  });

  @override
  State<ProfileTab> createState() => _ProfileTabState();
}

class _ProfileTabState extends State<ProfileTab> {
  final _friendCode = TextEditingController();

  void _open(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);

  void _enterFriendCode() {
    final t = AppStrings.t;
    showCupertinoDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(AppStrings.refEnterCode),
        content: Padding(
          padding: const EdgeInsets.only(top: 12),
          child: CupertinoTextField(
            controller: _friendCode,
            placeholder: 'TR-XXXXXX',
            textCapitalization: TextCapitalization.characters,
            autofocus: true,
          ),
        ),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: Text(t('Отмена', 'Anulează'))),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              final code = _friendCode.text.trim();
              Navigator.pop(ctx);
              if (code.isEmpty) return;
              final res = await LicenseService.applyReferralCode(code);
              if (mounted) dsToast(context, res['message']?.toString() ?? '');
              widget.onRefresh();
            },
            child: Text(t('Применить', 'Aplică')),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    final lic = widget.license;
    final cfg = widget.config;
    final refCode = lic?.referralCode ?? '';
    return DSPage(
      title: AppStrings.navProfile,
      onRefresh: widget.onRefresh,
      children: [
        DSSection(
          children: [
            DSRow(
              icon: DSIcon(CupertinoIcons.checkmark_seal_fill, (lic?.isLicensed ?? false) ? DS.success : DS.danger),
              title: t('Подписка', 'Abonament'),
              value: lic == null
                  ? null
                  : lic.isLicensed
                      ? '${lic.daysLeft} ${t('дн.', 'zile')}'
                      : t('не активна', 'inactiv'),
              onTap: () async {
                await dsPush(context, SubscriptionScreen(config: cfg, license: lic));
                widget.onRefresh();
              },
            ),
          ],
        ),
        DSSection(
          header: t('Язык', 'Limba'),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: SizedBox(
                width: double.infinity,
                child: CupertinoSlidingSegmentedControl<String>(
                  groupValue: AppStrings.lang,
                  children: const {'RU': Text('Русский'), 'RO': Text('Română')},
                  onValueChanged: (v) {
                    if (v == null) return;
                    DS.tap();
                    widget.onLanguageChanged(v);
                    setState(() {});
                  },
                ),
              ),
            ),
          ],
        ),
        DSSection(
          header: AppStrings.refTitle,
          footer: t(
            'Друг вводит ваш код — вы оба получаете +${cfg.referralBonusDays} дн. подписки.',
            'Prietenul introduce codul dvs. — primiți amândoi +${cfg.referralBonusDays} zile.',
          ),
          children: [
            if (refCode.isNotEmpty)
              DSRow(
                icon: const DSIcon(CupertinoIcons.gift_fill, CupertinoColors.systemPink),
                title: t('Ваш код', 'Codul dvs.'),
                value: refCode,
                trailing: Icon(CupertinoIcons.doc_on_doc, size: 20, color: DS.label2(context)),
                onTap: () {
                  Clipboard.setData(ClipboardData(text: refCode));
                  dsToast(context, t('Код скопирован', 'Cod copiat'));
                },
              ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.person_badge_plus_fill, CupertinoColors.systemTeal),
              title: AppStrings.refEnterCode,
              onTap: _enterFriendCode,
            ),
          ],
        ),
        DSSection(
          header: t('Дополнительно', 'Suplimentar'),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.lock_shield_fill, CupertinoColors.systemGrey),
              title: t('Свой ключ Яндекс API', 'Cheia proprie Yandex API'),
              subtitle: t('Запасной поиск адресов', 'Căutarea adreselor de rezervă'),
              onTap: () => dsPush(context, const YandexKeyScreen()),
            ),
          ],
        ),
        DSSection(
          header: AppStrings.contactTitle,
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.paperplane_fill, DS.telegram),
              title: 'Telegram',
              value: '@${cfg.telegram}',
              onTap: () => _open('https://t.me/${cfg.telegram}'),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.chat_bubble_fill, Color(0xFF25D366)),
              title: 'WhatsApp',
              onTap: () => _open('https://wa.me/${cfg.whatsapp.replaceAll('+', '')}'),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.phone_fill, DS.success),
              title: t('Позвонить', 'Sună'),
              value: cfg.phone,
              onTap: () => _open('tel:${cfg.phone}'),
            ),
          ],
        ),
        DSSection(
          header: t('О приложении', 'Despre aplicație'),
          children: [
            const DSRow(title: 'Taxi Radar', value: '1.16 · iOS'),
            ValueListenableBuilder<String>(
              valueListenable: LiveActivityService.diagnosticsNotifier,
              builder: (context, diag, _) => DSRow(
                title: t('Диагностика', 'Diagnostic'),
                subtitle: diag,
              ),
            ),
          ],
        ),
      ],
    );
  }
}
