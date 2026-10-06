import 'package:flutter/material.dart';
import '../services/phone_numbers.dart';
import '../l10n/app_strings.dart';
import '../models/client_summary.dart';
import '../services/community_service.dart';

class ClientsScreen extends StatefulWidget {
  const ClientsScreen({super.key});

  @override
  State<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends State<ClientsScreen> {
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _reviewController = TextEditingController();

  ClientSummary? _summary;
  String _currentPhone = '';
  bool _isLoading = false;
  bool _isSavingTag = false;

  Future<void> _checkPhone() async {
    // Сервер принимает только «+373…» — приводим «078 12 34 56» к нему, как Android.
    final phone = PhoneNumbers.normalize(_phoneController.text);
    if (phone == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.isRu ? 'Введите номер, например 078123456' : 'Introduceți numărul, de ex. 078123456')),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _currentPhone = phone;
      _summary = null;
    });

    final res = await CommunityService.checkClient(phone);
    if (mounted) {
      setState(() {
        _summary = res;
        _isLoading = false;
      });
      if (res == null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(CommunityService.lastClientError)));
      }
    }
  }

  Future<void> _toggleTag(String tagKey, bool currentState) async {
    if (_currentPhone.isEmpty) return;
    setState(() => _isSavingTag = true);
    final res = await CommunityService.tagClient(_currentPhone, tagKey, !currentState);
    if (mounted) {
      setState(() {
        _summary = res;
        _isSavingTag = false;
      });
    }
  }

  Future<void> _submitReview() async {
    final text = _reviewController.text.trim();
    if (_currentPhone.isEmpty || text.isEmpty) return;

    _reviewController.clear();
    final res = await CommunityService.reviewClient(_currentPhone, text);
    if (mounted) {
      setState(() => _summary = res);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Отзыв сохранён!')),
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
        title: Text(AppStrings.tileClients, style: const TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        children: [
          // Поле поиска номера
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            color: const Color(0xFF1E2638),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    AppStrings.clientsCheckTitle,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _phoneController,
                          keyboardType: TextInputType.phone,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            hintText: AppStrings.clientsPhoneHint,
                            hintStyle: TextStyle(color: Colors.grey.shade400),
                            filled: true,
                            fillColor: const Color(0xFF2A364F),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                          ),
                          onSubmitted: (_) => _checkPhone(),
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.amber.shade700,
                          foregroundColor: Colors.black,
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        onPressed: _isLoading ? null : _checkPhone,
                        child: _isLoading
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black),
                              )
                            : Text(AppStrings.clientsCheck, style: const TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          if (_summary != null) ...[
            const SizedBox(height: 16),

            // Отметки о клиенте
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              color: const Color(0xFF1E2638),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Отметки: $_currentPhone',
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: ClientSummary.tagLabelsRu.entries.map((e) {
                        final count = _summary!.tags[e.key] ?? 0;
                        final isMine = _summary!.mine.contains(e.key);
                        final isNeg = ClientSummary.negativeTags.contains(e.key);

                        return FilterChip(
                          avatar: isMine ? const Icon(Icons.check, size: 16, color: Colors.white) : null,
                          label: Text(
                            count > 0 ? '${e.value} ($count)' : e.value,
                            style: TextStyle(
                              color: isMine ? Colors.white : (count > 0 ? Colors.amberAccent : Colors.grey.shade300),
                              fontWeight: isMine ? FontWeight.bold : FontWeight.normal,
                            ),
                          ),
                          selected: isMine,
                          selectedColor: isNeg ? Colors.redAccent.shade700 : Colors.blueAccent.shade700,
                          backgroundColor: const Color(0xFF2A364F),
                          onSelected: _isSavingTag ? null : (_) => _toggleTag(e.key, isMine),
                        );
                      }).toList(),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 14),

            // Отзывы
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              color: const Color(0xFF1E2638),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Отзывы водителей', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 12),
                    if (_summary!.reviews.isEmpty)
                      const Text('Отзывов пока нет.', style: TextStyle(color: Colors.white54))
                    else
                      ..._summary!.reviews.map((r) => Container(
                            margin: const EdgeInsets.only(bottom: 8),
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: const Color(0xFF2A364F),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(r.text, style: const TextStyle(color: Colors.white, fontSize: 14)),
                                const SizedBox(height: 4),
                                Text(
                                  r.mine ? 'Ваш отзыв' : (r.admin ? 'Администратор' : 'Водитель'),
                                  style: TextStyle(color: Colors.white.withValues(alpha: 0.4), fontSize: 11),
                                ),
                              ],
                            ),
                          )),
                    const Divider(color: Colors.white10, height: 24),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _reviewController,
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              hintText: AppStrings.clientsReviewHint,
                              hintStyle: TextStyle(color: Colors.grey.shade400),
                              filled: true,
                              fillColor: const Color(0xFF2A364F),
                              contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          icon: const Icon(Icons.send_rounded, color: Colors.amberAccent),
                          onPressed: _submitReview,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
