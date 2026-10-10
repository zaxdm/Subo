import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../site_info.dart';
import '../theme.dart';

/// Las páginas de la web pública.
enum SitePage {
  map('Mapa'),
  about('Nosotros'),
  download('Descargar app'),
  privacy('Privacidad'),
  terms('Términos'),
  contact('Contacto');

  const SitePage(this.label);

  final String label;
}

/// Un medio para escribirle a Subo: el correo, WhatsApp o una red.
class ContactChannel {
  const ContactChannel(this.label, this.icon, this.value, this.link);

  final String label;
  final IconData icon;

  /// Lo que se lee; vacío si todavía no se definió.
  final String value;

  /// A dónde lleva al tocarlo; `null` si todavía no se definió.
  final Uri? link;

  bool get ready => link != null;

  Future<void> open() async {
    final link = this.link;
    if (link != null) await launchUrl(link, mode: LaunchMode.externalApplication);
  }

  /// Los medios de [SiteInfo], estén ya definidos o no.
  static List<ContactChannel> get all => [
        ContactChannel(
          'Correo',
          Icons.mail_outline,
          SiteInfo.email,
          SiteInfo.email.isEmpty ? null : Uri(scheme: 'mailto', path: SiteInfo.email),
        ),
        ContactChannel(
          'WhatsApp',
          Icons.chat_outlined,
          SiteInfo.whatsapp.isEmpty ? '' : '+${SiteInfo.whatsapp}',
          SiteInfo.whatsapp.isEmpty ? null : Uri.parse('https://wa.me/${SiteInfo.whatsapp}'),
        ),
        _network('Facebook', Icons.facebook, SiteInfo.facebook),
        _network('Instagram', Icons.camera_alt_outlined, SiteInfo.instagram),
        _network('TikTok', Icons.music_note_outlined, SiteInfo.tiktok),
      ];

  static ContactChannel _network(String label, IconData icon, String url) {
    final link = url.isEmpty ? null : Uri.tryParse(url);
    return ContactChannel(label, icon, link == null ? '' : link.host + link.path, link);
  }
}

/// El marco de la web de los pasajeros: la barra de arriba con la marca y la
/// cuenta y, en el mapa, una franja de pie siempre a la vista. Las páginas de
/// texto llevan el pie completo ([SiteFooter]) al final de su contenido.
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
  final SitePage page;
  final void Function(SitePage page) onPage;

  /// `null` sin sesión.
  final String? accountName;
  final VoidCallback onSignIn;
  final VoidCallback? onSignOut;

  /// Las páginas que caben en la barra de arriba; las legales van en el pie.
  static const _navPages = [SitePage.map, SitePage.about, SitePage.download, SitePage.contact];

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
              // El mapa ocupa toda la pantalla, así que su pie es una franja.
              if (page == SitePage.map) _FooterBar(onPage: onPage, wide: wide),
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
            child: const _Brand(),
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

class _Brand extends StatelessWidget {
  const _Brand();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 34,
          height: 34,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(10)),
          child: const Text('S', style: TextStyle(color: Palette.ink, fontWeight: FontWeight.w700, fontSize: 15)),
        ),
        const SizedBox(width: 10),
        const Text('Subo', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 18)),
      ],
    );
  }
}

/// Un enlace del pie, claro sobre el fondo oscuro.
class _FooterLink extends StatelessWidget {
  const _FooterLink(this.label, this.onTap);

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onTap,
      style: TextButton.styleFrom(
        foregroundColor: const Color(0xFFE9E1C6),
        padding: const EdgeInsets.symmetric(horizontal: 10),
        minimumSize: const Size(0, 36),
        alignment: Alignment.centerLeft,
        textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
      ),
      child: Text(label),
    );
  }
}

/// El pie del mapa: una franja oscura con las páginas de información.
class _FooterBar extends StatelessWidget {
  const _FooterBar({required this.onPage, required this.wide});

  final void Function(SitePage page) onPage;
  final bool wide;

  static const _pages = [SitePage.about, SitePage.download, SitePage.privacy, SitePage.terms, SitePage.contact];

  @override
  Widget build(BuildContext context) {
    final links = Wrap(
      alignment: WrapAlignment.center,
      children: [for (final item in _pages) _FooterLink(item.label, () => onPage(item))],
    );
    return Container(
      width: double.infinity,
      color: Palette.sidebar,
      padding: EdgeInsets.symmetric(horizontal: wide ? 28 : 8, vertical: 8),
      child: wide
          ? Row(
              children: [
                const Text('© 2026 Subo · Hecho en Trujillo, Perú',
                    style: TextStyle(color: Palette.sidebarMuted, fontSize: 13)),
                const Spacer(),
                links,
              ],
            )
          : links,
    );
  }
}

/// El pie completo, al final de las páginas de texto: la marca, las páginas
/// del sitio y cómo escribirle a Subo.
class SiteFooter extends StatelessWidget {
  const SiteFooter({super.key, required this.onPage});

  final void Function(SitePage page) onPage;

  @override
  Widget build(BuildContext context) {
    Widget column(String title, List<Widget> children) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 10, bottom: 6),
              child: Text(title,
                  style: const TextStyle(color: Palette.accent, fontSize: 12, fontWeight: FontWeight.w700)),
            ),
            ...children,
          ],
        );

    final about = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _Brand(),
        const SizedBox(height: 14),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 300),
          child: const Text(
            'Las líneas de micro de Trujillo en un solo mapa: recorrido, pasaje, horario y avisos de la calle.',
            style: TextStyle(color: Palette.sidebarMuted, fontSize: 13, height: 1.6),
          ),
        ),
      ],
    );
    final site = column('El sitio', [
      for (final item in [SitePage.map, SitePage.about, SitePage.download, SitePage.contact])
        _FooterLink(item.label, () => onPage(item)),
    ]);
    final legal = column('Legal', [
      for (final item in [SitePage.privacy, SitePage.terms]) _FooterLink(item.label, () => onPage(item)),
    ]);
    final channels = column('Escríbenos', [
      for (final channel in ContactChannel.all.where((channel) => channel.ready))
        _FooterLink('${channel.label}: ${channel.value}', channel.open),
      _FooterLink('Formulario de contacto', () => onPage(SitePage.contact)),
    ]);

    return Container(
      width: double.infinity,
      color: Palette.sidebar,
      padding: const EdgeInsets.fromLTRB(28, 40, 28, 20),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1040),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Wrap(
                spacing: 56,
                runSpacing: 28,
                children: [about, site, legal, channels],
              ),
              const SizedBox(height: 28),
              const Divider(color: Color(0xFF3A3115)),
              const SizedBox(height: 14),
              const Text('© 2026 Subo · Hecho en Trujillo, Perú',
                  style: TextStyle(color: Palette.sidebarMuted, fontSize: 12)),
            ],
          ),
        ),
      ),
    );
  }
}
