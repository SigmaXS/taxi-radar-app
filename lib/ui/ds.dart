import 'dart:ui' as ui;

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Taxi Radar — система дизайна для iOS.
///
/// Принципы: системные цвета iOS (светлая и тёмная тема), шрифт системы (SF Pro),
/// иконки SF-стиля (CupertinoIcons), списки «inset grouped» как в Настройках,
/// один фирменный цвет — жёлтый такси — и сиреневый только для надбавки.
///
/// Отступы: 4 · 8 · 12 · 16 · 20 · 24 · 32.  Скругления: 10 (списки), 14 (крупные
/// плашки), 12 (кнопки), капсула (чипы).  Тени — нет; глубина — фоном и blur.
class DS {
  // ---------- отступы ----------
  static const double s4 = 4, s8 = 8, s12 = 12, s16 = 16, s20 = 20, s24 = 24, s32 = 32;
  static const double gutter = 20; // поля списков inset grouped

  // ---------- скругления ----------
  static const double rList = 10, rCard = 14, rButton = 12, rIcon = 7;

  // ---------- цвета (динамические: светлая / тёмная) ----------
  static const brand = CupertinoDynamicColor.withBrightness(color: Color(0xFFFFCC00), darkColor: Color(0xFFFFD60A));

  /// Надбавка — как на Android (сиреневый).
  static const surge = CupertinoDynamicColor.withBrightness(color: Color(0xFFAF52DE), darkColor: Color(0xFFBF5AF2));
  static const success = CupertinoColors.systemGreen;
  static const danger = CupertinoColors.systemRed;
  static const info = CupertinoColors.systemBlue;
  static const telegram = Color(0xFF2AABEE);

  static Color c(BuildContext context, Color color) => CupertinoDynamicColor.resolve(color, context);

  static Color bg(BuildContext c) => DS.c(c, CupertinoColors.systemGroupedBackground);
  static Color card(BuildContext c) => DS.c(c, CupertinoColors.secondarySystemGroupedBackground);
  static Color fill(BuildContext c) => DS.c(c, CupertinoColors.tertiarySystemFill);
  static Color label(BuildContext c) => DS.c(c, CupertinoColors.label);
  static Color label2(BuildContext c) => DS.c(c, CupertinoColors.secondaryLabel);
  static Color label3(BuildContext c) => DS.c(c, CupertinoColors.tertiaryLabel);
  static Color separator(BuildContext c) => DS.c(c, CupertinoColors.separator);

  // ---------- типографика (стили iOS) ----------
  static const largeTitle = TextStyle(fontSize: 34, fontWeight: FontWeight.w700, letterSpacing: 0.37);
  static const title1 = TextStyle(fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: 0.36);
  static const title2 = TextStyle(fontSize: 22, fontWeight: FontWeight.w700, letterSpacing: 0.35);
  static const title3 = TextStyle(fontSize: 20, fontWeight: FontWeight.w600, letterSpacing: 0.38);
  static const headline = TextStyle(fontSize: 17, fontWeight: FontWeight.w600, letterSpacing: -0.41);
  static const body = TextStyle(fontSize: 17, fontWeight: FontWeight.w400, letterSpacing: -0.41);
  static const callout = TextStyle(fontSize: 16, fontWeight: FontWeight.w400, letterSpacing: -0.32);
  static const subhead = TextStyle(fontSize: 15, fontWeight: FontWeight.w400, letterSpacing: -0.24);
  static const footnote = TextStyle(fontSize: 13, fontWeight: FontWeight.w400, letterSpacing: -0.08);
  static const caption = TextStyle(fontSize: 12, fontWeight: FontWeight.w400);

  /// Крупная цифра (надбавка, цена) — моноширинные цифры, чтобы не «прыгали».
  static const display = TextStyle(
    fontSize: 56,
    fontWeight: FontWeight.w700,
    letterSpacing: -1.2,
    fontFeatures: [ui.FontFeature.tabularFigures()],
  );

  // ---------- тема приложения ----------
  static ThemeData theme(Brightness b) {
    final dark = b == Brightness.dark;
    final bgColor = dark ? const Color(0xFF000000) : const Color(0xFFF2F2F7);
    final cardColor = dark ? const Color(0xFF1C1C1E) : const Color(0xFFFFFFFF);
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFFFFCC00),
      brightness: b,
    ).copyWith(
      primary: dark ? const Color(0xFFFFD60A) : const Color(0xFF1C1C1E),
      onPrimary: dark ? Colors.black : Colors.white,
      surface: cardColor,
      onSurface: dark ? Colors.white : Colors.black,
    );
    return ThemeData(
      useMaterial3: true,
      brightness: b,
      colorScheme: scheme,
      scaffoldBackgroundColor: bgColor,
      splashFactory: NoSplash.splashFactory,
      highlightColor: Colors.transparent,
      appBarTheme: AppBarTheme(
        backgroundColor: bgColor,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        titleTextStyle: headline.copyWith(color: dark ? Colors.white : Colors.black),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: dark ? const Color(0xFF2C2C2E) : const Color(0xFF1C1C1E),
        contentTextStyle: subhead.copyWith(color: Colors.white),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(rCard)),
      ),
      dividerColor: dark ? const Color(0x5C545458) : const Color(0x4A3C3C43),
      cupertinoOverrideTheme: CupertinoThemeData(
        brightness: b,
        primaryColor: dark ? const Color(0xFFFFD60A) : const Color(0xFF007AFF),
      ),
    );
  }

  // ---------- тактильный отклик ----------
  static void tap() => HapticFeedback.selectionClick();
  static void impact() => HapticFeedback.mediumImpact();
  static void success_() => HapticFeedback.lightImpact();
}

/// Цветная «плитка» со значком — как в Настройках iOS.
class DSIcon extends StatelessWidget {
  final IconData icon;
  final Color color;
  final double size;
  const DSIcon(this.icon, this.color, {super.key, this.size = 29});

  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: DS.c(context, color),
          borderRadius: BorderRadius.circular(DS.rIcon),
        ),
        child: Icon(icon, size: size * 0.62, color: CupertinoColors.white),
      );
}

/// Экран с крупным заголовком (large title), который сжимается при прокрутке.
class DSPage extends StatelessWidget {
  final String title;
  final List<Widget> children;
  final Widget? trailing;
  final Widget? leading;
  final Future<void> Function()? onRefresh;
  final bool large;
  const DSPage({
    super.key,
    required this.title,
    required this.children,
    this.trailing,
    this.leading,
    this.onRefresh,
    this.large = true,
  });

  @override
  Widget build(BuildContext context) {
    final bg = DS.bg(context);
    final nav = large
        ? CupertinoSliverNavigationBar(
            largeTitle: Text(title),
            trailing: trailing,
            leading: leading,
            backgroundColor: bg.withValues(alpha: 0.82),
            border: null,
            stretch: true,
          )
        : null;
    return ColoredBox(
      color: bg,
      child: CustomScrollView(
        physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
        slivers: [
          ?nav,
          if (onRefresh != null) CupertinoSliverRefreshControl(onRefresh: onRefresh),
          SliverSafeArea(
            top: nav == null,
            sliver: SliverList(delegate: SliverChildListDelegate([...children, const SizedBox(height: DS.s32)])),
          ),
        ],
      ),
    );
  }
}

/// Вложенный экран: обычный заголовок по центру, кнопка «назад», фон списка.
class DSSubpage extends StatelessWidget {
  final String title;
  final List<Widget> children;
  final Widget? trailing;
  const DSSubpage({super.key, required this.title, required this.children, this.trailing});

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: DS.bg(context),
        body: CustomScrollView(
          physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
          slivers: [
            CupertinoSliverNavigationBar(
              largeTitle: Text(title),
              trailing: trailing,
              backgroundColor: DS.bg(context).withValues(alpha: 0.82),
              border: null,
              previousPageTitle: '',
            ),
            SliverSafeArea(
              top: false,
              sliver: SliverList(delegate: SliverChildListDelegate([...children, const SizedBox(height: DS.s32)])),
            ),
          ],
        ),
      );
}

/// Секция «inset grouped»: заголовок мелкими заглавными, строки, сноска.
class DSSection extends StatelessWidget {
  final String? header;
  final String? footer;
  final List<Widget> children;
  const DSSection({super.key, this.header, this.footer, required this.children});

  @override
  Widget build(BuildContext context) => CupertinoListSection.insetGrouped(
        margin: const EdgeInsets.fromLTRB(DS.gutter, DS.s8, DS.gutter, DS.s8),
        backgroundColor: DS.bg(context),
        decoration: BoxDecoration(color: DS.card(context), borderRadius: BorderRadius.circular(DS.rList)),
        header: header == null
            ? null
            : Text(header!.toUpperCase(), style: DS.footnote.copyWith(color: DS.label2(context))),
        footer: footer == null ? null : Text(footer!, style: DS.footnote.copyWith(color: DS.label2(context))),
        hasLeading: children.any((w) => w is DSRow && w.icon != null),
        children: children,
      );
}

/// Строка списка: значок, заголовок, подзаголовок, справа значение / переключатель / шеврон.
class DSRow extends StatelessWidget {
  final DSIcon? icon;
  final String title;
  final String? subtitle;
  final String? value;
  final Widget? trailing;
  final VoidCallback? onTap;
  final int badge;
  final Color? titleColor;
  const DSRow({
    super.key,
    this.icon,
    required this.title,
    this.subtitle,
    this.value,
    this.trailing,
    this.onTap,
    this.badge = 0,
    this.titleColor,
  });

  @override
  Widget build(BuildContext context) {
    final right = <Widget>[
      if (badge > 0)
        Container(
          constraints: const BoxConstraints(minWidth: 22),
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
          decoration: BoxDecoration(color: DS.c(context, DS.danger), borderRadius: BorderRadius.circular(11)),
          child: Text(badge > 99 ? '99+' : '$badge',
              textAlign: TextAlign.center,
              style: DS.footnote.copyWith(color: Colors.white, fontWeight: FontWeight.w600)),
        ),
      ?trailing,
      if (trailing == null && onTap != null) const CupertinoListTileChevron(),
    ];
    return CupertinoListTile.notched(
      leading: icon,
      title: Text(title, style: DS.body.copyWith(color: titleColor ?? DS.label(context))),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, maxLines: 3, style: DS.footnote.copyWith(color: DS.label2(context))),
      additionalInfo: value == null ? null : Text(value!, style: DS.body.copyWith(color: DS.label2(context))),
      trailing: right.isEmpty ? null : Row(mainAxisSize: MainAxisSize.min, children: _spaced(right)),
      backgroundColor: DS.card(context),
      backgroundColorActivated: DS.c(context, CupertinoColors.systemFill),
      onTap: onTap == null
          ? null
          : () {
              DS.tap();
              onTap!();
            },
    );
  }

  static List<Widget> _spaced(List<Widget> w) {
    final out = <Widget>[];
    for (var i = 0; i < w.length; i++) {
      if (i > 0) out.add(const SizedBox(width: 6));
      out.add(w[i]);
    }
    return out;
  }
}

/// Переключатель в строке списка.
class DSSwitchRow extends StatelessWidget {
  final DSIcon? icon;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  const DSSwitchRow({super.key, this.icon, required this.title, this.subtitle, required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) => DSRow(
        icon: icon,
        title: title,
        subtitle: subtitle,
        trailing: CupertinoSwitch(
          value: value,
          activeTrackColor: DS.c(context, DS.success),
          onChanged: (v) {
            DS.tap();
            onChanged(v);
          },
        ),
      );
}

/// Главная кнопка: заливка, высота 50, скругление 12, тактильный отклик.
class DSButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool destructive;
  final bool secondary;
  final bool loading;
  const DSButton(this.label,
      {super.key, this.icon, this.onPressed, this.destructive = false, this.secondary = false, this.loading = false});

  @override
  Widget build(BuildContext context) {
    final Color bg;
    final Color fg;
    if (secondary) {
      bg = DS.fill(context);
      fg = destructive ? DS.c(context, DS.danger) : DS.label(context);
    } else if (destructive) {
      bg = DS.c(context, DS.danger);
      fg = Colors.white;
    } else {
      bg = DS.c(context, DS.brand);
      fg = Colors.black;
    }
    return SizedBox(
      height: 50,
      width: double.infinity,
      child: CupertinoButton(
        padding: EdgeInsets.zero,
        color: bg,
        disabledColor: bg.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(DS.rButton),
        onPressed: onPressed == null || loading
            ? null
            : () {
                DS.impact();
                onPressed!();
              },
        child: loading
            ? CupertinoActivityIndicator(color: fg)
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (icon != null) ...[Icon(icon, size: 19, color: fg), const SizedBox(width: 8)],
                  Text(label, style: DS.headline.copyWith(color: fg)),
                ],
              ),
      ),
    );
  }
}

/// Поле ввода в стиле iOS.
class DSField extends StatelessWidget {
  final TextEditingController controller;
  final String placeholder;
  final TextInputType? keyboardType;
  final ValueChanged<String>? onSubmitted;
  final IconData? prefixIcon;
  final int maxLines;
  final TextCapitalization capitalization;
  const DSField({
    super.key,
    required this.controller,
    required this.placeholder,
    this.keyboardType,
    this.onSubmitted,
    this.prefixIcon,
    this.maxLines = 1,
    this.capitalization = TextCapitalization.none,
  });

  @override
  Widget build(BuildContext context) => CupertinoTextField(
        controller: controller,
        placeholder: placeholder,
        keyboardType: keyboardType,
        onSubmitted: onSubmitted,
        maxLines: maxLines,
        minLines: 1,
        textCapitalization: capitalization,
        style: DS.body.copyWith(color: DS.label(context)),
        placeholderStyle: DS.body.copyWith(color: DS.label3(context)),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        prefix: prefixIcon == null
            ? null
            : Padding(padding: const EdgeInsets.only(left: 10), child: Icon(prefixIcon, size: 19, color: DS.label2(context))),
        decoration: BoxDecoration(color: DS.fill(context), borderRadius: BorderRadius.circular(DS.rList)),
      );
}

/// Блок с отступами по краям списка — для произвольного содержимого.
class DSInset extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  const DSInset({super.key, required this.child, this.padding = const EdgeInsets.fromLTRB(DS.gutter, DS.s8, DS.gutter, DS.s8)});

  @override
  Widget build(BuildContext context) => Padding(padding: padding, child: child);
}

/// Плашка (карточка) с фоном списка — для крупного содержимого, не для каждой строки.
class DSCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  const DSCard({super.key, required this.child, this.padding = const EdgeInsets.all(DS.s16), this.onTap});

  @override
  Widget build(BuildContext context) {
    final box = Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(color: DS.card(context), borderRadius: BorderRadius.circular(DS.rCard)),
      child: child,
    );
    return DSInset(
      child: onTap == null
          ? box
          : GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () {
                DS.tap();
                onTap!();
              },
              child: box,
            ),
    );
  }
}

/// Полупрозрачная «стеклянная» подложка — для элементов поверх карты.
class DSGlass extends StatelessWidget {
  final Widget child;
  final BorderRadius radius;
  final EdgeInsets padding;
  const DSGlass({super.key, required this.child, this.radius = const BorderRadius.all(Radius.circular(DS.rCard)), this.padding = EdgeInsets.zero});

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: radius,
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 24, sigmaY: 24),
          child: Container(
            padding: padding,
            color: DS.card(context).withValues(alpha: 0.78),
            child: child,
          ),
        ),
      );
}

/// Короткое сообщение снизу (snackbar) с тактильным откликом.
void dsToast(BuildContext context, String text) {
  HapticFeedback.lightImpact();
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text), duration: const Duration(seconds: 3)));
}

/// Переход «как в iOS» (сдвиг справа, свайп назад).
Future<T?> dsPush<T>(BuildContext context, Widget page) =>
    Navigator.of(context).push<T>(CupertinoPageRoute(builder: (_) => page));
