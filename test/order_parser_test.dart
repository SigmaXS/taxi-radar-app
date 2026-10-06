import 'package:flutter_test/flutter_test.dart';
import 'package:taxi_radar_app/services/fare_calculator.dart';
import 'package:taxi_radar_app/services/order_parser_service.dart';

void main() {
  group('Разбор карточки со скриншота', () {
    test('карта с подписями улиц и надбавка на кнопке', () {
      const text = 'Omite\nPrioritate: -4\nstr. PETRICANI\nBUIUCANI\nChișinău\nstr. ISMAIL\nbd. DACIA\nBOTANICA\n'
          '800 m · 4 min.\nPreluare apropiată\nA\nstrada Grenoble, 126A\nB\nstrada Luncii, 2/A\n+15 L\nAcceptă';
      final o = OrderParserService.parse(text);
      expect(o.pointA, 'strada Grenoble, 126A');
      expect(o.pointB, 'strada Luncii, 2/A');
      expect(o.stops, isEmpty);
      expect(o.surgeBonus, 15);
      expect(o.pickupKm, closeTo(0.8, 0.001));
      expect(o.tariff, 'Эконом');
    });

    test('подъезд и комментарий пассажира — не заезд и не Б', () {
      const text = '1,5 km · 5 min.\nPreluare apropiată\nA\nstrada Calea Basarabiei, 8\n'
          'B\nstrada Alecu Russo, 63/2, entrance 1\nPasager\npoarta 3\n+35 L\nAcceptă';
      final o = OrderParserService.parse(text);
      expect(o.pointA, 'strada Calea Basarabiei, 8');
      expect(o.pointB, 'strada Alecu Russo, 63/2, entrance 1');
      expect(o.stops, isEmpty);
      expect(o.surgeBonus, 35);
      expect(o.pickupKm, closeTo(1.5, 0.001));
    });

    test('метка склеена с адресом, настоящий заезд', () {
      const text = 'Комфорт\n2,7 км · 6 мин\nA strada Alba-Iulia, 75F\nstrada Ion Creangă, 10\n'
          'B strada Gheorghe Asachi, 68/1\nПринять';
      final o = OrderParserService.parse(text);
      expect(o.tariff, 'Комфорт');
      expect(o.pointA, 'strada Alba-Iulia, 75F');
      expect(o.pointB, 'strada Gheorghe Asachi, 68/1');
      expect(o.stops, ['strada Ion Creangă, 10']);
    });

    test('без адресов — цена не выдумывается', () {
      final o = OrderParserService.parse('Omite\nAcceptă');
      expect(o.hasRoute, isFalse);
      expect(o.price, 0);
      expect(o.priceText, 'адрес не распознан');
    });

    test('доставка распознаётся', () {
      expect(OrderParserService.isDelivery(['Доставка', 'Откуда', 'ул. Пушкина 1']), isTrue);
      expect(OrderParserService.isDelivery(['Эконом', 'A', 'ул. Пушкина 1']), isFalse);
    });
  });

  group('Тариф', () {
    test('минималка включает первые 2 км', () {
      expect(FareCalculator.price('Эконом', 2, 0, 0, 0), 30);
      expect(FareCalculator.price('Эконом', 9.4, 0, 15, 0), (30 + 3.5 * 7.4 + 15).round());
      expect(FareCalculator.price('Комфорт', 9.4, 0, 15, 35), (45 + 3.5 * 7.4 + 15).round() + 35);
    });

    test('загородные км дороже', () {
      expect(FareCalculator.price('Эконом', 2, 10, 20, 0), (30 + 5.3 * 10 + 20).round());
      expect(FareCalculator.price('Комфорт+', 2, 10, 20, 0), (65 + 7.3 * 10 + 20).round());
    });

    test('граница города', () {
      expect(FareCalculator.isInsideCity(47.0105, 28.8638), isTrue); // центр
      expect(FareCalculator.isInsideCity(46.9350, 28.9330), isFalse); // аэропорт
    });
  });
}
