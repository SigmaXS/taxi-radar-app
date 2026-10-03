import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../models/ride_item.dart';
import '../services/rides_service.dart';

class RidesScreen extends StatefulWidget {
  const RidesScreen({super.key});

  @override
  State<RidesScreen> createState() => _RidesScreenState();
}

class _RidesScreenState extends State<RidesScreen> {
  final TextEditingController _fromController = TextEditingController();
  final TextEditingController _toController = TextEditingController();

  String _kind = 'passenger'; // 'passenger' or 'driver'
  List<RideItem> _rides = [];
  List<String> _places = [];
  int _total = 0;
  int _offset = 0;
  String _routeTitle = '';
  bool _isLoading = false;
  bool _isLoadingMore = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadPlaces();
    _performSearch();
  }

  Future<void> _loadPlaces() async {
    final list = await RidesService.fetchPlaces();
    if (mounted) setState(() => _places = list);
  }

  Future<void> _performSearch({bool append = false}) async {
    if (_isLoading || _isLoadingMore) return;

    if (!append) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
        _offset = 0;
        _rides = [];
      });
    } else {
      setState(() => _isLoadingMore = true);
    }

    final from = _fromController.text.trim();
    String to = _toController.text.trim();
    if (to == 'В ПМР') to = '@pmr';
    if (to == 'В Молдову') to = '@md';
    if (to == 'В Украину') to = '@ua';
    if (to == 'В Европу') to = '@eu';

    final res = await RidesService.searchRides(
      kind: _kind,
      from: from,
      to: to,
      offset: _offset,
      limit: 20,
    );

    if (mounted) {
      if (res != null) {
        setState(() {
          _total = res.total;
          _routeTitle = res.route;
          _offset += res.items.length;
          if (append) {
            _rides.addAll(res.items);
          } else {
            _rides = res.items;
          }
          _isLoading = false;
          _isLoadingMore = false;
        });
      } else {
        setState(() {
          _isLoading = false;
          _isLoadingMore = false;
          _errorMessage = 'Не удалось загрузить заявки';
        });
      }
    }
  }

  void _swapDirections() {
    final temp = _fromController.text;
    _fromController.text = _toController.text;
    _toController.text = temp;
    _performSearch();
  }

  void _openUrl(String url) async {
    final uri = Uri.parse(url);
    if (await canLaunchUrl(uri)) {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121826),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E2638),
        elevation: 0,
        title: const Text('Попутчики', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: () => _performSearch(),
          ),
        ],
      ),
      body: Column(
        children: [
          // Панель поиска
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            color: const Color(0xFF1E2638),
            child: Column(
              children: [
                // Переключатель Пассажиры / Машины
                SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(
                      value: 'passenger',
                      icon: Icon(Icons.person_rounded),
                      label: Text('Пассажиры'),
                    ),
                    ButtonSegment(
                      value: 'driver',
                      icon: Icon(Icons.directions_car_rounded),
                      label: Text('Машины'),
                    ),
                  ],
                  selected: {_kind},
                  style: ButtonStyle(
                    backgroundColor: WidgetStateProperty.resolveWith((states) {
                      if (states.contains(WidgetState.selected)) return Colors.amber.shade700;
                      return const Color(0xFF2A364F);
                    }),
                    foregroundColor: WidgetStateProperty.resolveWith((states) {
                      if (states.contains(WidgetState.selected)) return Colors.black;
                      return Colors.white70;
                    }),
                  ),
                  onSelectionChanged: (set) {
                    setState(() => _kind = set.first);
                    _performSearch();
                  },
                ),

                const SizedBox(height: 12),

                // Поля Откуда -> Куда с кнопкой реверса
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        children: [
                          TextField(
                            controller: _fromController,
                            style: const TextStyle(color: Colors.white, fontSize: 14),
                            decoration: InputDecoration(
                              hintText: 'Откуда (город)',
                              hintStyle: TextStyle(color: Colors.grey.shade400),
                              filled: true,
                              fillColor: const Color(0xFF2A364F),
                              isDense: true,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                            ),
                          ),
                          const SizedBox(height: 6),
                          TextField(
                            controller: _toController,
                            style: const TextStyle(color: Colors.white, fontSize: 14),
                            decoration: InputDecoration(
                              hintText: 'Куда (город или страна)',
                              hintStyle: TextStyle(color: Colors.grey.shade400),
                              filled: true,
                              fillColor: const Color(0xFF2A364F),
                              isDense: true,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Column(
                      children: [
                        IconButton.filled(
                          style: IconButton.styleFrom(backgroundColor: const Color(0xFF2A364F)),
                          icon: const Icon(Icons.swap_vert_rounded, color: Colors.amberAccent),
                          tooltip: 'Поменять местами',
                          onPressed: _swapDirections,
                        ),
                        IconButton.filled(
                          style: IconButton.styleFrom(backgroundColor: Colors.amber.shade700),
                          icon: const Icon(Icons.search_rounded, color: Colors.black),
                          tooltip: 'Найти',
                          onPressed: () => _performSearch(),
                        ),
                      ],
                    ),
                  ],
                ),

                const SizedBox(height: 8),

                // Быстрые фильтры направлений
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      'В ПМР', 'В Молдову', 'В Украину', 'В Европу',
                      if (_places.isNotEmpty) ..._places.take(15)
                      else ...['Кишинёв', 'Тирасполь', 'Бельцы', 'Бендеры', 'Рыбница'],
                    ].map((dest) {
                      return Padding(
                        padding: const EdgeInsets.only(right: 6),
                        child: ActionChip(
                          label: Text(dest, style: const TextStyle(fontSize: 11, color: Colors.white70)),
                          backgroundColor: const Color(0xFF2A364F),
                          padding: const EdgeInsets.all(2),
                          onPressed: () {
                            _toController.text = dest;
                            _performSearch();
                          },
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
          ),

          // Статусная строка результатов
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: const Color(0xFF172033),
            child: Text(
              _isLoading
                  ? 'Поиск попутчиков…'
                  : 'Найдено $_total ${_kind == "driver" ? "машин" : "заявок"} · ${_routeTitle.isNotEmpty ? _routeTitle : "Все направления"}',
              style: TextStyle(color: Colors.grey.shade400, fontSize: 13),
            ),
          ),

          // Список заявок
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator(color: Colors.amberAccent))
                : _errorMessage != null
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.cloud_off_rounded, color: Colors.white54, size: 48),
                            const SizedBox(height: 10),
                            Text(_errorMessage!, style: const TextStyle(color: Colors.white70)),
                            const SizedBox(height: 12),
                            ElevatedButton(
                              onPressed: () => _performSearch(),
                              child: const Text('Повторить'),
                            ),
                          ],
                        ),
                      )
                    : _rides.isEmpty
                        ? Center(
                            child: Text(
                              'Заявок по этому маршруту не найдено',
                              style: TextStyle(color: Colors.grey.shade400),
                            ),
                          )
                        : ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            itemCount: _rides.length + (_offset < _total ? 1 : 0),
                            itemBuilder: (ctx, i) {
                              if (i == _rides.length) {
                                return Padding(
                                  padding: const EdgeInsets.symmetric(vertical: 16),
                                  child: Center(
                                    child: _isLoadingMore
                                        ? const CircularProgressIndicator(color: Colors.amberAccent)
                                        : ElevatedButton.icon(
                                            style: ElevatedButton.styleFrom(
                                              backgroundColor: const Color(0xFF2A364F),
                                              foregroundColor: Colors.white,
                                            ),
                                            icon: const Icon(Icons.expand_more_rounded),
                                            label: Text('Загрузить ещё (${_total - _offset})'),
                                            onPressed: () => _performSearch(append: true),
                                          ),
                                  ),
                                );
                              }

                              final r = _rides[i];
                              return _buildRideCard(r);
                            },
                          ),
          ),
        ],
      ),
    );
  }

  Widget _buildRideCard(RideItem r) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      color: const Color(0xFF1E2638),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Маршрут и тип (Пассажир / Машина / Перевозчик)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    '${r.from} → ${r.to}',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: r.kind == 'driver'
                        ? (r.isCarrier ? Colors.purple.shade700 : Colors.teal.shade700)
                        : Colors.blue.shade700,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    r.kind == 'driver'
                        ? (r.isCarrier ? 'Перевозчик' : 'Едет машина')
                        : 'Пассажир',
                    style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 8),

            // Время и места
            Wrap(
              spacing: 12,
              children: [
                if (r.when.isNotEmpty)
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.access_time_rounded, color: Colors.amberAccent, size: 14),
                      const SizedBox(width: 4),
                      Text(r.when, style: const TextStyle(color: Colors.amberAccent, fontSize: 12)),
                    ],
                  ),
                if (r.people != null && r.people! > 0)
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.people_alt_rounded, color: Colors.white70, size: 14),
                      const SizedBox(width: 4),
                      Text('${r.people} пасс.', style: const TextStyle(color: Colors.white70, fontSize: 12)),
                    ],
                  ),
                if (r.seats != null && r.seats! > 0)
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.airline_seat_recline_normal_rounded, color: Colors.greenAccent, size: 14),
                      const SizedBox(width: 4),
                      Text('${r.seats} мест', style: const TextStyle(color: Colors.greenAccent, fontSize: 12)),
                    ],
                  ),
              ],
            ),

            if (r.text.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                r.text,
                style: const TextStyle(color: Colors.white, fontSize: 14, height: 1.3),
              ),
            ],

            const SizedBox(height: 8),

            // Источник
            if (r.source != null || r.author != null)
              Text(
                [r.source, r.author].where((s) => s != null && s.isNotEmpty).join(' · '),
                style: TextStyle(color: Colors.white.withValues(alpha: 0.4), fontSize: 11),
              ),

            const Divider(color: Colors.white10, height: 20),

            // Кнопки связи (Позвонить / Написать / Группа)
            Row(
              children: [
                if (r.phone != null) ...[
                  Expanded(
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.green.shade700,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        padding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                      icon: const Icon(Icons.phone_rounded, size: 16),
                      label: const Text('Позвонить', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                      onPressed: () => _openUrl('tel:${r.phone}'),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (r.telegram != null) ...[
                  Expanded(
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF0088CC),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        padding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                      icon: const Icon(Icons.telegram, size: 16),
                      label: const Text('Написать', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                      onPressed: () => _openUrl('https://t.me/${r.telegram}'),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (r.groupLink != null)
                  IconButton.filled(
                    style: IconButton.styleFrom(
                      backgroundColor: const Color(0xFF2A364F),
                      foregroundColor: Colors.white70,
                    ),
                    icon: const Icon(Icons.open_in_new_rounded, size: 18),
                    tooltip: 'Открыть группу',
                    onPressed: () => _openUrl(r.groupLink!),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
