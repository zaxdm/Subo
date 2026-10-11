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
        return switch (body['password']) {
          'micro-2026' => json({'token': 'token-de-prueba', 'user': {'displayName': 'Ana'}}),
          'pasajero-2026' => json({'token': 'token-de-pasajero', 'user': {'displayName': 'Luis Rojas'}}),
          _ => json({'error': 'credenciales_invalidas', 'message': 'Correo o contraseña incorrectos'}, 401),
        };
      }
      if (path == '/api/auth/register') {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        // Con este correo el servidor pide confirmar con un código.
        if (body['email'] == 'nuevo@correo.pe') {
          return json({'verificationRequired': true, 'email': 'nuevo@correo.pe', 'minutes': 15}, 202);
        }
        if (body['email'] == 'ana@correo.pe') {
          return json({'error': 'correo_en_uso', 'message': 'Ese correo ya tiene una cuenta. Inicia sesión con ella'}, 409);
        }
        return json({'token': 'token-de-pasajero', 'user': {'displayName': 'Rosa Díaz'}}, 201);
      }
      if (path == '/api/auth/register/verify') {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        return body['code'] == '482913'
            ? json({'token': 'token-de-pasajero', 'user': {'displayName': 'Rosa Díaz'}}, 201)
            : json({'error': 'peticion_invalida', 'message': 'Ese código no es correcto o ya caducó. Pide uno nuevo'}, 400);
      }
      // Las líneas publicadas y las alertas las ve cualquiera, con o sin cuenta.
      if (request.method == 'GET' && path == '/api/routes') return json({'routes': [_route]});
      if (request.method == 'GET' && path == '/api/alerts') {
        return json({
          'alerts': [
            {'id': 'a1', 'type': 'TRAFFIC', 'latitude': -8.11, 'longitude': -79.03, 'placeName': 'Av. España'},
          ],
        });
      }
      if (request.headers['Authorization'] == 'Bearer token-de-pasajero') {
        if (path == '/api/assistant') {
          return json({'answer': 'Toma la California hasta Huanchaco.', 'routeId': 'california-porvenir'});
        }
        if (path == '/api/feedback') return json({'id': 'sugerencia-2'}, 201);
        return json({'error': 'solo_administrador', 'message': 'Tu cuenta no tiene acceso de administrador'}, 403);
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

/// Se entra por Inicio; al mapa se va desde la barra de arriba.
Future<void> _openMap(WidgetTester tester) async {
  await tester.pumpAndSettle();
  await tester.tap(find.text('Mapa').first);
  await tester.pumpAndSettle();
}

Future<void> _signIn(WidgetTester tester, {String password = 'micro-2026'}) async {
  // Se entra a la web por el mapa; el acceso se abre desde la barra de arriba.
  if (find.text('Entrar').evaluate().isEmpty) {
    await tester.tap(find.text('Iniciar sesión'));
    await tester.pumpAndSettle();
  }
  await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), 'ana@correo.pe');
  await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), password);
  await tester.tap(find.text('Entrar'));
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

  group('acceso con Google', () {
    test('pide un desafío y entra con la credencial de Google', () async {
      final log = <http.Request>[];
      final api = ApiClient(
        client: MockClient((request) async {
          log.add(request);
          final path = request.url.path.split('/server').last;
          http.Response json(Object body, [int status = 200]) =>
              http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});
          return switch (path) {
            '/api/auth/google/challenge' => json({'nonce': 'desafio-de-un-solo-uso'}),
            '/api/auth/google' => json({'token': 't', 'user': {'displayName': 'Luis Rojas'}}),
            // No es administrador: es un pasajero.
            _ => json({'error': 'solo_administrador', 'message': 'Sin acceso'}, 403),
          };
        }),
      );

      final nonce = await api.googleChallenge();
      final session = await api.signInWithGoogle(idToken: 'credencial', nonce: nonce, existingPassword: '');

      expect(session.name, 'Luis Rojas');
      expect(session.isAdmin, isFalse);
      final sent = jsonDecode(log.firstWhere((r) => r.url.path.endsWith('/api/auth/google')).body) as Map;
      // Sin contraseña escrita no se manda ninguna.
      expect(sent, {'idToken': 'credencial', 'nonce': 'desafio-de-un-solo-uso'});
    });

    test('si el correo ya tenía cuenta, dice que hace falta su contraseña', () async {
      final api = ApiClient(
        client: MockClient((request) async => http.Response.bytes(
              utf8.encode(jsonEncode({
                'error': 'google_vinculacion_requerida',
                'message': 'Este correo ya tiene una cuenta. Escribe su contraseña',
              })),
              409,
              headers: {'content-type': 'application/json; charset=utf-8'},
            )),
      );
      await expectLater(
        api.signInWithGoogle(idToken: 'credencial', nonce: 'n'),
        throwsA(isA<ApiException>().having((e) => e.message, 'mensaje', contains('Escribe su contraseña'))),
      );
      expect(api.isSignedIn, isFalse);
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
      expect(find.text('Inicia sesión'), findsOneWidget);
    });

    testWidgets('un pasajero entra por el mismo acceso y no ve el panel', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _openMap(tester);
      await _signIn(tester, password: 'pasajero-2026');

      expect(find.text('Hola, Luis. ¿Qué micro tomas hoy?'), findsOneWidget);
      expect(find.text('California'), findsOneWidget);
      // Nada del panel: ni sus secciones ni la forma de cambiar una ruta.
      expect(find.text('Sugerencias'), findsNothing);
      expect(find.text('Editar ruta'), findsNothing);
      expect(find.text('Nueva ruta'), findsNothing);
      expect(find.text('CENTRO DE CONTROL'), findsNothing);
      // Y tampoco pidió el catálogo de administración.
      expect(log.any((request) => request.url.path.endsWith('/api/admin/routes')), isFalse);

      await tester.tap(find.text('California'));
      await tester.pumpAndSettle();
      expect(find.text('PARADEROS'), findsOneWidget);
      expect(find.text('Huanchaco'), findsOneWidget);
    });

    testWidgets('se abre por Inicio, y el mapa es la segunda pestaña', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await tester.pumpAndSettle();

      expect(find.text('Hecho en Trujillo, para moverse por Trujillo'), findsOneWidget);
      expect(find.text('¿Qué micro tomas hoy?'), findsNothing);
      // En la barra, Inicio va antes que Mapa.
      expect(tester.getTopLeft(find.text('Inicio').first).dx, lessThan(tester.getTopLeft(find.text('Mapa').first).dx));

      await tester.tap(find.text('Ver el mapa'));
      await tester.pumpAndSettle();
      expect(find.text('¿Qué micro tomas hoy?'), findsOneWidget);
    });

    testWidgets('el mapa se ve sin cuenta, pero abrir una línea pide sesión', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _openMap(tester);
      await tester.pumpAndSettle();

      // Sin cuenta ya se ven el mapa y las líneas.
      expect(find.text('¿Qué micro tomas hoy?'), findsOneWidget);
      expect(find.text('El Porvenir – Huanchaco'), findsOneWidget);

      // Tocar una línea lleva al acceso, no a su recorrido.
      await tester.tap(find.text('California'));
      await tester.pumpAndSettle();
      expect(find.text('PARADEROS'), findsNothing);
      expect(find.text('Entra para ver el recorrido de la línea.'), findsOneWidget);

      // Al entrar, la línea que quería ver ya está abierta.
      await _signIn(tester, password: 'pasajero-2026');
      expect(find.text('PARADEROS'), findsOneWidget);
      expect(find.text('Todas las líneas'), findsOneWidget);
    });

    testWidgets('el asistente pide sesión y recomienda una línea', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _openMap(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Asistente'));
      await tester.pumpAndSettle();
      expect(find.text('Entrar para preguntar'), findsOneWidget);

      await _signIn(tester, password: 'pasajero-2026');
      await tester.tap(find.text('Asistente'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('¿Qué micro va a Huanchaco?'));
      await tester.pumpAndSettle();
      expect(find.text('Toma la California hasta Huanchaco.'), findsOneWidget);

      await tester.tap(find.text('Ver California en el mapa'));
      await tester.pumpAndSettle();
      expect(find.text('PARADEROS'), findsOneWidget);
    });

    testWidgets('las alertas y las páginas del pie se ven sin cuenta', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _openMap(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Alertas'));
      await tester.pumpAndSettle();
      expect(find.text('Tráfico'), findsOneWidget);
      expect(find.text('Av. España'), findsOneWidget);

      await tester.tap(find.text('Inicio').last);
      await tester.pumpAndSettle();
      expect(find.text('Hecho en Trujillo, para moverse por Trujillo'), findsOneWidget);

      // Inicio cuenta lo que ya hay en el mapa y deja sitio para las fotos.
      expect(find.text('líneas en el mapa'), findsOneWidget);
      expect(find.text('Foto de portada'), findsOneWidget);
      expect(find.text('Foto del equipo'), findsOneWidget);

      // La descarga de la app tiene su página.
      await tester.tap(find.text('Descargar app').first);
      await tester.pumpAndSettle();
      expect(find.text('Lleva Subo en tu celular'), findsOneWidget);
      expect(find.text('Descargar para Android'), findsOneWidget);
      expect(find.text('Para Android · Versión 1.0 · 4 MB'), findsOneWidget);

      // Las páginas de texto llevan el pie completo al final.
      await tester.ensureVisible(find.text('Privacidad'));
      await tester.tap(find.text('Privacidad'));
      await tester.pumpAndSettle();
      expect(find.text('Qué datos usamos y para qué'), findsOneWidget);

      await tester.ensureVisible(find.text('Términos'));
      await tester.tap(find.text('Términos'));
      await tester.pumpAndSettle();
      expect(find.text('Las reglas para usar Subo'), findsOneWidget);

      await tester.tap(find.text('Contacto').first);
      await tester.pumpAndSettle();
      expect(find.text('Envíanos un mensaje'), findsOneWidget);
      expect(find.text('WhatsApp'), findsOneWidget);
      expect(find.text('Correo'), findsOneWidget);
    });

    testWidgets('el acceso es una ventana sobre la página y se puede cerrar', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await _openMap(tester);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Iniciar sesión'));
      await tester.pumpAndSettle();

      // El mapa sigue debajo.
      expect(find.byType(Dialog), findsOneWidget);
      expect(find.text('¿Qué micro tomas hoy?'), findsOneWidget);

      await tester.tap(find.byTooltip('Cerrar'));
      await tester.pumpAndSettle();
      expect(find.byType(Dialog), findsNothing);
      expect(find.text('¿Qué micro tomas hoy?'), findsOneWidget);
    });

    testWidgets('escribir al equipo sin cuenta pide entrar y no pierde el mensaje', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Contacto').first);
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Tu mensaje'), 'Falta la línea a Laredo');
      await tester.tap(find.text('Enviar mensaje'));
      await tester.pumpAndSettle();
      expect(find.byType(Dialog), findsOneWidget);
      expect(log.any((request) => request.url.path.endsWith('/api/feedback')), isFalse);

      await _signIn(tester, password: 'pasajero-2026');
      await tester.tap(find.text('Enviar mensaje'));
      await tester.pumpAndSettle();
      final sent = jsonDecode(log.lastWhere((request) => request.url.path.endsWith('/api/feedback')).body) as Map;
      expect(sent['message'], 'Falta la línea a Laredo');
    });

    testWidgets('con cuenta se puede escribir al equipo', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _signIn(tester, password: 'pasajero-2026');
      await tester.tap(find.text('Contacto').first);
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Tu mensaje'), 'La línea B ya no pasa por Mansiche');
      await tester.tap(find.text('Enviar mensaje'));
      await tester.pumpAndSettle();

      final sent = jsonDecode(log.lastWhere((request) => request.url.path.endsWith('/api/feedback')).body) as Map;
      expect(sent['subject'], 'Una ruta está mal');
      expect(sent['message'], 'La línea B ya no pasa por Mansiche');
      expect(find.text('Mensaje enviado. Gracias por ayudarnos a mejorar.'), findsOneWidget);
    });

    testWidgets('se puede crear una cuenta, que es de pasajero', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _openMap(tester);
      await tester.tap(find.text('Iniciar sesión'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Crea tu cuenta'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Tu nombre'), 'Rosa Díaz');
      await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), 'rosa@correo.pe');
      // Una contraseña corta no llega a mandarse.
      await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'corta');
      await tester.tap(find.text('Crear mi cuenta'));
      await tester.pumpAndSettle();
      expect(find.text('Usa al menos 8 caracteres'), findsOneWidget);
      expect(log.any((request) => request.url.path.endsWith('/register')), isFalse);

      await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'micro-seguro-1');
      await tester.tap(find.text('Crear mi cuenta'));
      await tester.pumpAndSettle();

      final sent = jsonDecode(log.firstWhere((request) => request.url.path.endsWith('/register')).body) as Map;
      expect(sent, {'displayName': 'Rosa Díaz', 'email': 'rosa@correo.pe', 'password': 'micro-seguro-1'});
      expect(find.text('Hola, Rosa. ¿Qué micro tomas hoy?'), findsOneWidget);
      expect(find.text('Editar ruta'), findsNothing);
    });

    testWidgets('al crear la cuenta hay que escribir el código que llega al correo', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      final log = <http.Request>[];
      await tester.pumpWidget(AdminApp(api: _fakeApi(log: log)));
      await _openMap(tester);
      await tester.tap(find.text('Iniciar sesión'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Crea tu cuenta'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Tu nombre'), 'Rosa Díaz');
      await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), 'nuevo@correo.pe');
      await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'micro-seguro-1');
      await tester.tap(find.text('Crear mi cuenta'));
      await tester.pumpAndSettle();

      // Todavía no entra: se le pide el código.
      expect(find.text('Escribe el código'), findsOneWidget);
      expect(find.text('Te enviamos un código de 6 dígitos a nuevo@correo.pe. Vale 15 minutos.'), findsOneWidget);
      expect(find.text('Hola, Rosa. ¿Qué micro tomas hoy?'), findsNothing);

      // Un código equivocado no entra y dice por qué.
      await tester.enterText(find.widgetWithText(TextField, 'Código'), '000000');
      await tester.tap(find.text('Confirmar y entrar'));
      await tester.pumpAndSettle();
      expect(find.text('Ese código no es correcto o ya caducó. Pide uno nuevo'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Código'), '482913');
      await tester.tap(find.text('Confirmar y entrar'));
      await tester.pumpAndSettle();

      final sent = jsonDecode(log.lastWhere((r) => r.url.path.endsWith('/register/verify')).body) as Map;
      expect(sent, {'email': 'nuevo@correo.pe', 'code': '482913'});
      expect(find.text('Hola, Rosa. ¿Qué micro tomas hoy?'), findsOneWidget);
    });

    testWidgets('no se puede crear una cuenta con un correo que ya tiene una', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi()));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Iniciar sesión'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Crea tu cuenta'));
      await tester.pumpAndSettle();

      await tester.enterText(find.widgetWithText(TextFormField, 'Tu nombre'), 'Otra Ana');
      await tester.enterText(find.widgetWithText(TextFormField, 'Correo'), 'ana@correo.pe');
      await tester.enterText(find.widgetWithText(TextFormField, 'Contraseña'), 'otra-clave-99');
      await tester.tap(find.text('Crear mi cuenta'));
      await tester.pumpAndSettle();

      expect(find.text('Ese correo ya tiene una cuenta. Inicia sesión con ella'), findsOneWidget);
      expect(find.text('Escribe el código'), findsNothing);
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

      // De vuelta en Inicio, como cualquier visitante.
      expect(find.text('Iniciar sesión'), findsOneWidget);
      expect(find.text('Hecho en Trujillo, para moverse por Trujillo'), findsOneWidget);
      expect(find.text('Editar ruta'), findsNothing);
    });
  });

  group('enlace compartido', () {
    test('solo /ruta/<id> apunta a una línea', () {
      expect(sharedLineOf(Uri.parse('https://subo.pe/ruta/california-porvenir')), 'california-porvenir');
      expect(sharedLineOf(Uri.parse('https://subo.pe/')), isNull);
      expect(sharedLineOf(Uri.parse('https://subo.pe/ruta/')), isNull);
      expect(sharedLineOf(Uri.parse('https://subo.pe/otra/california-porvenir')), isNull);
    });

    testWidgets('quien llega por el enlace va directo a esa línea', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi(), sharedLine: 'california-porvenir'));
      await tester.pumpAndSettle();

      // Sin cuenta, lo mismo que al tocarla: se le pide entrar y luego la ve.
      expect(find.text('Entra para ver el recorrido de la línea.'), findsOneWidget);
      await _signIn(tester, password: 'pasajero-2026');
      expect(find.text('PARADEROS'), findsOneWidget);
    });

    testWidgets('un enlace a una línea que ya no existe deja el mapa como siempre', (tester) async {
      tester.view.physicalSize = const Size(1400, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);

      await tester.pumpWidget(AdminApp(api: _fakeApi(), sharedLine: 'borrada'));
      await tester.pumpAndSettle();

      expect(find.text('¿Qué micro tomas hoy?'), findsOneWidget);
      expect(find.text('Entra para ver el recorrido de la línea.'), findsNothing);
    });
  });
}