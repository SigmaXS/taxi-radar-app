import 'dart:async';
import 'package:flutter/material.dart';
import '../l10n/app_strings.dart';
import '../models/flight.dart';
import '../services/community_service.dart';
import 'airport_web_screen.dart';

class AirportScreen extends StatefulWidget {
  const AirportScreen({super.key});

  @override
  State<AirportScreen> createState() => _AirportScreenState();
}

class _AirportScreenState extends State<AirportScreen> {
  AirportStatus? _status;
  bool _isLoading = true;
  bool _showAll = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _loadStatus();
    _timer = Timer.periodic(const Duration(seconds: 40), (_) => _loadStatus(silent: true));
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _loadStatus({bool silent = false}) async {
    if (!silent) setState(() => _isLoading = true);
    final s = await CommunityService.getAirportStatus();
    if (mounted) {
      setState(() {
        _status = s;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = _status;
    final flights = s?.flights ?? [];
    final landed = flights.where((f) => f.status == 'landed').toList();
    final upcoming = flights.where((f) => f.status != 'landed').toList();

    final lastLanded = landed.length > 3 ? landed.sublist(landed.length - 3) : landed;
    final nextFlights = upcoming.take(3).toList();
    final restFlights = upcoming.skip(3).toList();

    return Scaffold(
      backgroundColor: const Color(0xFF121826),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E2638),
        elevation: 0,
        title: Text(AppStrings.tileAirport, style: const TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => _loadStatus(),
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Colors.amberAccent))
          : RefreshIndicator(
              onRefresh: () => _loadStatus(),
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                children: [
                  // Карточка очереди машин
                  Card(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    color: const Color(0xFF1E2638),
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.local_taxi_rounded, color: Colors.amberAccent, size: 36),
                              const SizedBox(width: 14),
                              Text(
                                '${s?.queue ?? 0}',
                                style: const TextStyle(
                                  fontSize: 44,
                                  fontWeight: FontWeight.w900,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            AppStrings.airportQueueCaption,
                            style: const TextStyle(color: Colors.white70, fontSize: 14),
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 12),

                  // Кнопка официального табло
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF2A364F),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    icon: const Icon(Icons.open_in_browser_rounded, color: Colors.blueAccent),
                    label: Text(AppStrings.airportOfficial),
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const AirportWebScreen()),
                      );
                    },
                  ),

                  const SizedBox(height: 16),

                  // Секция: СЕЛИ
                  if (lastLanded.isNotEmpty) ...[
                    _buildSectionHeader(AppStrings.airportLandedTitle, Colors.greenAccent),
                    ...lastLanded.map((f) => _buildFlightRow(f)),
                    const SizedBox(height: 14),
                  ],

                  // Секция: БЛИЖАЙШИЕ
                  if (nextFlights.isNotEmpty) ...[
                    _buildSectionHeader(AppStrings.airportNextTitle, Colors.amberAccent),
                    ...nextFlights.map((f) => _buildFlightRow(f)),
                    const SizedBox(height: 14),
                  ],

                  // Секция: ПОЗЖЕ СЕГОДНЯ
                  if (restFlights.isNotEmpty) ...[
                    if (_showAll) ...[
                      _buildSectionHeader(AppStrings.airportLaterTitle, Colors.blueAccent),
                      ...restFlights.map((f) => _buildFlightRow(f)),
                    ],
                    const SizedBox(height: 10),
                    Center(
                      child: TextButton(
                        onPressed: () => setState(() => _showAll = !_showAll),
                        child: Text(
                          _showAll
                              ? 'Скрыть остальные (${restFlights.length})'
                              : 'Все рейсы дня (ещё ${restFlights.length})',
                          style: const TextStyle(color: Colors.amberAccent, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ),
                  ],

                  if (flights.isEmpty)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.only(top: 40),
                        child: Text(
                          AppStrings.airportNoFlights,
                          style: TextStyle(color: Colors.grey.shade400),
                        ),
                      ),
                    ),
                ],
              ),
            ),
    );
  }

  Widget _buildSectionHeader(String title, Color color) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, top: 4),
      child: Text(
        title,
        style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.bold, letterSpacing: 1.1),
      ),
    );
  }

  Widget _buildFlightRow(Flight f) {
    final cityName = Flight.city(f.from);
    final isLanded = f.status == 'landed';

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF1E2638),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: isLanded ? Colors.green.shade900 : Colors.blue.shade900,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              f.time,
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  cityName,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
                ),
                Text(
                  f.flight,
                  style: TextStyle(color: Colors.grey.shade400, fontSize: 12),
                ),
              ],
            ),
          ),
          Text(
            isLanded ? 'Сел' : (f.delayed ? 'Задержка' : 'В пути'),
            style: TextStyle(
              color: isLanded ? Colors.greenAccent : (f.delayed ? Colors.redAccent : Colors.amberAccent),
              fontWeight: FontWeight.bold,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }
}
