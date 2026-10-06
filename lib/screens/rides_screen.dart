import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../l10n/app_strings.dart';
import '../models/ride_item.dart';
import '../services/rides_service.dart';
import '../ui/ds.dart';

/// «Попутчики»: заявки пассажиров и машин из групп, поиск по направлению.
class RidesScreen extends StatefulWidget {
  const RidesScreen({super.key});

  @override
  State<RidesScreen> createState() => _RidesScreenState();
}

class _RidesScreenState extends State<RidesScreen> {
  final _fromController = TextEditingController();
  final _toController = TextEditingController();

  String _kind = 'passenger'; // passenger | driver
  List<RideItem> _rides = [];
  List<String> _places = [];
  int _total = 0;
  int _offset = 0;
  String _routeTitle = '';
  bool _isLoading = false;
  bool _isLoadingMore = false;
  String? _errorMessage;

  // Быстрые направления: подпись на языке приложения → код для сервера.
  static List<(String, String)> get _regions => [
        (AppStrings.t('В ПМР', 'Spre Transnistria'), '@pmr'),
        (AppStrings.t('В Молдову', 'Spre Moldova'), '@md'),
        (AppStrings.t('В Украину', 'Spre Ucraina'), '@ua'),
        (AppStrings.t('В Европу', 'Spre Europa'), '@eu'),
      ];

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
    FocusScope.of(context).unfocus();
    setState(() {
      if (append) {
        _isLoadingMore = true;
      } else {
        _isLoading = true;
        _errorMessage = null;
        _offset = 0;
        _rides = [];
      }
    });

    final from = _fromController.text.trim();
    var to = _toController.text.trim();
    for (final r in _regions) {
      if (to == r.$1) to = r.$2;
    }

    final res = await RidesService.searchRides(kind: _kind, from: from, to: to, offset: _offset, limit: 20);
    if (!mounted) return;
    setState(() {
      _isLoading = false;
      _isLoadingMore = false;
      if (res == null) {
        _errorMessage = AppStrings.t('Не удалось загрузить заявки', 'Nu s-au putut încărca cererile');
        return;
      }
      _total = res.total;
      _routeTitle = res.route;
      _offset += res.items.length;
      append ? _rides.addAll(res.items) : _rides = res.items;
    });
  }

  void _swap() {
    DS.tap();
    final tmp = _fromController.text;
    _fromController.text = _toController.text;
    _toController.text = tmp;
    _performSearch();
  }

  void _open(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    final quick = [
      ..._regions.map((r) => r.$1),
      if (_places.isNotEmpty) ..._places.take(15) else ...['Кишинёв', 'Тирасполь', 'Бельцы', 'Бендеры', 'Рыбница'],
    ];
    return DSSubpage(
      title: AppStrings.tileRides,
      trailing: CupertinoButton(
        padding: EdgeInsets.zero,
        onPressed: () => _performSearch(),
        child: Icon(CupertinoIcons.arrow_clockwise, color: DS.label(context)),
      ),
      children: [
        DSInset(
          child: SizedBox(
            width: double.infinity,
            child: CupertinoSlidingSegmentedControl<String>(
              groupValue: _kind,
              children: {
                'passenger': Text(t('Пассажиры', 'Pasageri')),
                'driver': Text(t('Машины', 'Mașini')),
              },
              onValueChanged: (v) {
                if (v == null) return;
                DS.tap();
                setState(() => _kind = v);
                _performSearch();
              },
            ),
          ),
        ),
        DSInset(
          child: Row(
            children: [
              Expanded(
                child: Column(
                  children: [
                    DSField(
                      controller: _fromController,
                      placeholder: t('Откуда (город)', 'De unde (oraș)'),
                      prefixIcon: CupertinoIcons.circle,
                      onSubmitted: (_) => _performSearch(),
                    ),
                    const SizedBox(height: DS.s8),
                    DSField(
                      controller: _toController,
                      placeholder: t('Куда (город или страна)', 'Încotro (oraș sau țară)'),
                      prefixIcon: CupertinoIcons.location_solid,
                      onSubmitted: (_) => _performSearch(),
                    ),
                  ],
                ),
              ),
              CupertinoButton(
                padding: const EdgeInsets.only(left: DS.s8),
                onPressed: _swap,
                child: Icon(CupertinoIcons.arrow_up_arrow_down, color: DS.label2(context)),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 36,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: DS.gutter),
            itemCount: quick.length,
            separatorBuilder: (_, _) => const SizedBox(width: DS.s8),
            itemBuilder: (ctx, i) => GestureDetector(
              onTap: () {
                DS.tap();
                _toController.text = quick[i];
                _performSearch();
              },
              child: Container(
                alignment: Alignment.center,
                padding: const EdgeInsets.symmetric(horizontal: 14),
                decoration: BoxDecoration(color: DS.fill(context), borderRadius: BorderRadius.circular(100)),
                child: Text(quick[i], style: DS.subhead.copyWith(color: DS.label(context))),
              ),
            ),
          ),
        ),
        DSInset(
          padding: const EdgeInsets.fromLTRB(DS.gutter + 12, DS.s16, DS.gutter, 0),
          child: Text(
            _isLoading
                ? t('Ищу…', 'Caut…')
                : '${t('Найдено', 'Găsite')} $_total · ${_routeTitle.isNotEmpty ? _routeTitle : t('все направления', 'toate direcțiile')}',
            style: DS.footnote.copyWith(color: DS.label2(context)),
          ),
        ),
        if (_isLoading)
          const Padding(padding: EdgeInsets.all(DS.s32), child: CupertinoActivityIndicator())
        else if (_errorMessage != null)
          Padding(
            padding: const EdgeInsets.all(DS.s32),
            child: Column(
              children: [
                Icon(CupertinoIcons.wifi_slash, size: 40, color: DS.label3(context)),
                const SizedBox(height: DS.s12),
                Text(_errorMessage!, style: DS.subhead.copyWith(color: DS.label2(context))),
                const SizedBox(height: DS.s16),
                DSButton(t('Повторить', 'Reîncearcă'), secondary: true, onPressed: () => _performSearch()),
              ],
            ),
          )
        else if (_rides.isEmpty)
          Padding(
            padding: const EdgeInsets.all(DS.s32),
            child: Text(t('Заявок по этому маршруту нет', 'Nu sunt cereri pe această rută'),
                textAlign: TextAlign.center, style: DS.subhead.copyWith(color: DS.label2(context))),
          )
        else ...[
          ..._rides.map(_card),
          if (_offset < _total)
            DSInset(
              child: DSButton(
                t('Показать ещё (${_total - _offset})', 'Arată mai mult (${_total - _offset})'),
                secondary: true,
                loading: _isLoadingMore,
                onPressed: () => _performSearch(append: true),
              ),
            ),
        ],
      ],
    );
  }

  Widget _card(RideItem r) {
    final t = AppStrings.t;
    final kindText = r.kind == 'driver'
        ? (r.isCarrier ? t('Перевозчик', 'Transportator') : t('Едет машина', 'Merge mașina'))
        : t('Пассажир', 'Pasager');
    final kindColor = r.kind == 'driver' ? (r.isCarrier ? DS.surge : CupertinoColors.systemTeal) : DS.info;
    final meta = <String>[
      if (r.when.isNotEmpty) r.when,
      if (r.people != null && r.people! > 0) '${r.people} ${t('пасс.', 'pas.')}',
      if (r.seats != null && r.seats! > 0) '${r.seats} ${t('мест', 'locuri')}',
    ];
    final source = [r.source, r.author].where((s) => s != null && s.isNotEmpty).join(' · ');
    return DSCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Text('${r.from} → ${r.to}', style: DS.headline.copyWith(color: DS.label(context)))),
              const SizedBox(width: DS.s8),
              Text(kindText, style: DS.footnote.copyWith(color: DS.c(context, kindColor), fontWeight: FontWeight.w600)),
            ],
          ),
          if (meta.isNotEmpty) ...[
            const SizedBox(height: DS.s4),
            Text(meta.join(' · '), style: DS.subhead.copyWith(color: DS.label2(context))),
          ],
          if (r.text.isNotEmpty) ...[
            const SizedBox(height: DS.s8),
            Text(r.text, style: DS.callout.copyWith(color: DS.label(context), height: 1.3)),
          ],
          if (source.isNotEmpty) ...[
            const SizedBox(height: DS.s8),
            Text(source, style: DS.caption.copyWith(color: DS.label3(context))),
          ],
          if (r.phone != null || r.telegram != null || r.groupLink != null) ...[
            const SizedBox(height: DS.s12),
            Row(
              children: [
                if (r.phone != null) ...[
                  Expanded(child: _action(CupertinoIcons.phone_fill, t('Позвонить', 'Sună'), DS.success, () => _open('tel:${r.phone}'))),
                  const SizedBox(width: DS.s8),
                ],
                if (r.telegram != null) ...[
                  Expanded(
                      child: _action(CupertinoIcons.paperplane_fill, t('Написать', 'Scrie'), DS.telegram,
                          () => _open('https://t.me/${r.telegram}'))),
                  const SizedBox(width: DS.s8),
                ],
                if (r.groupLink != null)
                  CupertinoButton(
                    padding: const EdgeInsets.all(DS.s8),
                    color: DS.fill(context),
                    borderRadius: BorderRadius.circular(DS.rButton),
                    minimumSize: const Size(40, 40),
                    onPressed: () => _open(r.groupLink!),
                    child: Icon(CupertinoIcons.arrow_up_right_square, size: 20, color: DS.label(context)),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _action(IconData icon, String label, Color color, VoidCallback onTap) => CupertinoButton(
        padding: const EdgeInsets.symmetric(vertical: 10),
        color: DS.c(context, color),
        borderRadius: BorderRadius.circular(DS.rButton),
        minimumSize: const Size(40, 40),
        onPressed: () {
          DS.tap();
          onTap();
        },
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 17, color: Colors.white),
            const SizedBox(width: 6),
            Text(label, style: DS.subhead.copyWith(color: Colors.white, fontWeight: FontWeight.w600)),
          ],
        ),
      );
}
