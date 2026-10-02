import 'package:shared_preferences/shared_preferences.dart';
import '../models/app_config.dart';
import 'api_service.dart';

class LicenseStatus {
  final bool isLicensed;
  final bool isTrial;
  final int daysLeft;
  final String message;
  final String referralCode;
  final bool banned;

  LicenseStatus({
    required this.isLicensed,
    required this.isTrial,
    required this.daysLeft,
    required this.message,
    required this.referralCode,
    this.banned = false,
  });
}

class LicenseService {
  static const String keyActivated = 'is_activated';
  static const String keyExpires = 'license_expires';
  static const String keyTrialUsed = 'trial_already_used';
  static const String keyReferralCode = 'referral_code';

  static int calculateDaysLeft(String? expiresStr) {
    if (expiresStr == null || expiresStr.isEmpty) return 0;
    try {
      final exp = DateTime.parse(expiresStr.take(19)).toUtc();
      final now = DateTime.now().toUtc();
      final diff = exp.difference(now).inMilliseconds;
      if (diff <= 0) return 0;
      return (diff / (1000 * 60 * 60 * 24)).ceil();
    } catch (_) {
      return 0;
    }
  }

  static Future<LicenseStatus> checkLicense() async {
    final prefs = await SharedPreferences.getInstance();
    final trialUsed = prefs.getBool(keyTrialUsed) ?? false;
    String refCode = prefs.getString(keyReferralCode) ?? '';

    Map<String, dynamic>? res;
    bool isTrialMode = false;

    if (!trialUsed) {
      // Первый запуск — запрашиваем 7 дней бесплатного доступа
      res = await ApiService.post('/api/request-trial');
      if (res != null) {
        await prefs.setBool(keyTrialUsed, true);
        isTrialMode = true;
      }
    } else {
      // Повторный запуск — проверяем статус лицензии
      res = await ApiService.post('/api/check-license');
    }

    // Если нет ответа сервера (офлайн) — берем сохраненное состояние
    if (res == null) {
      final expStr = prefs.getString(keyExpires);
      final days = calculateDaysLeft(expStr);
      final act = prefs.getBool(keyActivated) ?? false;
      return LicenseStatus(
        isLicensed: act && days > 0,
        isTrial: false,
        daysLeft: days,
        message: 'Нет связи с сервером',
        referralCode: refCode,
      );
    }

    final bool valid = res['valid'] == true;
    final bool banned = res['is_banned'] == true;
    final String msg = res['message']?.toString() ?? '';
    final String expiresStr = res['expires']?.toString() ?? prefs.getString(keyExpires) ?? '';

    final int days = calculateDaysLeft(expiresStr);
    await prefs.setBool(keyActivated, valid && days > 0);
    if (expiresStr.isNotEmpty) {
      await prefs.setString(keyExpires, expiresStr);
    }

    // Загружаем актуальный реферальный код
    try {
      final refRes = await ApiService.post('/api/referral/me');
      if (refRes != null && refRes['ok'] == true) {
        refCode = refRes['code']?.toString() ?? refCode;
        if (refCode.isNotEmpty) {
          await prefs.setString(keyReferralCode, refCode);
        }
      }
    } catch (_) {}

    return LicenseStatus(
      isLicensed: valid && days > 0,
      isTrial: isTrialMode || (res['type']?.toString().contains('Пробный') ?? false),
      daysLeft: days,
      message: msg,
      referralCode: refCode,
      banned: banned,
    );
  }

  static Future<Map<String, dynamic>> activateKey(String key) async {
    final cleanKey = key.trim().toUpperCase();
    final res = await ApiService.post('/api/activate-device', {'key': cleanKey});
    if (res == null) {
      return {'ok': false, 'message': 'Нет связи с сервером'};
    }

    final bool valid = res['valid'] == true;
    final String msg = res['message']?.toString() ?? (valid ? 'Доступ открыт' : 'Неверный ключ');

    if (valid) {
      final prefs = await SharedPreferences.getInstance();
      final expiresStr = res['expires']?.toString() ?? '';
      await prefs.setBool(keyActivated, true);
      if (expiresStr.isNotEmpty) {
        await prefs.setString(keyExpires, expiresStr);
      }
    }

    return {
      'ok': valid,
      'message': msg,
    };
  }

  static Future<Map<String, dynamic>> applyReferralCode(String code) async {
    final cleanCode = code.trim().toUpperCase();
    final res = await ApiService.post('/api/referral/apply', {'code': cleanCode});
    if (res == null) {
      return {'ok': false, 'message': 'Нет связи с сервером'};
    }
    return res;
  }

  static Future<AppConfig> fetchAppConfig() async {
    final res = await ApiService.post('/api/app-config');
    if (res != null) {
      return AppConfig.fromJson(res);
    }
    return AppConfig.defaultPlan();
  }
}

extension StringTake on String {
  String take(int n) => length <= n ? this : substring(0, n);
}
