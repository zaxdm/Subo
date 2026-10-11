import 'dart:math' as math;

import 'package:latlong2/latlong.dart';

/// Los metros que hay de [point] al tramo más cercano de [line]. Infinito si la
/// línea no tiene puntos.
///
/// Trata el suelo como plano alrededor de [point]: dentro de una ciudad el
/// error es de centímetros, y así no hace falta trigonometría esférica para
/// cada tramo de cada línea.
double metersToLine(LatLng point, List<LatLng> line) {
  if (line.isEmpty) return double.infinity;
  const metersPerDegree = 111320.0;
  final shrink = math.cos(point.latitude * math.pi / 180);
  // Cada punto, en metros al este y al norte de [point].
  double east(LatLng other) => (other.longitude - point.longitude) * metersPerDegree * shrink;
  double north(LatLng other) => (other.latitude - point.latitude) * metersPerDegree;

  var best = double.infinity;
  var ax = east(line.first);
  var ay = north(line.first);
  if (line.length == 1) return math.sqrt(ax * ax + ay * ay);
  for (var i = 1; i < line.length; i++) {
    final bx = east(line[i]);
    final by = north(line[i]);
    final dx = bx - ax;
    final dy = by - ay;
    final length = dx * dx + dy * dy;
    // Por dónde cae [point] a lo largo del tramo: 0 en un extremo, 1 en el otro.
    final along = length == 0 ? 0.0 : (-(ax * dx + ay * dy) / length).clamp(0.0, 1.0);
    final x = ax + dx * along;
    final y = ay + dy * along;
    final distance = x * x + y * y;
    if (distance < best) best = distance;
    ax = bx;
    ay = by;
  }
  return math.sqrt(best);
}
