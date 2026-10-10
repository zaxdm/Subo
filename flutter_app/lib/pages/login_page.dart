import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api.dart';
import '../theme.dart';

/// Acceso al panel con una cuenta de Subo marcada como administrador.
///
/// Solo se recuerda la dirección del servidor; ni la contraseña ni la sesión
/// se guardan en el navegador.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.api, required this.onSignedIn});

  final ApiClient api;
  final void Function(String accountName, List<BusRoute> routes) onSignedIn;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  static const _serverKey = 'ruta-norte-server';

  final _form = GlobalKey<FormState>();
  final _server = TextEditingController(text: defaultServerUrl);
  final _email = TextEditingController();
  final _password = TextEditingController();

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
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final name = await widget.api.signIn(server: _server.text, email: _email.text, password: _password.text);
      final routes = await widget.api.routes();
      try {
        (await SharedPreferences.getInstance()).setString(_serverKey, widget.api.baseUrl);
      } catch (_) {
        // No poder recordarla no impide entrar.
      }
      if (!mounted) return;
      _password.clear();
      widget.onSignedIn(name, routes);
    } catch (error) {
      widget.api.signOut();
      if (!mounted) return;
      setState(() => _error = error is ApiException ? error.message : 'No se pudo iniciar sesión. Inténtalo de nuevo.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
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
                Text('BIENVENIDO', style: text.labelSmall?.copyWith(color: Palette.accentText)),
                const SizedBox(height: 10),
                Text('Ingresa al panel', style: text.titleLarge?.copyWith(fontSize: 23)),
                const SizedBox(height: 6),
                Text('Usa tu cuenta de Subo con permisos de administrador.',
                    style: text.bodySmall?.copyWith(fontSize: 13)),
                const SizedBox(height: 24),
                TextFormField(
                  controller: _email,
                  enabled: !_busy,
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.username, AutofillHints.email],
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(labelText: 'Correo', hintText: 'admin@correo.pe'),
                  validator: (value) => (value ?? '').trim().contains('@') ? null : 'Escribe tu correo',
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _password,
                  enabled: !_busy,
                  obscureText: !_showPassword,
                  autofillHints: const [AutofillHints.password],
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: InputDecoration(
                    labelText: 'Contraseña',
                    suffixIcon: IconButton(
                      tooltip: _showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña',
                      icon: Icon(_showPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined, size: 20),
                      onPressed: () => setState(() => _showPassword = !_showPassword),
                    ),
                  ),
                  validator: (value) => (value ?? '').isEmpty ? 'Escribe tu contraseña' : null,
                ),
                // Casi nadie necesita tocar la dirección del servidor: va plegada.
                Theme(
                  data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
                  child: ExpansionTile(
                    tilePadding: EdgeInsets.zero,
                    childrenPadding: const EdgeInsets.only(bottom: 8),
                    title: Text('Opciones avanzadas', style: text.bodySmall?.copyWith(fontSize: 13)),
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
                const SizedBox(height: 4),
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(child: Text(_busy ? 'Entrando…' : 'Entrar al panel', overflow: TextOverflow.ellipsis)),
                      _busy
                          ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                          : const Icon(Icons.arrow_forward, size: 18),
                    ],
                  ),
                ),
                const SizedBox(height: 14),
                Text('Los permisos se comprueban en el servidor.', textAlign: TextAlign.center, style: text.bodySmall),
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
            text: 'Las rutas de tu ciudad,\n',
            children: const [TextSpan(text: 'en tus manos.', style: TextStyle(color: Palette.accentText))],
          ),
          style: headline,
        ),
        const SizedBox(height: 18),
        ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 400),
          child: Text(
            'Actualiza los recorridos, identifica cada micro y ayuda a más personas a llegar a su destino.',
            style: text.bodyMedium?.copyWith(color: Palette.muted, fontSize: narrow ? 14 : 16, height: 1.7),
          ),
        ),
      ],
    );
  }
}
