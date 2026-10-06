import 'package:flutter_test/flutter_test.dart';
import 'package:taxi_radar_app/services/phone_numbers.dart';

void main() {
  test('местные номера приводятся к +373', () {
    expect(PhoneNumbers.normalize('078123456'), '+37378123456');
    expect(PhoneNumbers.normalize('078 12 34 56'), '+37378123456');
    expect(PhoneNumbers.normalize('78123456'), '+37378123456');
    expect(PhoneNumbers.normalize('37378123456'), '+37378123456');
    expect(PhoneNumbers.normalize('+373 78 123 456'), '+37378123456');
    expect(PhoneNumbers.normalize('0037378123456'), '+37378123456');
  });

  test('не номер — null', () {
    expect(PhoneNumbers.normalize('12345'), isNull);
    expect(PhoneNumbers.normalize('abc'), isNull);
  });
}
