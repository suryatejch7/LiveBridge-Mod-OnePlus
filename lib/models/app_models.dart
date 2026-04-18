import 'dart:typed_data';

class InstalledApp {
  const InstalledApp({
    required this.packageName,
    required this.label,
    this.icon,
    this.isSystem = false,
  });

  final String packageName;
  final String label;
  final Uint8List? icon;
  final bool isSystem;
}

class DeviceInfo {
  const DeviceInfo({
    required this.manufacturer,
    required this.brand,
    required this.marketName,
    required this.model,
    this.rawModel = '',
    this.product = '',
    this.fingerprint = '',
    this.display = '',
  });

  final String manufacturer;
  final String brand;
  final String marketName;
  final String model;
  final String rawModel;
  final String product;
  final String fingerprint;
  final String display;

  bool get isPixel {
    final String all = '$manufacturer $brand $marketName $model'.toLowerCase();
    return all.contains('google') || all.contains('pixel');
  }

  bool get isSamsung {
    final String all = '$manufacturer $brand'.toLowerCase();
    return all.contains('samsung');
  }

  bool get isAospDevice {
    final String all =
        '$manufacturer $brand $marketName $model $rawModel $product '
                '$fingerprint $display'
            .toLowerCase();
    const List<String> customRomMarkers = <String>[
      'lineage',
      'evolution',
      'evox',
      'crdroid',
      'pixelos',
      'arrowos',
      'risingos',
      'yaap',
      'derpfest',
      'aosp',
    ];
    return isPixel ||
        all.contains('nothing') ||
        all.contains('motorola') ||
        customRomMarkers.any(all.contains);
  }

  bool get shouldHideLiveUpdatesPromotion => isSamsung || isAospDevice;

  String get label {
    if (marketName.isNotEmpty) return marketName;
    if (model.isNotEmpty) return model;
    if (brand.isNotEmpty) return brand;
    if (manufacturer.isNotEmpty) return manufacturer;
    return 'device';
  }
}

enum PackageMode { all, include, exclude }

extension PackageModeId on PackageMode {
  String get id {
    switch (this) {
      case PackageMode.all:
        return 'all';
      case PackageMode.include:
        return 'include';
      case PackageMode.exclude:
        return 'exclude';
    }
  }

  static PackageMode from(String value) {
    switch (value) {
      case 'include':
        return PackageMode.include;
      case 'exclude':
        return PackageMode.exclude;
      default:
        return PackageMode.all;
    }
  }
}

enum NotificationDedupMode { otpStatus, otpOnly }

extension NotificationDedupModeId on NotificationDedupMode {
  String get id {
    switch (this) {
      case NotificationDedupMode.otpStatus:
        return 'otp_status';
      case NotificationDedupMode.otpOnly:
        return 'otp_only';
    }
  }

  static NotificationDedupMode from(String? value) {
    switch (value) {
      case 'otp_only':
        return NotificationDedupMode.otpOnly;
      default:
        return NotificationDedupMode.otpStatus;
    }
  }
}

enum AppCompactTextSource { title, text }

extension AppCompactTextSourceId on AppCompactTextSource {
  String get id {
    switch (this) {
      case AppCompactTextSource.title:
        return 'title';
      case AppCompactTextSource.text:
        return 'text';
    }
  }

  static AppCompactTextSource from(String? value) {
    switch (value) {
      case 'text':
        return AppCompactTextSource.text;
      default:
        return AppCompactTextSource.title;
    }
  }
}

enum AppNotificationIconSource { notification, app }

extension AppNotificationIconSourceId on AppNotificationIconSource {
  String get id {
    switch (this) {
      case AppNotificationIconSource.notification:
        return 'notification';
      case AppNotificationIconSource.app:
        return 'app';
    }
  }

  static AppNotificationIconSource from(String? value) {
    switch (value) {
      case 'app':
        return AppNotificationIconSource.app;
      default:
        return AppNotificationIconSource.notification;
    }
  }
}

class AppPresentationOverride {
  const AppPresentationOverride({
    this.compactTextSource = AppCompactTextSource.title,
    this.iconSource = AppNotificationIconSource.notification,
    this.liveDurationTimeoutMs = -1,
  });

  final AppCompactTextSource compactTextSource;
  final AppNotificationIconSource iconSource;
  final int liveDurationTimeoutMs;

  bool get isDefault =>
      compactTextSource == AppCompactTextSource.title &&
      iconSource == AppNotificationIconSource.notification &&
      (liveDurationTimeoutMs == -1 || liveDurationTimeoutMs == 0);

  Map<String, dynamic> toJsonEntry() {
    return <String, dynamic>{
      'compact_text': compactTextSource.id,
      'icon_source': iconSource.id,
      if (liveDurationTimeoutMs != -1)
        'live_duration_timeout_ms': liveDurationTimeoutMs,
    };
  }

  static AppPresentationOverride fromJsonEntry(Map<String, dynamic> json) {
    return AppPresentationOverride(
      compactTextSource: AppCompactTextSourceId.from(
        json['compact_text'] as String?,
      ),
      iconSource: AppNotificationIconSourceId.from(
        json['icon_source'] as String?,
      ),
      liveDurationTimeoutMs: (json['live_duration_timeout_ms'] as num?)?.toInt() ?? -1,
    );
  }
}
