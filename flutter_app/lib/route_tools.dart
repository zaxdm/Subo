import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api.dart';

/// Un camino que sigue las calles.
class StreetPath {
  const StreetPath(this.points, this.meters);

  final List<GeoPoint> points;
  final double meters;
}

/// Traza por las calles el camino que pasa por unos puntos, en orden, con el
/// servicio OSRM público de FOSSGIS (routing.openstreetmap.de), el mismo que
/// usa la app: gratuito, sin clave y basado en OpenStreetMap.
///
/// Sus condiciones piden no pasar de una petición por segundo y evitar el uso
/// intensivo: aquí solo se llama cuando alguien del equipo pulsa el botón.
class StreetRouter {
  StreetRouter({http.Client? client}) : _client = client ?? http.Client();

  static const _base = 'https://routing.openstreetmap.de/routed-car/route/v1/driving';

  /// Puntos por petición: con más, la dirección no cabe.
  static const _chunk = 80;

  final http.Client _client;

  /// `null` si el servicio no responde o no encuentra un camino.
  Future<StreetPath?> through(List<GeoPoint> points) async {
    if (points.length < 2) return null;
    final path = <GeoPoint>[];
    var meters = 0.0;
    // Los tramos se encadenan: cada uno empieza donde acabó el anterior.
    for (var start = 0; start < points.length - 1; start += _chunk - 1) {
      if (start > 0) await Future<void>.delayed(const Duration(seconds: 1));
      final end = start + _chunk > points.length ? points.length : start + _chunk;
      final piece = await _request(points.sublist(start, end));
      if (piece == null) return null;
      path.addAll(path.isEmpty ? piece.points : piece.points.skip(1));
      meters += piece.meters;
    }
    return path.length < 2 ? null : StreetPath(path, meters);
  }

  Future<StreetPath?> _request(List<GeoPoint> points) async {
    try {
      final response =
          await _client.get(Uri.parse(buildUrl(points))).timeout(const Duration(seconds: 25));
      return response.statusCode == 200 ? parse(utf8.decode(response.bodyBytes)) : null;
    } catch (_) {
      return null;
    }
  }

  /// Las coordenadas van como longitud,latitud separadas por punto y coma.
  static String buildUrl(List<GeoPoint> points) {
    final coordinates = points
        .map((point) => '${point.longitude.toStringAsFixed(6)},${point.latitude.toStringAsFixed(6)}')
        .join(';');
    return '$_base/$coordinates?overview=full&geometries=geojson';
  }

  /// Lee la respuesta de OSRM; `null` si no trae un camino usable.
  static StreetPath? parse(String body) {
    try {
      final root = jsonDecode(body) as Map<String, dynamic>;
      if (root['code'] != 'Ok') return null;
      final route = (root['routes'] as List).first as Map<String, dynamic>;
      final coordinates = (route['geometry'] as Map<String, dynamic>)['coordinates'] as List;
      final points = [
        // GeoJSON guarda [longitud, latitud].
        for (final pair in coordinates)
          GeoPoint(((pair as List)[1] as num).toDouble(), (pair[0] as num).toDouble()),
      ];
      return points.length < 2 ? null : StreetPath(points, (route['distance'] as num?)?.toDouble() ?? 0);
    } catch (_) {
      return null;
    }
  }
}

/// Una ruta nueva con los datos de [route], para no volver a escribirlos: sirve
/// para las variantes de una misma empresa. Con [reversed] es su vuelta: mismos
/// puntos en el orden contrario, y origen y destino cambiados.
///
/// Queda sin guardar y como pendiente, para que no se publique sin revisarla.
BusRoute duplicateRoute(BusRoute route, {bool reversed = false}) {
  final copy = BusRoute.fromJson(route.toJson())..status = RouteStatus.pending;
  if (!reversed) return copy..name = _limit('${route.name.trim()} (copia)');
  return copy
    ..name = _limit('${route.name.trim()} (vuelta)')
    ..origin = route.destination
    ..destination = route.origin
    // La ida y la vuelta ya son dos rutas distintas.
    ..bidirectional = false
    ..stops = copy.stops.reversed.toList()
    ..path = copy.path.reversed.toList();
}

String _limit(String name) => name.length <= 80 ? name : name.substring(0, 80);
