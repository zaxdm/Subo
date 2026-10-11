import 'package:flutter/material.dart';

import '../theme.dart';

/// El logo de Subo: los escalones del micro y un pasajero subiendo, sobre una
/// ficha crema. Es el mismo dibujo que el ícono de la app y el de la web.
class SuboMark extends StatelessWidget {
  const SuboMark({super.key, this.size = 34});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: Palette.paper,
        borderRadius: BorderRadius.circular(size * 0.23),
        border: Border.all(color: Palette.fieldLine),
      ),
      child: CustomPaint(painter: _SuboMarkPainter()),
    );
  }
}

class _SuboMarkPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    // El dibujo está pensado en una rejilla de 96.
    canvas.scale(size.width / 96, size.height / 96);
    final steps = Path()
      ..moveTo(21, 77)
      ..lineTo(39, 77)
      ..lineTo(39, 59)
      ..lineTo(57, 59)
      ..lineTo(57, 41)
      ..lineTo(75, 41);
    canvas.drawPath(
      steps,
      Paint()
        ..color = Palette.ink
        ..style = PaintingStyle.stroke
        ..strokeWidth = 11
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
    canvas.drawCircle(const Offset(66, 22), 9, Paint()..color = Palette.accent);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
