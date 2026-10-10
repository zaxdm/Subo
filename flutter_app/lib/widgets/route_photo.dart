import 'package:flutter/material.dart';

import '../theme.dart';

/// La foto del micro o, si no hay (o no carga), un fondo con la letra de la ruta.
class RoutePhoto extends StatelessWidget {
  const RoutePhoto({super.key, required this.url, required this.letter, this.emptyHint});

  /// Dirección HTTPS de la foto; `null` si la ruta no tiene.
  final String? url;
  final String letter;

  /// Texto en lugar de la letra cuando no hay foto (la vista previa del editor).
  final String? emptyHint;

  @override
  Widget build(BuildContext context) {
    final placeholder = _placeholder(emptyHint);
    final url = this.url;
    if (url == null) return placeholder;
    return Image.network(
      url,
      fit: BoxFit.cover,
      width: double.infinity,
      height: double.infinity,
      // Una foto de otro sitio sin permisos CORS no se puede pintar en el lienzo:
      // se enseña con un <img> normal en lugar de fallar.
      webHtmlElementStrategy: WebHtmlElementStrategy.fallback,
      errorBuilder: (_, _, _) => _placeholder(emptyHint == null ? null : 'No se pudo cargar la imagen. Revisa la URL.'),
      loadingBuilder: (_, child, progress) => progress == null ? child : placeholder,
    );
  }

  Widget _placeholder(String? hint) {
    return Container(
      alignment: Alignment.center,
      padding: const EdgeInsets.all(16),
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Palette.accentSoft, Color(0xFFFBF6E3)],
        ),
      ),
      child: hint != null
          ? Text(hint, textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF8F8565), fontSize: 12))
          : Text(
              letter.isEmpty ? 'RN' : letter,
              style: const TextStyle(color: Palette.accentText, fontSize: 46, fontWeight: FontWeight.w700),
            ),
    );
  }
}
