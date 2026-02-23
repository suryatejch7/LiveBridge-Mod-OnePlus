import 'package:flutter/services.dart';
import '../models/app_models.dart';

class LiveBridgePlatform {
  static const MethodChannel _channel = MethodChannel('livebridge/platform');
  static const Duration _appsTtl = Duration(minutes: 10);
  static List<InstalledApp>? _appsBox;
  static DateTime? _appsTs;

  static Future<bool> _askBool(
    String method, [
    Map<String, dynamic>? args,
  ]) async {
    final bool? res = await _channel.invokeMethod<bool>(method, args);
    return res ?? false;
  }

  static Future<String> _askStr(
    String method, [
    Map<String, dynamic>? args,
  ]) async {
    final String? res = await _channel.invokeMethod<String>(method, args);
    return res ?? '';
  }

  static Future<bool> isNotificationListenerEnabled() =>
      _askBool('isNotificationListenerEnabled');
  static Future<bool> isDeviceBlocked() => _askBool('isDeviceBlocked');
  static Future<bool> setPixelJokeBypassEnabled(bool value) =>
      _askBool('setPixelJokeBypassEnabled', {'value': value});
  static Future<bool> openNotificationListenerSettings() =>
      _askBool('openNotificationListenerSettings');
  static Future<bool> isNotificationPermissionGranted() =>
      _askBool('isNotificationPermissionGranted');
  static Future<bool> requestNotificationPermission() =>
      _askBool('requestNotificationPermission');
  static Future<bool> canPostPromotedNotifications() =>
      _askBool('canPostPromotedNotifications');
  static Future<bool> openPromotedNotificationSettings() =>
      _askBool('openPromotedNotificationSettings');
  static Future<bool> openAppNotificationSettings() =>
      _askBool('openAppNotificationSettings');

  static Future<String> getPackageRules() => _askStr('getPackageRules');
  static Future<bool> setPackageRules(String value) =>
      _askBool('setPackageRules', {'value': value});
  static Future<String> getPackageMode() => _askStr('getPackageMode');
  static Future<bool> setPackageMode(String value) =>
      _askBool('setPackageMode', {'value': value});

  static Future<bool> getOnlyWithProgress() => _askBool('getOnlyWithProgress');
  static Future<bool> setOnlyWithProgress(bool value) =>
      _askBool('setOnlyWithProgress', {'value': value});
  static Future<bool> getConverterEnabled() => _askBool('getConverterEnabled');
  static Future<bool> setConverterEnabled(bool value) =>
      _askBool('setConverterEnabled', {'value': value});
  static Future<bool> getKeepAliveForegroundEnabled() =>
      _askBool('getKeepAliveForegroundEnabled');
  static Future<bool> setKeepAliveForegroundEnabled(bool value) =>
      _askBool('setKeepAliveForegroundEnabled', {'value': value});
  static Future<bool> getAospCuttingEnabled() =>
      _askBool('getAospCuttingEnabled');
  static Future<bool> setAospCuttingEnabled(bool value) =>
      _askBool('setAospCuttingEnabled', {'value': value});
  static Future<bool> getSmartStatusDetectionEnabled() =>
      _askBool('getSmartStatusDetectionEnabled');
  static Future<bool> setSmartStatusDetectionEnabled(bool value) =>
      _askBool('setSmartStatusDetectionEnabled', {'value': value});
  static Future<bool> getSmartNavigationEnabled() =>
      _askBool('getSmartNavigationEnabled');
  static Future<bool> setSmartNavigationEnabled(bool value) =>
      _askBool('setSmartNavigationEnabled', {'value': value});
  static Future<bool> getOtpDetectionEnabled() =>
      _askBool('getOtpDetectionEnabled');
  static Future<bool> setOtpDetectionEnabled(bool value) =>
      _askBool('setOtpDetectionEnabled', {'value': value});
  static Future<bool> getOtpAutoCopyEnabled() =>
      _askBool('getOtpAutoCopyEnabled');
  static Future<bool> setOtpAutoCopyEnabled(bool value) =>
      _askBool('setOtpAutoCopyEnabled', {'value': value});

  static Future<String> getOtpPackageRules() => _askStr('getOtpPackageRules');
  static Future<bool> setOtpPackageRules(String value) =>
      _askBool('setOtpPackageRules', {'value': value});
  static Future<String> getOtpPackageMode() => _askStr('getOtpPackageMode');
  static Future<bool> setOtpPackageMode(String value) =>
      _askBool('setOtpPackageMode', {'value': value});

  static Future<List<InstalledApp>> getInstalledApps({
    bool forceRefresh = false,
  }) async {
    final DateTime ts = DateTime.now();
    if (!forceRefresh &&
        _appsBox != null &&
        _appsTs != null &&
        ts.difference(_appsTs!) <= _appsTtl) {
      return _appsBox!;
    }
    final List<dynamic>? res = await _channel.invokeMethod<List<dynamic>>(
      'getInstalledApps',
    );
    if (res == null) return <InstalledApp>[];

    final List<InstalledApp> apps = res
        .whereType<Map>()
        .map((Map e) {
          final Map<String, dynamic> m = Map<String, dynamic>.from(e);
          final String pkg = (m['packageName'] as String?) ?? '';
          return InstalledApp(
            packageName: pkg,
            label: (m['label'] as String?) ?? pkg,
            icon: m['icon'] is Uint8List ? m['icon'] as Uint8List : null,
          );
        })
        .where((app) => app.packageName.isNotEmpty)
        .toList();

    _appsBox = apps;
    _appsTs = ts;
    return apps;
  }

  static Future<bool> getAppListAccessGranted() =>
      _askBool('getAppListAccessGranted');
  static Future<bool> setAppListAccessGranted(bool value) =>
      _askBool('setAppListAccessGranted', {'value': value});
  static Future<bool> getBackgroundWarningDismissed() =>
      _askBool('getBackgroundWarningDismissed');
  static Future<bool> setBackgroundWarningDismissed(bool value) =>
      _askBool('setBackgroundWarningDismissed', {'value': value});
  static Future<bool> getSamsungWarningDismissed() =>
      _askBool('getSamsungWarningDismissed');
  static Future<bool> setSamsungWarningDismissed(bool value) =>
      _askBool('setSamsungWarningDismissed', {'value': value});
  static Future<bool> hasExpandedSectionsState() =>
      _askBool('hasExpandedSectionsState');
  static Future<String> getExpandedSections() => _askStr('getExpandedSections');
  static Future<bool> setExpandedSections(String value) =>
      _askBool('setExpandedSections', {'value': value});
  static Future<String> getAppPresentationOverrides() =>
      _askStr('getAppPresentationOverrides');
  static Future<bool> setAppPresentationOverrides(String value) =>
      _askBool('setAppPresentationOverrides', {'value': value});
  static Future<String> saveAppPresentationOverridesToDownloads() =>
      _askStr('saveAppPresentationOverridesToDownloads');
  static Future<bool> hasCustomParserDictionary() =>
      _askBool('hasCustomParserDictionary');
  static Future<String> getParserDictionaryJson() =>
      _askStr('getParserDictionaryJson');
  static Future<String> saveParserDictionaryToDownloads() =>
      _askStr('saveParserDictionaryToDownloads');
  static Future<bool> setCustomParserDictionary(String value) =>
      _askBool('setCustomParserDictionary', {'value': value});
  static Future<bool> clearCustomParserDictionary() =>
      _askBool('clearCustomParserDictionary');

  static Future<DeviceInfo> getDeviceInfo() async {
    final Map<dynamic, dynamic>? res = await _channel
        .invokeMethod<Map<dynamic, dynamic>>('getDeviceInfo');
    final Map<String, dynamic> m = res == null
        ? const <String, dynamic>{}
        : Map<String, dynamic>.from(res);
    return DeviceInfo(
      manufacturer: (m['manufacturer'] as String?) ?? '',
      brand: (m['brand'] as String?) ?? '',
      marketName:
          (m['marketName'] as String?) ?? ((m['model'] as String?) ?? ''),
      model: (m['model'] as String?) ?? '',
      rawModel: (m['rawModel'] as String?) ?? '',
      product: (m['product'] as String?) ?? '',
      fingerprint: (m['fingerprint'] as String?) ?? '',
      display: (m['display'] as String?) ?? '',
    );
  }
}
