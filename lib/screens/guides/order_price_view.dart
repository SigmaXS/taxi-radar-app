import 'package:flutter/cupertino.dart';

import '../../l10n/app_strings.dart';
import '../../services/order_parser_service.dart';
import '../../services/radar_alerts.dart';
import '../../ui/ds.dart';

/// Цена заказа крупно и одна строка «Эконом · 4.5 км · 18 мин» — без адресов.
class OrderPriceView extends StatelessWidget {
  final ParsedOrder order;
  const OrderPriceView({super.key, required this.order});

  @override
  Widget build(BuildContext context) {
    final line = [
      orderTariffLabel(order.tariff),
      if (order.distanceTime.isNotEmpty) order.distanceTime,
      if (order.surgeBonus > 0) AppStrings.t('надбавка +${order.surgeBonus}', 'adaos +${order.surgeBonus}'),
    ].join(' · ');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          order.priceText,
          style: DS.display.copyWith(
            fontSize: order.price > 0 ? 48 : 28,
            height: 1.1,
            color: order.price > 0 ? DS.c(context, DS.success) : DS.label2(context),
          ),
        ),
        const SizedBox(height: DS.s4),
        Text(line, style: DS.subhead.copyWith(color: DS.label2(context))),
      ],
    );
  }
}
