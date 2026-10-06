import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../l10n/app_strings.dart';
import '../models/client_summary.dart';
import '../services/community_service.dart';
import '../services/phone_numbers.dart';
import '../ui/ds.dart';

/// «Клиенты»: проверка номера, отметки водителей и отзывы.
class ClientsScreen extends StatefulWidget {
  const ClientsScreen({super.key});

  @override
  State<ClientsScreen> createState() => _ClientsScreenState();
}

class _ClientsScreenState extends State<ClientsScreen> {
  final _phoneController = TextEditingController();
  final _reviewController = TextEditingController();

  ClientSummary? _summary;
  String _currentPhone = '';
  bool _isLoading = false;
  bool _isSavingTag = false;

  Future<void> _checkPhone() async {
    FocusScope.of(context).unfocus();
    // Сервер принимает только «+373…» — приводим «078 12 34 56» к нему, как Android.
    final phone = PhoneNumbers.normalize(_phoneController.text);
    if (phone == null) {
      dsToast(context, AppStrings.t('Введите номер, например 078123456', 'Introduceți numărul, de ex. 078123456'));
      return;
    }
    setState(() {
      _isLoading = true;
      _currentPhone = phone;
      _summary = null;
    });
    final res = await CommunityService.checkClient(phone);
    if (!mounted) return;
    setState(() {
      _summary = res;
      _isLoading = false;
    });
    if (res == null) dsToast(context, CommunityService.lastClientError);
  }

  Future<void> _toggleTag(String tagKey, bool currentState) async {
    if (_currentPhone.isEmpty) return;
    setState(() => _isSavingTag = true);
    final res = await CommunityService.tagClient(_currentPhone, tagKey, !currentState);
    if (!mounted) return;
    setState(() {
      if (res != null) _summary = res;
      _isSavingTag = false;
    });
    if (res == null) dsToast(context, CommunityService.lastClientError);
  }

  Future<void> _submitReview() async {
    final text = _reviewController.text.trim();
    if (_currentPhone.isEmpty || text.isEmpty) return;
    FocusScope.of(context).unfocus();
    final res = await CommunityService.reviewClient(_currentPhone, text);
    if (!mounted) return;
    if (res != null) {
      _reviewController.clear();
      setState(() => _summary = res);
      dsToast(context, AppStrings.t('Отзыв сохранён', 'Recenzia a fost salvată'));
    } else {
      dsToast(context, CommunityService.lastClientError);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    final s = _summary;
    final labels = AppStrings.isRu ? ClientSummary.tagLabelsRu : ClientSummary.tagLabelsRo;
    return DSSubpage(
      title: AppStrings.tileClients,
      children: [
        DSSection(
          header: AppStrings.clientsCheckTitle,
          footer: t('Номер видно в Яндекс Про, когда звоните клиенту.',
              'Numărul se vede în Yandex Pro când sunați clientul.'),
          children: [
            Padding(
              padding: const EdgeInsets.all(DS.s12),
              child: Column(
                children: [
                  DSField(
                    controller: _phoneController,
                    placeholder: AppStrings.clientsPhoneHint,
                    keyboardType: TextInputType.phone,
                    prefixIcon: CupertinoIcons.phone,
                    onSubmitted: (_) => _checkPhone(),
                  ),
                  const SizedBox(height: DS.s12),
                  DSButton(AppStrings.clientsCheck, icon: CupertinoIcons.search, loading: _isLoading, onPressed: _checkPhone),
                ],
              ),
            ),
          ],
        ),
        if (s != null) ...[
          DSSection(
            header: '${t('Отметки', 'Etichete')} · ${PhoneNumbers.pretty(_currentPhone)}',
            footer: t(
              'Нажмите, чтобы поставить или снять свою отметку. Плохие отметки видны, когда их поставили минимум двое.',
              'Apăsați ca să puneți sau să scoateți eticheta. Etichetele negative se văd după ce le pun cel puțin doi.',
            ),
            children: [
              Padding(
                padding: const EdgeInsets.all(DS.s12),
                child: Wrap(
                  spacing: DS.s8,
                  runSpacing: DS.s8,
                  children: labels.entries.map((e) {
                    final count = s.tags[e.key] ?? 0;
                    final mine = s.mine.contains(e.key);
                    final negative = ClientSummary.negativeTags.contains(e.key);
                    return _TagChip(
                      label: count > 0 ? '${e.value} · $count' : e.value,
                      selected: mine,
                      color: negative ? DS.danger : DS.success,
                      onTap: _isSavingTag ? null : () => _toggleTag(e.key, mine),
                    );
                  }).toList(),
                ),
              ),
            ],
          ),
          DSSection(
            header: t('Отзывы водителей', 'Recenziile șoferilor'),
            children: [
              if (s.reviews.isEmpty)
                DSRow(title: t('Отзывов пока нет', 'Încă nu sunt recenzii'), titleColor: DS.label2(context))
              else
                for (final r in s.reviews)
                  DSRow(
                    title: r.text,
                    subtitle: r.mine
                        ? t('Ваш отзыв', 'Recenzia dvs.')
                        : (r.admin ? t('Администратор', 'Administrator') : t('Водитель', 'Șofer')),
                  ),
              Padding(
                padding: const EdgeInsets.all(DS.s12),
                child: Row(
                  children: [
                    Expanded(child: DSField(controller: _reviewController, placeholder: AppStrings.clientsReviewHint, maxLines: 3)),
                    CupertinoButton(
                      padding: const EdgeInsets.only(left: DS.s8),
                      onPressed: _submitReview,
                      child: Icon(CupertinoIcons.arrow_up_circle_fill, size: 32, color: DS.c(context, DS.info)),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

/// Отметка-капсула: выбранная — залита цветом, иначе — серая.
class _TagChip extends StatelessWidget {
  final String label;
  final bool selected;
  final Color color;
  final VoidCallback? onTap;
  const _TagChip({required this.label, required this.selected, required this.color, this.onTap});

  @override
  Widget build(BuildContext context) {
    final c = DS.c(context, color);
    return GestureDetector(
      onTap: onTap == null
          ? null
          : () {
              DS.tap();
              onTap!();
            },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? c : DS.fill(context),
          borderRadius: BorderRadius.circular(100),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (selected) ...[const Icon(CupertinoIcons.checkmark_alt, size: 14, color: Colors.white), const SizedBox(width: 4)],
            Text(label, style: DS.subhead.copyWith(color: selected ? Colors.white : DS.label(context))),
          ],
        ),
      ),
    );
  }
}
