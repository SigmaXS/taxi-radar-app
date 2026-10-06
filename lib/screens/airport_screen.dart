import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../l10n/app_strings.dart';
import '../models/flight.dart';
import '../services/community_service.dart';
import '../ui/ds.dart';
import 'airport_web_screen.dart';

/// Аэропорт: сколько машин в очереди и прилёты (сели / ближайшие / позже).
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
    _timer = Timer.periodic(const Duration(seconds: 40), (_) => _loadStatus());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _loadStatus() async {
    final s = await CommunityService.getAirportStatus();
    if (mounted) {
      setState(() {
        _status = s ?? _status;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    final flights = _status?.flights ?? [];
    final landed = flights.where((f) => f.status == 'landed').toList();
    final upcoming = flights.where((f) => f.status != 'landed').toList();
    final lastLanded = landed.length > 3 ? landed.sublist(landed.length - 3) : landed;
    final next = upcoming.take(3).toList();
    final rest = upcoming.skip(3).toList();

    return Scaffold(
      backgroundColor: DS.bg(context),
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        slivers: [
          CupertinoSliverNavigationBar(
            largeTitle: Text(AppStrings.tileAirport),
            backgroundColor: DS.bg(context).withValues(alpha: 0.82),
            border: null,
            previousPageTitle: '',
          ),
          CupertinoSliverRefreshControl(onRefresh: _loadStatus),
          SliverSafeArea(
            top: false,
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                if (_isLoading)
                  const Padding(padding: EdgeInsets.all(DS.s32), child: CupertinoActivityIndicator())
                else ...[
                  DSCard(
                    child: Row(
                      children: [
                        Text('${_status?.queue ?? 0}', style: DS.display.copyWith(color: DS.label(context), height: 1)),
                        const SizedBox(width: DS.s16),
                        Expanded(
                          child: Text(AppStrings.airportQueueCaption, style: DS.subhead.copyWith(color: DS.label2(context))),
                        ),
                        Icon(CupertinoIcons.car_detailed, size: 34, color: DS.c(context, DS.brand)),
                      ],
                    ),
                  ),
                  DSSection(
                    children: [
                      DSRow(
                        icon: const DSIcon(CupertinoIcons.globe, DS.info),
                        title: AppStrings.airportOfficial,
                        onTap: () => dsPush(context, const AirportWebScreen()),
                      ),
                    ],
                  ),
                  if (lastLanded.isNotEmpty)
                    DSSection(header: AppStrings.airportLandedTitle, children: lastLanded.map(_row).toList()),
                  if (next.isNotEmpty) DSSection(header: AppStrings.airportNextTitle, children: next.map(_row).toList()),
                  if (rest.isNotEmpty && _showAll)
                    DSSection(header: AppStrings.airportLaterTitle, children: rest.map(_row).toList()),
                  if (rest.isNotEmpty)
                    CupertinoButton(
                      onPressed: () => setState(() => _showAll = !_showAll),
                      child: Text(
                        _showAll
                            ? t('Скрыть остальные (${rest.length})', 'Ascunde restul (${rest.length})')
                            : t('Все рейсы дня (ещё ${rest.length})', 'Toate zborurile zilei (încă ${rest.length})'),
                        style: DS.callout.copyWith(color: DS.c(context, DS.info)),
                      ),
                    ),
                  if (flights.isEmpty)
                    Padding(
                      padding: const EdgeInsets.all(DS.s32),
                      child: Text(AppStrings.airportNoFlights,
                          textAlign: TextAlign.center, style: DS.subhead.copyWith(color: DS.label2(context))),
                    ),
                ],
                const SizedBox(height: DS.s32),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  Widget _row(Flight f) {
    final t = AppStrings.t;
    final landed = f.status == 'landed';
    final status = landed ? t('Сел', 'A aterizat') : (f.delayed ? t('Задержка', 'Întârziere') : t('В пути', 'În zbor'));
    final color = landed ? DS.success : (f.delayed ? DS.danger : CupertinoColors.systemOrange);
    return DSRow(
      title: Flight.city(f.from),
      subtitle: f.flight,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(status, style: DS.subhead.copyWith(color: DS.c(context, color), fontWeight: FontWeight.w600)),
          const SizedBox(width: DS.s12),
          Text(f.time, style: DS.headline.copyWith(color: DS.label(context), fontFeatures: const [FontFeature.tabularFigures()])),
        ],
      ),
    );
  }
}
