import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n/app_strings.dart';
import '../models/chat_message.dart';
import '../services/community_service.dart';
import '../ui/ds.dart';

/// Чат водителей — как на Android: ник на сервере, подгрузка новых, модерация админом.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final List<ChatMessage> _messages = [];
  String? _nickname;
  bool _admin = false;
  bool _muted = false;
  bool _loading = true;
  bool _sending = false;
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _load(initial: true);
    _poll = Timer.periodic(const Duration(seconds: 4), (_) => _load());
  }

  @override
  void dispose() {
    _poll?.cancel();
    _scroll.dispose();
    _input.dispose();
    super.dispose();
  }

  int get _lastId => _messages.isEmpty ? 0 : _messages.last.id;

  Future<void> _load({bool initial = false}) async {
    final page = await CommunityService.getChat(after: initial ? 0 : _lastId);
    if (!mounted || page == null) {
      if (mounted && initial) setState(() => _loading = false);
      return;
    }
    final atBottom = !_scroll.hasClients || _scroll.position.pixels >= _scroll.position.maxScrollExtent - 60;
    setState(() {
      _loading = false;
      _nickname = page.nickname;
      _admin = page.admin;
      _muted = page.muted;
      if (initial) _messages.clear();
      _messages.removeWhere((m) => page.deleted.contains(m.id));
      final known = _messages.map((m) => m.id).toSet();
      _messages.addAll(page.messages.where((m) => !known.contains(m.id)));
    });
    if (_messages.isNotEmpty) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt('last_seen_chat_id', _lastId);
    }
    if (initial || (atBottom && page.messages.isNotEmpty)) _toBottom(animated: !initial);
    if (initial && page.nickname == null && mounted) _askNickname();
  }

  void _toBottom({bool animated = true}) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      final end = _scroll.position.maxScrollExtent;
      animated
          ? _scroll.animateTo(end, duration: const Duration(milliseconds: 250), curve: Curves.easeOut)
          : _scroll.jumpTo(end);
    });
  }

  Future<void> _send() async {
    final text = _input.text.trim();
    if (text.isEmpty || _sending) return;
    if (_nickname == null) {
      _askNickname();
      return;
    }
    setState(() => _sending = true);
    final error = await CommunityService.sendChatMessage(text);
    if (!mounted) return;
    setState(() => _sending = false);
    if (error != null) {
      dsToast(context, error);
      return;
    }
    DS.success_();
    _input.clear();
    await _load();
    _toBottom();
  }

  void _askNickname() {
    final t = AppStrings.t;
    final controller = TextEditingController(text: _nickname ?? '');
    showCupertinoDialog(
      context: context,
      barrierDismissible: true,
      builder: (ctx) => CupertinoAlertDialog(
        title: Text(AppStrings.chatNickTitle),
        content: Padding(
          padding: const EdgeInsets.only(top: 12),
          child: CupertinoTextField(
            controller: controller,
            autofocus: true,
            placeholder: t('Например: Андрей Ботаника', 'De ex.: Andrei Botanica'),
            maxLength: 20,
          ),
        ),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(ctx), child: Text(t('Отмена', 'Anulează'))),
          CupertinoDialogAction(
            isDefaultAction: true,
            onPressed: () async {
              final nick = controller.text.trim();
              final error = await CommunityService.setChatNickname(nick);
              if (!ctx.mounted || !mounted) return;
              Navigator.pop(ctx);
              if (error != null) {
                dsToast(context, error);
              } else {
                setState(() => _nickname = nick);
              }
            },
            child: Text(t('Сохранить', 'Salvează')),
          ),
        ],
      ),
    );
  }

  void _moderate(ChatMessage m) {
    if (!_admin) return;
    final t = AppStrings.t;
    DS.impact();
    showCupertinoModalPopup(
      context: context,
      builder: (ctx) => CupertinoActionSheet(
        title: Text(m.author),
        message: Text(m.text, maxLines: 3, overflow: TextOverflow.ellipsis),
        actions: [
          CupertinoActionSheetAction(
            isDestructiveAction: true,
            onPressed: () => _doModerate(ctx, m, 'delete'),
            child: Text(t('Удалить сообщение', 'Șterge mesajul')),
          ),
          if (!m.mine)
            CupertinoActionSheetAction(
              isDestructiveAction: true,
              onPressed: () => _doModerate(ctx, m, 'mute'),
              child: Text(t('Заглушить автора', 'Blochează autorul')),
            ),
        ],
        cancelButton: CupertinoActionSheetAction(onPressed: () => Navigator.pop(ctx), child: Text(t('Отмена', 'Anulează'))),
      ),
    );
  }

  Future<void> _doModerate(BuildContext ctx, ChatMessage m, String action) async {
    Navigator.pop(ctx);
    final error = await CommunityService.moderateChat(m.id, action);
    if (!mounted) return;
    if (error != null) {
      dsToast(context, error);
    } else {
      await _load(initial: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    return Scaffold(
      backgroundColor: DS.bg(context),
      appBar: CupertinoNavigationBar(
        backgroundColor: DS.bg(context).withValues(alpha: 0.85),
        border: Border(bottom: BorderSide(color: DS.separator(context), width: 0.33)),
        middle: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(AppStrings.tileChat, style: DS.headline.copyWith(color: DS.label(context))),
            if (_nickname != null)
              Text(t('вы: $_nickname', 'dvs.: $_nickname'), style: DS.caption.copyWith(color: DS.label2(context))),
          ],
        ),
        trailing: CupertinoButton(
          padding: EdgeInsets.zero,
          onPressed: _askNickname,
          child: Icon(CupertinoIcons.person_crop_circle, color: DS.label(context)),
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: _loading
                ? const Center(child: CupertinoActivityIndicator())
                : _messages.isEmpty
                    ? Center(
                        child: Text(
                          t('Сообщений пока нет.\nНапишите первое!', 'Încă nu sunt mesaje.\nScrieți primul!'),
                          textAlign: TextAlign.center,
                          style: DS.subhead.copyWith(color: DS.label2(context)),
                        ),
                      )
                    : ListView.builder(
                        controller: _scroll,
                        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(DS.s12, DS.s12, DS.s12, DS.s8),
                        itemCount: _messages.length,
                        itemBuilder: (ctx, i) {
                          final m = _messages[i];
                          final prev = i > 0 ? _messages[i - 1] : null;
                          final grouped = prev != null && prev.author == m.author && prev.mine == m.mine;
                          return _Bubble(message: m, showAuthor: !grouped, onLongPress: () => _moderate(m));
                        },
                      ),
          ),
          _composer(),
        ],
      ),
    );
  }

  Widget _composer() {
    final t = AppStrings.t;
    return DSGlass(
      radius: BorderRadius.zero,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(DS.s12, DS.s8, DS.s8, DS.s8),
          child: _muted
              ? Padding(
                  padding: const EdgeInsets.all(DS.s8),
                  child: Text(t('Вам запрещено писать в чат', 'Vi s-a interzis să scrieți în chat'),
                      textAlign: TextAlign.center, style: DS.subhead.copyWith(color: DS.label2(context))),
                )
              : Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Expanded(
                      child: CupertinoTextField(
                        controller: _input,
                        placeholder: AppStrings.chatMessageHint,
                        minLines: 1,
                        maxLines: 5,
                        maxLength: 500,
                        textCapitalization: TextCapitalization.sentences,
                        style: DS.body.copyWith(color: DS.label(context)),
                        placeholderStyle: DS.body.copyWith(color: DS.label3(context)),
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                        decoration: BoxDecoration(
                          color: DS.card(context),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: DS.separator(context), width: 0.5),
                        ),
                      ),
                    ),
                    CupertinoButton(
                      padding: const EdgeInsets.only(left: DS.s8, bottom: 2),
                      minimumSize: const Size(36, 36),
                      onPressed: _sending ? null : _send,
                      child: _sending
                          ? const CupertinoActivityIndicator()
                          : Icon(CupertinoIcons.arrow_up_circle_fill, size: 34, color: DS.c(context, DS.info)),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _Bubble extends StatelessWidget {
  final ChatMessage message;
  final bool showAuthor;
  final VoidCallback onLongPress;
  const _Bubble({required this.message, required this.showAuthor, required this.onLongPress});

  @override
  Widget build(BuildContext context) {
    final m = message;
    final time = m.timestamp > 0 ? DateFormat('HH:mm').format(DateTime.fromMillisecondsSinceEpoch(m.timestamp)) : '';
    final mineColor = DS.c(context, DS.info);
    return Padding(
      padding: EdgeInsets.only(top: showAuthor ? DS.s8 : 2),
      child: Column(
        crossAxisAlignment: m.mine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          if (showAuthor && !m.mine)
            Padding(
              padding: const EdgeInsets.only(left: DS.s12, bottom: 2),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(m.author, style: DS.caption.copyWith(color: DS.label2(context), fontWeight: FontWeight.w600)),
                  if (m.admin) ...[
                    const SizedBox(width: 4),
                    Icon(CupertinoIcons.checkmark_seal_fill, size: 13, color: DS.c(context, DS.info)),
                  ],
                ],
              ),
            ),
          GestureDetector(
            onLongPress: onLongPress,
            child: Container(
              constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.76),
              padding: const EdgeInsets.fromLTRB(DS.s12, DS.s8, DS.s12, DS.s8),
              decoration: BoxDecoration(
                color: m.mine ? mineColor : DS.card(context),
                borderRadius: BorderRadius.circular(18),
              ),
              child: Wrap(
                alignment: WrapAlignment.end,
                crossAxisAlignment: WrapCrossAlignment.end,
                spacing: DS.s8,
                children: [
                  Text(m.text, style: DS.body.copyWith(color: m.mine ? Colors.white : DS.label(context))),
                  Text(time,
                      style: DS.caption.copyWith(
                          color: m.mine ? Colors.white.withValues(alpha: 0.7) : DS.label3(context), fontSize: 11)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
