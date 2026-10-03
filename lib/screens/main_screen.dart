import 'dart:async';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../l10n/app_strings.dart';
import '../models/app_config.dart';
import '../services/community_service.dart';
import '../services/license_service.dart';
import '../services/traffic_model.dart';
import '../services/yandex_surge_service.dart';
import 'tabs/map_tab.dart';
import 'tabs/profile_tab.dart';
import 'tabs/radar_tab.dart';
import 'tabs/useful_tab.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;
  LicenseStatus? _license;
  AppConfig _config = AppConfig.defaultPlan();
  int _unreadChat = 0;
  Timer? _chatBadgeTimer;

  @override
  void initState() {
    super.initState();
    _loadLanguage();
    _syncData();
    _chatBadgeTimer = Timer.periodic(const Duration(seconds: 25), (_) => _pollChatBadge());
  }

  @override
  void dispose() {
    _chatBadgeTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadLanguage() async {
    final prefs = await SharedPreferences.getInstance();
    final l = prefs.getString('app_lang') ?? 'RU';
    setState(() => AppStrings.lang = l);
  }

  Future<void> _setLanguage(String lang) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('app_lang', lang);
    setState(() => AppStrings.lang = lang);
  }

  Future<void> _syncData() async {
    final lic = await LicenseService.checkLicense();
    final cfg = await LicenseService.fetchAppConfig();

    TrafficModel.updateSharedTraffic(
      weekday: cfg.trafficWeekday,
      weekend: cfg.trafficWeekend,
    );
    YandexSurgeService.updateBases(cfg.surgeBase);

    if (mounted) {
      setState(() {
        _license = lic;
        _config = cfg;
      });
      _pollChatBadge();
    }
  }

  Future<void> _pollChatBadge() async {
    final prefs = await SharedPreferences.getInstance();
    final lastSeen = prefs.getInt('last_seen_chat_id') ?? 0;
    final count = await CommunityService.getUnreadChatCount(lastSeen);
    if (mounted) {
      setState(() => _unreadChat = count);
    }
  }

  void _showNoticeDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E2638),
        title: Row(
          children: [
            const Icon(Icons.notifications_active_rounded, color: Colors.amberAccent),
            const SizedBox(width: 8),
            Text(AppStrings.isRu ? 'Уведомления' : 'Notificări', style: const TextStyle(color: Colors.white)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Версия: ${_config.latestVersionName.isNotEmpty ? _config.latestVersionName : "1.16"}',
              style: const TextStyle(color: Colors.amberAccent, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _config.updateNotes.isNotEmpty
                  ? _config.updateNotes
                  : (AppStrings.isRu
                      ? 'Всё работает в штатном режиме. Все дорожные события и радар тарифов активны.'
                      : 'Totul funcționează normal. Toate alertele și radarul sunt active.'),
              style: const TextStyle(color: Colors.white70, fontSize: 14),
            ),
          ],
        ),
        actions: [
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.amber.shade700),
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121826),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E2638),
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(AppStrings.appName, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 19)),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                  decoration: BoxDecoration(
                    color: Colors.amber.shade700,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: const Text('1.16', style: TextStyle(color: Colors.black, fontSize: 10, fontWeight: FontWeight.bold)),
                ),
              ],
            ),
            Text(
              AppStrings.appSubtitle,
              style: TextStyle(fontSize: 11, color: Colors.grey.shade400),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_rounded, color: Colors.white70),
            tooltip: 'Оповещения',
            onPressed: _showNoticeDialog,
          ),
          TextButton(
            onPressed: () => _setLanguage(AppStrings.lang == 'RU' ? 'RO' : 'RU'),
            child: Text(
              AppStrings.lang,
              style: const TextStyle(color: Colors.amberAccent, fontWeight: FontWeight.bold, fontSize: 15),
            ),
          ),
        ],
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: [
          RadarTab(license: _license, config: _config, onRefresh: _syncData),
          const MapTab(),
          UsefulTab(
            unreadChat: _unreadChat,
            onChatOpened: () => setState(() => _unreadChat = 0),
          ),
          ProfileTab(
            config: _config,
            license: _license,
            onLanguageChanged: _setLanguage,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        backgroundColor: const Color(0xFF1E2638),
        indicatorColor: Colors.amber.shade700.withValues(alpha: 0.3),
        selectedIndex: _currentIndex,
        onDestinationSelected: (idx) {
          setState(() => _currentIndex = idx);
        },
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.radar_rounded, color: Colors.grey),
            selectedIcon: const Icon(Icons.radar_rounded, color: Colors.amberAccent),
            label: AppStrings.navRadar,
          ),
          NavigationDestination(
            icon: const Icon(Icons.map_rounded, color: Colors.grey),
            selectedIcon: const Icon(Icons.map_rounded, color: Colors.amberAccent),
            label: AppStrings.navMap,
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: _unreadChat > 0,
              label: Text(_unreadChat.toString()),
              child: const Icon(Icons.dashboard_rounded, color: Colors.grey),
            ),
            selectedIcon: Badge(
              isLabelVisible: _unreadChat > 0,
              label: Text(_unreadChat.toString()),
              child: const Icon(Icons.dashboard_rounded, color: Colors.amberAccent),
            ),
            label: AppStrings.navUseful,
          ),
          NavigationDestination(
            icon: const Icon(Icons.person_rounded, color: Colors.grey),
            selectedIcon: const Icon(Icons.person_rounded, color: Colors.amberAccent),
            label: AppStrings.navProfile,
          ),
        ],
      ),
    );
  }
}
