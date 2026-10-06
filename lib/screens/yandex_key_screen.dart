import 'package:flutter/cupertino.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../l10n/app_strings.dart';
import '../ui/ds.dart';

/// Свой ключ «API Геокодера» Яндекса — запасной поиск адресов.
class YandexKeyScreen extends StatefulWidget {
  const YandexKeyScreen({super.key});

  @override
  State<YandexKeyScreen> createState() => _YandexKeyScreenState();
}

class _YandexKeyScreenState extends State<YandexKeyScreen> {
  final _keyController = TextEditingController();
  bool _hasSavedKey = false;

  @override
  void initState() {
    super.initState();
    _loadKey();
  }

  Future<void> _loadKey() async {
    final prefs = await SharedPreferences.getInstance();
    final k = prefs.getString('custom_yandex_key') ?? '';
    setState(() {
      _keyController.text = k;
      _hasSavedKey = k.isNotEmpty;
    });
  }

  Future<void> _saveKey() async {
    final k = _keyController.text.trim();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('custom_yandex_key', k);
    setState(() => _hasSavedKey = k.isNotEmpty);
    if (mounted) {
      dsToast(context, k.isNotEmpty ? AppStrings.t('Ключ сохранён', 'Cheia a fost salvată') : AppStrings.t('Ключ удалён', 'Cheia a fost ștearsă'));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    return DSSubpage(
      title: t('Ключ Яндекс API', 'Cheia Yandex API'),
      children: [
        DSSection(
          footer: t(
            'Свой бесплатный ключ «API Геокодера» (1000 запросов в сутки) нужен, только если сервер недоступен.',
            'Cheia gratuită proprie «API Geocoder» (1000 cereri pe zi) e necesară doar dacă serverul nu e disponibil.',
          ),
          children: [
            DSRow(
              icon: DSIcon(_hasSavedKey ? CupertinoIcons.checkmark_alt : CupertinoIcons.cloud_fill, _hasSavedKey ? DS.success : DS.info),
              title: _hasSavedKey ? t('Свой ключ подключён', 'Cheia proprie este conectată') : t('Адреса ищет сервер Taxi Radar', 'Adresele le caută serverul Taxi Radar'),
            ),
          ],
        ),
        DSSection(
          header: t('Ключ', 'Cheie'),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: Column(
                children: [
                  DSField(controller: _keyController, placeholder: '1a2b3c4d-xxxx-xxxx-xxxx-…'),
                  const SizedBox(height: DS.s12),
                  DSButton(t('Сохранить', 'Salvează'), secondary: true, onPressed: _saveKey),
                  if (_hasSavedKey) ...[
                    const SizedBox(height: DS.s8),
                    DSButton(
                      t('Удалить ключ', 'Șterge cheia'),
                      secondary: true,
                      destructive: true,
                      onPressed: () {
                        _keyController.clear();
                        _saveKey();
                      },
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
        DSSection(
          header: t('Как получить', 'Cum se obține'),
          footer: t(
            'Нажмите «Подключить API» → «API Геокодера», выберите бесплатный тариф и скопируйте ключ сюда.',
            'Apăsați «Conectează API» → «API Geocoder», alegeți tariful gratuit și copiați cheia aici.',
          ),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.globe, DS.danger),
              title: 'developer.tech.yandex.ru',
              onTap: () => launchUrl(Uri.parse('https://developer.tech.yandex.ru'), mode: LaunchMode.externalApplication),
            ),
          ],
        ),
      ],
    );
  }
}
