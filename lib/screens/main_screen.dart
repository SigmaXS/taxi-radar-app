import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_strings.dart';
import '../models/app_config.dart';
import '../services/community_service.dart';
import '../services/license_service.dart';
import '../services/traffic_model.dart';
import '../services/yandex_surge_service.dart';
import '../ui/ds.dart';
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

    TrafficModel.updateSharedTraffic(weekday: cfg.trafficWeekday, weekend: cfg.trafficWeekend);
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
    if (mounted) setState(() => _unreadChat = count);
  }

  BottomNavigationBarItem _item(IconData icon, IconData active, String label, {int badge = 0}) {
    Widget withBadge(IconData i) => badge <= 0
        ? Icon(i)
        : Badge(
            label: Text(badge > 99 ? '99+' : '$badge'),
            backgroundColor: DS.c(context, DS.danger),
            child: Icon(i),
          );
    return BottomNavigationBarItem(icon: withBadge(icon), activeIcon: withBadge(active), label: label);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: DS.bg(context),
      extendBody: true,
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
            onRefresh: _syncData,
          ),
        ],
      ),
      // Таб-бар iOS: полупрозрачный, с размытием, значки SF-стиля.
      bottomNavigationBar: CupertinoTabBar(
        currentIndex: _currentIndex,
        activeColor: DS.label(context),
        inactiveColor: DS.label3(context),
        backgroundColor: DS.card(context).withValues(alpha: 0.86),
        border: Border(top: BorderSide(color: DS.separator(context), width: 0.33)),
        onTap: (i) {
          DS.tap();
          setState(() => _currentIndex = i);
        },
        items: [
          _item(CupertinoIcons.dot_radiowaves_left_right, CupertinoIcons.dot_radiowaves_left_right, AppStrings.navRadar),
          _item(CupertinoIcons.map, CupertinoIcons.map_fill, AppStrings.navMap),
          _item(CupertinoIcons.square_grid_2x2, CupertinoIcons.square_grid_2x2_fill, AppStrings.navUseful,
              badge: _unreadChat),
          _item(CupertinoIcons.person_crop_circle, CupertinoIcons.person_crop_circle_fill, AppStrings.navProfile),
        ],
      ),
    );
  }
}
