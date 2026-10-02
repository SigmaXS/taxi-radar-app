import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class YandexKeyScreen extends StatefulWidget {
  const YandexKeyScreen({super.key});

  @override
  State<YandexKeyScreen> createState() => _YandexKeyScreenState();
}

class _YandexKeyScreenState extends State<YandexKeyScreen> {
  final TextEditingController _keyController = TextEditingController();
  bool _hasSavedKey = false;

  @override
  void initState() {
    super.initState();
    _loadKey();
  }

  Future<void> _loadKey() async {
    final prefs = await SharedPreferences.getInstance();
    final k = prefs.getString('custom_yandex_key') ?? '';
    setState(() {
      _keyController.text = k;
      _hasSavedKey = k.isNotEmpty;
    });
  }

  Future<void> _saveKey() async {
    final k = _keyController.text.trim();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('custom_yandex_key', k);
    setState(() => _hasSavedKey = k.isNotEmpty);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(k.isNotEmpty ? 'Ключ Яндекс API сохранён!' : 'Ключ удалён')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF121826),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E2638),
        elevation: 0,
        title: const Text('Ключ Яндекс API', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        children: [
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
                      Icon(
                        _hasSavedKey ? Icons.check_circle_rounded : Icons.info_outline_rounded,
                        color: _hasSavedKey ? Colors.greenAccent : Colors.amberAccent,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        _hasSavedKey ? 'Ключ подключён' : 'Используется общий сервер',
                        style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'По умолчанию адреса ищет сервер Taxi Radar. Вы можете указать свой личный бесплатный ключ «API Геокодера» из Кабинета разработчика Яндекса (1000 запросов в сутки).',
                    style: TextStyle(color: Colors.white70, fontSize: 13, height: 1.4),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _keyController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'API ключ Яндекса',
                      labelStyle: TextStyle(color: Colors.grey.shade400),
                      hintText: '1a2b3c4d-xxxx-xxxx-xxxx-...',
                      hintStyle: TextStyle(color: Colors.white24),
                      filled: true,
                      fillColor: const Color(0xFF2A364F),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.amber.shade700,
                            foregroundColor: Colors.black,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                          ),
                          onPressed: _saveKey,
                          child: const Text('Сохранить ключ', style: TextStyle(fontWeight: FontWeight.bold)),
                        ),
                      ),
                      if (_hasSavedKey) ...[
                        const SizedBox(width: 8),
                        IconButton(
                          icon: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent),
                          onPressed: () {
                            _keyController.clear();
                            _saveKey();
                          },
                        ),
                      ],
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'Инструкция по получению:',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
          ),
          const SizedBox(height: 8),
          const Text(
            '1. Перейдите на developer.tech.yandex.ru\n'
            '2. Нажмите «Подключить API» → «API Геокодера».\n'
            '3. Выберите бесплатный тариф и скопируйте созданный ключ сюда.',
            style: TextStyle(color: Colors.white60, fontSize: 13, height: 1.5),
          ),
        ],
      ),
    );
  }
}
