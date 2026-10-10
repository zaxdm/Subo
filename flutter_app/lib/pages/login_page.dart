import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api.dart';
import '../theme.dart';

/// La puerta de Subo: entrar, crear una cuenta de pasajero o mirar las líneas
/// sin cuenta.
///
/// Pasajeros y administradores entran por aquí con el mismo formulario; el
/// servidor decide a dónde va cada uno. Solo se recuerda la dirección del
/// servidor; ni la contraseña ni la sesión se guardan en el navegador.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.api, required this.onSignedIn, required this.onGuest});

  final ApiClient api;
  final void Function(Session session, List<BusRoute> routes) onSignedIn;

  /// Alguien que solo quiere mirar las líneas, sin cuenta.
  final void Function(List<BusRoute> routes) onGuest;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  static const _serverKey = 'ruta-norte-server';
  static const _passwordMinLength = 8;

  final _form = GlobalKey<FormState>();
  final _server = TextEditingController(text: defaultServerUrl);
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();

  /// `true`: se está creando una cuenta en lugar de entrar con una.
  bool _registering = false;
  bool _busy = false;
  bool _showPassword = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _restoreServer();
  }

  Future<void> _restoreServer() async {
    try {
      final saved = (await SharedPreferences.getInstance()).getString(_serverKey);
      if (saved != null && saved.isNotEmpty && mounted) _server.text = saved;
    } catch (_) {
      // Sin almacenamiento (modo privado): se usa la dirección por defecto.
    }
  }

  @override
  void dispose() {
    _server.dispose();
    _name.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  /// Hace [work] con el formulario bloqueado y enseña el fallo si lo hay.
  Future<void> _run(Future<void> Function() work, String fallback) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await work();
    } catch (error) {
      widget.api.signOut();
      if (!mounted) return;
      setState(() => _error = error is ApiException ? error.message : fallback);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submit() async {
    if (!_form.currentState!.validate()) return;
    await _run(() async {
      final api = widget.api;
      final session = _registering
          ? await api.register(
              server: _server.text,
              displayName: _name.text,
              email: _email.text,
              password: _password.text,
            )
          : await api.signIn(server: _server.text, email: _email.text, password: _password.text);
      final routes = session.isAdmin ? await api.routes() : await api.publicRoutes();
      try {
        (await SharedPreferences.getInstance()).setString(_serverKey, api.baseUrl);
      } catch (_) {
        // No poder recordarla no impide entrar.
      }
      if (!mounted) return;
      _password.clear();
      widget.onSignedIn(session, routes);
    }, _registering ? 'No se pudo crear la cuenta. Inténtalo de nuevo.' : 'No se pudo iniciar sesión. Inténtalo de nuevo.');
  }

  Future<void> _browse() => _run(() async {
        final routes = await widget.api.publicRoutes(server: _server.text);
        if (mounted) widget.onGuest(routes);
      }, 'No se pudieron cargar las líneas. Inténtalo de nuevo.');

  void _toggleMode() {
    setState(() {
      _registering = !_registering;
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 860;
        final card = ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440), child: _card(context));
        return Center(
          child: SingleChildScrollView(
            padding: EdgeInsets.symmetric(horizontal: wide ? 0 : 20, vertical: 28),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1080),
              child: wide
                  ? Row(
                      children: [
                        const Expanded(flex: 6, child: _Intro()),
                        const SizedBox(width: 56),
                        Expanded(flex: 5, child: card),
                      ],
                    )
                  : Column(children: [const _Intro(), const SizedBox(height: 28), card]),
            ),
          ),
        );
      },
    );
  }

  Widget _card(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: const BorderSide(color: Palette.line),
      ),
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Form(
          key: _form,
          child: AutofillGroup(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(_registering ? 'CUENTA NUEVA' : 'BIENVENIDO',
                    style: text.labelSmall?.copyWith(color: Palette.accentText)),
                const SizedBox(height: 10),
                Text(_registering ? 'Crea tu cuenta' : 'Inicia sesión', style: text.titleLarge?.copyWith(fontSize: 23)),
                const SizedBox(height: 6),
                Text(
                  _registering
                      ? 'Es gratis y te sirve también en la app del celular.'
                      : 'Entra con tu cuenta de Subo, la misma de la app.',
                  style: text.bodySmall?.copyWith(fontSize: 13),
                ),
                const SizedBox(height: 24),
                if (_registering) ...[
                  TextFormField(
                    controller: _name,
                    enabled: !_busy,
                    textCapitalization: TextCapitalization.words,
                    autofillHints: const [AutofillHints.name],
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(labelText: 'Tu nombre'),
                    validator: (value) => (value ?? '').trim().isEmpty ? 'Escribe tu nombre' : null,
                  ),
                  const SizedBox(height: 16),
                ],
                TextFormField(
                  controller: _email,
                  enabled: !_busy,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.username, AutofillHints.email],
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(labelText: 'Correo', hintText: 'tucorreo@gmail.com'),
                  validator: (value) => (value ?? '').trim().contains('@') ? null : 'Escribe tu correo',
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _password,
                  enabled: !_busy,
                  obscureText: !_showPassword,
                  autofillHints: [_registering ? AutofillHints.newPassword : AutofillHints.password],
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: InputDecoration(
                    labelText: 'Contraseña',
                    helperText: _registering ? 'Al menos $_passwordMinLength caracteres' : null,
                    suffixIcon: IconButton(
                      tooltip: _showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña',
                      icon: Icon(_showPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined, size: 20),
                      onPressed: () => setState(() => _showPassword = !_showPassword),
                    ),
                  ),
                  validator: (value) {
                    final password = value ?? '';
                    if (password.isEmpty) return 'Escribe tu contraseña';
                    if (_registering && password.length < _passwordMinLength) {
                      return 'Usa al menos $_passwordMinLength caracteres';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 20),
                if (_error != null) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Palette.dangerSoft,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: const Color(0xFFE8C3B9)),
                    ),
                    child: Text(_error!, style: const TextStyle(color: Palette.danger, fontSize: 13)),
                  ),
                  const SizedBox(height: 12),
                ],
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text(
                          _busy ? 'Un momento…' : (_registering ? 'Crear mi cuenta' : 'Entrar'),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      _busy
                          ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Icon(Icons.arrow_forward, size: 18),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                OutlinedButton(
                  onPressed: _busy ? null : _browse,
                  child: const Text('Ver las líneas sin cuenta'),
                ),
                const SizedBox(height: 14),
                Wrap(
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(_registering ? '¿Ya tienes cuenta?' : '¿Primera vez en Subo?',
                        style: text.bodySmall?.copyWith(fontSize: 13)),
                    TextButton(
                      onPressed: _busy ? null : _toggleMode,
                      child: Text(_registering ? 'Inicia sesión' : 'Crea tu cuenta'),
                    ),
                  ],
                ),
                // Casi nadie necesita tocar la dirección del servidor: va plegada.
                Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    childrenPadding: const EdgeInsets.only(bottom: 8),
                    title: Text('Opciones avanzadas', style: text.bodySmall),
                    children: [
                      TextFormField(
                        controller: _server,
                        enabled: !_busy,
                        keyboardType: TextInputType.url,
                        decoration: const InputDecoration(
                          labelText: 'Servidor de la app',
                          hintText: 'https://tu-proyecto.supabase.co/functions/v1/server',
                        ),
                        validator: (value) {
                          try {
                            normalizeServerUrl(value ?? '');
                            return null;
                          } on ApiException catch (error) {
                            return error.message;
                          }
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Intro extends StatelessWidget {
  const _Intro();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final narrow = MediaQuery.sizeOf(context).width < 860;
    final headline = (narrow ? text.headlineMedium : text.headlineLarge?.copyWith(fontSize: 48))!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(30)),
          child: Text('SUBO · TRUJILLO',
              style: text.labelSmall?.copyWith(color: const Color(0xFF7A5A00), letterSpacing: 1)),
        ),
        const SizedBox(height: 22),
        Text.rich(
          TextSpan(
            text: 'Sube al micro correcto,\n',
            children: const [TextSpan(text: 'sin dar vueltas.', style: TextStyle(color: Palette.accentText))],
          ),
          style: headline,
        ),
        const SizedBox(height: 18),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Text(
            'Mira las líneas de Trujillo antes de salir: por dónde pasan, dónde paran, cuánto cuesta el pasaje y en qué horario.',
            style: text.bodyMedium?.copyWith(color: Palette.muted, fontSize: narrow ? 14 : 16, height: 1.7),
          ),
        ),
        const SizedBox(height: 26),
        const _Perk(icon: Icons.alt_route, label: 'El recorrido de cada línea, en el mapa'),
        const _Perk(icon: Icons.place_outlined, label: 'Paraderos por donde pasa'),
        const _Perk(icon: Icons.payments_outlined, label: 'Pasaje, horario y cuánto demora'),
      ],
    );
  }
}

/// Una de las cosas que se pueden hacer en Subo, en una línea.
class _Perk extends StatelessWidget {
  const _Perk({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(9)),
            child: Icon(icon, size: 17, color: Palette.accentText),
          ),
          const SizedBox(width: 12),
          Flexible(child: Text(label, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}
