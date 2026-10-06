import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../l10n/app_strings.dart';
import '../ui/ds.dart';

/// Официальное табло airport.md внутри приложения.
class AirportWebScreen extends StatefulWidget {
  const AirportWebScreen({super.key});

  @override
  State<AirportWebScreen> createState() => _AirportWebScreenState();
}

class _AirportWebScreenState extends State<AirportWebScreen> {
  late final WebViewController _controller;
  bool _isLoading = true;
  bool _hasError = false;

  String get _url => AppStrings.isRu
      ? 'https://airport.md/ru/passenger/online-panel?today-flights=1#arrivals'
      : 'https://airport.md/passenger/online-panel?today-flights=1#arrivals';

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageStarted: (_) => setState(() {
            _isLoading = true;
            _hasError = false;
          }),
          onPageFinished: (_) => setState(() => _isLoading = false),
          onWebResourceError: (_) => setState(() {
            _isLoading = false;
            _hasError = true;
          }),
        ),
      )
      ..loadRequest(Uri.parse(_url));
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    return Scaffold(
      backgroundColor: DS.bg(context),
      appBar: CupertinoNavigationBar(
        backgroundColor: DS.bg(context).withValues(alpha: 0.85),
        middle: Text(AppStrings.airportOfficial, style: DS.headline.copyWith(color: DS.label(context))),
        previousPageTitle: '',
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: () => _controller.reload(),
          child: Icon(CupertinoIcons.arrow_clockwise, color: DS.label(context)),
        ),
      ),
      body: Stack(
        children: [
          if (!_hasError) WebViewWidget(controller: _controller),
          if (_isLoading) const Center(child: CupertinoActivityIndicator(radius: 14)),
          if (_hasError)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(DS.s32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(CupertinoIcons.wifi_slash, size: 44, color: DS.label3(context)),
                    const SizedBox(height: DS.s12),
                    Text(t('Не удалось загрузить сайт аэропорта', 'Nu s-a putut încărca site-ul aeroportului'),
                        textAlign: TextAlign.center, style: DS.subhead.copyWith(color: DS.label2(context))),
                    const SizedBox(height: DS.s16),
                    DSButton(t('Повторить', 'Reîncearcă'), secondary: true, onPressed: () => _controller.reload()),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
