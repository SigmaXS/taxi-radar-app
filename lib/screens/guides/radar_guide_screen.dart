import 'package:flutter/cupertino.dart';

import '../../l10n/app_strings.dart';
import '../../ui/ds.dart';
import '../radar_alerts_screen.dart';
import 'guide_widgets.dart';

/// Инструкция «Радар надбавки»: что показывает и где видно на iPhone.
class RadarGuideScreen extends StatelessWidget {
  const RadarGuideScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppStrings.t;
    return DSSubpage(
      title: t('Радар надбавки', 'Radarul adaosului'),
      children: [
        DSInset(
          child: Text(
            t(
              'Радар раз в минуту узнаёт у Яндекса надбавку там, где вы стоите, — та же цифра, что на кнопке «Принять» '
                  '(+15, +35, +55). Так видно, стоять или ехать туда, где спрос.',
              'Radarul află de la Yandex o dată pe minut adaosul acolo unde stați — aceeași cifră ca pe butonul «Acceptă» '
                  '(+15, +35, +55). Așa vedeți dacă să stați sau să mergeți unde e cerere.',
            ),
            style: DS.callout.copyWith(color: DS.label2(context), height: 1.35),
          ),
        ),
        GuideStep(
          n: 1,
          title: t('Включите радар', 'Porniți radarul'),
          text: t(
            'На главном экране нажмите «Включить радар» и разрешите уведомления и геолокацию. '
                'Без геолокации надбавку не показываем — чужая цифра только путает.',
            'Pe ecranul principal apăsați «Pornește radarul» și permiteți notificările și localizarea. '
                'Fără localizare adaosul nu se arată — o cifră străină doar încurcă.',
          ),
        ),
        GuideStep(
          n: 2,
          title: t('Смотрите на иконку', 'Priviți pictograma'),
          text: t(
            'Надбавка — красной цифрой на значке Taxi Radar. Нет цифры — надбавки нет.',
            'Adaosul — cifră roșie pe pictograma Taxi Radar. Fără cifră — fără adaos.',
          ),
          picture: const MockAppIcon(badge: '35'),
        ),
        GuideStep(
          n: 3,
          title: t('И на экране блокировки', 'Și pe ecranul de blocare'),
          text: t(
            'Одно тихое уведомление обновляется само раз в минуту, без звука.',
            'O notificare discretă se actualizează singură o dată pe minut, fără sunet.',
          ),
          picture: PhoneFrame(
            height: 120,
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Column(
                children: [
                  Text('14:32', style: DS.title2.copyWith(color: DS.label(context))),
                  const SizedBox(height: 8),
                  MockBanner(
                    icon: CupertinoIcons.dot_radiowaves_left_right,
                    color: DS.surge,
                    title: t('Эконом +35', 'Econom +35'),
                    body: t('Радар включён · обновлено 14:32', 'Radar pornit · actualizat 14:32'),
                  ),
                ],
              ),
            ),
          ),
        ),
        GuideStep(
          n: 4,
          title: t('Баннер, когда надбавка выросла', 'Banner când adaosul crește'),
          text: t(
            'Сверху приходит баннер со звуком. Когда сообщать — от +15, от +35, при падении — настраивается.',
            'Sus apare un banner cu sunet. Când să anunțe — de la +15, de la +35, la scădere — se setează.',
          ),
          picture: SizedBox(
            width: 250,
            child: MockBanner(
              icon: CupertinoIcons.flame_fill,
              color: DS.surge,
              title: t('Надбавка выросла: Эконом +35', 'Adaosul a crescut: Econom +35'),
              body: t('было +15', 'era +15'),
            ),
          ),
        ),
        GuideStep(
          n: 5,
          title: t('Соседние районы — на карте', 'Cartierele vecine — pe hartă'),
          text: t(
            'Вкладка «Карта»: нажмите на любой район или найдите адрес — флажок покажет надбавку там.',
            'Fila «Hartă»: apăsați pe orice cartier sau căutați adresa — stegulețul arată adaosul acolo.',
          ),
        ),
        DSSection(
          footer: t(
            'Пока радар включён, iPhone следит за местом в фоне (синяя стрелка вверху) — так надбавка обновляется и в свёрнутом виде. Батарея садится немного быстрее.',
            'Cât radarul e pornit, iPhone urmărește locul în fundal (săgeata albastră sus) — așa adaosul se actualizează și minimizat. Bateria se descarcă puțin mai repede.',
          ),
          children: [
            DSRow(
              icon: const DSIcon(CupertinoIcons.bell_fill, DS.danger),
              title: t('Настроить уведомления', 'Setați notificările'),
              onTap: () => dsPush(context, const RadarAlertsScreen()),
            ),
          ],
        ),
      ],
    );
  }
}
