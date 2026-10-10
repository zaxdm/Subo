import 'package:flutter/widgets.dart';

/// Fuera del navegador no hay acceso con Google.
const googleSignInAvailable = false;

class GoogleButton extends StatelessWidget {
  const GoogleButton({super.key, required this.nonce, required this.onToken, this.width = 320});

  /// Pide al servidor el desafío de un solo uso con el que se firma el acceso.
  final Future<String> Function() nonce;

  /// Google confirmó la cuenta: su credencial y el desafío con el que se pidió.
  final void Function(String idToken, String nonce) onToken;
  final double width;

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
