import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/app_strings.dart';
import '../models/app_models.dart';
import '../platform/livebridge_platform.dart';
import '../widgets/shared_widgets.dart';

class AppPresentationSettingsPage extends StatefulWidget {
  const AppPresentationSettingsPage({super.key});

  @override
  State<AppPresentationSettingsPage> createState() =>
      _AppPresentationSettingsPageState();
}

class _AppPresentationSettingsPageState
    extends State<AppPresentationSettingsPage> {
  bool _isLoading = true;
  bool _busy = false;
  List<InstalledApp> _apps = const [];
  Map<String, AppPresentationOverride> _overrides = {};
  String _q = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Map<String, AppPresentationOverride> _parseOverrides(String raw) {
    final normalized = raw.trim();
    if (normalized.isEmpty) return {};
    final decoded = jsonDecode(normalized);
    if (decoded is! Map) return {};

    final Map<String, AppPresentationOverride> values = {};
    for (final entry in decoded.entries) {
      final packageName = (entry.key as String? ?? '').trim().toLowerCase();
      if (packageName.isEmpty || entry.value is! Map) continue;
      final parsed = AppPresentationOverride.fromJsonEntry(
        Map<String, dynamic>.from(entry.value as Map),
      );
      if (!parsed.isDefault) values[packageName] = parsed;
    }
    return values;
  }

  String _encodeOverrides(Map<String, AppPresentationOverride> values) {
    final payload = <String, dynamic>{};
    for (final packageName in values.keys.toList()..sort()) {
      final value = values[packageName];
      if (value != null && !value.isDefault) {
        payload[packageName] = value.toJsonEntry();
      }
    }
    return jsonEncode(payload);
  }

  Future<void> _load() async {
    setState(() => _isLoading = true);
    try {
      final apps = await LiveBridgePlatform.getInstalledApps();
      final raw = await LiveBridgePlatform.getAppPresentationOverrides();
      final overrides = _parseOverrides(raw);
      if (mounted) {
        setState(() {
          _apps = apps;
          _overrides = overrides;
          _isLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isLoading = false);
        _snack(AppStrings.of(context).appPresentationLoadFailed);
      }
    }
  }

  Future<void> _persistOverrides(
    Map<String, AppPresentationOverride> values,
  ) async {
    final saved = await LiveBridgePlatform.setAppPresentationOverrides(
      _encodeOverrides(values),
    );
    if (!saved && mounted) {
      _snack(AppStrings.of(context).appPresentationSaveFailed);
    }
  }

  Future<void> _openEditor(InstalledApp app) async {
    final String key = app.packageName.toLowerCase();
    final AppPresentationOverride current =
        _overrides[key] ?? const AppPresentationOverride();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(32)),
      ),
      builder: (context) => _AppPresentationEditorSheet(
        app: app,
        initialValue: current,
        onChanged: (AppPresentationOverride updated) {
          final Map<String, AppPresentationOverride> next =
              Map<String, AppPresentationOverride>.from(_overrides);
          updated.isDefault ? next.remove(key) : next[key] = updated;
          if (mounted) {
            setState(() => _overrides = next);
          }
          unawaited(_persistOverrides(next));
        },
      ),
    );
  }

  Future<void> _downloadOverrides() async {
    if (_busy) return;
    HapticFeedback.selectionClick();
    setState(() => _busy = true);
    try {
      final res =
          await LiveBridgePlatform.saveAppPresentationOverridesToDownloads();
      if (!mounted) return;
      _snack(
        res.trim().isEmpty
            ? AppStrings.of(context).appPresentationDownloadFailed
            : AppStrings.of(context).appPresentationSaved,
      );
    } catch (_) {
      if (mounted) {
        _snack(AppStrings.of(context).appPresentationDownloadFailed);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _uploadOverrides() async {
    if (_busy) return;
    HapticFeedback.selectionClick();
    setState(() => _busy = true);
    try {
      final res = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['json'],
        withData: true,
      );
      if (res == null || res.files.isEmpty) return;

      final selected = res.files.first;
      String raw = selected.bytes != null
          ? utf8.decode(selected.bytes!)
          : await File(selected.path!).readAsString();

      final saved = await LiveBridgePlatform.setAppPresentationOverrides(raw);
      if (!mounted) return;
      if (!saved) {
        _snack(AppStrings.of(context).appPresentationSaveFailed);
        return;
      }

      _snack(AppStrings.of(context).appPresentationUploadDone);
      await _load();
    } on PlatformException catch (e) {
      if (mounted) {
        _snack(
          e.code == 'invalid_app_overrides'
              ? AppStrings.of(context).appPresentationInvalidJson
              : AppStrings.of(context).appPresentationUploadFailed,
        );
      }
    } catch (_) {
      if (mounted) {
        _snack(AppStrings.of(context).appPresentationUploadFailed);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _summaryFor(InstalledApp app) {
    final s = AppStrings.of(context);
    final value =
        _overrides[app.packageName.toLowerCase()] ??
        const AppPresentationOverride();
    if (value.isDefault) return s.appPresentationDefaultSummary;

    final textSummary = value.compactTextSource == AppCompactTextSource.title
        ? s.appPresentationTextTitle
        : s.appPresentationTextNotification;
    final iconSummary =
        value.iconSource == AppNotificationIconSource.notification
        ? s.appPresentationIconNotification
        : s.appPresentationIconApp;
    return '$textSummary • $iconSummary';
  }

  void _snack(String value) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(value)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = AppStrings.of(context);
    final colorScheme = Theme.of(context).colorScheme;

    final paddingTop = MediaQuery.paddingOf(context).top;
    final paddingBottom = MediaQuery.paddingOf(context).bottom;

    final filtered = _apps.where((app) {
      if (_q.isEmpty) return true;
      final q = _q.toLowerCase();
      return app.label.toLowerCase().contains(q) ||
          app.packageName.toLowerCase().contains(q);
    }).toList();

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      extendBodyBehindAppBar: true,
      extendBody: true,
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Stack(
              children: [
                ListView.builder(
                  padding: EdgeInsets.only(
                    top: paddingTop + 88,
                    bottom: paddingBottom + 24,
                    left: 16,
                    right: 16,
                  ),
                  physics: const AlwaysScrollableScrollPhysics(
                    parent: BouncingScrollPhysics(),
                  ),
                  itemCount: filtered.length,
                  itemBuilder: (context, index) {
                    final app = filtered[index];
                    final hasCustom = _overrides.containsKey(
                      app.packageName.toLowerCase(),
                    );
                    final bool isDark =
                        colorScheme.brightness == Brightness.dark;
                    final shadowOpacity =
                        colorScheme.brightness == Brightness.dark ? 0.28 : 0.03;

                    return Container(
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(
                        color: isDark
                            ? colorScheme.surfaceContainerLow
                            : Colors.white,
                        borderRadius: BorderRadius.circular(24),
                        boxShadow: [
                          BoxShadow(
                            color: isDark
                                ? colorScheme.shadow.withValues(
                                    alpha: shadowOpacity,
                                  )
                                : Colors.black.withValues(alpha: shadowOpacity),
                            blurRadius: 16,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Material(
                        color: Colors.transparent,
                        child: InkWell(
                          borderRadius: BorderRadius.circular(24),
                          onTap: () => _openEditor(app),
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Row(
                              children: [
                                InstalledAppAvatar(app: app),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        app.label,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 16,
                                        ),
                                      ),
                                      const SizedBox(height: 2),
                                      Text(
                                        app.packageName,
                                        style: TextStyle(
                                          color: isDark
                                              ? colorScheme.onSurfaceVariant
                                              : Colors.grey[500],
                                          fontSize: 12,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        _summaryFor(app),
                                        style: TextStyle(
                                          color: colorScheme.primary.withValues(
                                            alpha: 0.8,
                                          ),
                                          fontSize: 13,
                                          fontWeight: FontWeight.w500,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                if (hasCustom)
                                  Container(
                                    padding: const EdgeInsets.all(8),
                                    decoration: BoxDecoration(
                                      color: colorScheme.primary.withValues(
                                        alpha:
                                            colorScheme.brightness ==
                                                Brightness.dark
                                            ? 0.2
                                            : 0.1,
                                      ),
                                      shape: BoxShape.circle,
                                    ),
                                    child: Icon(
                                      Icons.tune_rounded,
                                      color: colorScheme.primary,
                                      size: 20,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),

                Positioned(
                  top: paddingTop + 12,
                  left: 16,
                  right: 16,
                  child: Container(
                    height: 56,
                    decoration: BoxDecoration(
                      color: colorScheme.brightness == Brightness.dark
                          ? colorScheme.surfaceContainerHigh
                          : Colors.white,
                      borderRadius: BorderRadius.circular(28),
                      boxShadow: [
                        BoxShadow(
                          color: colorScheme.brightness == Brightness.dark
                              ? colorScheme.shadow.withValues(alpha: 0.34)
                              : Colors.black.withValues(alpha: 0.08),
                          blurRadius: 24,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    child: Row(
                      children: [
                        const SizedBox(width: 4),
                        IconButton(
                          icon: const Icon(Icons.arrow_back_rounded),
                          color: colorScheme.brightness == Brightness.dark
                              ? colorScheme.onSurface
                              : Colors.grey[800],
                          onPressed: () => Navigator.maybePop(context),
                        ),
                        Expanded(
                          child: TextField(
                            onChanged: (val) => setState(() => _q = val.trim()),
                            decoration: InputDecoration(
                              hintText: s.searchAppHint,
                              border: InputBorder.none,
                              hintStyle: TextStyle(
                                color: colorScheme.brightness == Brightness.dark
                                    ? colorScheme.onSurfaceVariant.withValues(
                                        alpha: 0.8,
                                      )
                                    : Colors.grey[400],
                                fontSize: 16,
                              ),
                            ),
                            style: const TextStyle(fontSize: 16),
                          ),
                        ),
                        if (_busy)
                          const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 16.0),
                            child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            ),
                          )
                        else
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.download_rounded),
                                color: colorScheme.primary,
                                tooltip: s.downloadSettings,
                                onPressed: _downloadOverrides,
                              ),
                              IconButton(
                                icon: const Icon(Icons.upload_file_rounded),
                                color: colorScheme.primary,
                                tooltip: s.uploadSettings,
                                onPressed: _uploadOverrides,
                              ),
                            ],
                          ),
                        const SizedBox(width: 4),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}

class _AppPresentationEditorSheet extends StatefulWidget {
  const _AppPresentationEditorSheet({
    required this.app,
    required this.initialValue,
    required this.onChanged,
  });
  final InstalledApp app;
  final AppPresentationOverride initialValue;
  final ValueChanged<AppPresentationOverride> onChanged;

  @override
  State<_AppPresentationEditorSheet> createState() =>
      _AppPresentationEditorSheetState();
}

class _AppPresentationEditorSheetState
    extends State<_AppPresentationEditorSheet> {
  late AppCompactTextSource _compactTextSource =
      widget.initialValue.compactTextSource;
  late AppNotificationIconSource _iconSource = widget.initialValue.iconSource;

  AppPresentationOverride _currentValue() {
    return AppPresentationOverride(
      compactTextSource: _compactTextSource,
      iconSource: _iconSource,
    );
  }

  void _emitChanged() {
    widget.onChanged(_currentValue());
  }

  @override
  Widget build(BuildContext context) {
    final s = AppStrings.of(context);

    return SafeArea(
      top: false,
      child: Padding(
        padding: EdgeInsets.only(
          left: 24,
          right: 24,
          top: 8,
          bottom: 24 + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                InstalledAppAvatar(app: widget.app),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.app.label,
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Text(
                        widget.app.packageName,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 32),
            Text(
              s.appPresentationTextSourceLabel,
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
            ),
            const SizedBox(height: 12),
            LiveBridgeToggleSelector<AppCompactTextSource>(
              value: _compactTextSource,
              options: <SelectorOption<AppCompactTextSource>>[
                SelectorOption<AppCompactTextSource>(
                  value: AppCompactTextSource.title,
                  title: s.appPresentationTextTitle,
                ),
                SelectorOption<AppCompactTextSource>(
                  value: AppCompactTextSource.text,
                  title: s.appPresentationTextNotification,
                ),
              ],
              onChanged: (AppCompactTextSource next) {
                if (_compactTextSource == next) return;
                setState(() => _compactTextSource = next);
                _emitChanged();
              },
            ),
            const SizedBox(height: 24),
            Text(
              s.appPresentationIconSourceLabel,
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
            ),
            const SizedBox(height: 12),
            LiveBridgeToggleSelector<AppNotificationIconSource>(
              value: _iconSource,
              options: <SelectorOption<AppNotificationIconSource>>[
                SelectorOption<AppNotificationIconSource>(
                  value: AppNotificationIconSource.notification,
                  title: s.appPresentationIconNotification,
                ),
                SelectorOption<AppNotificationIconSource>(
                  value: AppNotificationIconSource.app,
                  title: s.appPresentationIconApp,
                ),
              ],
              onChanged: (AppNotificationIconSource next) {
                if (_iconSource == next) return;
                setState(() => _iconSource = next);
                _emitChanged();
              },
            ),
            const SizedBox(height: 32),
            Row(
              children: [
                TextButton(
                  onPressed: () {
                    HapticFeedback.selectionClick();
                    setState(() {
                      _compactTextSource = AppCompactTextSource.title;
                      _iconSource = AppNotificationIconSource.notification;
                    });
                    _emitChanged();
                  },
                  child: Text(s.resetToDefault),
                ),
                const Spacer(),
                FilledButton.icon(
                  onPressed: () {
                    HapticFeedback.selectionClick();
                    Navigator.of(context).maybePop();
                  },
                  icon: const Icon(Icons.check_rounded),
                  label: Text(s.save),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
