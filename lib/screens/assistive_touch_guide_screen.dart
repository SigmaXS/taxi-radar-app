import 'package:flutter/material.dart';
import '../services/live_activity_service.dart';
import '../services/order_parser_service.dart';

class AssistiveTouchGuideScreen extends StatefulWidget {
  const AssistiveTouchGuideScreen({super.key});

  @override
  State<AssistiveTouchGuideScreen> createState() => _AssistiveTouchGuideScreenState();
}

class _AssistiveTouchGuideScreenState extends State<AssistiveTouchGuideScreen> {
  final TextEditingController _testTextController = TextEditingController(
    text: "Комфорт\n~ 85 лей\nПодача: 2 мин\nул. Штефан чел Маре 128\nКуда: Московский проспект 5/2",
  );

  ParsedOrder? _testResult;

  void _runTest() {
    final text = _testTextController.text.trim();
    if (text.isEmpty) return;
    final order = OrderParserService.parse(text);
    setState(() {
      _testResult = order;
    });
    LiveActivityService.processScannedOrder(order);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Расчёт выполнен и отправлен в Dynamic Island!'),
        backgroundColor: Colors.green,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Плавающая кнопка (AssistiveTouch)'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Приветственный баннер
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: Colors.amber.withValues(alpha: 0.2),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.touch_app, color: Colors.amber, size: 28),
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Считывание цены в 1 касание',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                            ),
                            Text(
                              'Работает поверх Яндекс Про на держателе',
                              style: TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Пока телефон зажат в автомобильном держателе, поверх экрана Яндекс Про размещается полупрозрачная точка. '
                    'При поступлении заказа вы делаете 1 легкий «тык» по точке — iPhone сканирует экран, сразу удаляет скриншот и '
                    'выводит чистую цену заказа и адреса в Dynamic Island!',
                    style: TextStyle(fontSize: 13, height: 1.4, color: Colors.white70),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Пошаговые карточки
          _buildStepCard(
            step: '1',
            title: 'Создайте команду в приложении «Команды» (Shortcuts)',
            description:
                '1. Откройте стандартное приложение «Команды» на iPhone.\n'
                '2. Нажмите «+» (Создать новую команду) и назовите её «TaxiRadar».\n'
                '3. Добавьте по порядку 4 быстрых действия:\n'
                '   • «Сделать снимок экрана»\n'
                '   • «Удалить фотографии [Снимок экрана]» (выключите тумблер «Спрашивать перед удалением»)\n'
                '   • «Извлечь текст из [Снимок экрана]»\n'
                '   • «Открыть URL»: taxiradar://order?text=[Извлеченный текст]',
            icon: Icons.alt_route,
          ),
          const SizedBox(height: 12),

          _buildStepCard(
            step: '2',
            title: 'Включите кнопку AssistiveTouch на iPhone',
            description:
                '1. Откройте «Настройки iPhone».\n'
                '2. Перейдите в: «Универсальный доступ» ➔ «Касание» ➔ «AssistiveTouch».\n'
                '3. Включите верхний тумблер «AssistiveTouch» — на экране появится плавающий кругляшок.\n'
                '4. Установите «Непрозрачность в покое» на 15–20%, чтобы кнопка стала полупрозрачной и не мешала карте.',
            icon: Icons.settings,
          ),
          const SizedBox(height: 12),

          _buildStepCard(
            step: '3',
            title: 'Назначьте команду на 1 касание',
            description:
                '1. В этом же меню AssistiveTouch найдите блок «Настройка действий».\n'
                '2. Нажмите «Одно касание».\n'
                '3. Пролистайте список в самый низ до раздела «Быстрые команды» и выберите «TaxiRadar».\n'
                '4. Готово! Теперь при нажатии на плавающую точку на экране сразу рассчитывается заказ.',
            icon: Icons.check_circle_outline,
          ),
          const SizedBox(height: 20),

          // Тестер сканирования
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.science, color: Colors.cyan, size: 20),
                      SizedBox(width: 8),
                      Text(
                        'Тестирование распознавания',
                        style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Вы можете вставить тестовый текст заказа или изменить его, чтобы проверить расчёт чистой цены:',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _testTextController,
                    maxLines: 4,
                    decoration: InputDecoration(
                      filled: true,
                      fillColor: Colors.black26,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                      hintText: 'Вставьте текст заказа с экрана Яндекс Про',
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.amber,
                        foregroundColor: Colors.black,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                      ),
                      onPressed: _runTest,
                      icon: const Icon(Icons.play_arrow),
                      label: const Text('Рассчитать и отправить в Остров'),
                    ),
                  ),
                  if (_testResult != null) ...[
                    const Divider(height: 24),
                    _buildResultCard(_testResult!),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStepCard({
    required String step,
    required String title,
    required String description,
    required IconData icon,
  }) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      color: const Color(0xFF1A2232),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 12,
                  backgroundColor: Colors.amber,
                  child: Text(
                    step,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.black),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                  ),
                ),
                Icon(icon, color: Colors.amber, size: 20),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: const TextStyle(fontSize: 12, height: 1.4, color: Colors.white70),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResultCard(ParsedOrder order) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.black38,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.green.withValues(alpha: 0.5)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Тариф: ${order.tariff}',
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: Colors.amber),
              ),
              Text(
                'Чистыми: ${order.netPrice.round()} MDL',
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: Colors.greenAccent),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text('📍 Подача: ${order.pointA}', style: const TextStyle(fontSize: 12)),
          Text('🏁 Куда: ${order.pointB}', style: const TextStyle(fontSize: 12)),
          const SizedBox(height: 6),
          Text(
            'Клиент: ${order.grossPrice.round()} MDL • Комиссия (28.5%): -${order.commissionAmount.round()} MDL',
            style: const TextStyle(fontSize: 11, color: Colors.grey),
          ),
        ],
      ),
    );
  }
}
