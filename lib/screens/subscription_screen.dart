import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../l10n/app_strings.dart';
import '../models/app_config.dart';
import '../services/api_service.dart';
import '../services/license_service.dart';
import '../ui/ds.dart';

/// «Подписка» — как на Android: статус, тарифы, оплата через Telegram, ввод ключа.
class SubscriptionScreen extends StatefulWidget {
  final AppConfig config;
  final LicenseStatus? license;
  const SubscriptionScreen({super.key, required this.config, required this.license});

  @override
  State<SubscriptionScreen> createState() => _SubscriptionScreenState();
}

class _SubscriptionScreenState extends State<SubscriptionScreen> {
  final _key = TextEditingController();
  final _promo = TextEditingController();
  int _selected = 0;
  bool _activating = false;
  String _deviceId = '';
  LicenseStatus? _license;

  @override
  void initState() {
    super.initState();
    _license = widget.license;
    ApiService.getDeviceId().then((id) => mounted ? setState(() => _deviceId = id) : null);
  }

  Future<void> _activate() async {
    final key = _key.text.trim();
    if (key.isEmpty) return;
    setState(() => _activating = true);
    final res = await LicenseService.activateKey(key);
    final lic = await LicenseService.checkLicense();
    if (!mounted) return;
    setState(() {
      _activating = false;
      _license = lic;
    });
    dsToast(context, res['message']?.toString() ?? '');
    if (res['ok'] == true || lic.isLicensed) _key.clear();
  }

  void _pay(Tariff t) {
    final cur = widget.config.currency;
    final promo = _promo.text.trim().isEmpty ? AppStrings.t('нет', 'nu') : _promo.text.trim();
    final msg = AppStrings.t(
      'Здравствуйте! Хочу оплатить подписку Taxi Radar (iPhone).\nТариф: ${t.days} дней — ${t.price} $cur\nПромокод: $promo\nМой ID: $_deviceId',
      'Bună ziua! Vreau să achit abonamentul Taxi Radar (iPhone).\nTarif: ${t.days} zile — ${t.price} $cur\nCod promo: $promo\nID-ul meu: $_deviceId',
    );
    launchUrl(Uri.parse('https://t.me/${widget.config.telegram}?text=${Uri.encodeComponent(msg)}'),
        mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final lic = _license;
    final active = lic?.isLicensed ?? false;
    final tariffs = widget.config.tariffs;
    final cur = widget.config.currency;
    if (_selected >= tariffs.length) _selected = 0;
    return DSSubpage(
      title: AppStrings.t('Подписка', 'Abonament'),
      children: [
        DSSection(
          footer: AppStrings.t(
            'Цена заказа поверх Яндекс Про · радар надбавки · карта · попутчики · аэропорт · клиенты',
            'Prețul comenzii peste Yandex Pro · radarul adaosului · harta · curse · aeroport · clienți',
          ),
          children: [
            DSRow(
              icon: DSIcon(active ? CupertinoIcons.checkmark_seal_fill : CupertinoIcons.xmark_seal_fill,
                  active ? DS.success : DS.danger),
              title: active
                  ? (lic!.isTrial ? AppStrings.licenseTrial : AppStrings.licenseActive)
                  : AppStrings.t('Подписка не активна', 'Abonamentul nu este activ'),
              value: active ? '${lic!.daysLeft} ${AppStrings.t('дн.', 'zile')}' : null,
            ),
          ],
        ),
        if (tariffs.isNotEmpty)
          DSSection(
            header: AppStrings.t('Тариф', 'Tarif'),
            children: [
              for (var i = 0; i < tariffs.length; i++)
                DSRow(
                  title: '${tariffs[i].days} ${AppStrings.t('дней', 'zile')}',
                  subtitle: '≈ ${(tariffs[i].price / tariffs[i].days).toStringAsFixed(1)} $cur ${AppStrings.t('в день', 'pe zi')}',
                  value: '${tariffs[i].price} $cur',
                  trailing: i == _selected
                      ? Icon(CupertinoIcons.checkmark_alt, color: DS.c(context, DS.info))
                      : const SizedBox(width: 22),
                  onTap: () => setState(() => _selected = i),
                ),
            ],
          ),
        if (tariffs.isNotEmpty)
          DSInset(child: DSField(controller: _promo, placeholder: AppStrings.t('Промокод (если есть)', 'Cod promo (dacă aveți)'))),
        if (tariffs.isNotEmpty)
          DSInset(
            child: DSButton(
              AppStrings.t('Оплатить ${tariffs[_selected].price} $cur', 'Achită ${tariffs[_selected].price} $cur'),
              icon: CupertinoIcons.paperplane_fill,
              onPressed: () => _pay(tariffs[_selected]),
            ),
          ),
        DSInset(
          padding: const EdgeInsets.fromLTRB(DS.gutter + 12, 0, DS.gutter + 12, DS.s8),
          child: Text(
            AppStrings.t(
              'Откроется Telegram с готовым сообщением: тариф, промокод и ваш ID. После оплаты вам пришлют ключ — введите его ниже.',
              'Se deschide Telegram cu mesajul gata: tariful, codul promo și ID-ul. După plată primiți o cheie — introduceți-o mai jos.',
            ),
            style: DS.footnote.copyWith(color: DS.label2(context)),
          ),
        ),
        DSSection(
          header: AppStrings.t('Уже есть ключ?', 'Aveți deja o cheie?'),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: Column(
                children: [
                  DSField(
                    controller: _key,
                    placeholder: AppStrings.licenseKeyHint,
                    capitalization: TextCapitalization.characters,
                    onSubmitted: (_) => _activate(),
                  ),
                  const SizedBox(height: DS.s12),
                  DSButton(AppStrings.licenseActivate, secondary: true, loading: _activating, onPressed: _activate),
                ],
              ),
            ),
          ],
        ),
        DSSection(
          children: [
            DSRow(
              title: AppStrings.t('Ваш ID', 'ID-ul dvs.'),
              subtitle: _deviceId,
              trailing: Icon(CupertinoIcons.doc_on_doc, size: 20, color: DS.label2(context)),
              onTap: () {
                Clipboard.setData(ClipboardData(text: _deviceId));
                dsToast(context, AppStrings.t('ID скопирован', 'ID copiat'));
              },
            ),
          ],
        ),
      ],
    );
  }
}
