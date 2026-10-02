class NetCalculator {
  static double calculateNet({
    required double orderPrice,
    required double tripKm,
    required double pickupKm,
    required double consumptionLPer100Km,
    required double fuelPrice,
    required double commissionPercent,
  }) {
    if (orderPrice <= 0) return 0;
    final commission = orderPrice * (commissionPercent / 100.0);
    final totalKm = tripKm + pickupKm;
    final fuelLiters = (totalKm * consumptionLPer100Km) / 100.0;
    final fuelCost = fuelLiters * fuelPrice;
    final net = orderPrice - commission - fuelCost;
    return net > 0 ? net : 0;
  }
}
