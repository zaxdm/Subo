import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ruta_norte_admin/api.dart';
import 'package:ruta_norte_admin/pages/alerts_page.dart';
import 'package:ruta_norte_admin/pages/feedback_page.dart';
import 'package:ruta_norte_admin/pages/stats_page.dart';
import 'package:ruta_norte_admin/pages/team_page.dart';
import 'package:ruta_norte_admin/theme.dart';

/// Un servidor de mentira con lo que piden las secciones del panel.
ApiClient _fakeApi(List<http.Request> log) {
  http.Response json(Object body, [int status = 200]) => http.Response.bytes(
        utf8.encode(jsonEncode(body)),
        status,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );

  return ApiClient(
    client: MockClient((request) async {
      log.add(request);
      final path = request.url.path.split('/server').last;
      return switch ((request.method, path)) {
        ('GET', '/api/admin/alerts') => json({
            'alerts': [
              {
                'id': 'a1',
                'type': 'OVERCHARGE',
                'latitude': -8.11,
                'longitude': -79.03,
                'placeName': 'Av. España',
                'comment': 'Cobran S/ 3 en vez de 2',
                'confirmations': 4,
                'dismissals': 1,
                'createdAt': 1791648000000,
                'authorId': 'u1',
                'authorName': 'Luis',
                'authorEmail': 'luis@correo.pe',
                'authorBlocked': false,
                'authorReputation': 'TRUSTED',
              },
            ],
          }),
        ('DELETE', '/api/admin/alerts/a1') => http.Response('', 204),
        ('POST', '/api/admin/users/u1/block') => http.Response('', 204),
        ('GET', '/api/admin/stats') => json({
            'users': 120,
            'newUsersThisWeek': 9,
            'activeAlerts': 3,
            'ratings': 14,
            'pendingRoutes': 1,
            'newFeedback': 2,
            'alertsThisWeek': {'TRAFFIC': 6, 'OVERCHARGE': 2},
            'alertsByHour': [for (var hour = 0; hour < 24; hour++) hour == 7 ? 5 : 0],
            'topRoutes': [
              {'id': 'california', 'name': 'California', 'letter': 'A', 'views': 40},
            ],
          }),
        ('GET', '/api/admin/contributors') => json({'contributors': []}),
        ('POST', '/api/admin/contributors') => json({'id': 'u2', 'displayName': 'Rosa', 'email': 'rosa@correo.pe'}),
        ('DELETE', '/api/admin/contributors/u2') => http.Response('', 204),
        ('GET', '/api/admin/feedback') => json({
            'feedback': [
              {
                'id': 's1',
                'subject': 'Rutas',
                'message': 'Falta la ruta a Moche',
                'createdAt': 1791648000000,
                'userName': 'Luis',
                'userEmail': 'luis@correo.pe',
                'status': 'NEW',
                'reply': null,
              },
            ],
          }),
        ('PUT', '/api/admin/feedback/s1') => http.Response('', 204),
        _ => json({'error': 'no_encontrada', 'message': 'Ese camino no existe'}, 404),
      };
    }),
  );
}

Future<void> _open(WidgetTester tester, Widget page) async {
  tester.view.physicalSize = const Size(1200, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(MaterialApp(theme: buildTheme(), home: Scaffold(body: page)));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('moderación enseña la alerta con su autor y deja bloquearlo y borrarla', (tester) async {
    final log = <http.Request>[];
    await _open(tester, AlertsPage(api: _fakeApi(log), onError: (error) => fail('$error')));

    expect(find.text('Cobro de más'), findsOneWidget);
    expect(find.text('Cobran S/ 3 en vez de 2'), findsOneWidget);
    expect(find.text('Luis · luis@correo.pe'), findsOneWidget);
    expect(find.text('Suele acertar'), findsOneWidget);
    expect(find.text('Sigue ahí: 4 · Ya no está: 1'), findsOneWidget);

    await tester.tap(find.text('Bloquear cuenta'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Bloquear'));
    await tester.pumpAndSettle();
    expect(jsonDecode(log.last.body), {'blocked': true});
    expect(find.text('Cuenta bloqueada'), findsOneWidget);
    expect(find.text('Desbloquear cuenta'), findsOneWidget);

    await tester.tap(find.text('Eliminar alerta'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Eliminar'));
    await tester.pumpAndSettle();
    expect(log.last.method, 'DELETE');
    expect(find.text('No hay alertas vigentes.'), findsOneWidget);
  });

  testWidgets('las estadísticas resumen cuentas, alertas y rutas más vistas', (tester) async {
    await _open(tester, StatsPage(api: _fakeApi([]), onError: (error) => fail('$error')));

    expect(find.text('120'), findsOneWidget);
    expect(find.text('+9 esta semana'), findsOneWidget);
    expect(find.text('A · California'), findsOneWidget);
    expect(find.text('Tráfico'), findsOneWidget);
    expect(find.text('Cobro de más'), findsOneWidget);
    expect(find.byTooltip('7:00 · 5 alertas'), findsOneWidget);
  });

  testWidgets('se añade y se quita un colaborador por su correo', (tester) async {
    final log = <http.Request>[];
    await _open(tester, TeamPage(api: _fakeApi(log), onError: (error) => fail('$error')));
    expect(find.text('Todavía no hay colaboradores.'), findsOneWidget);

    await tester.enterText(find.byType(TextField), ' rosa@correo.pe ');
    await tester.tap(find.text('Añadir'));
    await tester.pumpAndSettle();
    expect(jsonDecode(log.last.body), {'email': 'rosa@correo.pe'});
    expect(find.text('Rosa'), findsOneWidget);

    await tester.tap(find.text('Quitar'));
    await tester.pumpAndSettle();
    expect(log.last.method, 'DELETE');
    expect(find.text('Todavía no hay colaboradores.'), findsOneWidget);
  });

  testWidgets('responder una sugerencia la deja resuelta', (tester) async {
    final log = <http.Request>[];
    await _open(tester, FeedbackPage(api: _fakeApi(log), onError: (error) => fail('$error')));
    expect(find.text('Nueva'), findsOneWidget);

    await tester.tap(find.text('Responder'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Ya la agregamos, gracias');
    await tester.tap(find.text('Guardar'));
    await tester.pumpAndSettle();

    expect(jsonDecode(log.last.body), {'status': 'RESOLVED', 'reply': 'Ya la agregamos, gracias'});
    expect(find.text('Respuesta: Ya la agregamos, gracias'), findsOneWidget);
    expect(find.text('Resuelta'), findsOneWidget);
    expect(find.text('Cambiar respuesta'), findsOneWidget);
  });
}
