import 'package:flutter/material.dart';

import 'package:url_launcher/url_launcher.dart';

import '../api.dart';
import '../site_info.dart';
import '../theme.dart';
import '../widgets/site_shell.dart';

/// El lienzo de una página de información: el contenido centrado y, al final
/// del todo, el pie del sitio.
class _InfoScroll extends StatelessWidget {
  const _InfoScroll({required this.footer, required this.children, this.maxWidth = 720});

  final Widget footer;
  final List<Widget> children;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final narrow = MediaQuery.sizeOf(context).width < 720;
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        child: ConstrainedBox(
          // En una página corta, el pie se queda abajo de la pantalla.
          constraints: BoxConstraints(minHeight: constraints.maxHeight),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Padding(
                padding: EdgeInsets.symmetric(horizontal: narrow ? 20 : 32, vertical: narrow ? 28 : 48),
                child: Center(
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: maxWidth),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
                  ),
                ),
              ),
              footer,
            ],
          ),
        ),
      ),
    );
  }
}

/// Reparte [children] en columnas iguales o, en pantallas estrechas, uno
/// debajo de otro.
class _Columns extends StatelessWidget {
  const _Columns({required this.children, this.gap = 20});

  final List<Widget> children;
  final double gap;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 640) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final child in children) ...[child, if (child != children.last) SizedBox(height: gap)],
            ],
          );
        }
        return IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final child in children) ...[
                Expanded(child: child),
                if (child != children.last) SizedBox(width: gap),
              ],
            ],
          ),
        );
      },
    );
  }
}

/// El sitio de una foto. Enseña el archivo de `assets/nosotros/` con ese
/// nombre y, mientras no exista, un recuadro que dice qué foto va ahí.
class SitePhoto extends StatelessWidget {
  const SitePhoto({super.key, required this.file, required this.hint, this.aspectRatio = 4 / 3});

  /// Nombre del archivo dentro de `assets/nosotros/`.
  final String file;
  final String hint;
  final double aspectRatio;

  @override
  Widget build(BuildContext context) {
    final empty = Container(
      alignment: Alignment.center,
      padding: const EdgeInsets.all(16),
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Palette.accentSoft, Color(0xFFFBF6E3)],
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.photo_camera_outlined, size: 30, color: Palette.accentText),
          const SizedBox(height: 8),
          Text(hint, textAlign: TextAlign.center, style: const TextStyle(color: Palette.accentText, fontSize: 13)),
        ],
      ),
    );
    return AspectRatio(
      aspectRatio: aspectRatio,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        // El recuadro va debajo: se ve mientras la foto carga y si no existe.
        child: Stack(
          fit: StackFit.expand,
          children: [
            empty,
            Image.asset(
              'assets/nosotros/$file',
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.icon, this.value, this.label);

  final IconData icon;
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(16)),
              child: Icon(icon, size: 26, color: Palette.ink),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(value,
                      style: const TextStyle(
                          fontSize: 30, fontWeight: FontWeight.w900, letterSpacing: -0.5, color: Palette.ink, height: 1.1)),
                  const SizedBox(height: 2),
                  Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(fontSize: 14)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Una promesa de la franja amarilla: sin tarjeta, sobre el fondo de la franja.
class _Promise extends StatelessWidget {
  const _Promise(this.icon, this.title, this.body);

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(12)),
          child: Icon(icon, size: 20, color: Palette.ink),
        ),
        const SizedBox(height: 14),
        Text(title, style: text.titleSmall?.copyWith(fontSize: 15)),
        const SizedBox(height: 4),
        Text(body, style: text.bodyMedium?.copyWith(height: 1.6)),
      ],
    );
  }
}

/// Una etiqueta corta de color: un pasaje, un horario.
class _Tag extends StatelessWidget {
  const _Tag(this.label, this.background, this.foreground);

  final String label;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(10)),
      child: Text(label, style: TextStyle(color: foreground, fontWeight: FontWeight.w700, fontSize: 14)),
    );
  }
}

/// El dibujo de un recorrido: tres paraderos unidos, el último en amarillo.
class _RouteLine extends StatelessWidget {
  const _RouteLine();

  @override
  Widget build(BuildContext context) {
    Widget stop(Color fill) => Container(
          width: 22,
          height: 22,
          decoration: BoxDecoration(
            color: fill,
            shape: BoxShape.circle,
            border: Border.all(color: Palette.ink, width: 4),
          ),
        );
    Widget segment() => Expanded(
          child: Container(
            height: 5,
            margin: const EdgeInsets.symmetric(horizontal: 6),
            decoration: BoxDecoration(color: Palette.ink, borderRadius: BorderRadius.circular(3)),
          ),
        );
    return Row(
      children: [stop(Palette.accentWash), segment(), stop(Palette.accentWash), segment(), stop(Palette.accent)],
    );
  }
}

/// Una de las tres tarjetas grandes de la portada: un ejemplo arriba y, abajo,
/// qué resuelve.
class _Showcase extends StatelessWidget {
  const _Showcase({
    required this.color,
    required this.title,
    required this.body,
    required this.top,
    this.dark = false,
    this.bordered = false,
  });

  final Color color;
  final String title;
  final String body;
  final Widget top;

  /// Sobre el negro, el texto va claro.
  final bool dark;
  final bool bordered;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 230),
      padding: const EdgeInsets.all(28),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(26),
        border: bordered ? Border.all(color: Palette.line) : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          top,
          const SizedBox(height: 24),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title,
                  style: TextStyle(fontSize: 21, fontWeight: FontWeight.w700, color: dark ? Colors.white : Palette.ink)),
              const SizedBox(height: 8),
              Text(body,
                  style: TextStyle(
                      fontSize: 15, height: 1.6, color: dark ? const Color(0xFFCFC9B6) : Palette.accentText)),
            ],
          ),
        ],
      ),
    );
  }
}

/// La app en un teléfono que asoma por abajo de la portada, sobre la pista,
/// con dos líneas de verdad del catálogo.
class _PhoneMock extends StatelessWidget {
  const _PhoneMock({required this.routes});

  final List<BusRoute> routes;

  @override
  Widget build(BuildContext context) {
    Widget field(String label, String value) => Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(color: const Color(0xFFF5F2E8), borderRadius: BorderRadius.circular(12)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(fontSize: 11, color: Palette.faint)),
              Text(value, style: const TextStyle(fontSize: 13)),
            ],
          ),
        );
    Widget line(BusRoute route, Color badge) => Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            border: Border.all(color: const Color(0xFFEDE8D8)),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                decoration: BoxDecoration(color: badge, borderRadius: BorderRadius.circular(8)),
                child: Text(route.letter, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 12)),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(route.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                    Text('S/ ${route.fare.toStringAsFixed(2)} · ${route.origin}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 12, color: Palette.faint)),
                  ],
                ),
              ),
            ],
          ),
        );

    final phone = Container(
      width: 250,
      height: 410,
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      decoration: const BoxDecoration(
        color: Palette.ink,
        borderRadius: BorderRadius.vertical(top: Radius.circular(36)),
      ),
      child: Container(
        padding: const EdgeInsets.fromLTRB(14, 18, 14, 0),
        clipBehavior: Clip.antiAlias,
        decoration: const BoxDecoration(
          color: Palette.surface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(26)),
        ),
        // Lo que no cabe se queda bajo el borde, como en una pantalla de verdad.
        child: SingleChildScrollView(
          physics: const NeverScrollableScrollPhysics(),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              field('Desde', 'Mi ubicación'),
              const SizedBox(height: 10),
              field('Hasta', routes.isEmpty ? 'Tu destino' : routes.first.destination),
              const SizedBox(height: 16),
              const Text('Te sirven',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: Palette.faint)),
              const SizedBox(height: 10),
              for (final (index, route) in routes.take(2).indexed) ...[
                line(route, index == 0 ? Palette.accent : const Color(0xFFFFE89A)),
                const SizedBox(height: 10),
              ],
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(color: Palette.accentWash, borderRadius: BorderRadius.circular(12)),
                child: const Text('Aviso de la calle: desvío reportado', style: TextStyle(fontSize: 12, height: 1.4)),
              ),
            ],
          ),
        ),
      ),
    );

    return SizedBox(
      height: 420,
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          // La pista, con sus rayas.
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 90,
            child: Container(
              decoration: const BoxDecoration(
                color: Palette.inkSoft,
                borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
              ),
              alignment: Alignment.center,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  for (var i = 0; i < 4; i++)
                    Container(
                      width: 44,
                      height: 6,
                      decoration: BoxDecoration(color: const Color(0xFFFFE27A), borderRadius: BorderRadius.circular(3)),
                    ),
                ],
              ),
            ),
          ),
          phone,
        ],
      ),
    );
  }
}

class AboutPage extends StatelessWidget {
  const AboutPage({super.key, required this.routes, required this.onPage});

  /// Las líneas publicadas, para contar cuánto hay ya en el mapa.
  final List<BusRoute> routes;
  final void Function(SitePage page) onPage;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final stops = routes.fold<int>(0, (total, route) => total + route.stops.length);
    // Una línea de verdad para los ejemplos; `null` mientras carga el catálogo.
    final sample = routes.isEmpty ? null : routes.first;

    Widget title(String kicker, String heading) => Padding(
          padding: const EdgeInsets.only(top: 56, bottom: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(kicker, style: text.labelSmall?.copyWith(color: Palette.accentText)),
              const SizedBox(height: 8),
              Text(heading, style: text.headlineMedium?.copyWith(fontSize: 26)),
            ],
          ),
        );

    final narrow = MediaQuery.sizeOf(context).width < 720;
    final intro = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
          decoration: BoxDecoration(color: Palette.surface, borderRadius: BorderRadius.circular(30)),
          child: const Text('Hecho en Trujillo', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
        ),
        const SizedBox(height: 20),
        Text('¿Este micro me lleva?',
            style: text.headlineLarge?.copyWith(fontSize: narrow ? 40 : 58, letterSpacing: -1.5, height: 1.04)),
        const SizedBox(height: 20),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: Text(
            'Subo nació de esa pregunta. Queremos que la respuesta esté en tu celular o en tu computadora, '
            'antes de salir de casa.',
            style: text.bodyMedium?.copyWith(color: Palette.accentText, fontSize: narrow ? 16 : 19, height: 1.6),
          ),
        ), 
        const SizedBox(height: 24),
        Wrap(
          spacing: 10,
          runSpacing: 10, 
          children: [
            FilledButton(onPressed: () => onPage(SitePage.map), child: const Text('Ver el mapa')),
            OutlinedButton(
              onPressed: () => onPage(SitePage.download),
              // Sobre el amarillo, el botón blanco no necesita borde.
              style: OutlinedButton.styleFrom(side: BorderSide.none),
              child: const Text('Descargar la app'),
            ),
          ],
        ),
      ],
    );

    return _InfoScroll(
      maxWidth: 1040,
      footer: SiteFooter(onPage: onPage),
      children: [
        // La portada: un bloque del amarillo de la marca, con la app asomando
        // por abajo sobre la pista.
        Container(
          clipBehavior: Clip.antiAlias,
          padding: EdgeInsets.fromLTRB(narrow ? 24 : 48, narrow ? 28 : 56, narrow ? 24 : 48, 0),
          decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(32)),
          child: _Columns(
            gap: 40,
            children: [
              Padding(padding: EdgeInsets.only(bottom: narrow ? 8 : 56), child: intro),
              _PhoneMock(routes: routes),
            ],
          ),
        ),
        SizedBox(height: narrow ? 24 : 40),
        _Columns(
          gap: 16,
          children: [
            _Stat(Icons.directions_bus_outlined, '${routes.length}', 'líneas en el mapa'),
            _Stat(Icons.place_outlined, '$stops', 'puntos de referencia'),
            const _Stat(Icons.sell_outlined, 'S/ 0', 'lo que cuesta usar Subo'),
          ],
        ),
        SizedBox(height: narrow ? 56 : 96),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 620),
          child: Text('Todo lo que necesitas saber antes de subir',
              style: text.headlineLarge?.copyWith(fontSize: narrow ? 30 : 40)),
        ),
        const SizedBox(height: 40),
        _Columns(
          children: [
            _Showcase(
              color: Palette.accentWash,
              title: 'Recorrido y paraderos',
              body: 'Por dónde pasa cada línea y dónde te puedes subir o bajar.',
              top: const _RouteLine(),
            ),
            _Showcase(
              color: Palette.ink,
              dark: true,
              title: 'Pasaje y horario',
              body: 'Cuánto cuesta y en qué horario pasa, antes de esperar en la esquina.',
              top: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _Tag(sample == null ? 'S/ 2.00' : 'S/ ${sample.fare.toStringAsFixed(2)}', Palette.accent, Palette.ink),
                  _Tag(sample == null || sample.schedule.isEmpty ? '05:00 – 22:00' : sample.schedule, Palette.inkSoft,
                      Colors.white),
                ],
              ),
            ),
            _Showcase(
              color: Palette.surface,
              bordered: true,
              title: 'Avisos de la calle',
              body: 'Desvíos y cambios que reporta la gente. Si ves algo distinto, cuéntanos y lo corregimos.',
              top: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(color: Palette.accentWash, borderRadius: BorderRadius.circular(14)),
                child: const Row(
                  children: [
                    Icon(Icons.warning_amber_rounded, size: 20, color: Palette.ink),
                    SizedBox(width: 10),
                    Expanded(child: Text('Tráfico, obras o desvíos, contados por pasajeros', style: TextStyle(fontSize: 14))),
                  ],
                ),
              ),
            ),
          ],
        ),
        title('SUBO POR DENTRO', 'Así se ve Subo'),
        const _Columns(
          children: [
            SitePhoto(file: 'galeria-1.jpg', hint: 'Foto 1'),
            SitePhoto(file: 'galeria-2.jpg', hint: 'Foto 2'),
            SitePhoto(file: 'galeria-3.jpg', hint: 'Foto 3'),
          ],
        ),
        title('EL EQUIPO', 'Quiénes estamos detrás'),
        _Columns(
          gap: 40,
          children: [
            const SitePhoto(file: 'equipo.jpg', hint: 'Foto del equipo', aspectRatio: 16 / 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'Somos un equipo de Trujillo que usa el micro todos los días. Cargamos y revisamos las rutas, '
                  'con ayuda de colaboradores que recorren la ciudad.',
                  style: text.bodyMedium?.copyWith(fontSize: 15, height: 1.7),
                ),
                const SizedBox(height: 14),
                Text(
                  'El transporte cambia seguido, así que puede haber diferencias con lo que ves en la calle: si '
                  'encuentras una, cuéntanoslo y la corregimos.',
                  style: text.bodyMedium?.copyWith(fontSize: 15, height: 1.7),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 60),
        // El compromiso: una franja del amarillo suave, con el título a un lado.
        Container(
          padding: EdgeInsets.all(narrow ? 24 : 48),
          decoration: BoxDecoration(color: Palette.accentWash, borderRadius: BorderRadius.circular(32)),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final heading = Text('Lo que no va a cambiar', style: text.headlineLarge?.copyWith(fontSize: 34));
              const promises = [
                _Promise(Icons.volunteer_activism_outlined, 'Gratis para pasajeros',
                    'Mirar las líneas y los avisos no cuesta nada, ni en la web ni en la app.'),
                _Promise(Icons.lock_outline, 'Tus datos son tuyos',
                    'No vendemos tus datos y solo pedimos lo necesario para que la cuenta funcione.'),
                _Promise(Icons.sync, 'Una sola cuenta', 'La misma cuenta sirve en el celular y en el navegador.'),
              ];
              if (constraints.maxWidth < 820) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [heading, const SizedBox(height: 24), const _Columns(gap: 28, children: promises)],
                );
              }
              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 3, child: heading),
                  const SizedBox(width: 40),
                  for (final promise in promises) ...[
                    Expanded(flex: 2, child: promise),
                    if (promise != promises.last) const SizedBox(width: 28),
                  ],
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

/// De dónde se baja la app de Android y cómo se instala.
class DownloadPage extends StatelessWidget {
  const DownloadPage({super.key, required this.onPage});

  final void Function(SitePage page) onPage;

  static bool get _ready => SiteInfo.apkUrl.isNotEmpty;

  // Una dirección relativa se resuelve contra la de la web.
  static Future<void> _download() => launchUrl(Uri.base.resolve(SiteInfo.apkUrl));

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final details = [
      'Para Android',
      if (SiteInfo.apkVersion.isNotEmpty) 'Versión ${SiteInfo.apkVersion}',
      if (SiteInfo.apkSize.isNotEmpty) SiteInfo.apkSize,
    ].join(' · ');

    final narrow = MediaQuery.sizeOf(context).width < 720;

    final intro = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text('Lleva Subo en tu celular',
            style: text.headlineLarge?.copyWith(fontSize: narrow ? 38 : 52, height: 1.06)),
        const SizedBox(height: 22),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 470),
          child: Text(
            'La app tiene todo lo de la web y además te ubica en el mapa, te dice cómo llegar y te avisa '
            'cuando te toca bajar.',
            style: text.bodyMedium?.copyWith(color: Palette.body, fontSize: 18, height: 1.65),
          ),
        ),
        const SizedBox(height: 22),
        Wrap(
          spacing: 16,
          runSpacing: 12,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            FilledButton.icon(
              onPressed: _ready ? _download : null,
              icon: const Icon(Icons.download, size: 18, color: Palette.accent),
              label: Text(_ready ? 'Descargar para Android' : 'Disponible pronto'),
            ),
            if (_ready) Text(details, style: text.bodySmall?.copyWith(fontSize: 14, color: Palette.muted)),
          ],
        ),
        if (!_ready) ...[
          const SizedBox(height: 10),
          Text('Estamos preparando la descarga. Mientras tanto puedes usar Subo desde esta web.',
              style: text.bodySmall?.copyWith(fontSize: 14, color: Palette.muted)),
        ],
      ],
    );

    return _InfoScroll(
      maxWidth: 1100,
      footer: SiteFooter(onPage: onPage),
      children: [
        _Columns(
          gap: 56,
          children: [intro, const SitePhoto(file: 'app.jpg', hint: 'Captura de la app')],
        ),
        const SizedBox(height: 72),
        // Lo que solo tiene la app, en una franja blanca.
        Container(
          width: double.infinity,
          padding: EdgeInsets.all(narrow ? 24 : 40),
          decoration: BoxDecoration(
            color: Palette.surface, 
            borderRadius: BorderRadius.circular(26),
            border: Border.all(color: Palette.line),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Lo que ganas al instalarla', style: text.headlineMedium),
              const SizedBox(height: 36),
              const _Columns(
                gap: 40,
                children: [
                  _Gain(Icons.near_me_outlined, 'Líneas cerca de ti',
                      'Abre la app y mira qué micros pasan por donde estás parado.'),
                  _Gain(Icons.route_outlined, '¿A dónde vas?',
                      'Busca un lugar y te dice qué micro tomar, dónde subir y cuánto caminar.'),
                  _Gain(Icons.notifications_none, 'Aviso de llegada',
                      'Te avisa cuando estás por llegar, para que no te pases.'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 80),
        Text('Cómo instalarla', style: text.headlineMedium),
        const SizedBox(height: 8),
        Text('Toma menos de un minuto.', style: text.bodyLarge?.copyWith(color: Palette.muted)),
        const SizedBox(height: 60),
        const _Columns(
          gap: 0,
          children: [
            _Step(1, 'Descarga el archivo',
                'Toca el botón de arriba desde tu celular Android. Se baja un archivo que termina en .apk.'),
            _Step(2, 'Permite la instalación',
                'Al abrirlo, Android te pedirá permiso para instalar apps de este origen. Acéptalo.'),
            _Step(3, 'Entra con tu cuenta', 'Abre Subo y usa la misma cuenta que en la web, o crea una ahí mismo.'),
          ],
        ),
        const SizedBox(height: 40),
        // El cierre: la misma descarga, en negro.
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 28),
          decoration: BoxDecoration(color: Palette.ink, borderRadius: BorderRadius.circular(22)),
          child: Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 20,
            runSpacing: 20,
            children: [
              const Text('¿Listo? Descárgala y prueba hoy tu ruta.',
                  style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700)),
              FilledButton(
                onPressed: _ready ? _download : null,
                style: FilledButton.styleFrom(
                  backgroundColor: Palette.accent,
                  foregroundColor: Palette.ink,
                  textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                ),
                child: const Text('Descargar para Android'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Algo que solo da la app: un icono en un círculo amarillo y su explicación.
class _Gain extends StatelessWidget {
  const _Gain(this.icon, this.title, this.body);

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: const BoxDecoration(color: Palette.accent, shape: BoxShape.circle),
          child: Icon(icon, size: 20, color: Palette.ink),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 4),
              Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              Text(body, style: const TextStyle(fontSize: 15, height: 1.6, color: Palette.body)),
            ],
          ),
        ),
      ],
    );
  }
}

/// Un paso de la instalación: su número en un círculo negro montado sobre la
/// línea amarilla que une los tres.
class _Step extends StatelessWidget {
  const _Step(this.number, this.title, this.body);

  final int number;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(border: Border(top: BorderSide(color: Palette.accent, width: 3))),
      padding: const EdgeInsets.only(right: 28, bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // El círculo se sube para quedar a caballo de la línea.
          Transform.translate(
            offset: const Offset(0, -23),
            child: Container(
              width: 40,
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Palette.ink,
                shape: BoxShape.circle,
                border: Border.all(color: Palette.paper, width: 4),
              ),
              child: Text('$number', style: const TextStyle(color: Palette.accent, fontWeight: FontWeight.w700)),
            ),
          ),
          Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          Text(body, style: const TextStyle(fontSize: 15, height: 1.6, color: Palette.body)),
        ],
      ),
    );
  }
}

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key, required this.onPage});

  final void Function(SitePage page) onPage;

  @override
  Widget build(BuildContext context) {
    return _LegalPage(
      page: SitePage.privacy,
      onPage: onPage,
      title: 'Qué datos usamos y para qué',
      lead: 'Te lo contamos sin letra pequeña. Si algo no queda claro, escríbenos desde Contacto.',
      summary: const [
        'No vendemos ni alquilamos tus datos.',
        'No guardamos un historial de por dónde te mueves.',
        'Puedes pedir que borremos tu cuenta cuando quieras.',
      ],
      sections: const [
        (
          'Datos de tu cuenta',
          'Al crear una cuenta guardamos tu nombre, tu correo y tu contraseña. La contraseña se guarda cifrada: '
              'nadie del equipo puede leerla. Si entras con Google, recibimos tu nombre, tu correo y tu foto de perfil.',
        ),
        (
          'Tu ubicación',
          'La app usa tu ubicación para mostrarte las líneas que pasan cerca y para avisarte cuando llegas a tu '
              'punto de referencia. No guardamos un historial de por dónde te mueves. Cuando publicas una alerta, sí se guarda '
              'el punto del mapa donde la dejaste, porque es parte del aviso.',
        ),
        (
          'Lo que publicas',
          'Las alertas, las calificaciones y las sugerencias que envías quedan asociadas a tu cuenta. En las '
              'alertas, otras personas ven tu nombre y tu foto de perfil.',
        ),
        (
          'Asistente y revisión de fotos',
          'Las preguntas que le haces al asistente y las fotos que subes se envían a un servicio de inteligencia '
              'artificial de Google (Gemini) para responderte y para comprobar que la foto es apropiada. No envíes ahí '
              'datos personales o sensibles.',
        ),
        (
          'Con quién compartimos',
          'No vendemos ni alquilamos tus datos. Usamos proveedores para funcionar: el servidor y la base de datos '
              '(Supabase), el alojamiento de la web (Cloudflare) y los mapas (OpenStreetMap).',
        ),
        (
          'Tus derechos',
          'Puedes pedir que corrijamos o eliminemos tu cuenta y tus datos cuando quieras, conforme a la Ley de '
              'Protección de Datos Personales del Perú (Ley N.º 29733). Escríbenos desde Contacto y lo atendemos.',
        ),
      ],
    );
  }
}

/// Una página legal: arriba se cambia entre Privacidad y Términos, a la
/// izquierda va el índice y, a la derecha, el texto.
class _LegalPage extends StatefulWidget {
  const _LegalPage({
    required this.page,
    required this.onPage,
    required this.title,
    required this.lead,
    required this.sections,
    this.summary = const [],
  });

  /// Cuál de las dos se está leyendo.
  final SitePage page;
  final void Function(SitePage page) onPage;
  final String title;
  final String lead;

  /// Lo esencial en tres líneas; vacío si la página no lo lleva.
  final List<String> summary;

  /// Cada apartado: su título y su texto.
  final List<(String, String)> sections;

  @override
  State<_LegalPage> createState() => _LegalPageState();
}

class _LegalPageState extends State<_LegalPage> {
  late final _anchors = [for (final _ in widget.sections) GlobalKey()];

  /// El apartado al que se saltó desde el índice.
  int _current = 0;

  void _jump(int index) {
    setState(() => _current = index);
    final target = _anchors[index].currentContext;
    if (target != null) {
      Scrollable.ensureVisible(target, duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final narrow = MediaQuery.sizeOf(context).width < 720;

    Widget tab(SitePage page, String label) {
      final active = page == widget.page;
      return active
          ? FilledButton(
              onPressed: null,
              style: FilledButton.styleFrom(
                disabledBackgroundColor: Palette.ink,
                disabledForegroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w400),
              ),
              child: Text(label),
            )
          : OutlinedButton(
              onPressed: () => widget.onPage(page),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w400),
              ),
              child: Text(label),
            );
    }

    final index = Container(
      decoration: const BoxDecoration(border: Border(left: BorderSide(color: Palette.line, width: 2))),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Padding(
            padding: EdgeInsets.only(left: 16, bottom: 8),
            child: Text('En esta página', style: TextStyle(fontSize: 13, color: Palette.faint)),
          ),
          for (final (i, section) in widget.sections.indexed)
            InkWell(
              onTap: () => _jump(i),
              child: Container(
                // La marca amarilla tapa la línea gris del índice.
                transform: Matrix4.translationValues(-2, 0, 0),
                decoration: BoxDecoration(
                  border: Border(
                    left: BorderSide(color: i == _current ? Palette.accent : Colors.transparent, width: 2),
                  ),
                ),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Text(
                  section.$1,
                  style: TextStyle(
                    fontSize: 15,
                    color: i == _current ? Palette.ink : Palette.muted,
                    fontWeight: i == _current ? FontWeight.w500 : FontWeight.w400,
                  ),
                ),
              ),
            ),
        ],
      ),
    );

    final article = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (widget.summary.isNotEmpty) ...[
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 22),
            decoration: BoxDecoration(color: Palette.accentWash, borderRadius: BorderRadius.circular(18)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('En corto', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                const SizedBox(height: 10),
                for (final line in widget.summary)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(line, style: const TextStyle(fontSize: 15, height: 1.55, color: Palette.accentText)),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 36),
        ],
        for (final (i, section) in widget.sections.indexed) ...[
          Text(section.$1, key: _anchors[i], style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w700)),
          const SizedBox(height: 10),
          Text(section.$2, style: const TextStyle(fontSize: 16, height: 1.75, color: Color(0xFF3A3528))),
          const SizedBox(height: 36),
        ],
        const Divider(),
        const SizedBox(height: 24),
        Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: 16,
          runSpacing: 12,
          children: [
            const Text('¿Te quedó alguna duda?', style: TextStyle(fontSize: 15, color: Palette.body)),
            FilledButton(
              onPressed: () => widget.onPage(SitePage.contact),
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16)),
              child: const Text('Escríbenos'),
            ),
          ],
        ),
      ],
    );

    return _InfoScroll(
      maxWidth: 1050,
      footer: SiteFooter(onPage: widget.onPage),
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [tab(SitePage.privacy, 'Privacidad'), tab(SitePage.terms, 'Términos de uso')],
        ),
        const SizedBox(height: 28),
        Text(widget.title, style: text.headlineLarge?.copyWith(fontSize: narrow ? 34 : 46)),
        const SizedBox(height: 12),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 640),
          child: Text(widget.lead, style: text.bodyMedium?.copyWith(color: Palette.body, fontSize: 18, height: 1.6)),
        ),
        const SizedBox(height: 44),
        if (narrow)
          article
        else
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: 240, child: index),
              const SizedBox(width: 56),
              Expanded(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 680), child: article)),
            ],
          ),
      ],
    );
  }
}

class TermsPage extends StatelessWidget {
  const TermsPage({super.key, required this.onPage});

  final void Function(SitePage page) onPage;

  @override
  Widget build(BuildContext context) {
    return _LegalPage(
      page: SitePage.terms,
      onPage: onPage,
      title: 'Las reglas para usar Subo',
      lead: 'Al usar Subo aceptas estas condiciones. Son pocas y están pensadas para que el servicio le sirva a todos.',
      sections: const [
        (
          'La información es una guía',
          'Los recorridos, horarios y pasajes son referenciales. Las empresas de transporte pueden cambiarlos sin '
              'aviso, y Subo no opera ningún micro. Confirma en el punto de referencia cuando tengas dudas.',
        ),
        (
          'Tu cuenta',
          'Eres responsable de lo que se hace con tu cuenta. Usa una contraseña que no compartas y avísanos si '
              'crees que alguien más entró con ella.',
        ),
        (
          'Lo que publicas',
          'Publica solo avisos reales y relacionados con el transporte y la calle. No se permite contenido falso, '
              'ofensivo, violento u obsceno, ni datos personales de otras personas. Podemos retirar publicaciones y '
              'suspender cuentas que no respeten esto.',
        ),
        (
          'Uso correcto',
          'No intentes entrar a partes del servicio que no te corresponden, ni copiar el catálogo de forma masiva, '
              'ni interferir con su funcionamiento.',
        ),
        (
          'Cambios',
          'Podemos actualizar estos términos y el servicio. Si el cambio es importante, lo avisaremos en la app o '
              'en esta web.',
        ),
      ],
    );
  }
}

/// Cómo escribirle a Subo: un formulario que llega al buzón del administrador
/// y, al lado, el correo, WhatsApp y las redes.
class ContactPage extends StatefulWidget {
  const ContactPage({
    super.key,
    required this.api,
    required this.signedIn,
    required this.onSignIn,
    required this.onError,
    required this.onPage,
  });

  final ApiClient api;
  final bool signedIn;
  final VoidCallback onSignIn;
  final void Function(Object error) onError;
  final void Function(SitePage page) onPage;

  @override
  State<ContactPage> createState() => _ContactPageState();
}

class _ContactPageState extends State<ContactPage> {
  static const _subjects = ['Una ruta está mal', 'Falta una línea', 'Problema con mi cuenta', 'Sugerencia', 'Otro'];

  final _form = GlobalKey<FormState>();
  final _message = TextEditingController();
  String _subject = _subjects.first;
  bool _busy = false;
  bool _sent = false;

  @override
  void dispose() {
    _message.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    if (_busy || !_form.currentState!.validate()) return;
    // El buzón pide cuenta: lo escrito se queda esperando mientras se entra.
    if (!widget.signedIn) {
      widget.onSignIn();
      return;
    }
    setState(() => _busy = true);
    try {
      await widget.api.sendFeedback(subject: _subject, message: _message.text);
      if (!mounted) return;
      setState(() {
        _sent = true;
        _message.clear();
      });
    } catch (error) {
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _formCard(TextTheme text) {
    final Widget body;
    if (_sent) {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.check_circle_outline, color: Palette.success),
              const SizedBox(width: 10),
              Expanded(child: Text('Mensaje enviado. Gracias por ayudarnos a mejorar.', style: text.titleMedium)),
            ],
          ),
          const SizedBox(height: 16),
          OutlinedButton(onPressed: () => setState(() => _sent = false), child: const Text('Escribir otro mensaje')),
        ],
      );
    } else {
      body = Form(
        key: _form,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('¿Sobre qué es?', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
            const SizedBox(height: 12),
            // El asunto se elige tocando una pastilla: la elegida va en negro.
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final subject in _subjects)
                  subject == _subject
                      ? FilledButton(
                          onPressed: _busy ? null : () {},
                          style: FilledButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w400),
                          ),
                          child: Text(subject),
                        )
                      : OutlinedButton(
                          onPressed: _busy ? null : () => setState(() => _subject = subject),
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                            textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w400),
                          ),
                          child: Text(subject),
                        ),
              ],
            ),
            const SizedBox(height: 22),
            const Text('Tu mensaje', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            TextFormField(
              controller: _message,
              enabled: !_busy,
              minLines: 6,
              maxLines: 12,
              maxLength: 1000,
              decoration: const InputDecoration(hintText: 'Por ejemplo: la línea B ya no entra por la avenida…'),
              validator: (value) => (value ?? '').trim().isEmpty ? 'Escribe tu mensaje' : null,
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 14,
              runSpacing: 10,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                FilledButton(onPressed: _busy ? null : _send, child: Text(_busy ? 'Enviando…' : 'Enviar mensaje')),
                if (!widget.signedIn)
                  Text('Te pediremos entrar con tu cuenta para enviarlo.',
                      style: text.bodySmall?.copyWith(fontSize: 14, color: Palette.muted)),
              ],
            ),
          ],
        ),
      );
    }
    return Card(child: Padding(padding: const EdgeInsets.all(28), child: body));
  }

  /// A quién le llega el mensaje, en una tarjeta del amarillo de la marca.
  Widget _teamCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(22)),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Llega directo al equipo', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          SizedBox(height: 8),
          Text(
            'Tu mensaje lo lee el administrador de Subo. Si es una ruta, mientras más detalle (calle, hora, '
            'línea), más rápido la corregimos.',
            style: TextStyle(fontSize: 15, height: 1.6, color: Palette.accentText),
          ),
        ],
      ),
    );
  }

  /// Los otros medios: los que ya existen se pueden abrir; los que faltan se
  /// anuncian como pastillas.
  Widget _channelsCard(TextTheme text) {
    final ready = ContactChannel.all.where((channel) => channel.ready).toList();
    final soon = ContactChannel.all.where((channel) => !channel.ready).toList();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (ready.isNotEmpty) ...[
              const Text('También por aquí', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
              const SizedBox(height: 6),
              for (final channel in ready)
                InkWell(
                  onTap: channel.open,
                  borderRadius: BorderRadius.circular(10),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: Row(
                      children: [
                        Container(
                          width: 38,
                          height: 38,
                          decoration: const BoxDecoration(color: Palette.accentSoft, shape: BoxShape.circle),
                          child: Icon(channel.icon, size: 19, color: Palette.ink),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(channel.label, style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 14)),
                              Text(channel.value,
                                  style: text.bodySmall?.copyWith(fontSize: 13), overflow: TextOverflow.ellipsis),
                            ],
                          ),
                        ),
                        const Icon(Icons.open_in_new, size: 16, color: Palette.muted),
                      ],
                    ),
                  ),
                ),
              if (soon.isNotEmpty) const SizedBox(height: 14),
            ],
            if (soon.isNotEmpty) ...[
              const Text('Pronto también en', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
              const SizedBox(height: 14),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final channel in soon)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                      decoration: BoxDecoration(color: Palette.chip, borderRadius: BorderRadius.circular(30)),
                      child: Text(channel.label, style: const TextStyle(fontSize: 14, color: Palette.muted)),
                    ),
                ],
              ),
            ],
            const Divider(height: 28),
            Row(
              children: [
                const Icon(Icons.place_outlined, size: 18, color: Palette.muted),
                const SizedBox(width: 8),
                Expanded(child: Text('Trujillo, La Libertad, Perú', style: text.bodySmall?.copyWith(fontSize: 13))),
              ],
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final narrow = MediaQuery.sizeOf(context).width < 720;
    final aside = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [_teamCard(), const SizedBox(height: 16), _channelsCard(text)],
    );
    return _InfoScroll(
      maxWidth: 1000,
      footer: SiteFooter(onPage: widget.onPage),
      children: [
        Text('Escríbenos', style: text.headlineLarge?.copyWith(fontSize: narrow ? 36 : 46)),
        const SizedBox(height: 10),
        Text('¿Una ruta cambió, falta una línea o tienes una idea? Cuéntanos y lo revisamos.',
            style: text.bodyMedium?.copyWith(color: Palette.body, fontSize: 18, height: 1.6)),
        const SizedBox(height: 36),
        LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth < 760) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [_formCard(text), const SizedBox(height: 20), aside],
              );
            }
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 5, child: _formCard(text)),
                const SizedBox(width: 24),
                Expanded(flex: 3, child: aside),
              ],
            );
          },
        ),
      ],
    );
  }
}
