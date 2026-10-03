import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../l10n/app_strings.dart';
import '../../models/report.dart';
import '../../services/community_service.dart';
import '../../services/yandex_surge_service.dart';

class MapTab extends StatefulWidget {
  const MapTab({super.key});

  @override
  State<MapTab> createState() => _MapTabState();
}

class _MapTabState extends State<MapTab> {
  final MapController _mapController = MapController();
  final LatLng _center = const LatLng(47.0245, 28.8353); // Кишинёв
  LatLng? _userLocation;

  LatLng? _selectedPoint;
  SurgeResult? _selectedSurge;
  bool _isCheckingSurge = false;

  List<ReportItem> _reports = [];
  bool _roadAlerts = false;
  bool _showRoad = true;
  bool _showAddr = true;

  @override
  void initState() {
    super.initState();
    _loadAlertsPref();
    _fetchUserLocation();
    _loadReports();
  }

  Future<void> _loadAlertsPref() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _roadAlerts = prefs.getBool('road_alerts') ?? false;
    });
  }

  Future<void> _fetchUserLocation() async {
    try {
      LocationPermission perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) {
        perm = await Geolocator.requestPermission();
      }
      if (perm == LocationPermission.whileInUse || perm == LocationPermission.always) {
        final pos = await Geolocator.getCurrentPosition(
          locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 5)),
        );
        setState(() {
          _userLocation = LatLng(pos.latitude, pos.longitude);
        });
      }
    } catch (_) {}
  }

  Future<void> _loadReports() async {
    final lat = _userLocation?.latitude ?? _center.latitude;
    final lon = _userLocation?.longitude ?? _center.longitude;
    final list = await CommunityService.getReports(lat, lon);
    if (mounted) {
      setState(() => _reports = list);
    }
  }

  Future<void> _checkSurgeAt(LatLng point) async {
    setState(() {
      _selectedPoint = point;
      _isCheckingSurge = true;
      _selectedSurge = null;
    });

    final res = await YandexSurgeService.getSurgeAll(point.longitude, point.latitude);
    if (mounted) {
      setState(() {
        _isCheckingSurge = false;
        _selectedSurge = res;
      });
    }
  }

  void _onMapTap(TapPosition tapPosition, LatLng point) {
    _checkSurgeAt(point);
  }

  void _goToCurrentLocation() async {
    if (_userLocation != null) {
      _mapController.move(_userLocation!, 15);
      _checkSurgeAt(_userLocation!);
    } else {
      await _fetchUserLocation();
      if (_userLocation != null) {
        _mapController.move(_userLocation!, 15);
        _checkSurgeAt(_userLocation!);
      }
    }
  }

  void _showAddReportDialog() {
    final target = _selectedPoint ?? _userLocation ?? _center;
    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF1E2638),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Что отметить на карте?',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                const SizedBox(height: 6),
                Text(
                  'Метку увидят все водители в радиусе 15 км',
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade400),
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: RoadReports.types.map((type) {
                    return ActionChip(
                      avatar: Text(type.emoji, style: const TextStyle(fontSize: 18)),
                      label: Text(
                        AppStrings.isRu ? type.labelRu : type.labelRo,
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                      ),
                      backgroundColor: type.color.withValues(alpha: 0.85),
                      onPressed: () async {
                        Navigator.pop(ctx);
                        final res = await CommunityService.addReport(type.key, target.latitude, target.longitude);
                        if (mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(res['ok'] == true ? 'Метка добавлена!' : (res['message'] ?? 'Ошибка')),
                              backgroundColor: res['ok'] == true ? Colors.green.shade700 : Colors.red.shade700,
                            ),
                          );
                          _loadReports();
                        }
                      },
                    );
                  }).toList(),
                ),
                const SizedBox(height: 12),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: _center,
            initialZoom: 13.0,
            onTap: _onMapTap,
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              userAgentPackageName: 'com.example.taxiradar',
            ),

            // Метки дорожных событий
            MarkerLayer(
              markers: _reports.where((r) {
                final type = RoadReports.byKey(r.type);
                if (type == null) return false;
                return type.road ? _showRoad : _showAddr;
              }).map((r) {
                final type = RoadReports.byKey(r.type);
                return Marker(
                  point: LatLng(r.lat, r.lon),
                  width: 36,
                  height: 36,
                  child: GestureDetector(
                    onTap: () {
                      _showReportVoteDialog(r, type);
                    },
                    child: Container(
                      decoration: BoxDecoration(
                        color: type?.color ?? Colors.blue,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 2),
                        boxShadow: const [BoxShadow(color: Colors.black45, blurRadius: 4)],
                      ),
                      child: Center(
                        child: Text(type?.emoji ?? '📍', style: const TextStyle(fontSize: 16)),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),

            // Метка выбранной точки со спросом
            if (_selectedPoint != null)
              MarkerLayer(
                markers: [
                  Marker(
                    point: _selectedPoint!,
                    width: 140,
                    height: 70,
                    child: Column(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.85),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: Colors.amberAccent, width: 1.5),
                          ),
                          child: _isCheckingSurge
                              ? const SizedBox(
                                  width: 14,
                                  height: 14,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.amber),
                                )
                              : Text(
                                  _selectedSurge != null
                                      ? 'Э: +${_selectedSurge!.econom} L\nК: +${_selectedSurge!.comfort} L'
                                      : 'Нет надбавки',
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                                ),
                        ),
                        const Icon(Icons.location_on, color: Colors.amberAccent, size: 28),
                      ],
                    ),
                  ),
                ],
              ),

            // Текущее местоположение пользователя
            if (_userLocation != null)
              MarkerLayer(
                markers: [
                  Marker(
                    point: _userLocation!,
                    width: 24,
                    height: 24,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.blueAccent,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                        boxShadow: const [BoxShadow(color: Colors.black45, blurRadius: 4)],
                      ),
                    ),
                  ),
                ],
              ),
          ],
        ),

        // Верхняя панель: Переключатели и фильтры
        PositionEdge(
          top: 10,
          left: 10,
          right: 10,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFF1E2638).withValues(alpha: 0.92),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  children: [
                    FilterChip(
                      label: Text(AppStrings.mapLayerRoad, style: const TextStyle(fontSize: 12)),
                      selected: _showRoad,
                      selectedColor: Colors.blue.shade700,
                      onSelected: (val) => setState(() => _showRoad = val),
                    ),
                    const SizedBox(width: 6),
                    FilterChip(
                      label: Text(AppStrings.mapLayerAddr, style: const TextStyle(fontSize: 12)),
                      selected: _showAddr,
                      selectedColor: Colors.teal.shade700,
                      onSelected: (val) => setState(() => _showAddr = val),
                    ),
                    const SizedBox(width: 6),
                    IconButton(
                      icon: Icon(
                        _roadAlerts ? Icons.notifications_active_rounded : Icons.notifications_off_rounded,
                        color: _roadAlerts ? Colors.amberAccent : Colors.white38,
                        size: 20,
                      ),
                      tooltip: AppStrings.mapRoadAlerts,
                      onPressed: () async {
                        final prefs = await SharedPreferences.getInstance();
                        setState(() => _roadAlerts = !_roadAlerts);
                        await prefs.setBool('road_alerts', _roadAlerts);
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(_roadAlerts ? 'Оповещения в дороге включены' : 'Оповещения в дороге выключены'),
                              duration: const Duration(seconds: 1),
                            ),
                          );
                        }
                      },
                    ),
                  ],
                ),
              ),
              FloatingActionButton.small(
                heroTag: 'add_report_btn',
                backgroundColor: Colors.amber.shade700,
                onPressed: _showAddReportDialog,
                child: const Icon(Icons.add_location_alt_rounded, color: Colors.black),
              ),
            ],
          ),
        ),

        // Кнопка "Я здесь"
        Positioned(
          bottom: 20,
          right: 16,
          child: FloatingActionButton.extended(
            heroTag: 'my_loc_btn',
            backgroundColor: const Color(0xFF1E2638),
            foregroundColor: Colors.white,
            icon: const Icon(Icons.my_location_rounded, color: Colors.amberAccent),
            label: Text(AppStrings.mapIAmHere),
            onPressed: _goToCurrentLocation,
          ),
        ),
      ],
    );
  }

  void _showReportVoteDialog(ReportItem r, ReportType? type) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E2638),
        title: Row(
          children: [
            Text(type?.emoji ?? '📍', style: const TextStyle(fontSize: 24)),
            const SizedBox(width: 10),
            Text(type?.labelRu ?? 'Метка', style: const TextStyle(color: Colors.white)),
          ],
        ),
        content: const Text(
          'Метка ещё актуальна?',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            child: Text(AppStrings.mapNotHere, style: const TextStyle(color: Colors.redAccent)),
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.voteReport(r.id, false);
              _loadReports();
            },
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.greenAccent.shade700),
            child: Text(AppStrings.mapStillHere, style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
            onPressed: () async {
              Navigator.pop(ctx);
              await CommunityService.voteReport(r.id, true);
              _loadReports();
            },
          ),
        ],
      ),
    );
  }
}

class PositionEdge extends StatelessWidget {
  final double? top;
  final double? bottom;
  final double? left;
  final double? right;
  final Widget child;

  const PositionEdge({super.key, this.top, this.bottom, this.left, this.right, required this.child});

  @override
  Widget build(BuildContext context) {
    return Positioned(top: top, bottom: bottom, left: left, right: right, child: child);
  }
}
