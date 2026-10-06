import 'package:flutter/cupertino.dart';

import '../../l10n/app_strings.dart';
import '../../services/community_service.dart';
import '../../ui/ds.dart';
import '../airport_screen.dart';
import '../chat_screen.dart';
import '../clients_screen.dart';
import '../rides_screen.dart';

/// «Полезное» — те же четыре раздела, что и раньше, в стиле iOS.
class UsefulTab extends StatefulWidget {
  final int unreadChat;
  final VoidCallback onChatOpened;

  const UsefulTab({super.key, required this.unreadChat, required this.onChatOpened});

  @override
  State<UsefulTab> createState() => _UsefulTabState();
}

class _UsefulTabState extends State<UsefulTab> {
  int _airportQueue = 0;

  @override
  void initState() {
    super.initState();
    _fetchAirportQuickQueue();
  }

  Future<void> _fetchAirportQuickQueue() async {
    final status = await CommunityService.getAirportStatus();
    if (mounted && status != null) setState(() => _airportQueue = status.queue);
  }

  @override
  Widget build(BuildContext context) {
    return DSPage(
      title: AppStrings.navUseful,
      onRefresh: _fetchAirportQuickQueue,
      children: [
        DSSection(
          footer: AppStrings.t('Всё, что облегчает смену', 'Totul ce ușurează munca'),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.chat_bubble_2_fill, DS.info),
              title: AppStrings.tileChat,
              subtitle: AppStrings.tileChatSub,
              badge: widget.unreadChat,
              onTap: () {
                widget.onChatOpened();
                dsPush(context, const ChatScreen());
              },
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.person_2_fill, DS.surge),
              title: AppStrings.tileClients,
              subtitle: AppStrings.tileClientsSub,
              onTap: () => dsPush(context, const ClientsScreen()),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.airplane, CupertinoColors.systemOrange),
              title: AppStrings.tileAirport,
              subtitle: _airportQueue > 0 ? '$_airportQueue ${AppStrings.airportQueueCaption}' : AppStrings.tileAirportSub,
              onTap: () => dsPush(context, const AirportScreen()),
            ),
            DSRow(
              icon: const DSIcon(CupertinoIcons.arrow_branch, DS.success),
              title: AppStrings.tileRides,
              subtitle: AppStrings.tileRidesSub,
              onTap: () => dsPush(context, const RidesScreen()),
            ),
          ],
        ),
      ],
    );
  }
}
