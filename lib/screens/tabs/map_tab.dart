import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_strings.dart';
import '../../models/app_config.dart';
import '../../models/report.dart';
import '../../services/api_service.dart';
import '../../services/community_service.dart';
import '../../services/radar_alerts.dart';
import '../../services/road_alerts.dart';
import '../../services/yandex_surge_service.dart';
import '../../ui/ds.dart';

/// Карта — как на Android: подложка Яндекса (ночная), вращение, поиск адреса,
/// флажок спроса, метки водителей и «Ваши точки», слои под шестерёнкой,
/// колокольчик предупреждений в дороге.
class MapTab extends StatefulWidget {
  final AppConfig config;
  const MapTab({super.key, required this.config});

  @override
  State<MapTab> createState() => _MapTabState();
}

class _MapTabState extends State<MapTab> {
  final _map = MapController();
  final _search = TextEditingController();
  static const _chisinau = LatLng(47.0245, 28.8353);
  StreamSubscription<MapEvent>? _events;

  LatLng? _me;
  LatLng? _flag;
  String _flagLabel = '';
  String _flagValue = '';
  bool _flagHot = false;
  bool _probing = false;
  LatLng? _pending;
  DateTime _lastProbe = DateTime.fromMillisecondsSinceEpoch(0);

  List<ReportItem> _reports = [];
  List<PlaceItem> _places = [];
  double _rotation = 0;

  // Слои и режимы (запоминаются).
  bool _showRoad = true, _showAddr = true, _showPlaces = true;
  bool? _night; // null — как в системе
  bool _roadAlerts = false;

  @override
  void initState() {
    super.initState();
    _loadPrefs();
    _locate(move: true);
    _events = _map.mapEventStream.listen((e) {
      if (e.camera.rotation != _rotation) setState(() => _rotation = e.camera.rotation);
    });
  }

  @override
  void dispose() {
    _events?.cancel();
    super.dispose();
  }

  Future<void> _loadPrefs() async {
    final p = await SharedPreferences.getInstance();
    final alerts = await RoadAlerts.enabled();
    if (!mounted) return;
    setState(() {
      _showRoad = p.getBool('layer_road') ?? true;
      _showAddr = p.getBool('layer_addr') ?? true;
      _showPlaces = p.getBool('layer_places') ?? true;
      final n = p.getInt('map_night') ?? -1;
      _night = n < 0 ? null : n == 1;
      _roadAlerts = alerts;
    });
  }

  Future<void> _savePrefs() async {
    final p = await SharedPreferences.getInstance();
    await p.setBool('layer_road', _showRoad);
    await p.setBool('layer_addr', _showAddr);
    await p.setBool('layer_places', _showPlaces);
    await p.setInt('map_night', _night == null ? -1 : (_night! ? 1 : 0));
  }

  Future<void> _locate({bool move = false}) async {
    try {
      var perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) perm = await Geolocator.requestPermission();
      if (perm == LocationPermission.whileInUse || perm == LocationPermission.always) {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 6)),
        );
        if (!mounted) return;
        setState(() => _me = LatLng(pos.latitude, pos.longitude));
        if (move) {
          _map.move(_me!, 15);
          _probe(_me!);
        }
      }
    } catch (_) {}
    _loadLayers();
  }

  Future<void> _loadLayers() async {
    final c = _me ?? _chisinau;
    final results = await Future.wait([
      CommunityService.getReports(c.latitude, c.longitude),
      CommunityService.getPlaces(c.latitude, c.longitude),
    ]);
    if (!mounted) return;
    setState(() {
      _reports = results[0] as List<ReportItem>;
      _places = results[1] as List<PlaceItem>;
    });
  }

  // ---------- флажок спроса: не чаще раза в секунду, всегда последняя точка ----------

  void _probe(LatLng p) {
    setState(() {
      _flag = p;
      _flagValue = '…';
      _flagLabel = '';
      _flagHot = false;
    });
    _pending = p;
    if (!_probing) _runProbe();
  }

  Future<void> _runProbe() async {
    final p = _pending;
    if (p == null) return;
    _pending = null;
    _probing = true;
    final wait = 1000 - DateTime.now().difference(_lastProbe).inMilliseconds;
    if (wait > 0) await Future.delayed(Duration(milliseconds: wait));
    _lastProbe = DateTime.now();
    final s = await YandexSurgeService.getSurgeAll(p.longitude, p.latitude);
    final settings = await RadarAlertSettings.load();
    _probing = false;
    if (!mounted) return;
    if (_pending != null) {
      _runProbe();
      return;
    }
    final value = s == null
        ? null
        : switch (settings.tariff) {
            'comfort' => s.comfort,
            'comfortplus' => s.comfortPlus,
            _ => s.econom,
          };
    setState(() {
      _flagLabel = _tariffLetter(settings.tariff);
      _flagValue = value == null ? '?' : (value > 0 ? '+$value' : '0');
      _flagHot = (value ?? 0) > 0;
    });
  }

  String _tariffLetter(String key) => switch (key) {
        'comfort' => AppStrings.t('К', 'C'),
        'comfortplus' => AppStrings.t('К+', 'C+'),
        _ => AppStrings.t('Э', 'E'),
      };

  // ---------- поиск адреса: варианты рядом с водителем ----------

  Future<void> _searchAddress(String q) async {
    q = q.trim();
    if (q.length < 3) return;
    FocusScope.of(context).unfocus();
    final me = _me ?? _chisinau;
    var list = await CommunityService.searchAddress(q, me.latitude, me.longitude);
    if (list == null) {
      final one = await ApiService.geocodeAddress(q);
      list = one == null ? [] : [FoundAddress(name: q, desc: '', lat: one['lat']!, lon: one['lon']!, km: 0)];
    }
    if (!mounted) return;
    if (list.isEmpty) {
      dsToast(context, AppStrings.t('Адрес не нашёлся', 'Adresa nu a fost găsită'));
    } else if (list.length == 1) {
      _goTo(list.first);
    } else {
      final found = list;
      showCupertinoModalPopup(
        context: context,
        builder: (ctx) => CupertinoActionSheet(
          title: Text(AppStrings.t('Какой адрес?', 'Care adresă?')),
          actions: [
            for (final f in found)
              CupertinoActionSheetAction(
                onPressed: () {
                  Navigator.pop(ctx);
                  _goTo(f);
                },
                child: Column(
                  children: [
                    Text(f.name, textAlign: TextAlign.center),
                    Text(
                      [f.desc, if (f.km > 0) AppStrings.t('${f.km.toStringAsFixed(1)} км от вас', 'la ${f.km.toStringAsFixed(1)} km')]
                          .where((s) => s.isNotEmpty)
                          .join(' · '),
                      style: DS.footnote.copyWith(color: DS.label2(ctx)),
                    ),
                  ],
                ),
              ),
          ],
          cancelButton: CupertinoActionSheetAction(
            onPressed: () => Navigator.pop(ctx),
            child: Text(AppStrings.t('Отмена', 'Anulează')),
          ),
        ),
      );
    }
  }

  void _goTo(FoundAddress f) {
    final p = LatLng(f.lat, f.lon);
    _map.move(p, 16);
    _probe(p);
  }

  // ---------- метки и «Ваши точки» ----------

  void _addAt(LatLng point) {
    DS.impact();
    final roadFirst = RoadReports.types.where((t) => t.road).take(4).toList();
    final rest = RoadReports.types.where((t) => !roadFirst.contains(t)).toList();
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text(AppStrings.t('Что отметить здесь?', 'Ce marcați aici?')),
        message: Text(AppStrings.t('Метку увидят все водители рядом', 'Marcajul îl văd toți șoferii din apropiere')),
        actions: [
          ...roadFirst.map((t) => _typeAction(ctx, t.icon, t.color, t.label, () => _addReport(t, point))),
          _typeAction(ctx, CupertinoIcons.star_fill, CupertinoColors.systemPink,
              AppStrings.t('Ваша точка (еда, мойка, заправка)', 'Punctul dvs. (mâncare, spălătorie, benzinărie)'),
              () => _addPlaceType(point)),
          ...rest.map((t) => _typeAction(ctx, t.icon, t.color, t.label, () => _addReport(t, point))),
        ],
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.pop(ctx),
          child: Text(AppStrings.t('Отмена', 'Anulează')),
        ),
      ),
    );
  }

  Widget _typeAction(BuildContext ctx, IconData icon, Color color, String label, VoidCallback onTap) =>
      CupertinoActionSheetAction(
        onPressed: () {
          Navigator.pop(ctx);
          onTap();
        },
        child: Row(
          children: [
            DSIcon(icon, color, size: 26),
            const SizedBox(width: DS.s12),
            Expanded(child: Text(label, style: DS.body.copyWith(color: DS.label(ctx)))),
          ],
        ),
      );

  Future<void> _addReport(ReportType type, LatLng p) async {
    final res = await CommunityService.addReport(type.key, p.latitude, p.longitude);
    if (!mounted) return;
    dsToast(
      context,
      res['ok'] == true
          ? AppStrings.t('Отмечено: ${type.label} — увидят все водители', 'Marcat: ${type.label} — îl văd toți șoferii')
          : (res['message']?.toString() ?? AppStrings.t('Ошибка', 'Eroare')),
    );
    _loadLayers();
  }

  void _addPlaceType(LatLng p) {
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text(AppStrings.t('Ваша точка', 'Punctul dvs.')),
        actions: PlaceType.types.map((t) => _typeAction(ctx, t.icon, t.color, t.label, () => _addPlace(t, p))).toList(),
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.pop(ctx),
          child: Text(AppStrings.t('Отмена', 'Anulează')),
        ),
      ),
    );
  }

  void _addPlace(PlaceType type, LatLng p) {
    final name = TextEditingController();
    final note = TextEditingController();
    showCupertinoDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(type.label),
        content: Column(
          children: [
            const SizedBox(height: DS.s12),
            CupertinoTextField(controller: name, autofocus: true, placeholder: AppStrings.t('Название', 'Denumire'), maxLength: 60),
            const SizedBox(height: DS.s8),
            CupertinoTextField(controller: note, placeholder: AppStrings.t('Комментарий (по желанию)', 'Comentariu (opțional)'), maxLength: 200),
          ],
        ),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: Text(AppStrings.t('Отмена', 'Anulează'))),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              Navigator.pop(ctx);
              final error = await CommunityService.addPlace(type.key, name.text.trim(), note.text.trim(), p.latitude, p.longitude);
              if (!mounted) return;
              dsToast(context, error ?? AppStrings.t('Точка добавлена — её видят все водители', 'Punctul a fost adăugat'));
              _loadLayers();
            },
            child: Text(AppStrings.t('Добавить', 'Adaugă')),
          ),
        ],
      ),
    );
  }

  void _openReport(ReportItem r) {
    final type = RoadReports.byKey(r.type);
    if (type == null) return;
    final ago = r.createdMs > 0 ? DateTime.now().difference(DateTime.fromMillisecondsSinceEpoch(r.createdMs)).inMinutes : null;
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text(type.label),
        message: Text([
          if (ago != null) AppStrings.t('отмечено $ago мин назад', 'marcat acum $ago min'),
          AppStrings.t('Ещё здесь?', 'Mai este aici?'),
        ].join(' · ')),
        actions: [
          CupertinoActionSheetAction(
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.voteReport(r.id, true);
              _loadLayers();
            },
            child: Text(AppStrings.mapStillHere),
          ),
          CupertinoActionSheetAction(
            isDestructiveAction: true,
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.voteReport(r.id, false);
              _loadLayers();
            },
            child: Text(AppStrings.mapNotHere),
          ),
        ],
        cancelButton: CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: Text(AppStrings.t('Закрыть', 'Închide'))),
      ),
    );
  }

  void _openPlace(PlaceItem p) {
    final type = PlaceType.byKey(p.type);
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text(p.name),
        message: Text([
          type?.label ?? '',
          if (p.note.isNotEmpty) p.note,
          AppStrings.t('советуют ${p.up} · не советуют ${p.down}', 'recomandă ${p.up} · nu recomandă ${p.down}'),
        ].where((s) => s.isNotEmpty).join('\n')),
        actions: [
          CupertinoActionSheetAction(
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.votePlace(p.id, p.vote == 1 ? 0 : 1);
              _loadLayers();
            },
            child: Text(p.vote == 1 ? AppStrings.t('Убрать «советую»', 'Retrage «recomand»') : AppStrings.t('Советую', 'Recomand')),
          ),
          CupertinoActionSheetAction(
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.votePlace(p.id, p.vote == -1 ? 0 : -1);
              _loadLayers();
            },
            child: Text(p.vote == -1 ? AppStrings.t('Убрать «не советую»', 'Retrage «nu recomand»') : AppStrings.t('Не советую', 'Nu recomand')),
          ),
          if (p.mine)
            CupertinoActionSheetAction(
              isDestructiveAction: true,
              onPressed: () async {
                Navigator.pop(ctx);
                await CommunityService.deletePlace(p.id);
                _loadLayers();
              },
              child: Text(AppStrings.t('Удалить мою точку', 'Șterge punctul meu')),
            ),
        ],
        cancelButton: CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: Text(AppStrings.t('Закрыть', 'Închide'))),
      ),
    );
  }

  // ---------- слои (шестерёнка) и колокольчик ----------

  void _openLayers() {
    DS.tap();
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheet) {
          void set(void Function() f) {
            setState(f);
            setSheet(() {});
            _savePrefs();
          }

          final nightNow = _night ?? (MediaQuery.platformBrightnessOf(context) == Brightness.dark);
          return Container(
            decoration: BoxDecoration(
              color: DS.bg(ctx),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            ),
            child: SafeArea(
              top: false,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(height: DS.s8),
                  Container(width: 36, height: 5, decoration: BoxDecoration(color: DS.label3(ctx), borderRadius: BorderRadius.circular(3))),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(DS.gutter, DS.s16, DS.gutter, 0),
                    child: Row(
                      children: [
                        Text(AppStrings.t('Слои карты', 'Straturile hărții'), style: DS.title3.copyWith(color: DS.label(ctx))),
                      ],
                    ),
                  ),
                  DSSection(
                    footer: AppStrings.t(
                      'Нажмите на карту — флажок покажет надбавку. Зажмите палец — поставить метку или «Вашу точку».',
                      'Apăsați pe hartă — stegulețul arată adaosul. Țineți apăsat — puneți un marcaj sau «Punctul dvs.».',
                    ),
                    children: [
                      DSSwitchRow(
                        icon: const DSIcon(CupertinoIcons.shield_lefthalf_fill, Color(0xFF1565C0)),
                        title: AppStrings.t('Дорога', 'Drum'),
                        subtitle: AppStrings.t('Полиция, радары, ДТП', 'Poliție, radare, accidente'),
                        value: _showRoad,
                        onChanged: (v) => set(() => _showRoad = v),
                      ),
                      DSSwitchRow(
                        icon: const DSIcon(CupertinoIcons.house_fill, CupertinoColors.systemGrey),
                        title: AppStrings.t('Адреса', 'Adrese'),
                        subtitle: AppStrings.t('Не выходят, сложная подача', 'Nu ies, acces dificil'),
                        value: _showAddr,
                        onChanged: (v) => set(() => _showAddr = v),
                      ),
                      DSSwitchRow(
                        icon: const DSIcon(CupertinoIcons.star_fill, CupertinoColors.systemPink),
                        title: AppStrings.t('Ваши точки', 'Punctele dvs.'),
                        subtitle: AppStrings.t('Еда, мойка, заправка', 'Mâncare, spălătorie, benzinărie'),
                        value: _showPlaces,
                        onChanged: (v) => set(() => _showPlaces = v),
                      ),
                      DSSwitchRow(
                        icon: const DSIcon(CupertinoIcons.moon_fill, CupertinoColors.systemIndigo),
                        title: AppStrings.t('Ночная карта', 'Hartă de noapte'),
                        value: nightNow,
                        onChanged: (v) => set(() => _night = v),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  void _toggleAlerts() {
    if (_roadAlerts) {
      RoadAlerts.setEnabled(false);
      setState(() => _roadAlerts = false);
      DS.tap();
      return;
    }
    showCupertinoDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(AppStrings.t('Предупреждения в дороге', 'Avertizări pe drum')),
        content: Padding(
          padding: const EdgeInsets.only(top: DS.s8),
          child: Text(AppStrings.t(
            'Как в Waze: водители отмечают радары, полицию и опасные места. Когда вы едете к такой метке, '
                'Taxi Radar предупредит за 400 м и ещё раз за 100 м. Едете в другую сторону — молчит.\n\n'
                'Работает, пока включён радар: iPhone чаще сверяет место, батарея садится немного быстрее.',
            'Ca în Waze: șoferii marchează radare, poliție și locuri periculoase. Când mergeți spre un marcaj, '
                'Taxi Radar vă avertizează la 400 m și încă o dată la 100 m. Dacă mergeți în altă direcție — tace.\n\n'
                'Funcționează cât radarul e pornit: iPhone verifică locul mai des, bateria se descarcă puțin mai repede.',
          )),
        ),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: Text(AppStrings.t('Не сейчас', 'Nu acum'))),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () {
              Navigator.pop(ctx);
              RoadAlerts.setEnabled(true);
              RadarAlerts.requestPermission();
              setState(() => _roadAlerts = true);
              DS.success_();
            },
            child: Text(AppStrings.t('Включить', 'Pornește')),
          ),
        ],
      ),
    );
  }

  // ---------- карта ----------

  String get _tiles {
    final key = Uri.encodeComponent(widget.config.tilesApiKey);
    final lang = AppStrings.isRu ? 'ru_RU' : 'ro_MD';
    return 'https://tiles.api-maps.yandex.ru/v1/tiles/?apikey=$key&lang=$lang&l=map&maptype=driving'
        '&projection=web_mercator&scale=2&x={x}&y={y}&z={z}';
  }

  /// Ночная подложка как на Android: инверсия яркости + поворот оттенка обратно.
  static const _nightMatrix = <double>[
    0.4707, -1.1726, -0.1181, 0, 209.1, //
    -0.3493, -0.3526, -0.1181, 0, 209.1,
    -0.3749, -1.2584, 0.7533, 0, 224.4,
    0, 0, 0, 1, 0,
  ];

  @override
  Widget build(BuildContext context) {
    final hasTiles = widget.config.tilesApiKey.isNotEmpty;
    final night = _night ?? (MediaQuery.platformBrightnessOf(context) == Brightness.dark);
    final top = MediaQuery.paddingOf(context).top;
    final bottom = MediaQuery.paddingOf(context).bottom + 58; // над таб-баром
    return Stack(
      children: [
        FlutterMap(
          mapController: _map,
          options: MapOptions(
            initialCenter: _chisinau,
            initialZoom: 13,
            minZoom: 9,
            maxZoom: 19,
            backgroundColor: night ? const Color(0xFF15161A) : const Color(0xFFF2F2F2),
            interactionOptions: const InteractionOptions(flags: InteractiveFlag.all),
            onTap: (_, p) {
              FocusScope.of(context).unfocus();
              _probe(p);
            },
            onLongPress: (_, p) => _addAt(p),
          ),
          children: [
            if (hasTiles)
              TileLayer(
                urlTemplate: _tiles,
                userAgentPackageName: 'md.sigmars.taxiradar',
                tileBuilder: night
                    ? (context, tile, _) => ColorFiltered(colorFilter: const ColorFilter.matrix(_nightMatrix), child: tile)
                    : null,
              ),
            MarkerLayer(
              rotate: true,
              markers: [
                if (_showPlaces)
                  for (final p in _places)
                    Marker(
                      point: LatLng(p.lat, p.lon),
                      width: 34,
                      height: 34,
                      child: GestureDetector(
                        onTap: () => _openPlace(p),
                        child: _Pin(icon: PlaceType.byKey(p.type)?.icon ?? CupertinoIcons.star_fill, color: PlaceType.byKey(p.type)?.color ?? CupertinoColors.systemPink),
                      ),
                    ),
                for (final r in _reports)
                  if (RoadReports.byKey(r.type) case final type? when (type.road ? _showRoad : _showAddr))
                    Marker(
                      point: LatLng(r.lat, r.lon),
                      width: 34,
                      height: 34,
                      child: GestureDetector(onTap: () => _openReport(r), child: _Pin(icon: type.icon, color: type.color)),
                    ),
                if (_me != null)
                  Marker(
                    point: _me!,
                    width: 22,
                    height: 22,
                    child: Container(
                      decoration: BoxDecoration(
                        color: CupertinoColors.systemBlue,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                      ),
                    ),
                  ),
                if (_flag != null)
                  Marker(
                    point: _flag!,
                    width: 120,
                    height: 64,
                    alignment: Alignment.topCenter,
                    child: _SurgeFlag(label: _flagLabel, value: _flagValue, hot: _flagHot),
                  ),
              ],
            ),
          ],
        ),
        if (!hasTiles)
          Center(
            child: Padding(
              padding: const EdgeInsets.all(DS.s32),
              child: Text(
                AppStrings.t('Карта загрузится, когда появится связь с сервером', 'Harta se va încărca când apare conexiunea'),
                textAlign: TextAlign.center,
                style: DS.subhead.copyWith(color: DS.label2(context)),
              ),
            ),
          ),
        // Поиск + шестерёнка
        Positioned(
          top: top + DS.s8,
          left: DS.s12,
          right: DS.s12,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DSGlass(
                radius: BorderRadius.circular(DS.rCard),
                child: Row(
                  children: [
                    const SizedBox(width: DS.s12),
                    Icon(CupertinoIcons.search, size: 18, color: DS.label2(context)),
                    Expanded(
                      child: CupertinoTextField(
                        controller: _search,
                        placeholder: AppStrings.t('Поиск адреса — спрос там', 'Căutare adresă — cererea acolo'),
                        textInputAction: TextInputAction.search,
                        onSubmitted: _searchAddress,
                        style: DS.callout.copyWith(color: DS.label(context)),
                        placeholderStyle: DS.callout.copyWith(color: DS.label3(context)),
                        padding: const EdgeInsets.symmetric(horizontal: DS.s8, vertical: 13),
                        decoration: null,
                        clearButtonMode: OverlayVisibilityMode.editing,
                      ),
                    ),
                    CupertinoButton(
                      padding: const EdgeInsets.symmetric(horizontal: DS.s12),
                      onPressed: _openLayers,
                      child: Icon(CupertinoIcons.gear_alt_fill, size: 24, color: DS.label(context)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: DS.s8),
              Row(
                children: [
                  GestureDetector(
                    onTap: _toggleAlerts,
                    child: DSGlass(
                      radius: BorderRadius.circular(100),
                      padding: const EdgeInsets.symmetric(horizontal: DS.s12, vertical: DS.s8),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_roadAlerts ? CupertinoIcons.bell_fill : CupertinoIcons.bell_slash,
                              size: 16, color: _roadAlerts ? DS.c(context, DS.success) : DS.label2(context)),
                          const SizedBox(width: 6),
                          Text(
                            AppStrings.t('Предупреждать в дороге', 'Avertizări pe drum'),
                            style: DS.footnote.copyWith(color: DS.label(context), fontWeight: FontWeight.w600),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const Spacer(),
                  AnimatedOpacity(
                    opacity: _rotation % 360 == 0 ? 0 : 1,
                    duration: const Duration(milliseconds: 200),
                    child: GestureDetector(
                      onTap: () {
                        DS.tap();
                        _map.rotate(0);
                        setState(() => _rotation = 0);
                      },
                      child: DSGlass(
                        radius: BorderRadius.circular(100),
                        padding: const EdgeInsets.all(DS.s8),
                        child: Transform.rotate(
                          angle: _rotation * 3.1415926535 / 180,
                          child: const Icon(CupertinoIcons.location_north_fill, size: 22, color: CupertinoColors.systemRed),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        // Метка (+) и «Я здесь»
        Positioned(
          left: DS.s16,
          bottom: bottom + DS.s12,
          child: _RoundButton(icon: CupertinoIcons.plus, onTap: () => _addAt(_flag ?? _me ?? _map.camera.center)),
        ),
        Positioned(
          right: DS.s16,
          bottom: bottom + DS.s12,
          child: _RoundButton(
            icon: CupertinoIcons.location_fill,
            onTap: () {
              if (_me != null) {
                _map.move(_me!, 15);
                _probe(_me!);
              }
              _locate(move: _me == null);
            },
          ),
        ),
      ],
    );
  }
}

/// Круглая метка на карте со значком SF-стиля.
class _Pin extends StatelessWidget {
  final IconData icon;
  final Color color;
  const _Pin({required this.icon, required this.color});

  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 2),
          boxShadow: const [BoxShadow(color: Color(0x40000000), blurRadius: 4, offset: Offset(0, 1))],
        ),
        child: Icon(icon, size: 16, color: Colors.white),
      );
}

/// Флажок спроса: буква тарифа мелко, надбавка крупно; сиреневый — есть надбавка.
class _SurgeFlag extends StatelessWidget {
  final String label;
  final String value;
  final bool hot;
  const _SurgeFlag({required this.label, required this.value, required this.hot});

  @override
  Widget build(BuildContext context) {
    final bg = hot ? DS.c(context, DS.surge) : DS.c(context, DS.brand);
    final fg = hot ? Colors.white : Colors.black;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(16),
            boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 6, offset: Offset(0, 2))],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              if (label.isNotEmpty) ...[
                Text(label, style: DS.footnote.copyWith(color: fg.withValues(alpha: 0.75), fontWeight: FontWeight.w600)),
                const SizedBox(width: 4),
              ],
              Text(value, style: DS.title3.copyWith(color: fg, fontWeight: FontWeight.w700)),
            ],
          ),
        ),
        CustomPaint(size: const Size(14, 8), painter: _Tail(bg)),
      ],
    );
  }
}

class _Tail extends CustomPainter {
  final Color color;
  _Tail(this.color);

  @override
  void paint(Canvas canvas, Size size) {
    final path = ui.Path()
      ..moveTo(0, 0)
      ..lineTo(size.width, 0)
      ..lineTo(size.width / 2, size.height)
      ..close();
    canvas.drawPath(path, Paint()..color = color);
  }

  @override
  bool shouldRepaint(_Tail old) => old.color != color;
}

class _RoundButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _RoundButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: () {
          DS.tap();
          onTap();
        },
        child: DSGlass(
          radius: BorderRadius.circular(100),
          child: SizedBox(width: 50, height: 50, child: Icon(icon, size: 22, color: DS.label(context))),
        ),
      );
}
