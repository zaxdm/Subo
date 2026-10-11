import 'dart:async';
import 'dart:js_interop';
import 'dart:ui_web' as ui_web;

import 'package:flutter/widgets.dart';
import 'package:web/web.dart' as web;

import '../site_info.dart';

const googleSignInAvailable = true;

// La biblioteca de Google (accounts.google.com/gsi/client) que carga index.html.
@JS('google')
external JSAny? get _google;

@JS('google.accounts.id.initialize')
external void _initialize(_Config config);

@JS('google.accounts.id.renderButton')
external void _renderButton(web.HTMLElement parent, _ButtonOptions options);

extension type _Config._(JSObject _) implements JSObject {
  // Los nombres son los que espera Google.
  // ignore: non_constant_identifier_names
  external factory _Config({String client_id, String nonce, JSFunction callback});
}

extension type _Credential._(JSObject _) implements JSObject {
  external String? get credential;
}

extension type _ButtonOptions._(JSObject _) implements JSObject {
  external factory _ButtonOptions({
    String type,
    String theme,
    String size,
    String text,
    String shape,
    String locale,
    int width,
  });
}

/// El botón oficial de Google, pintado por Google dentro de la página.
///
/// Cada acceso se firma con un desafío de un solo uso que da el servidor y
/// caduca a los cinco minutos, así que el botón se vuelve a preparar con uno
/// nuevo antes de que caduque. Tras un intento, quien lo usa lo recrea (con
/// otra `key`) para que el siguiente lleve otro desafío.
class GoogleButton extends StatefulWidget {
  const GoogleButton({super.key, required this.nonce, required this.onToken, this.width = 320});

  /// Pide al servidor el desafío de un solo uso con el que se firma el acceso.
  final Future<String> Function() nonce;

  /// Google confirmó la cuenta: su credencial y el desafío con el que se pidió.
  final void Function(String idToken, String nonce) onToken;
  final double width;

  @override
  State<GoogleButton> createState() => _GoogleButtonState();
}

class _GoogleButtonState extends State<GoogleButton> {
  static const _refreshEvery = Duration(minutes: 4);
  static var _views = 0;

  late final String _viewType = 'subo-google-button-${_views++}';
  final _holder = web.document.createElement('div') as web.HTMLDivElement;
  Timer? _refresh;

  /// `false` si Google o el servidor no respondieron: no se enseña nada.
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _holder.style
      ..width = '100%'
      ..height = '100%'
      ..display = 'flex'
      ..justifyContent = 'center'
      ..overflow = 'hidden';
    ui_web.platformViewRegistry.registerViewFactory(_viewType, (int _) => _holder);
    _prepare();
    _refresh = Timer.periodic(_refreshEvery, (_) => _prepare());
  }

  @override
  void dispose() {
    _refresh?.cancel();
    super.dispose();
  }

  Future<void> _prepare() async {
    try {
      // La biblioteca se carga aparte; se le da un momento a que llegue.
      for (var i = 0; i < 40 && _google == null; i++) {
        await Future<void>.delayed(const Duration(milliseconds: 150));
      }
      if (_google == null || !mounted) return;

      final nonce = await widget.nonce();
      if (!mounted) return;
      _initialize(_Config(
        client_id: SiteInfo.googleClientId,
        nonce: nonce,
        callback: ((_Credential response) {
          final token = response.credential;
          if (token != null && mounted) widget.onToken(token, nonce);
        }).toJS,
      ));
      // Cada preparación pinta el botón de nuevo: se quita el anterior.
      while (_holder.firstChild != null) {
        _holder.removeChild(_holder.firstChild!);
      }
      _renderButton(
        _holder,
        _ButtonOptions(
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          locale: 'es',
          // Google no pinta botones de más de 400 de ancho.
          width: widget.width.clamp(200, 400).round(),
        ),
      );
      if (mounted) setState(() => _ready = true);
    } catch (_) {
      // Sin Google se sigue pudiendo entrar con correo y contraseña.
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: _ready ? 44 : 0,
      width: widget.width,
      child: HtmlElementView(viewType: _viewType),
    );
  }
}
