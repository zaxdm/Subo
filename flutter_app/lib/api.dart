import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

/// Dirección del servidor de Subo (la función `server` de Supabase).
///
/// Se puede cambiar al compilar con `--dart-define=SERVER_URL=...`, y también
/// desde "Opciones avanzadas" en la pantalla de acceso.
const defaultServerUrl = String.fromEnvironment(
  'SERVER_URL',
  defaultValue: 'https://ibgbrgeesrtrytmtygtz.supabase.co/functions/v1/server',
);

/// Fallo con un mensaje listo para enseñar al administrador.
class ApiException implements Exception {
  ApiException(this.message, {this.unauthorized = false, this.forbidden = false});

  final String message;

  /// La cuenta existe, pero no tiene permiso para eso.
  final bool forbidden;

  /// La sesión ya no vale: hay que volver a la pantalla de acceso.
  final bool unauthorized;

  @override
  String toString() => message;
}

/// La cuenta con la que se entró.
class Session {
  const Session(this.name, {required this.isAdmin});

  final String name;

  /// `false`: un pasajero, que no ve el panel de administración.
  final bool isAdmin;
}

/// Un punto del mapa.
class GeoPoint {
  GeoPoint(this.latitude, this.longitude);

  double latitude;
  double longitude;

  factory GeoPoint.fromJson(Map<String, dynamic> json) =>
      GeoPoint((json['latitude'] as num).toDouble(), (json['longitude'] as num).toDouble());

  Map<String, dynamic> toJson() => {'latitude': latitude, 'longitude': longitude};
}

/// Un paradero: un punto con nombre.
class RouteStop extends GeoPoint {
  RouteStop(this.name, double latitude, double longitude) : super(latitude, longitude);

  String name;

  factory RouteStop.fromJson(Map<String, dynamic> json) => RouteStop(
        json['name'] as String? ?? '',
        (json['latitude'] as num).toDouble(),
        (json['longitude'] as num).toDouble(),
      );

  @override
  Map<String, dynamic> toJson() => {'name': name.trim(), 'latitude': latitude, 'longitude': longitude};
}

/// Estados de una ruta en el catálogo.
enum RouteStatus {
  approved('APPROVED', 'Publicada'),
  pending('PENDING', 'Pendiente'),
  rejected('REJECTED', 'Oculta');

  const RouteStatus(this.code, this.label);

  /// Como lo guarda el servidor.
  final String code;
  final String label;

  static RouteStatus fromCode(String? code) =>
      values.firstWhere((status) => status.code == code, orElse: () => RouteStatus.approved);
}

/// Una ruta del catálogo, tal como la devuelve y la recibe el servidor.
class BusRoute {
  BusRoute({
    this.id,
    this.letter = '',
    this.name = '',
    this.vehicleName = '',
    this.origin = '',
    this.destination = '',
    this.durationMinutes = 0,
    this.fare = 0,
    this.schedule = '',
    this.description = '',
    this.imageUrl,
    this.bidirectional = true,
    this.status = RouteStatus.approved,
    List<RouteStop>? stops,
    List<GeoPoint>? path,
  })  : stops = stops ?? [],
        path = path ?? [];

  /// `null` en una ruta que todavía no se guardó.
  final String? id;
  String letter;
  String name;
  String vehicleName;
  String origin;
  String destination;
  int durationMinutes;
  double fare;
  String schedule;
  String description;
  String? imageUrl;
  bool bidirectional;
  RouteStatus status;
  List<RouteStop> stops;

  /// Trazado dibujado a mano; vacío si la app debe estimarlo entre paraderos.
  List<GeoPoint> path;

  factory BusRoute.fromJson(Map<String, dynamic> json) => BusRoute(
        id: json['id'] as String?,
        letter: json['letter'] as String? ?? '',
        name: json['name'] as String? ?? '',
        vehicleName: json['vehicleName'] as String? ?? '',
        origin: json['origin'] as String? ?? '',
        destination: json['destination'] as String? ?? '',
        durationMinutes: (json['durationMinutes'] as num?)?.toInt() ?? 0,
        fare: (json['fare'] as num?)?.toDouble() ?? 0,
        schedule: json['schedule'] as String? ?? '',
        description: json['description'] as String? ?? '',
        imageUrl: (json['imageUrl'] as String?)?.trim().isEmpty ?? true ? null : json['imageUrl'] as String,
        bidirectional: json['bidirectional'] as bool? ?? true,
        status: RouteStatus.fromCode(json['status'] as String?),
        stops: [
          for (final stop in json['stops'] as List? ?? const []) RouteStop.fromJson(stop as Map<String, dynamic>),
        ],
        path: [
          for (final point in json['path'] as List? ?? const []) GeoPoint.fromJson(point as Map<String, dynamic>),
        ],
      );

  Map<String, dynamic> toJson() => {
        'letter': letter.trim(),
        'name': name.trim(),
        'vehicleName': vehicleName.trim(),
        'origin': origin.trim(),
        'destination': destination.trim(),
        'durationMinutes': durationMinutes,
        'fare': fare,
        'schedule': schedule.trim(),
        'description': description.trim(),
        'imageUrl': imageUrl,
        'bidirectional': bidirectional,
        'status': status.code,
        'stops': [for (final stop in stops) stop.toJson()],
        // Un trazado necesita al menos dos puntos; si no, la app lo estima.
        'path': path.length >= 2 ? [for (final point in path) point.toJson()] : null,
        'pathSnapped': false,
      };

  /// Copia independiente, para editar sin tocar la del catálogo hasta guardar.
  BusRoute copy() => BusRoute.fromJson({...toJson(), 'id': id});

  /// URL de la foto solo si es segura de cargar (HTTPS).
  String? get safeImageUrl {
    final url = Uri.tryParse(imageUrl ?? '');
    return url != null && url.scheme == 'https' ? imageUrl : null;
  }
}

/// Un aviso de la calle: tráfico, un accidente, obras…
class CityAlert {
  CityAlert({
    required this.id,
    required this.type,
    required this.latitude,
    required this.longitude,
    this.placeName,
    this.comment,
    this.confirmations = 0,
  });

  final String id;
  final String type;
  final double latitude;
  final double longitude;
  final String? placeName;
  final String? comment;

  /// Cuánta gente dijo que sigue ahí.
  final int confirmations;

  /// El tipo, dicho como en la app.
  String get label => switch (type) {
        'TRAFFIC' => 'Tráfico',
        'ACCIDENT' => 'Accidente',
        'OVERCHARGE' => 'Cobro de más',
        'ROADWORK' => 'Obras',
        'CROWDED' => 'Micro lleno',
        'DANGER' => 'Zona peligrosa',
        _ => 'Aviso',
      };

  factory CityAlert.fromJson(Map<String, dynamic> json) => CityAlert(
        id: json['id'] as String? ?? '',
        type: json['type'] as String? ?? '',
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        placeName: json['placeName'] as String?,
        comment: json['comment'] as String?,
        confirmations: (json['confirmations'] as num?)?.toInt() ?? 0,
      );
}

/// Lo que contesta el asistente y, si recomienda una línea, cuál.
class AssistantAnswer {
  const AssistantAnswer(this.text, this.routeId);

  final String text;
  final String? routeId;
}

/// Una sugerencia que alguien mandó desde Soporte en la app.
class Suggestion {
  Suggestion({
    required this.id,
    required this.subject,
    required this.message,
    required this.createdAt,
    this.appVersion = '',
    this.device = '',
    this.userName,
    this.userEmail,
  });

  final String id;
  final String subject;
  final String message;
  final DateTime createdAt;
  final String appVersion;
  final String device;

  /// `null` si la cuenta que la mandó ya no existe.
  final String? userName;
  final String? userEmail;

  factory Suggestion.fromJson(Map<String, dynamic> json) => Suggestion(
        id: json['id'] as String,
        subject: json['subject'] as String? ?? '',
        message: json['message'] as String? ?? '',
        createdAt: DateTime.fromMillisecondsSinceEpoch((json['createdAt'] as num?)?.toInt() ?? 0),
        appVersion: json['appVersion'] as String? ?? '',
        device: json['device'] as String? ?? '',
        userName: json['userName'] as String?,
        userEmail: json['userEmail'] as String?,
      );
}

/// Deja la dirección del servidor en su forma canónica o explica qué le pasa.
String normalizeServerUrl(String raw) {
  final url = Uri.tryParse(raw.trim());
  if (url == null || !url.hasScheme || url.host.isEmpty) {
    throw ApiException('Escribe la dirección completa del servidor (https://…).');
  }
  if (url.userInfo.isNotEmpty || url.hasQuery || url.hasFragment) {
    throw ApiException('Usa la dirección del servidor sin credenciales ni parámetros.');
  }
  final local = url.scheme == 'http' && (url.host == 'localhost' || url.host == '127.0.0.1');
  if (url.scheme != 'https' && !local) {
    throw ApiException('El servidor debe usar HTTPS (HTTP solo en localhost).');
  }
  return url.toString().replaceAll(RegExp(r'/+$'), '');
}

/// Habla con el servidor. La sesión vive solo en memoria: al recargar la
/// página hay que volver a entrar.
class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  static const _timeout = Duration(seconds: 30);

  String baseUrl = defaultServerUrl;
  String? _token;

  bool get isSignedIn => _token != null;

  /// Entra con una cuenta. Todos usan el mismo acceso: es el servidor quien
  /// dice si la cuenta es de administrador o de pasajero.
  Future<Session> signIn({required String server, required String email, required String password}) {
    return _open(server, '/api/auth/login', {'email': email.trim(), 'password': password}, email);
  }

  /// Crea una cuenta de pasajero y entra con ella. Por aquí nunca sale una
  /// cuenta de administrador: esas las da el administrador principal.
  Future<Session> register({
    required String server,
    required String displayName,
    required String email,
    required String password,
  }) {
    return _open(
      server,
      '/api/auth/register',
      {'displayName': displayName.trim(), 'email': email.trim(), 'password': password},
      email,
    );
  }

  Future<Session> _open(String server, String path, Map<String, dynamic> body, String email) async {
    baseUrl = normalizeServerUrl(server);
    _token = null;
    try {
      final opened = await _send('POST', path, body: body);
      _token = opened['token'] as String?;
      if (_token == null) throw ApiException('El servidor no devolvió una sesión. Revisa su dirección.');
      final user = opened['user'];
      final name = (user is Map ? user['displayName'] as String? : null) ?? email.trim();
      try {
        final admin = await _send('GET', '/api/admin/me');
        return Session(admin['displayName'] as String? ?? name, isAdmin: true);
      } on ApiException catch (error) {
        // Que no sea administrador no es un fallo: es un pasajero.
        if (!error.forbidden) rethrow;
        return Session(name, isAdmin: false);
      }
    } catch (_) {
      _token = null;
      rethrow;
    }
  }

  void signOut() => _token = null;

  /// Las líneas publicadas, tal como las ve cualquier pasajero. No pide sesión.
  Future<List<BusRoute>> publicRoutes({String? server}) async {
    if (server != null) baseUrl = normalizeServerUrl(server);
    final data = await _send('GET', '/api/routes');
    return [for (final route in data['routes'] as List? ?? const []) BusRoute.fromJson(route as Map<String, dynamic>)];
  }

  Future<List<BusRoute>> routes() async {
    final data = await _send('GET', '/api/admin/routes');
    return [for (final route in data['routes'] as List? ?? const []) BusRoute.fromJson(route as Map<String, dynamic>)];
  }

  /// Crea la ruta o, si ya tiene id, la actualiza. Devuelve la versión guardada.
  Future<BusRoute> saveRoute(BusRoute route) async {
    final id = route.id;
    final data = id == null
        ? await _send('POST', '/api/admin/routes', body: route.toJson())
        : await _send('PUT', '/api/admin/routes/${Uri.encodeComponent(id)}', body: route.toJson());
    return BusRoute.fromJson(data);
  }

  Future<void> deleteRoute(String id) => _send('DELETE', '/api/admin/routes/${Uri.encodeComponent(id)}');

  /// El buzón de sugerencias, de la más reciente a la más antigua.
  Future<List<Suggestion>> feedback() async {
    final data = await _send('GET', '/api/admin/feedback');
    return [for (final item in data['feedback'] as List? ?? const []) Suggestion.fromJson(item as Map<String, dynamic>)];
  }

  Future<void> deleteFeedback(String id) => _send('DELETE', '/api/admin/feedback/${Uri.encodeComponent(id)}');

  /// Sube una foto y devuelve su dirección pública.
  Future<String> uploadImage(Uint8List bytes, String contentType) async {
    final data = await _send('POST', '/api/admin/images', bytes: bytes, contentType: contentType);
    final url = data['url'] as String?;
    if (url == null) throw ApiException('El servidor no devolvió la dirección de la foto.');
    return url;
  }

  /// Las alertas que la gente dejó en la calle y siguen vigentes. No pide sesión.
  Future<List<CityAlert>> alerts() async {
    // Todo Trujillo desde su centro; el servidor recorta el radio a su máximo.
    final data = await _send('GET', '/api/alerts?lat=-8.112&lon=-79.0288&radius=30000');
    return [for (final alert in data['alerts'] as List? ?? const []) CityAlert.fromJson(alert as Map<String, dynamic>)];
  }

  /// Le pregunta al asistente. Pide sesión.
  Future<AssistantAnswer> ask(String question) async {
    final data = await _send('POST', '/api/assistant', body: {'question': question.trim(), 'language': 'es'});
    return AssistantAnswer(data['answer'] as String? ?? '', data['routeId'] as String?);
  }

  /// Manda un mensaje al buzón del equipo. Pide sesión.
  Future<void> sendFeedback({required String subject, required String message}) {
    return _send('POST', '/api/feedback', body: {'subject': subject, 'message': message.trim(), 'appVersion': 'web'});
  }

  Future<Map<String, dynamic>> _send(
    String method,
    String path, {
    Map<String, dynamic>? body,
    Uint8List? bytes,
    String? contentType,
  }) async {
    final request = http.Request(method, Uri.parse(baseUrl + path));
    request.headers['Accept'] = 'application/json';
    final token = _token;
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    } else if (bytes != null) {
      request.headers['Content-Type'] = contentType ?? 'application/octet-stream';
      request.bodyBytes = bytes;
    }

    final http.Response response;
    try {
      response = await http.Response.fromStream(await _client.send(request).timeout(_timeout));
    } on TimeoutException {
      throw ApiException('El servidor tardó demasiado en responder. Inténtalo de nuevo.');
    } on http.ClientException {
      throw ApiException('No se pudo conectar con el servidor. Revisa tu conexión y su dirección.');
    }

    Map<String, dynamic> data = const {};
    final text = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (text.isNotEmpty) {
      try {
        final decoded = jsonDecode(text);
        if (decoded is Map<String, dynamic>) data = decoded;
      } on FormatException {
        throw ApiException('El servidor devolvió una respuesta inesperada. Revisa su dirección.');
      }
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      // Un 401 al entrar es una contraseña mala; en cualquier otro momento, la sesión caducó.
      final expired = response.statusCode == 401 && !path.startsWith('/api/auth/');
      if (expired) _token = null;
      throw ApiException(
        data['message'] as String? ?? 'No se pudo completar la operación (${response.statusCode}).',
        unauthorized: expired,
        forbidden: response.statusCode == 403,
      );
    }
    return data;
  }
}
