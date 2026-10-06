import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'screens/main_screen.dart';
import 'services/live_activity_service.dart';
import 'ui/ds.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LiveActivityService.init();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const TaxiRadarApp());
}

class TaxiRadarApp extends StatelessWidget {
  const TaxiRadarApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Taxi Radar',
      debugShowCheckedModeBanner: false,
      // Светлая и тёмная — как выбрано в iPhone.
      themeMode: ThemeMode.system,
      theme: DS.theme(Brightness.light),
      darkTheme: DS.theme(Brightness.dark),
      home: const MainScreen(),
    );
  }
}
