import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';

/// Una página de texto: un título, una entrada y secciones con su contenido.
class _Article extends StatelessWidget {
  const _Article({required this.kicker, required this.title, required this.lead, required this.children});

  final String kicker;
  final String title;
  final String lead;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final narrow = MediaQuery.sizeOf(context).width < 720;
    return SingleChildScrollView(
      padding: EdgeInsets.symmetric(horizontal: narrow ? 20 : 32, vertical: narrow ? 28 : 48),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(kicker, style: text.labelSmall?.copyWith(color: Palette.accentText)),
              const SizedBox(height: 10),
              Text(title, style: narrow ? text.headlineMedium : text.headlineLarge),
              const SizedBox(height: 12),
              Text(lead, style: text.bodyMedium?.copyWith(color: Palette.muted, fontSize: 16, height: 1.7)),
              const SizedBox(height: 12),
              ...children,
            ],
          ),
        ),
      ),
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

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const _Article(
      kicker: 'NOSOTROS',
      title: 'Hecho en Trujillo, para moverse por Trujillo',
      lead: 'Subo nació de una pregunta que todos hemos hecho en un paradero: «¿este micro me lleva?». '
          'Queremos que la respuesta esté en tu celular o en tu computadora, antes de salir de casa.',
      children: [
        _Section(
          'Qué hacemos',
          'Reunimos las líneas de micro de la ciudad en un solo mapa: por dónde pasan, en qué paraderos se '
              'detienen, cuánto cuesta el pasaje y en qué horario trabajan. También mostramos los avisos que deja la '
              'gente sobre tráfico, obras o accidentes.',
        ),
        _Section(
          'Cómo se arma la información',
          'Las rutas las carga y revisa nuestro equipo, con ayuda de colaboradores que recorren la ciudad. '
              'El transporte cambia seguido, así que puede haber diferencias con lo que ves en la calle: si '
              'encuentras una, cuéntanoslo desde Contacto y la corregimos.',
        ),
        _Section(
          'La app y la web',
          'Subo funciona en el celular y en el navegador con la misma cuenta. En la app, además, puedes enviar '
              'alertas desde donde estás y recibir un aviso cuando te toca bajar.',
        ),
        _Section(
          'Nuestro compromiso',
          'Subo es gratis para los pasajeros. No vendemos tus datos y solo pedimos lo necesario para que la '
              'cuenta funcione.',
        ),
      ],
    );
  }
}

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const _Article(
      kicker: 'POLÍTICA DE PRIVACIDAD',
      title: 'Qué datos usamos y para qué',
      lead: 'Te lo contamos sin letra pequeña. Si algo no queda claro, escríbenos desde Contacto.',
      children: [
        _Section(
          'Datos de tu cuenta',
          'Al crear una cuenta guardamos tu nombre, tu correo y tu contraseña. La contraseña se guarda cifrada: '
              'nadie del equipo puede leerla. Si entras con Google, recibimos tu nombre, tu correo y tu foto de perfil.',
        ),
        _Section(
          'Tu ubicación',
          'La app usa tu ubicación para mostrarte las líneas que pasan cerca y para avisarte cuando llegas a tu '
              'paradero. No guardamos un historial de por dónde te mueves. Cuando publicas una alerta, sí se guarda '
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
  const TermsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const _Article(
      kicker: 'TÉRMINOS DE USO',
      title: 'Las reglas para usar Subo',
      lead: 'Al usar Subo aceptas estas condiciones. Son pocas y están pensadas para que el servicio le sirva a todos.',
      children: [
        _Section(
          'La información es una guía',
          'Los recorridos, horarios y pasajes son referenciales. Las empresas de transporte pueden cambiarlos sin '
              'aviso, y Subo no opera ningún micro. Confirma en el paradero cuando tengas dudas.',
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

/// Un formulario para escribirle al equipo. Llega al mismo buzón que las
/// sugerencias de la app, así que pide sesión.
class ContactPage extends StatefulWidget {
  const ContactPage({super.key, required this.api, required this.signedIn, required this.onSignIn, required this.onError});

  final ApiClient api;
  final bool signedIn;
  final VoidCallback onSignIn;
  final void Function(Object error) onError;

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

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final Widget body;
    if (!widget.signedIn) {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Para escribirnos, entra con tu cuenta. Así podemos responderte y evitamos mensajes basura.',
              style: text.bodyMedium),
          const SizedBox(height: 16),
          FilledButton(onPressed: widget.onSignIn, child: const Text('Entrar para escribirnos')),
        ],
      );
    } else if (_sent) {
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
            Align(
              alignment: Alignment.centerLeft,
              child: FilledButton(onPressed: _busy ? null : _send, child: Text(_busy ? 'Enviando…' : 'Enviar mensaje')),
            ),
          ],
        ),
      );
    }

    return _Article(
      kicker: 'CONTACTO',
      title: 'Escríbenos',
      lead: '¿Una ruta cambió, falta una línea o tienes una idea? Tu mensaje llega directo al equipo de Subo.',
      children: [
        const SizedBox(height: 24),
        Card(child: Padding(padding: const EdgeInsets.all(24), child: body)),
      ],
    );
  }
}
