import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_norte_admin/api.dart';
import 'package:ruta_norte_admin/pages/editor_page.dart';
import 'package:ruta_norte_admin/route_tools.dart';
import 'package:ruta_norte_admin/theme.dart';

BusRoute _california() => BusRoute(
      id: 'r1',
      letter: 'C',
      name: 'California',
      vehicleName: 'Empresa California',
      origin: 'El Porvenir',
      destination: 'Huanchaco',
      durationMinutes: 50,
      fare: 2,
      stops: [
        RouteStop('Mercado', -8.08, -79.00),
        RouteStop('Óvalo Grau', -8.11, -79.03),
        RouteStop('Playa', -8.08, -79.12),
      ],
      path: [GeoPoint(-8.08, -79.00), GeoPoint(-8.10, -79.02), GeoPoint(-8.08, -79.12)],
    );

/// Lo que responde OSRM: las coordenadas van como [longitud, latitud].
String _osrm(List<List<double>> coordinates, [double meters = 4200]) => jsonEncode({
      'code': 'Ok',
      'routes': [
        {
          'distance': meters,
          'geometry': {'coordinates': coordinates},
        },
      ],
    });

void main() {
  group('trazar por las calles', () {
    test('pide el camino por los puntos, en orden y con longitud primero', () async {
      final asked = <Uri>[];
      final streets = StreetRouter(
        client: MockClient((request) async {
          asked.add(request.url);
          return http.Response(
            _osrm([
              [-79.00, -8.08],
              [-79.01, -8.09],
              [-79.03, -8.11],
            ]),
            200,
          );
        }),
      );

      final path = await streets.through(_california().stops.take(2).toList());

      expect(asked.single.host, 'routing.openstreetmap.de');
      expect(asked.single.path, endsWith('/-79.000000,-8.080000;-79.030000,-8.110000'));
      expect(path!.points, hasLength(3));
      expect(path.points[1].latitude, -8.09);
      expect(path.points[1].longitude, -79.01);
      expect(path.meters, 4200);
    });

    test('sin servicio o sin camino no devuelve nada', () async {
      final caido = StreetRouter(client: MockClient((_) async => http.Response('saturado', 503)));
      expect(await caido.through(_california().stops), isNull);
      expect(StreetRouter.parse('{"code":"NoRoute"}'), isNull);
      expect(StreetRouter.parse('esto no es JSON'), isNull);
      // Con un solo punto no hay nada que trazar.
      expect(await caido.through([GeoPoint(-8.1, -79.0)]), isNull);
    });
  });

  group('copiar una ruta', () {
    test('duplicar conserva los datos y queda como ruta nueva y pendiente', () {
      final copy = duplicateRoute(_california());
      expect(copy.id, isNull);
      expect(copy.name, 'California (copia)');
      expect(copy.fare, 2);
      expect(copy.status, RouteStatus.pending);
      expect(copy.stops.map((stop) => stop.name), ['Mercado', 'Óvalo Grau', 'Playa']);
    });

    test('la vuelta invierte el recorrido y cambia origen por destino', () {
      final original = _california();
      final back = duplicateRoute(original, reversed: true);
      expect(back.id, isNull);
      expect(back.name, 'California (vuelta)');
      expect(back.origin, 'Huanchaco');
      expect(back.destination, 'El Porvenir');
      expect(back.bidirectional, isFalse);
      expect(back.stops.map((stop) => stop.name), ['Playa', 'Óvalo Grau', 'Mercado']);
      expect(back.path.first.longitude, -79.12);
      // La original no se toca.
      expect(original.stops.first.name, 'Mercado');
    });
  });

  testWidgets('el editor traza el recorrido por las calles y lo guarda', (tester) async {
    tester.view.physicalSize = const Size(1400, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    final saved = <Map<String, dynamic>>[];
    final api = ApiClient(
      client: MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        saved.add(body);
        return http.Response.bytes(utf8.encode(jsonEncode({...body, 'id': 'r1'})), 200,
            headers: {'content-type': 'application/json; charset=utf-8'});
      }),
    );
    final streets = StreetRouter(
      client: MockClient((_) async => http.Response(
            _osrm([
              [-79.00, -8.08],
              [-79.01, -8.09],
              [-79.02, -8.10],
              [-79.03, -8.11],
            ]),
            200,
          )),
    );
    final route = _california()..path = [];

    await tester.pumpWidget(MaterialApp(
      theme: buildTheme(),
      home: Scaffold(
        body: EditorPage(
          api: api,
          route: route,
          streets: streets,
          onSaved: (_) {},
          onDeleted: (_) {},
          onCancel: () {},
          onError: (error) => fail('$error'),
        ),
      ),
    ));
    await tester.pump();

    await tester.tap(find.text('Trazar por las calles'));
    await tester.pumpAndSettle();
    expect(route.path, hasLength(4));
    expect(find.textContaining('Recorrido trazado: 4.2 km'), findsOneWidget);

    // Al guardar, el editor queda esperando a que lo cierren: no se asienta.
    await tester.tap(find.text('Guardar ruta'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    expect(saved.single['path'], hasLength(4));
  });
}
