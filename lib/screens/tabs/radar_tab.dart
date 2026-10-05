import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../l10n/app_strings.dart';
import '../../models/app_config.dart';
import '../../services/license_service.dart';
import '../../services/live_activity_service.dart';
import '../../services/order_parser_service.dart';
import '../assistive_touch_guide_screen.dart';

class RadarTab extends StatefulWidget {
  final LicenseStatus? license;
  final AppConfig config;
  final VoidCallback onRefresh;

  const RadarTab({
    super.key,
    required this.license,
    required this.config,
    required this.onRefresh,
  });

  @override
  State<RadarTab> createState() => _RadarTabState();
}

class _RadarTabState extends State<RadarTab> {
  final TextEditingController _keyController = TextEditingController();
  bool _isActivating = false;
  bool _isMonitoring = false;

  @override
  void initState() {
    super.initState();
    _loadMonitoringState();
  }

  Future<void> _loadMonitoringState() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _isMonitoring = prefs.getBool('is_monitoring') ?? false;
    });
  }

  Future<void> _toggleMonitoring() async {
    final nextState = !_isMonitoring;

    if (nextState) {
      LiveActivityResult res;
      try {
        res = await LiveActivityService.startMonitoring();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Ошибка запуска: $e'),
              backgroundColor: Colors.red.shade800,
              duration: const Duration(seconds: 5),
            ),
          );
        }
        return;
      }
      if (!res.success) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(res.errorMessage ?? 'Не удалось запустить Dynamic Island'),
              backgroundColor: Colors.red.shade800,
              duration: const Duration(seconds: 5),
            ),
          );
        }
        return;
      }

      setState(() {
        _isMonitoring = true;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Мониторинг активен! Спрос и радары отображаются в Динамическом острове.'),
            backgroundColor: Colors.green.shade700,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } else {
      await LiveActivityService.stopMonitoring();
      setState(() {
        _isMonitoring = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppStrings.radarStop),
            backgroundColor: Colors.grey.shade800,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    }
  }

  Future<void> _activateKey() async {
    final key = _keyController.text.trim();
    if (key.isEmpty) return;

    setState(() => _isActivating = true);
    final res = await LicenseService.activateKey(key);
    setState(() => _isActivating = false);

    if (mounted) {
      final msg = res['message']?.toString() ?? 'Ответ сервера';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(msg)),
      );
      if (res['ok'] == true) {
        _keyController.clear();
        widget.onRefresh();
      }
    }
  }

  void _openUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    final lic = widget.license;
    final isLicensed = lic?.isLicensed ?? false;
    final daysLeft = lic?.daysLeft ?? 0;

    return RefreshIndicator(
      onRefresh: () async => widget.onRefresh(),
      child: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // Карточка лицензии
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 12,
                        height: 12,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isLicensed ? Colors.greenAccent : Colors.redAccent,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        isLicensed
                            ? (lic?.isTrial == true
                                ? '${AppStrings.licenseTrial} · $daysLeft дн.'
                                : '${AppStrings.licenseActive} · $daysLeft дн.')
                            : AppStrings.licenseExpired,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
                  if (!isLicensed) ...[
                    const SizedBox(height: 14),
                    TextField(
                      controller: _keyController,
                      style: const TextStyle(color: Colors.white),
                      decoration: InputDecoration(
                        hintText: AppStrings.licenseKeyHint,
                        hintStyle: TextStyle(color: Colors.grey.shade400),
                        filled: true,
                        fillColor: const Color(0xFF2A364F),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(10),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.amber.shade700,
                          foregroundColor: Colors.black,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        onPressed: _isActivating ? null : _activateKey,
                        child: _isActivating
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                              )
                            : Text(AppStrings.licenseActivate, style: const TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // Кнопка Старт / Стоп мониторинга
          SizedBox(
            width: double.infinity,
            height: 56,
            child: ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: _isMonitoring ? Colors.red.shade700 : const Color(0xFF00C853),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                elevation: 4,
              ),
              icon: Icon(_isMonitoring ? Icons.stop_circle_rounded : Icons.play_circle_filled_rounded, size: 28),
              label: Text(
                _isMonitoring ? AppStrings.radarStop : AppStrings.radarStart,
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              onPressed: _toggleMonitoring,
            ),
          ),

          const SizedBox(height: 12),

          // Карточка AssistiveTouch / Плавающая кнопка
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              leading: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.amber.withValues(alpha: 0.2),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.touch_app, color: Colors.amber, size: 24),
              ),
              title: const Text(
                'Плавающая кнопка (AssistiveTouch)',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              subtitle: const Text(
                'Считывание цены и точек А/Б в 1 касание поверх Яндекс Про',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
              trailing: const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.grey),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(builder: (_) => const AssistiveTouchGuideScreen()),
                );
              },
            ),
          ),

          // Карточка последнего распознанного заказа (если был скан)
          ValueListenableBuilder<ParsedOrder?>(
            valueListenable: LiveActivityService.latestOrderNotifier,
            builder: (context, order, _) {
              if (order == null) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Card(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  color: const Color(0xFF162521),
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              '🎯 Заказ со скриншота',
                              style: TextStyle(fontWeight: FontWeight.bold, color: Colors.amber, fontSize: 14),
                            ),
                            Row(
                              children: [
                                Text(
                                  '${order.price.round()} MDL',
                                  style: const TextStyle(fontWeight: FontWeight.w900, color: Colors.greenAccent, fontSize: 18),
                                ),
                                const SizedBox(width: 8),
                                IconButton(
                                  icon: const Icon(Icons.close_rounded, size: 18, color: Colors.white54),
                                  padding: EdgeInsets.zero,
                                  constraints: const BoxConstraints(),
                                  tooltip: 'Сбросить и вернуться к радару',
                                  onPressed: () => LiveActivityService.clearCurrentOrder(),
                                ),
                              ],
                            ),
                          ],
                        ),
                        if (order.distanceTime.isNotEmpty) ...[
                          const SizedBox(height: 4),
                          Text(
                            '⏱ ${order.distanceTime}',
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Colors.cyanAccent),
                          ),
                        ],
                        const SizedBox(height: 6),
                        Text('📍 Подача: ${order.pointA}', style: const TextStyle(fontSize: 12, color: Colors.white70)),
                        Text('🏁 Куда: ${order.pointB}', style: const TextStyle(fontSize: 12, color: Colors.white70)),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),

          const SizedBox(height: 16),

          // Единый радар спроса (без лишних тарифов)
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
                      Row(
                        children: [
                          Icon(Icons.radar_rounded, color: Colors.amber.shade400, size: 22),
                          const SizedBox(width: 8),
                          const Text(
                            'Радар надбавки',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                        ],
                      ),
                      ValueListenableBuilder<String>(
                        valueListenable: LiveActivityService.currentSurgeNotifier,
                        builder: (context, surge, _) {
                          final isHigh = surge != '+0' && surge.isNotEmpty;
                          return Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: isHigh ? Colors.orange.withValues(alpha: 0.2) : Colors.white10,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(
                                color: isHigh ? Colors.orangeAccent : Colors.white24,
                                width: 1,
                              ),
                            ),
                            child: Text(
                              surge,
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w900,
                                color: isHigh ? Colors.orangeAccent : Colors.white70,
                              ),
                            ),
                          );
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    _isMonitoring
                        ? '🟢 Радар активен · Значение надбавки передаётся в Dynamic Island и обновляется каждые 25 сек.'
                        : '⚪ Радар выключен · Нажмите «Запустить радар», чтобы включить отображение на Dynamic Island.',
                    style: TextStyle(fontSize: 13, color: Colors.grey.shade400),
                  ),
                ],
              ),
            ),
          ),


          const SizedBox(height: 16),

          // Группа и контакты
          Row(
            children: [
              Expanded(
                child: InkWell(
                  onTap: () => _openUrl(widget.config.groupUrl),
                  borderRadius: BorderRadius.circular(14),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1E2638),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.telegram, color: Color(0xFF29B6F6), size: 24),
                        SizedBox(width: 8),
                        Text('Telegram группа', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: InkWell(
                  onTap: () => _openUrl('https://t.me/${widget.config.telegram}'),
                  borderRadius: BorderRadius.circular(14),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1E2638),
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.support_agent_rounded, color: Colors.greenAccent, size: 24),
                        SizedBox(width: 8),
                        Text('Поддержка', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
