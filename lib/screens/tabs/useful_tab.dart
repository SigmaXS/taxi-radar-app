import 'package:flutter/material.dart';
import '../../l10n/app_strings.dart';
import '../../services/community_service.dart';
import '../airport_screen.dart';
import '../chat_screen.dart';
import '../clients_screen.dart';
import '../rides_screen.dart';

class UsefulTab extends StatefulWidget {
  final int unreadChat;
  final VoidCallback onChatOpened;

  const UsefulTab({
    super.key,
    required this.unreadChat,
    required this.onChatOpened,
  });

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
    if (mounted && status != null) {
      setState(() => _airportQueue = status.queue);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        Text(
          AppStrings.isRu ? 'Всё, что облегчает смену' : 'Totul ce ușurează munca',
          style: TextStyle(color: Colors.grey.shade400, fontSize: 14),
        ),
        const SizedBox(height: 12),

        // Чат водителей (с бейджем непрочитанных)
        _buildTile(
          icon: Icons.chat_bubble_rounded,
          iconColor: Colors.blueAccent,
          title: AppStrings.tileChat,
          subtitle: AppStrings.tileChatSub,
          badgeCount: widget.unreadChat,
          onTap: () {
            widget.onChatOpened();
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ChatScreen()),
            );
          },
        ),

        const SizedBox(height: 12),

        // Клиенты
        _buildTile(
          icon: Icons.people_alt_rounded,
          iconColor: Colors.purpleAccent,
          title: AppStrings.tileClients,
          subtitle: AppStrings.tileClientsSub,
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ClientsScreen()),
            );
          },
        ),

        const SizedBox(height: 12),

        // Аэропорт
        _buildTile(
          icon: Icons.local_airport_rounded,
          iconColor: Colors.amberAccent,
          title: AppStrings.tileAirport,
          subtitle: _airportQueue > 0
              ? '$_airportQueue ${AppStrings.airportQueueCaption}'
              : AppStrings.tileAirportSub,
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const AirportScreen()),
            );
          },
        ),

        const SizedBox(height: 12),

        // Попутчики
        _buildTile(
          icon: Icons.alt_route_rounded,
          iconColor: Colors.greenAccent,
          title: AppStrings.tileRides,
          subtitle: AppStrings.tileRidesSub,
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const RidesScreen()),
            );
          },
        ),
      ],
    );
  }

  Widget _buildTile({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String subtitle,
    int badgeCount = 0,
    required VoidCallback onTap,
  }) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      color: const Color(0xFF1E2638),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: iconColor.withValues(alpha: 0.15),
            shape: BoxShape.circle,
          ),
          child: Icon(icon, color: iconColor, size: 28),
        ),
        title: Text(
          title,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
        ),
        subtitle: Text(
          subtitle,
          style: TextStyle(color: Colors.grey.shade400, fontSize: 13),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (badgeCount > 0)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                margin: const EdgeInsets.only(right: 8),
                decoration: BoxDecoration(
                  color: Colors.redAccent,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  badgeCount > 99 ? '99+' : badgeCount.toString(),
                  style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold),
                ),
              ),
            const Icon(Icons.arrow_forward_ios_rounded, color: Colors.white30, size: 16),
          ],
        ),
        onTap: onTap,
      ),
    );
  }
}
