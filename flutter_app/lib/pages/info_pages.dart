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

/// El encabezado de una página: de qué va, el título y una entrada.
class _Heading extends StatelessWidget {
  const _Heading({required this.kicker, required this.title, required this.lead});

  final String kicker;
  final String title;
  final String lead;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final narrow = MediaQuery.sizeOf(context).width < 720;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(kicker, style: text.labelSmall?.copyWith(color: Palette.accentText)),
        const SizedBox(height: 10),
        Text(title, style: narrow ? text.headlineMedium : text.headlineLarge),
        const SizedBox(height: 12),
        Text(lead, style: text.bodyMedium?.copyWith(color: Palette.muted, fontSize: 16, height: 1.7)),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(this.title, this.body);

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: text.titleMedium?.copyWith(fontSize: 17)),
          const SizedBox(height: 8),
          Text(body, style: text.bodyMedium?.copyWith(height: 1.7)),
        ],
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
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 22),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(14)),
              child: Icon(icon, size: 22, color: Palette.accentText),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(value,
                      style: const TextStyle(
                          fontSize: 28, fontWeight: FontWeight.w700, letterSpacing: -0.5, color: Palette.ink, height: 1.1)),
                  const SizedBox(height: 2),
                  Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(fontSize: 13)),
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

class _Feature extends StatelessWidget {
  const _Feature(this.icon, this.title, this.body);

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(12)),
              child: Icon(icon, size: 21, color: Palette.accentText),
            ),
            const SizedBox(height: 16),
            Text(title, style: text.titleMedium),
            const SizedBox(height: 6),
            Text(body, style: text.bodyMedium?.copyWith(color: Palette.muted, height: 1.6)),
          ],
        ),
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
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(color: Palette.surface, borderRadius: BorderRadius.circular(30)),
          child: Text('SUBO · TRUJILLO', style: text.labelSmall?.copyWith(color: Palette.ink)),
        ),
        const SizedBox(height: 16),
        Text('Hecho en Trujillo, para moverse por Trujillo',
            style: (narrow ? text.headlineMedium : text.headlineLarge)?.copyWith(color: Palette.ink)),
        const SizedBox(height: 14),
        Text(
          'Subo nació de una pregunta que todos hemos hecho en la calle: «¿este micro me lleva?». '
          'Queremos que la respuesta esté en tu celular o en tu computadora, antes de salir de casa.',
          style: text.bodyMedium?.copyWith(color: Palette.ink, fontSize: 16, height: 1.7),
        ),
        const SizedBox(height: 24),
        Wrap(
          spacing: 12,
          runSpacing: 12,
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
        // La portada: un bloque del amarillo de la marca.
        Container(
          padding: EdgeInsets.all(narrow ? 24 : 40),
          decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(28)),
          child: _Columns(
            gap: 40,
            children: [intro, const SitePhoto(file: 'portada.jpg', hint: 'Foto de portada')],
          ),
        ),
        const SizedBox(height: 24),
        _Columns(
          children: [
            _Stat(Icons.directions_bus_outlined, '${routes.length}', 'líneas en el mapa'),
            _Stat(Icons.place_outlined, '$stops', 'puntos de referencia'),
            const _Stat(Icons.sell_outlined, 'S/ 0', 'lo que cuesta usar Subo'),
          ],
        ),
        title('QUÉ HACEMOS', 'Todo lo que necesitas antes de subir'),
        const _Columns(
          children: [
            _Feature(
              Icons.alt_route,
              'Las líneas en un mapa',
              'Por dónde pasa cada micro, por qué lugares, cuánto cuesta el pasaje y en qué horario trabaja.',
            ),
            _Feature(
              Icons.warning_amber_rounded,
              'Avisos de la calle',
              'Tráfico, obras o accidentes, contados por la misma gente que va en el micro.',
            ),
            _Feature(
              Icons.auto_awesome_outlined,
              'Un asistente que responde',
              'Pregúntale qué micro tomar como le preguntarías a alguien en la esquina.',
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
        const SizedBox(height: 56),
        // El compromiso: una franja del amarillo suave.
        Container(
          padding: EdgeInsets.all(narrow ? 24 : 36),
          decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(28)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('NUESTRO COMPROMISO', style: text.labelSmall?.copyWith(color: Palette.accentText)),
              const SizedBox(height: 8),
              Text('Lo que no va a cambiar', style: text.headlineMedium?.copyWith(fontSize: 26)),
              const SizedBox(height: 24),
              const _Columns(
                gap: 28,
                children: [
                  _Promise(Icons.volunteer_activism_outlined, 'Gratis para pasajeros',
                      'Mirar las líneas y los avisos no cuesta nada, ni en la web ni en la app.'),
                  _Promise(Icons.lock_outline, 'Tus datos son tuyos',
                      'No vendemos tus datos y solo pedimos lo necesario para que la cuenta funcione.'),
                  _Promise(Icons.sync, 'Una sola cuenta', 'La misma cuenta sirve en el celular y en el navegador.'),
                ],
              ),
            ],
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

    final intro = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const _Heading(
          kicker: 'DESCARGAR APP',
          title: 'Lleva Subo en tu celular',
          lead: 'La app tiene todo lo de la web y además te ubica en el mapa, te dice cómo llegar y te avisa '
              'cuando te toca bajar.',
        ),
        const SizedBox(height: 24),
        FilledButton.icon(
          onPressed: _ready ? _download : null,
          icon: const Icon(Icons.download, size: 18),
          label: Text(_ready ? 'Descargar para Android' : 'Disponible pronto'),
        ),
        const SizedBox(height: 10),
        Text(
          _ready ? details : 'Estamos preparando la descarga. Mientras tanto puedes usar Subo desde esta web.',
          style: text.bodySmall?.copyWith(fontSize: 13),
        ),
      ],
    );

    return _InfoScroll(
      maxWidth: 1040,
      footer: SiteFooter(onPage: onPage),
      children: [
        _Columns(
          gap: 40,
          children: [intro, const SitePhoto(file: 'app.jpg', hint: 'Captura de la app')],
        ),
        title('SOLO EN LA APP', 'Lo que ganas al instalarla'),
        const _Columns(
          children: [
            _Feature(Icons.near_me_outlined, 'Líneas cerca de ti',
                'Abre la app y mira qué micros pasan por donde estás parado.'),
            _Feature(Icons.route_outlined, '¿A dónde vas?',
                'Busca un lugar y te dice qué micro tomar, dónde subir y cuánto caminar.'),
            _Feature(Icons.notifications_active_outlined, 'Aviso de llegada',
                'Te avisa cuando estás por llegar, para que no te pases.'),
          ],
        ),
        title('CÓMO INSTALARLA', 'Tres pasos'),
        const _Columns(
          children: [
            _Feature(Icons.looks_one_outlined, 'Descarga el archivo',
                'Toca el botón de arriba desde tu celular Android. Se baja un archivo que termina en .apk.'),
            _Feature(Icons.looks_two_outlined, 'Permite la instalación',
                'Al abrirlo, Android te pedirá permiso para instalar apps de este origen. Acéptalo.'),
            _Feature(Icons.looks_3_outlined, 'Entra con tu cuenta',
                'Abre Subo y usa la misma cuenta que en la web, o crea una ahí mismo.'),
          ],
        ),
      ],
    );
  }
}

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key, required this.onPage});

  final void Function(SitePage page) onPage;

  @override
  Widget build(BuildContext context) {
    return _InfoScroll(
      footer: SiteFooter(onPage: onPage),
      children: const [
        _Heading(
          kicker: 'POLÍTICA DE PRIVACIDAD',
          title: 'Qué datos usamos y para qué',
          lead: 'Te lo contamos sin letra pequeña. Si algo no queda claro, escríbenos desde Contacto.',
        ),
        _Section(
          'Datos de tu cuenta',
          'Al crear una cuenta guardamos tu nombre, tu correo y tu contraseña. La contraseña se guarda cifrada: '
              'nadie del equipo puede leerla. Si entras con Google, recibimos tu nombre, tu correo y tu foto de perfil.',
        ),
        _Section(
          'Tu ubicación',
          'La app usa tu ubicación para mostrarte las líneas que pasan cerca y para avisarte cuando llegas a tu '
              'punto de referencia. No guardamos un historial de por dónde te mueves. Cuando publicas una alerta, sí se guarda '
              'el punto del mapa donde la dejaste, porque es parte del aviso.',
        ),
        _Section(
          'Lo que publicas',
          'Las alertas, las calificaciones y las sugerencias que envías quedan asociadas a tu cuenta. En las '
              'alertas, otras personas ven tu nombre y tu foto de perfil.',
        ),
        _Section(
          'Asistente y revisión de fotos',
          'Las preguntas que le haces al asistente y las fotos que subes se envían a un servicio de inteligencia '
              'artificial de Google (Gemini) para responderte y para comprobar que la foto es apropiada. No envíes ahí '
              'datos personales o sensibles.',
        ),
        _Section(
          'Con quién compartimos',
          'No vendemos ni alquilamos tus datos. Usamos proveedores para funcionar: el servidor y la base de datos '
              '(Supabase), el alojamiento de la web (Cloudflare) y los mapas (OpenStreetMap).',
        ),
        _Section(
          'Tus derechos',
          'Puedes pedir que corrijamos o eliminemos tu cuenta y tus datos cuando quieras, conforme a la Ley de '
              'Protección de Datos Personales del Perú (Ley N.º 29733). Escríbenos desde Contacto y lo atendemos.',
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
    return _InfoScroll(
      footer: SiteFooter(onPage: onPage),
      children: const [
        _Heading(
          kicker: 'TÉRMINOS DE USO',
          title: 'Las reglas para usar Subo',
          lead: 'Al usar Subo aceptas estas condiciones. Son pocas y están pensadas para que el servicio le sirva a todos.',
        ),
        _Section(
          'La información es una guía',
          'Los recorridos, horarios y pasajes son referenciales. Las empresas de transporte pueden cambiarlos sin '
              'aviso, y Subo no opera ningún micro. Confirma en el punto de referencia cuando tengas dudas.',
        ),
        _Section(
          'Tu cuenta',
          'Eres responsable de lo que se hace con tu cuenta. Usa una contraseña que no compartas y avísanos si '
              'crees que alguien más entró con ella.',
        ),
        _Section(
          'Lo que publicas',
          'Publica solo avisos reales y relacionados con el transporte y la calle. No se permite contenido falso, '
              'ofensivo, violento u obsceno, ni datos personales de otras personas. Podemos retirar publicaciones y '
              'suspender cuentas que no respeten esto.',
        ),
        _Section(
          'Uso correcto',
          'No intentes entrar a partes del servicio que no te corresponden, ni copiar el catálogo de forma masiva, '
              'ni interferir con su funcionamiento.',
        ),
        _Section(
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
            Text('Envíanos un mensaje', style: text.titleMedium),
            const SizedBox(height: 4),
            Text('Llega directo al administrador de Subo.', style: text.bodySmall?.copyWith(fontSize: 13)),
            const SizedBox(height: 20),
            DropdownButtonFormField<String>(
              initialValue: _subject,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Asunto'),
              items: [for (final subject in _subjects) DropdownMenuItem(value: subject, child: Text(subject))],
              onChanged: _busy ? null : (value) => setState(() => _subject = value ?? _subject),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _message,
              enabled: !_busy,
              minLines: 5,
              maxLines: 10,
              maxLength: 1000,
              decoration: const InputDecoration(labelText: 'Tu mensaje', alignLabelWithHint: true),
              validator: (value) => (value ?? '').trim().isEmpty ? 'Escribe tu mensaje' : null,
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 14,
              runSpacing: 10,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                FilledButton(onPressed: _busy ? null : _send, child: Text(_busy ? 'Enviando…' : 'Enviar mensaje')),
                if (!widget.signedIn)
                  Text('Al enviar te pediremos entrar con tu cuenta.', style: text.bodySmall),
              ],
            ),
          ],
        ),
      );
    }
    return Card(child: Padding(padding: const EdgeInsets.all(24), child: body));
  }

  Widget _channelsCard(TextTheme text) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('También por aquí', style: text.titleMedium),
            const SizedBox(height: 12),
            for (final channel in ContactChannel.all)
              InkWell(
                onTap: channel.ready ? channel.open : null,
                borderRadius: BorderRadius.circular(10),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Row(
                    children: [
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(10)),
                        child: Icon(channel.icon, size: 19, color: Palette.accentText),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(channel.label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                            Text(channel.ready ? channel.value : 'Próximamente',
                                style: text.bodySmall?.copyWith(fontSize: 13), overflow: TextOverflow.ellipsis),
                          ],
                        ),
                      ),
                      if (channel.ready) const Icon(Icons.open_in_new, size: 16, color: Palette.muted),
                    ],
                  ),
                ),
              ),
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
    return _InfoScroll(
      maxWidth: 1040,
      footer: SiteFooter(onPage: widget.onPage),
      children: [
        const _Heading(
          kicker: 'CONTACTO',
          title: 'Escríbenos',
          lead: '¿Una ruta cambió, falta una línea o tienes una idea? Cuéntanos y lo revisamos.',
        ),
        const SizedBox(height: 32),
        LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth < 760) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [_formCard(text), const SizedBox(height: 20), _channelsCard(text)],
              );
            }
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 3, child: _formCard(text)),
                const SizedBox(width: 24),
                Expanded(flex: 2, child: _channelsCard(text)),
              ],
            );
          },
        ),
      ],
    );
  }
}
