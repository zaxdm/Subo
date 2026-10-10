import 'package:flutter/material.dart';

import '../theme.dart';

/// Las páginas de la web pública.
enum SitePage {
  map('Mapa'),
  about('Nosotros'),
  privacy('Privacidad'),
  terms('Términos'),
  contact('Contacto');

  const SitePage(this.label);

  final String label;
}

/// El marco de la web de los pasajeros: la barra de arriba con la marca y la
/// cuenta, y el pie con las páginas de información.
class SiteShell extends StatelessWidget {
  const SiteShell({
    super.key,
    required this.child,
    required this.page,
    required this.onPage,
    required this.onSignIn,
    this.accountName,
    this.onSignOut,
  });

  final Widget child;

  /// La página que se está viendo; `null` en el acceso.
  final SitePage? page;
  final void Function(SitePage page) onPage;

  /// `null` con sesión iniciada.
  final String? accountName;
  final VoidCallback onSignIn;
  final VoidCallback? onSignOut;

  /// Las páginas que caben en la barra de arriba; las legales van en el pie.
  static const _navPages = [SitePage.map, SitePage.about, SitePage.contact];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 760;
          return Column(
            children: [
              _navbar(wide),
              Expanded(child: child),
              _Footer(onPage: onPage, wide: wide),
            ],
          );
        },
      ),
    );
  }

  Widget _navbar(bool wide) {
    final name = accountName;
    return Container(
      height: 60,
      color: Palette.sidebar,
      padding: EdgeInsets.symmetric(horizontal: wide ? 28 : 14),
      child: Row(
        children: [
          InkWell(
            onTap: () => onPage(SitePage.map),
            borderRadius: BorderRadius.circular(10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 34,
                  height: 34,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(10)),
                  child: const Text('S', style: TextStyle(color: Palette.ink, fontWeight: FontWeight.w800, fontSize: 15)),
                ),
                const SizedBox(width: 10),
                const Text('Subo', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18)),
              ],
            ),
          ),
          if (wide) ...[
            const SizedBox(width: 28),
            for (final item in _navPages)
              TextButton(
                onPressed: () => onPage(item),
                style: TextButton.styleFrom(
                  foregroundColor: item == page ? Palette.accent : const Color(0xFFD6CDB0),
                ),
                child: Text(item.label),
              ),
          ],
          const Spacer(),
          if (name != null) ...[
            if (wide)
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 200),
                child: Text(name,
                    overflow: TextOverflow.ellipsis, style: const TextStyle(color: Color(0xFFD6CDB0), fontSize: 13)),
              ),
            TextButton(
              onPressed: onSignOut,
              style: TextButton.styleFrom(foregroundColor: Palette.accent),
              child: const Text('Cerrar sesión'),
            ),
          ] else
            FilledButton(
              onPressed: onSignIn,
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12)),
              child: const Text('Iniciar sesión'),
            ),
          if (!wide)
            PopupMenuButton<SitePage>(
              tooltip: 'Menú',
              icon: const Icon(Icons.menu, color: Colors.white),
              onSelected: onPage,
              itemBuilder: (_) => [for (final item in _navPages) PopupMenuItem(value: item, child: Text(item.label))],
            ),
        ],
      ),
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({required this.onPage, required this.wide});

  final void Function(SitePage page) onPage;
  final bool wide;

  static const _pages = [SitePage.about, SitePage.privacy, SitePage.terms, SitePage.contact];

  @override
  Widget build(BuildContext context) {
    final links = Wrap(
      alignment: WrapAlignment.center,
      children: [
        for (final item in _pages)
          TextButton(
            onPressed: () => onPage(item),
            style: TextButton.styleFrom(
              foregroundColor: Palette.muted,
              padding: const EdgeInsets.symmetric(horizontal: 10),
              minimumSize: const Size(0, 34),
              textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
            ),
            child: Text(item.label),
          ),
      ],
    );
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        color: Palette.surface,
        border: Border(top: BorderSide(color: Palette.line)),
      ),
      padding: EdgeInsets.symmetric(horizontal: wide ? 28 : 8, vertical: 4),
      child: wide
          ? Row(
              children: [
                const Text('© 2026 Subo · Hecho en Trujillo, Perú', style: TextStyle(color: Palette.muted, fontSize: 12)),
                const Spacer(),
                links,
              ],
            )
          : links,
    );
  }
}
