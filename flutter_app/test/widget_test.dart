import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_norte_admin/api.dart';
import 'package:ruta_norte_admin/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _route = {
  'id': 'california-porvenir',
  'letter': 'A',
  'name': 'California',
  'vehicleName': 'Empresa California',
  'origin': 'El Porvenir',
  'destination': 'Huanchaco',
  'durationMinutes': 38,
  'fare': 2,
  'schedule': '05:00 – 22:30',
  'description': '',
  'imageUrl': null,
  'bidirectional': true,
  'status': 'APPROVED',
  'stops': [
    {'name': 'El Porvenir', 'latitude': -8.0806, 'longitude': -78.995},
    {'name': 'Huanchaco', 'latitude': -8.0797, 'longitude': -79.1187},
  ],
  'path': null,
};

/// Un servidor de mentira con una cuenta de administrador y una ruta.
ApiClient _fakeApi({List<http.Request>? log}) {
  http.Response json(Object body, [int status = 200]) => http.Response.bytes(
        utf8.encode(jsonEncode(body)),
        status,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );

  return ApiClient(
    client: MockClient((request) async {
      log?.add(request);
      final path = request.url.path.split('/server').last;
      if (path == '/api/auth/login') {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        return body['password'] == 'micro-2026'
            ? json({'token': 'token-de-prueba', 'user': {'displayName': 'Ana'}})
            : json({'error': 'credenciales_invalidas', 'message': 'Correo o contraseña incorrectos'}, 401);
      }
      if (request.headers['Authorization'] != 'Bearer token-de-prueba') {
        return json({'error': 'no_autorizado', 'message': 'Inicia sesión para continuar'}, 401);
      }
      return switch ((request.method, path)) {
        ('GET', '/api/admin/me') => json({'displayName': 'Ana Quispe'}),
        ('GET', '/api/admin/routes') => json({'routes': [_route]}),
        ('GET', '/api/admin/feedback') => json({
            'feedback': [
              {
                'id': 'sugerencia-1',
                'subject': 'Rutas',
                'message': 'Falta la ruta a Moche',
                'appVersion': '1.0',
                'device': 'Pixel 8',
                'createdAt': 1791648000000,
                'userName': 'Luis',
                'userEmail': 'luis@correo.pe',
              },
            ],
          }),
        ('DELETE', '/api/admin/feedback/sugerencia-1') => http.Response('', 204),
        ('PUT', '/api/admin/routes/california-porvenir') =>
          json({..._route, ...jsonDecode(request.body) as Map<String, dynamic>, 'id': 'california-porvenir'}),
        _ => json({'error': 'no_encontrada', 'message': 'Ese camino no existe'}, 404),
      };
    }),
  );
}

Future<void> _signIn(WidgetTester tester, {String password = 'micro-2026'}) async {
  await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), 'ana@correo.pe');
  await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), password);
  await tester.tap(find.text('Entrar al panel'));
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  group('dirección del servidor', () {
    test('quita las barras del final', () {
      expect(normalizeServerUrl(' https://x.supabase.co/functions/v1/server/ '), 'https://x.supabase.co/functions/v1/server');
    });

    test('solo acepta HTTP en localhost', () {
      expect(normalizeServerUrl('http://localhost:54321/functions/v1/server'), startsWith('http://localhost'));
      expect(() => normalizeServerUrl('http://ejemplo.pe/server'), throwsA(isA<ApiException>()));
    });

    test('rechaza credenciales y parámetros', () {
      expect(() => normalizeServerUrl('https://ana:clave@x.pe/server'), throwsA(isA<ApiException>()));
      expect(() => normalizeServerUrl('https://x.pe/server?a=1'), throwsA(isA<ApiException>()));
      expect(() => normalizeServerUrl('no es una dirección'), throwsA(isA<ApiException>()));
    });
  });

  group('ruta', () {
    test('se lee y se manda con el formato del servidor', () {
      final route = BusRoute.fromJson(_route);
      expect(route.status, RouteStatus.approved);
      expect(route.stops, hasLength(2));
      expect(route.path, isEmpty);

      final sent = route.toJson();
      expect(sent['fare'], 2.0);
      expect(sent['status'], 'APPROVED');
      // Sin trazado dibujado se manda null, para que la app lo estime.
      expect(sent['path'], isNull);
      expect(sent.containsKey('id'), isFalse);
    });

    test('un solo punto no es un trazado', () {
      final route = BusRoute.fromJson(_route)..path.add(GeoPoint(-8.1, -79));
      expect(route.toJson()['path'], isNull);
      route.path.add(GeoPoint(-8.2, -79));
      expect(route.toJson()['path'], hasLength(2));
    });

    test('la copia de trabajo no toca la original', () {
      final original = BusRoute.fromJson(_route);
      final copy = original.copy()
        ..name = 'Otra'
        ..stops.removeLast();
      expect(copy.id, original.id);
      expect(original.name, 'California');
      expect(original.stops, hasLength(2));
    });

    test('solo se cargan fotos por HTTPS', () {
      expect((BusRoute()..imageUrl = 'https://x.pe/micro.jpg').safeImageUrl, isNotNull);
      expect((BusRoute()..imageUrl = 'http://x.pe/micro.jpg').safeImageUrl, isNull);
      expect((BusRoute()..imageUrl = 'javascript:alert(1)').safeImageUrl, isNull);
    });
  });

  group('panel', () {
    testWidgets('con la contraseña mala no entra y dice por qué', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _signIn(tester, password: 'otra-cosa');

      expect(find.text('Correo o contraseña incorrectos'), findsOneWidget);
      expect(find.text('Ingresa al panel'), findsOneWidget);
    });

    testWidgets('al entrar se ve el catálogo y se puede filtrar', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _signIn(tester);

      expect(find.text('Ana Quispe'), findsOneWidget);
      expect(find.text('California'), findsOneWidget);
      expect(find.text('El Porvenir → Huanchaco'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Buscar por nombre, micro, origen o destino'), 'moche');
      await tester.pumpAndSettle();
      expect(find.text('California'), findsNothing);
      expect(find.text('Ninguna ruta coincide con la búsqueda.'), findsOneWidget);
    });

    testWidgets('editar una ruta la guarda en el servidor', (tester) async {
      tester.view.physicalSize = const Size(1400, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _signIn(tester);

      await tester.tap(find.text('Editar ruta'));
      await tester.pumpAndSettle();
      expect(find.text('Datos del micro'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextFormField, 'Nombre de la ruta'), 'California Norte');
      await tester.enterText(find.widgetWithText(TextFormField, 'Pasaje (S/)'), '2,50');
      await tester.tap(find.text('Guardar ruta'));
      await tester.pumpAndSettle();

      final saved = log.lastWhere((request) => request.method == 'PUT');
      final body = jsonDecode(saved.body) as Map<String, dynamic>;
      expect(body['name'], 'California Norte');
      expect(body['fare'], 2.5);
      expect(body['stops'], hasLength(2));

      // De vuelta en el catálogo, con el nombre nuevo.
      expect(find.text('California Norte'), findsOneWidget);
    });

    testWidgets('el buzón enseña las sugerencias y deja borrarlas', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _signIn(tester);

      await tester.tap(find.text('Sugerencias'));
      await tester.pumpAndSettle();
      expect(find.text('Falta la ruta a Moche'), findsOneWidget);
      expect(find.text('Luis · luis@correo.pe'), findsOneWidget);
      expect(find.text('Versión 1.0 · Pixel 8'), findsOneWidget);

      await tester.tap(find.byTooltip('Eliminar sugerencia'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Eliminar'));
      await tester.pumpAndSettle();

      expect(log.last.method, 'DELETE');
      expect(find.text('Falta la ruta a Moche'), findsNothing);
      expect(find.text('El buzón está vacío.'), findsOneWidget);

      // De vuelta en las rutas, el catálogo sigue ahí.
      await tester.tap(find.text('Rutas y micros').first);
      await tester.pumpAndSettle();
      expect(find.text('California'), findsOneWidget);
    });

    testWidgets('cerrar sesión vuelve al acceso', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _signIn(tester);
      await tester.tap(find.text('Cerrar sesión'));
      await tester.pumpAndSettle();

      expect(find.text('Ingresa al panel'), findsOneWidget);
    });
  });
}
