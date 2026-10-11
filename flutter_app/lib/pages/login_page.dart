import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/google_button.dart';

/// La puerta de Subo: entrar o crear una cuenta de pasajero.
///
/// Pasajeros y administradores entran por aquí con el mismo formulario; el
/// servidor decide a dónde va cada uno. En el navegador también se entra con
/// Google. Ni la contraseña ni la sesión se guardan en el navegador.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.api, required this.onSignedIn, this.reason, this.onClose});

  final ApiClient api;
  final void Function(Session session, List<BusRoute> routes) onSignedIn;

  /// Por qué se pide entrar, si alguien llegó aquí queriendo hacer algo.
  final String? reason;

  /// Con valor, el acceso es una ventana emergente: solo el formulario, con
  /// su botón de cerrar.
  final VoidCallback? onClose;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  static const _passwordMinLength = 8;

  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _code = TextEditingController();

  /// El correo que espera su código para terminar de crear la cuenta; `null`
  /// si no se está en ese paso.
  String? _confirming;

  /// Un aviso que no es un fallo, como que se mandó otro código.
  String? _notice;

  /// `true`: se está creando una cuenta en lugar de entrar con una.
  bool _registering = false;
  bool _busy = false;
  bool _showPassword = false;
  String? _error;

  /// Cada intento con Google gasta su desafío: al cambiar, el botón pide otro.
  int _googleAttempt = 0;

  @override
  void dispose() {
    _name.dispose();
    _email.dispose();
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  /// Hace [work] con el formulario bloqueado y enseña el fallo si lo hay.
  Future<void> _run(Future<void> Function() work, String fallback) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
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
          ? await api.register(displayName: _name.text, email: _email.text, password: _password.text)
          : await api.signIn(email: _email.text, password: _password.text);
      if (session == null) {
        // Falta confirmar el correo: se pasa a pedir el código que le llegó.
        if (mounted) setState(() => _confirming = _email.text.trim());
        return;
      }
      await _enter(session);
    }, _registering ? 'No se pudo crear la cuenta. Inténtalo de nuevo.' : 'No se pudo iniciar sesión. Inténtalo de nuevo.');
  }

  /// Con el código del correo, la cuenta se crea y se entra.
  Future<void> _confirm() async {
    final email = _confirming;
    if (email == null) return;
    if (_code.text.trim().length != 6) {
      setState(() => _error = 'Escribe los 6 dígitos del código');
      return;
    }
    await _run(() async {
      await _enter(await widget.api.confirmRegistration(email: email, code: _code.text));
    }, 'No se pudo confirmar el código. Inténtalo de nuevo.');
  }

  /// Pide otro código para el mismo registro.
  Future<void> _resend() async {
    await _run(() async {
      final session =
          await widget.api.register(displayName: _name.text, email: _email.text, password: _password.text);
      if (session != null) {
        await _enter(session);
      } else if (mounted) {
        setState(() => _notice = 'Listo. Si no te llega en un minuto, revisa el correo no deseado y vuelve a pedirlo.');
      }
    }, 'No se pudo enviar otro código. Inténtalo de nuevo.');
  }

  /// Google ya confirmó la cuenta; el servidor comprueba su credencial.
  Future<void> _withGoogle(String idToken, String nonce) async {
    await _run(() async {
      // Si el correo ya tenía cuenta, la contraseña escrita sirve para unirlas.
      final session = await widget.api.signInWithGoogle(
        idToken: idToken,
        nonce: nonce,
        existingPassword: _password.text,
      );
      await _enter(session);
    }, 'No se pudo entrar con Google. Inténtalo de nuevo.');
    if (mounted) setState(() => _googleAttempt++);
  }

  /// Con la sesión abierta, carga lo que le toca ver a esa cuenta y entra.
  Future<void> _enter(Session session) async {
    final api = widget.api;
    final routes = session.isAdmin ? await api.routes() : await api.publicRoutes();
    if (!mounted) return;
    _password.clear();
    widget.onSignedIn(session, routes);
  }

  void _toggleMode() {
    setState(() {
      _registering = !_registering;
      _error = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (widget.onClose != null) return _card(context);
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

  /// El paso de escribir el código que llegó al correo.
  Widget _codeCard(BuildContext context, String email) {
    final text = Theme.of(context).textTheme;
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(26),
        side: const BorderSide(color: Palette.line),
      ),
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                  decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(30)),
                  child: const Text('Confirma tu correo',
                      style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: Color(0xFF5C4C10))),
                ),
                const Spacer(),
                if (widget.onClose != null)
                  IconButton(
                    tooltip: 'Cerrar',
                    onPressed: _busy ? null : widget.onClose,
                    icon: const Icon(Icons.close, size: 18),
                    style: IconButton.styleFrom(backgroundColor: Palette.chip, fixedSize: const Size(44, 44)),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Text('Escribe el código', style: text.headlineLarge?.copyWith(fontSize: 28, letterSpacing: -0.5)),
            const SizedBox(height: 6),
            Text('Te enviamos un código de 6 dígitos a $email. Vale 15 minutos.',
                style: text.bodySmall?.copyWith(fontSize: 15, color: Palette.muted)),
            const SizedBox(height: 24),
            TextField(
              controller: _code,
              enabled: !_busy,
              autofocus: true,
              keyboardType: TextInputType.number,
              maxLength: 6,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 24, letterSpacing: 8, fontWeight: FontWeight.w600),
              onSubmitted: (_) => _confirm(),
              decoration: const InputDecoration(labelText: 'Código', counterText: ''),
            ),
            const SizedBox(height: 16),
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
            if (_notice != null) ...[
              Text(_notice!, style: text.bodySmall?.copyWith(fontSize: 13, color: Palette.success)),
              const SizedBox(height: 12),
            ],
            FilledButton(
              onPressed: _busy ? null : _confirm,
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              child: Text(_busy ? 'Un momento…' : 'Confirmar y entrar'),
            ),
            const SizedBox(height: 6),
            Wrap(
              alignment: WrapAlignment.spaceBetween,
              children: [
                TextButton(onPressed: _busy ? null : _resend, child: const Text('Enviar otro código')),
                TextButton(
                  onPressed: _busy
                      ? null
                      : () => setState(() {
                            _confirming = null;
                            _code.clear();
                            _error = null;
                            _notice = null;
                          }),
                  child: const Text('Cambiar el correo'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _card(BuildContext context) {
    final confirming = _confirming;
    if (confirming != null) return _codeCard(context, confirming);
    final text = Theme.of(context).textTheme;
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(26),
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
                Row(
                  children: [
                    // Al entrar, la marca; al crear la cuenta, que es gratis.
                    if (_registering)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                        decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(30)),
                        child: const Text('Gratis, para web y app',
                            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: Color(0xFF5C4C10))),
                      )
                    else
                      Container(
                        width: 40,
                        height: 40,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(12)),
                        child: const Text('S', style: TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
                      ),
                    const Spacer(),
                    if (widget.onClose != null)
                      IconButton(
                        tooltip: 'Cerrar',
                        onPressed: _busy ? null : widget.onClose,
                        icon: const Icon(Icons.close, size: 18),
                        style: IconButton.styleFrom(backgroundColor: Palette.chip, fixedSize: const Size(44, 44)),
                      ),
                  ],
                ),
                const SizedBox(height: 16),
                Text(_registering ? 'Crea tu cuenta' : 'Hola de nuevo',
                    style: text.headlineLarge?.copyWith(fontSize: 28, letterSpacing: -0.5)),
                const SizedBox(height: 6),
                Text(
                  _registering
                      ? 'Te sirve también en la app del celular.'
                      : widget.reason ?? 'Entra con la misma cuenta de la app.',
                  style: text.bodySmall?.copyWith(fontSize: 15, color: Palette.muted),
                ),
                const SizedBox(height: 20),
                if (googleSignInAvailable) ...[
                  LayoutBuilder(
                    builder: (context, constraints) => Center(
                      child: GoogleButton(
                        key: ValueKey(_googleAttempt),
                        nonce: widget.api.googleChallenge,
                        onToken: _withGoogle,
                        width: constraints.maxWidth.clamp(200, 400).toDouble(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Expanded(child: Divider()),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text('o con tu correo', style: text.bodySmall?.copyWith(fontSize: 13, color: Palette.faint)),
                      ),
                      const Expanded(child: Divider()),
                    ],
                  ),
                  const SizedBox(height: 16),
                ],
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
                // Entrar va en negro; crear la cuenta, en el amarillo de la marca.
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(52),
                    backgroundColor: _registering ? Palette.accent : Palette.ink,
                    foregroundColor: _registering ? Palette.ink : Colors.white,
                    textStyle: TextStyle(fontSize: 16, fontWeight: _registering ? FontWeight.w700 : FontWeight.w500),
                  ),
                  child: Text(
                    _busy ? 'Un momento…' : (_registering ? 'Crear mi cuenta' : 'Entrar'),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(height: 10),
                Wrap(
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(_registering ? '¿Ya tienes cuenta?' : '¿Primera vez en Subo?',
                        style: text.bodySmall?.copyWith(fontSize: 14, color: Palette.muted)),
                    TextButton(
                      onPressed: _busy ? null : _toggleMode,
                      child: Text(_registering ? 'Inicia sesión' : 'Crea tu cuenta'),
                    ),
                  ],
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
    final headline = (narrow ? text.headlineMedium : text.headlineLarge?.copyWith(fontSize: 44))!;
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
        const _Perk(icon: Icons.place_outlined, label: 'Lugares por donde pasa'),
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
