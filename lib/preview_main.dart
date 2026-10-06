// Только для предпросмотра экранов в браузере (flutter build web -t lib/preview_main.dart).
// В сборку для iPhone не входит.
import 'package:flutter/material.dart';

import 'screens/main_screen.dart';
import 'ui/ds.dart';

void main() {
  final dark = Uri.base.queryParameters['dark'] == '1';
  runApp(MaterialApp(
    debugShowCheckedModeBanner: false,
    themeMode: dark ? ThemeMode.dark : ThemeMode.light,
    theme: DS.theme(Brightness.light).copyWith(platform: TargetPlatform.iOS),
    darkTheme: DS.theme(Brightness.dark).copyWith(platform: TargetPlatform.iOS),
    home: const MainScreen(),
  ));
}
