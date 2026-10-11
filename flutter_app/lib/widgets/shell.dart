import 'package:flutter/material.dart';

import '../theme.dart';

/// El marco común de todas las pantallas: la barra lateral de la marca y la
/// cabecera con la cuenta. En pantallas estrechas la barra lateral se queda en
/// una franja arriba.
class AdminShell extends StatelessWidget {
  const AdminShell({
    super.key,
    required this.child,
    this.accountName,
    this.onSignOut,
    this.onViewSite,
    this.admin = false,
    this.signOutLabel = 'Cerrar sesión',
    this.section = AdminSection.routes,
    this.sections = AdminSection.values,
    this.onSection,
  });

  final Widget child;

  /// `null` antes de entrar.
  final String? accountName;
  final VoidCallback? onSignOut;

  /// Sale del panel a la web de los pasajeros, sin cerrar sesión.
  final VoidCallback? onViewSite;

  /// `true` con una cuenta de administrador: el marco enseña el panel. Si no,
  /// es la web de los pasajeros.
  final bool admin;

  /// Lo que dice el botón de salir: quien mira sin cuenta va a iniciar sesión.
  final String signOutLabel;

  /// La sección que se está viendo.
  final AdminSection section;

  /// Las secciones a las que esta cuenta puede ir.
  final List<AdminSection> sections;

  /// `null` antes de entrar: sin sesión no hay a dónde ir.
  final void Function(AdminSection section)? onSection;

  static const _wideBreakpoint = 900.0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= _wideBreakpoint;
          final content = Column(
            children: [
              _Header(
                accountName: accountName,
                onSignOut: onSignOut,
                onViewSite: onViewSite,
                signOutLabel: signOutLabel,
                admin: admin,
                compact: !wide,
              ),
              const Divider(),
              Expanded(child: child),
            ],
          );
          if (!wide) {
            return Column(
              children: [
                _Brand(compact: true, admin: admin),
                // Sin barra lateral, las secciones van en una franja bajo la marca.
                if (onSection != null)
                  Container(
                    width: double.infinity,
                    color: Palette.sidebar,
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                    // Son varias: en una pantalla estrecha se deslizan de lado.
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          for (final item in sections) ...[
                            _NavItem(
                              label: item.label,
                              icon: item.icon,
                              active: item == section,
                              onTap: () => onSection!(item),
                            ),
                            if (item != sections.last) const SizedBox(width: 8),
                          ],
                        ],
                      ),
                    ),
                  ),
                Expanded(child: content),
              ],
            );
          }
          return Row(
            children: [
              SizedBox(
                width: 230,
                child: _Sidebar(section: section, sections: sections, onSection: onSection, admin: admin),
              ),
              Expanded(
                child: Padding(padding: const EdgeInsets.symmetric(horizontal: 44), child: content),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Las secciones del panel.
enum AdminSection {
  routes('Rutas y micros', Icons.north_east),
  alerts('Alertas', Icons.report_outlined),
  feedback('Sugerencias', Icons.mail_outline),
  stats('Estadísticas', Icons.insights),
  team('Administradores', Icons.admin_panel_settings_outlined),
  profile('Mi perfil', Icons.person_outline);

  const AdminSection(this.label, this.icon);

  final String label;
  final IconData icon;
}

/// Una sección en la navegación; la activa va resaltada.
class _NavItem extends StatelessWidget {
  const _NavItem({required this.label, required this.icon, required this.active, required this.onTap});

  final String label;
  final IconData icon;
  final bool active;

  /// `null` si no hay otra sección a la que ir.
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: active ? Palette.sidebarActive : Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: BorderSide(color: active ? const Color(0xFF54471F) : Colors.transparent),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(icon, size: 16, color: active ? Palette.accent : Palette.sidebarMuted),
              const SizedBox(width: 12),
              Flexible(
                child: Text(label,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: active ? Colors.white : Palette.sidebarMuted,
                      fontWeight: FontWeight.w600,
                    )),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Sidebar extends StatelessWidget {
  const _Sidebar({required this.section, required this.sections, required this.onSection, required this.admin});

  final bool admin;
  final AdminSection section;
  final List<AdminSection> sections;
  final void Function(AdminSection section)? onSection;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Palette.sidebar,
      padding: const EdgeInsets.fromLTRB(24, 36, 24, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _Brand(compact: false, admin: admin),
          const SizedBox(height: 60),
          Text('TU CIUDAD, CONECTADA',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Palette.sidebarMuted, fontSize: 9)),
          const SizedBox(height: 16),
          // Los pasajeros solo tienen las líneas; las secciones son del panel.
          if (onSection == null)
            const _NavItem(label: 'Líneas', icon: Icons.alt_route, active: true, onTap: null)
          else
            for (final item in sections) ...[
              _NavItem(label: item.label, icon: item.icon, active: item == section, onTap: () => onSection!(item)),
              const SizedBox(height: 6),
            ],
          const Spacer(),
          const Row(
            children: [
              CircleAvatar(radius: 3, backgroundColor: Palette.accent),
              SizedBox(width: 8),
              Flexible(
                child: Text('Trujillo, La Libertad',
                    overflow: TextOverflow.ellipsis, style: TextStyle(color: Color(0xFFD6CDB0), fontSize: 12)),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const Text(
            'Un buen viaje empieza\ncon una buena ruta.',
            style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w500, height: 1.5),
          ),
        ],
      ),
    );
  }
}

class _Brand extends StatelessWidget {
  const _Brand({required this.compact, required this.admin});

  final bool compact;
  final bool admin;

  @override
  Widget build(BuildContext context) {
    final brand = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: compact ? 36 : 43,
          height: compact ? 36 : 43,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(13)),
          child: const Text('S', style: TextStyle(color: Palette.ink, fontWeight: FontWeight.w800, fontSize: 15)),
        ),
        const SizedBox(width: 12),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Subo',
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: compact ? 17 : 19)),
              const SizedBox(height: 3),
              Text(admin ? 'ADMINISTRACIÓN' : 'TRUJILLO',
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Color(0xFFCFC5A3), fontSize: 10, letterSpacing: 2)),
            ],
          ),
        ),
      ],
    );
    if (!compact) return brand;
    return Container(
      width: double.infinity,
      color: Palette.sidebar,
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      child: brand,
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({
    required this.accountName,
    required this.onSignOut,
    required this.onViewSite,
    required this.signOutLabel,
    required this.admin,
    required this.compact,
  });

  final String? accountName;
  final VoidCallback? onSignOut;
  final VoidCallback? onViewSite;
  final String signOutLabel;
  final bool admin;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: compact ? 60 : 88,
      padding: EdgeInsets.symmetric(horizontal: compact ? 20 : 0),
      child: Row(
        children: [
          Expanded(
            child: Text(admin ? 'CENTRO DE CONTROL' : 'TRANSPORTE PÚBLICO DE TRUJILLO',
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Palette.accentText)),
          ),
          // La cuenta va pegada a la derecha y, si el nombre es largo, se recorta.
          // En una pantalla estrecha deja su sitio a los botones.
          if (!compact || onViewSite == null)
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 260),
              child: Text(
                accountName ?? '',
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Palette.muted, fontSize: 12),
              ),
            ),
          if (onViewSite != null) ...[
            const SizedBox(width: 12),
            TextButton(onPressed: onViewSite, child: const Text('Ver la web')),
          ],
          if (onSignOut != null) ...[
            const SizedBox(width: 12),
            TextButton(onPressed: onSignOut, child: Text(signOutLabel)),
          ],
        ],
      ),
    );
  }
}
