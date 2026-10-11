import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:ruta_norte_admin/nearby.dart';

void main() {
  // Una calle de Trujillo que va de oeste a este, de unos 1100 m.
  const street = [LatLng(-8.112, -79.035), LatLng(-8.112, -79.025)];

  test('sobre la línea no hay distancia', () {
    expect(metersToLine(const LatLng(-8.112, -79.03), street), closeTo(0, 0.5));
  });

  test('mide hasta el tramo, no hasta sus extremos', () {
    // Una milésima de grado al norte son unos 111 m.
    expect(metersToLine(const LatLng(-8.111, -79.03), street), closeTo(111, 2));
  });

  test('pasado el final, mide hasta el último punto', () {
    final meters = metersToLine(const LatLng(-8.112, -79.024), street);
    expect(meters, closeTo(110, 3));
  });

  test('una línea sin puntos queda infinitamente lejos', () {
    expect(metersToLine(const LatLng(-8.112, -79.03), const []), double.infinity);
  });
}
