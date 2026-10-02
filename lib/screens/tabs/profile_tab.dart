import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../l10n/app_strings.dart';
import '../../models/app_config.dart';
import '../../services/license_service.dart';
import '../yandex_key_screen.dart';

class ProfileTab extends StatefulWidget {
  final AppConfig config;
  final LicenseStatus? license;
  final Function(String) onLanguageChanged;

  const ProfileTab({
    super.key,
    required this.config,
    required this.license,
    required this.onLanguageChanged,
  });

  @override
  State<ProfileTab> createState() => _ProfileTabState();
}

class _ProfileTabState extends State<ProfileTab> {
  final TextEditingController _consumptionController = TextEditingController();
  final TextEditingController _fuelPriceController = TextEditingController();
  final TextEditingController _commissionController = TextEditingController();
  final TextEditingController _friendCodeController = TextEditingController();


  @override
  void initState() {
    super.initState();
    _loadNetPrefs();
  }

  Future<void> _loadNetPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _consumptionController.text = (prefs.getDouble('fuel_consumption') ?? 8.5).toString();
      _fuelPriceController.text = (prefs.getDouble('fuel_price') ?? 24.5).toString();
      _commissionController.text = (prefs.getDouble('commission_percent') ?? 16.0).toString();
    });
  }

  Future<void> _saveNetPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final c = double.tryParse(_consumptionController.text) ?? 8.5;
    final fp = double.tryParse(_fuelPriceController.text) ?? 24.5;
    final com = double.tryParse(_commissionController.text) ?? 16.0;

    await prefs.setDouble('fuel_consumption', c);
    await prefs.setDouble('fuel_price', fp);
    await prefs.setDouble('commission_percent', com);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Параметры сохранены'), duration: Duration(seconds: 1)),
      );
    }
  }

  void _openUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  void _showEnterCodeDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E2638),
        title: Text(AppStrings.refEnterCode, style: const TextStyle(color: Colors.white)),
        content: TextField(
          controller: _friendCodeController,
          style: const TextStyle(color: Colors.white),
          decoration: InputDecoration(
            hintText: 'TR-XXXXXX',
            hintStyle: TextStyle(color: Colors.grey.shade400),
            filled: true,
            fillColor: const Color(0xFF2A364F),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
          ),
        ),
        actions: [
          TextButton(
            child: const Text('Отмена', style: TextStyle(color: Colors.white70)),
            onPressed: () => Navigator.pop(ctx),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.amber.shade700),
            child: const Text('Применить', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
            onPressed: () async {
              final code = _friendCodeController.text.trim();
              if (code.isNotEmpty) {
                Navigator.pop(ctx);
                final res = await LicenseService.applyReferralCode(code);
                if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(res['message']?.toString() ?? 'Ответ сервера')),
                  );
                }
              }
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final refCode = widget.license?.referralCode ?? 'TR-000000';

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        // Язык приложения
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          color: const Color(0xFF1E2638),
          child: ListTile(
            leading: const Icon(Icons.language_rounded, color: Colors.blueAccent),
            title: const Text('Язык приложения / Limba', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            trailing: SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'RU', label: Text('RU')),
                ButtonSegment(value: 'RO', label: Text('RO')),
              ],
              selected: {AppStrings.lang},
              onSelectionChanged: (set) {
                widget.onLanguageChanged(set.first);
                setState(() {});
              },
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Настройки чистой прибыли
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          color: const Color(0xFF1E2638),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  AppStrings.netTitle,
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                const SizedBox(height: 6),
                Text(
                  'Вычет комиссии и топлива из стоимости поездки',
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade400),
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: _consumptionController,
                        keyboardType: TextInputType.number,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          labelText: 'Расход л/100км',
                          labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                          filled: true,
                          fillColor: const Color(0xFF2A364F),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _fuelPriceController,
                        keyboardType: TextInputType.number,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          labelText: 'Цена топлива L',
                          labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                          filled: true,
                          fillColor: const Color(0xFF2A364F),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: TextField(
                        controller: _commissionController,
                        keyboardType: TextInputType.number,
                        style: const TextStyle(color: Colors.white),
                        decoration: InputDecoration(
                          labelText: 'Комиссия %',
                          labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                          filled: true,
                          fillColor: const Color(0xFF2A364F),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blueAccent.shade700,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    onPressed: _saveNetPrefs,
                    child: const Text('Сохранить параметры'),
                  ),
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Пригласи друга
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          color: const Color(0xFF1E2638),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      AppStrings.refTitle,
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.amber.shade700,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '+${widget.config.referralBonusDays} дн.',
                        style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 12),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  'Ваш код друга: $refCode',
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.amberAccent),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          side: const BorderSide(color: Colors.white24),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        onPressed: _showEnterCodeDialog,
                        child: Text(AppStrings.refEnterCode),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Свой ключ Яндекс Геокодера
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          color: const Color(0xFF1E2638),
          child: ListTile(
            leading: const Icon(Icons.vpn_key_rounded, color: Colors.amberAccent),
            title: const Text('Свой ключ Яндекс API', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            subtitle: const Text('Запасной ключ геокодирования', style: TextStyle(color: Colors.grey, fontSize: 13)),
            trailing: const Icon(Icons.arrow_forward_ios_rounded, color: Colors.white30, size: 16),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const YandexKeyScreen()),
              );
            },
          ),
        ),

        const SizedBox(height: 12),

        // Контакты техподдержки
        Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          color: const Color(0xFF1E2638),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(AppStrings.contactTitle, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(height: 12),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.phone_rounded, color: Colors.greenAccent, size: 28),
                      onPressed: () => _openUrl('tel:${widget.config.phone}'),
                    ),
                    IconButton(
                      icon: const Icon(Icons.telegram, color: Color(0xFF29B6F6), size: 28),
                      onPressed: () => _openUrl('https://t.me/${widget.config.telegram}'),
                    ),
                    IconButton(
                      icon: const Icon(Icons.chat_bubble_rounded, color: Color(0xFF25D366), size: 28),
                      onPressed: () => _openUrl('https://wa.me/${widget.config.whatsapp.replaceAll("+", "")}'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),

        const SizedBox(height: 20),
        const Center(
          child: Text(
            'Taxi Radar v1.16 · iOS & Android',
            style: TextStyle(color: Colors.white38, fontSize: 13),
          ),
        ),
        const SizedBox(height: 30),
      ],
    );
  }
}
