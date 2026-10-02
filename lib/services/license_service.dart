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
  static const String keyDaysLeft = 'license_days_left';
  static const String keyReferralCode = 'referral_code';

  static Future<LicenseStatus> checkLicense() async {
    final res = await ApiService.post('/api/license/check');
    final prefs = await SharedPreferences.getInstance();

    if (res == null) {
      final days = prefs.getInt(keyDaysLeft) ?? 0;
      final act = prefs.getBool(keyActivated) ?? false;
      return LicenseStatus(
        isLicensed: act && days > 0,
        isTrial: false,
        daysLeft: days,
        message: 'Нет связи с сервером',
        referralCode: prefs.getString(keyReferralCode) ?? '',
      );
    }

    final bool active = res['active'] == true;
    final bool trial = res['trial'] == true;
    final int days = (res['days_left'] as num?)?.toInt() ?? 0;
    final String code = res['referral_code']?.toString() ?? '';
    final bool banned = res['banned'] == true;
    final String msg = res['message']?.toString() ?? '';

    await prefs.setBool(keyActivated, active);
    await prefs.setInt(keyDaysLeft, days);
    if (code.isNotEmpty) await prefs.setString(keyReferralCode, code);

    return LicenseStatus(
      isLicensed: active,
      isTrial: trial,
      daysLeft: days,
      message: msg,
      referralCode: code,
      banned: banned,
    );
  }

  static Future<Map<String, dynamic>> activateKey(String key) async {
    final res = await ApiService.post('/api/license/activate', {'key': key});
    if (res == null) {
      return {'ok': false, 'message': 'Нет связи с сервером'};
    }
    return res;
  }

  static Future<Map<String, dynamic>> applyReferralCode(String code) async {
    final res = await ApiService.post('/api/referral/apply', {'code': code});
    if (res == null) {
      return {'ok': false, 'message': 'Нет связи с сервером'};
    }
    return res;
  }

  static Future<AppConfig> fetchAppConfig() async {
    final res = await ApiService.post('/api/app-config');
    if (res != null && res['ok'] == true) {
      return AppConfig.fromJson(res);
    }
    return AppConfig.defaultPlan();
  }
}
