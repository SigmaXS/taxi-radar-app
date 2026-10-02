import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../l10n/app_strings.dart';
import '../services/net_calculator.dart';
import '../services/traffic_model.dart';

class RidesScreen extends StatefulWidget {
  const RidesScreen({super.key});

  @override
  State<RidesScreen> createState() => _RidesScreenState();
}

class _RidesScreenState extends State<RidesScreen> {
  final TextEditingController _priceController = TextEditingController(text: '120');
  final TextEditingController _tripKmController = TextEditingController(text: '10.5');
  final TextEditingController _tripMinController = TextEditingController(text: '18');
  final TextEditingController _pickupKmController = TextEditingController(text: '1.5');
  final TextEditingController _bonusController = TextEditingController(text: '25');

  double _fuelConsumption = 8.5;
  double _fuelPrice = 24.5;
  double _commission = 16.0;

  double _calculatedNet = 0.0;
  int _trafficEstimatedMin = 18;

  @override
  void initState() {
    super.initState();
    _loadPrefsAndCalculate();
  }

  Future<void> _loadPrefsAndCalculate() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _fuelConsumption = prefs.getDouble('fuel_consumption') ?? 8.5;
      _fuelPrice = prefs.getDouble('fuel_price') ?? 24.5;
      _commission = prefs.getDouble('commission_percent') ?? 16.0;
    });
    _recalculate();
  }

  void _recalculate() {
    final price = double.tryParse(_priceController.text) ?? 0;
    final tripKm = double.tryParse(_tripKmController.text) ?? 0;
    final tripMin = int.tryParse(_tripMinController.text) ?? 0;
    final pickupKm = double.tryParse(_pickupKmController.text) ?? 0;
    final bonus = double.tryParse(_bonusController.text) ?? 0;

    final totalPrice = price + bonus;
    final net = NetCalculator.calculateNet(
      orderPrice: totalPrice,
      tripKm: tripKm,
      pickupKm: pickupKm,
      consumptionLPer100Km: _fuelConsumption,
      fuelPrice: _fuelPrice,
      commissionPercent: _commission,
    );

    final trafficMult = TrafficModel.getMultiplierNow();
    final adjustedMin = (tripMin * trafficMult).round();

    setState(() {
      _calculatedNet = net;
      _trafficEstimatedMin = adjustedMin;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121826),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E2638),
        elevation: 0,
        title: Text(AppStrings.tileRides, style: const TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        children: [
          // Карточка результата "Чистыми"
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF0D47A1),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
              child: Column(
                children: [
                  const Text('ЧИСТЫМИ С ЗАКАЗА', style: TextStyle(color: Colors.white70, fontSize: 13, letterSpacing: 1.2)),
                  const SizedBox(height: 6),
                  Text(
                    '~${_calculatedNet.toStringAsFixed(1)} L',
                    style: const TextStyle(fontSize: 40, fontWeight: FontWeight.w900, color: Colors.greenAccent),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'С учётом пробок: ~$_trafficEstimatedMin мин пути',
                    style: const TextStyle(color: Colors.white, fontSize: 14),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // Поля ввода заказа
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Параметры поездки', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _priceController,
                          keyboardType: TextInputType.number,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            labelText: 'Тариф заказа (L)',
                            labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                            filled: true,
                            fillColor: const Color(0xFF2A364F),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                          ),
                          onChanged: (_) => _recalculate(),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          controller: _bonusController,
                          keyboardType: TextInputType.number,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            labelText: 'Надбавка/кэф (L)',
                            labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                            filled: true,
                            fillColor: const Color(0xFF2A364F),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                          ),
                          onChanged: (_) => _recalculate(),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _tripKmController,
                          keyboardType: TextInputType.number,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            labelText: 'Дистанция (км)',
                            labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                            filled: true,
                            fillColor: const Color(0xFF2A364F),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                          ),
                          onChanged: (_) => _recalculate(),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          controller: _tripMinController,
                          keyboardType: TextInputType.number,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            labelText: 'Время (мин)',
                            labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                            filled: true,
                            fillColor: const Color(0xFF2A364F),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                          ),
                          onChanged: (_) => _recalculate(),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _pickupKmController,
                    keyboardType: TextInputType.number,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Подача до клиента (км)',
                      labelStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
                      filled: true,
                      fillColor: const Color(0xFF2A364F),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                    ),
                    onChanged: (_) => _recalculate(),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
