import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Dirección del servidor de Subo (la función `server` de Supabase).
///
/// Se puede cambiar al compilar con `--dart-define=SERVER_URL=...`.
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
  const Session(this.name, {required this.isAdmin, this.isSuperAdmin = false, this.email = '', this.avatarUrl});

  /// La sesión de la cuenta que describe el servidor en [user]. Lo que [user]
  /// no traiga se toma de [fallback].
  factory Session.of(Map<dynamic, dynamic> user, {required Session fallback}) {
    final role = user['role'];
    return Session(
      user['displayName'] as String? ?? fallback.name,
      isAdmin: role == null ? fallback.isAdmin : role == 'admin' || role == 'super_admin',
      isSuperAdmin: role == null ? fallback.isSuperAdmin : role == 'super_admin',
      email: user['email'] as String? ?? fallback.email,
      avatarUrl: user.containsKey('avatarUrl') ? user['avatarUrl'] as String? : fallback.avatarUrl,
    );
  }

  final String name;

  /// `false`: un pasajero, que no ve el panel de administración.
  final bool isAdmin;

  /// El administrador principal: el único que da o quita el acceso a los demás.
  final bool isSuperAdmin;

  /// Vacío si el servidor no lo dijo.
  final String email;

  /// La foto de perfil; `null` si no tiene.
  final String? avatarUrl;
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

/// Un punto de referencia: un punto con nombre.
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
    this.fareSchool,
    this.fareUniversity,
    this.frequencyMinutes,
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

  /// Pasajes reducidos y cada cuántos minutos pasa; `null` si no se saben.
  double? fareSchool;
  double? fareUniversity;
  int? frequencyMinutes;
  String schedule;
  String description;
  String? imageUrl;
  bool bidirectional;
  RouteStatus status;
  List<RouteStop> stops;

  /// Trazado dibujado a mano; vacío si la app debe estimarlo entre puntos de referencia.
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
        fareSchool: (json['fareSchool'] as num?)?.toDouble(),
        fareUniversity: (json['fareUniversity'] as num?)?.toDouble(),
        frequencyMinutes: (json['frequencyMinutes'] as num?)?.toInt(),
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
        'fareSchool': fareSchool,
        'fareUniversity': fareUniversity,
        'frequencyMinutes': frequencyMinutes,
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
    this.status = SuggestionStatus.fresh,
    this.reply,
  });

  final String id;
  final String subject;
  final String message;
  final DateTime createdAt;
  final String appVersion;
  final String device;
  SuggestionStatus status;

  /// Lo que se le contestó; quien la escribió lo ve en la app.
  String? reply;

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
        status: SuggestionStatus.fromCode(json['status'] as String?),
        reply: json['reply'] as String?,
      );
}

/// En qué va una sugerencia del buzón.
enum SuggestionStatus {
  fresh('NEW', 'Nueva'),
  inReview('IN_REVIEW', 'En revisión'),
  resolved('RESOLVED', 'Resuelta');

  const SuggestionStatus(this.code, this.label);

  /// Como lo guarda el servidor.
  final String code;
  final String label;

  static SuggestionStatus fromCode(String? code) =>
      values.firstWhere((status) => status.code == code, orElse: () => SuggestionStatus.fresh);
}

/// Una alerta vigente, con quién la publicó: lo que hace falta para moderarla.
class ModerationAlert {
  ModerationAlert({required this.alert, required this.authorId, required this.createdAt});

  final CityAlert alert;
  final String authorId;
  final DateTime createdAt;
  String? authorName;
  String? authorEmail;
  bool authorBlocked = false;
  String? imageUrl;
  int dismissals = 0;

  /// TRUSTED, NEW o UNRELIABLE, según cómo le fue con sus alertas anteriores.
  String reputation = 'NEW';

  factory ModerationAlert.fromJson(Map<String, dynamic> json) => ModerationAlert(
        alert: CityAlert.fromJson(json),
        authorId: json['authorId'] as String? ?? '',
        createdAt: DateTime.fromMillisecondsSinceEpoch((json['createdAt'] as num?)?.toInt() ?? 0),
      )
        ..authorName = json['authorName'] as String?
        ..authorEmail = json['authorEmail'] as String?
        ..authorBlocked = json['authorBlocked'] as bool? ?? false
        ..imageUrl = json['imageUrl'] as String?
        ..dismissals = (json['dismissals'] as num?)?.toInt() ?? 0
        ..reputation = json['authorReputation'] as String? ?? 'NEW';
}

/// El resumen de uso que enseña el panel.
class Stats {
  Stats.fromJson(Map<String, dynamic> json)
      : users = (json['users'] as num?)?.toInt() ?? 0,
        newUsersThisWeek = (json['newUsersThisWeek'] as num?)?.toInt() ?? 0,
        activeAlerts = (json['activeAlerts'] as num?)?.toInt() ?? 0,
        ratings = (json['ratings'] as num?)?.toInt() ?? 0,
        pendingRoutes = (json['pendingRoutes'] as num?)?.toInt() ?? 0,
        newFeedback = (json['newFeedback'] as num?)?.toInt() ?? 0,
        alertsThisWeek = {
          for (final entry in (json['alertsThisWeek'] as Map<String, dynamic>? ?? const {}).entries)
            entry.key: (entry.value as num).toInt(),
        },
        alertsByHour = [for (final value in json['alertsByHour'] as List? ?? const []) (value as num).toInt()],
        topRoutes = [
          for (final route in json['topRoutes'] as List? ?? const [])
            (
              name: (route as Map<String, dynamic>)['name'] as String? ?? '',
              letter: route['letter'] as String? ?? '',
              views: (route['views'] as num?)?.toInt() ?? 0,
            ),
        ];

  final int users;
  final int newUsersThisWeek;
  final int activeAlerts;
  final int ratings;
  final int pendingRoutes;
  final int newFeedback;

  /// Alertas de los últimos 7 días, por tipo.
  final Map<String, int> alertsThisWeek;

  /// Alertas de los últimos 30 días por hora del día (0-23), en hora de Perú.
  final List<int> alertsByHour;

  /// Las rutas más abiertas en los últimos 30 días.
  final List<({String name, String letter, int views})> topRoutes;
}

/// Una cuenta con acceso al panel.
class Admin {
  Admin.fromJson(Map<String, dynamic> json)
      : id = json['id'] as String? ?? '',
        name = json['displayName'] as String? ?? '',
        email = json['email'] as String? ?? '',
        isPrincipal = json['role'] == 'super_admin';

  final String id;
  final String name;
  final String email;

  /// El administrador principal: a él no se le quita el acceso.
  final bool isPrincipal;
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

/// Habla con el servidor. La sesión se guarda en el navegador: al recargar la
/// página se sigue dentro hasta cerrar sesión o hasta que caduque.
class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;
  static const _timeout = Duration(seconds: 30);
  static const _tokenKey = 'session_token';
  static const _serverKey = 'session_server';

  String baseUrl = defaultServerUrl;
  String? _token;

  bool get isSignedIn => _token != null;

  /// Recupera la sesión guardada en este navegador; `null` si no hay ninguna o
  /// ya no vale.
  Future<Session?> restore() async {
    final String? token;
    try {
      final saved = await SharedPreferences.getInstance();
      token = saved.getString(_tokenKey);
      if (token == null) return null;
      baseUrl = saved.getString(_serverKey) ?? baseUrl;
    } catch (_) {
      // Sin almacenamiento (una ventana privada, por ejemplo) no hay nada que recuperar.
      return null;
    }
    _token = token;
    try {
      final user = await _send('GET', '/api/me');
      return Session.of(user, fallback: const Session('Tu cuenta', isAdmin: false));
    } on ApiException {
      // Caducada ya se borró al recibir el 401; si solo falló la conexión, se
      // queda guardada para la próxima vez.
      _token = null;
      return null;
    }
  }

  /// Guarda la sesión abierta en el navegador, o la borra si ya no hay.
  Future<void> _remember() async {
    try {
      final saved = await SharedPreferences.getInstance();
      final token = _token;
      if (token == null) {
        await saved.remove(_tokenKey);
        await saved.remove(_serverKey);
      } else {
        await saved.setString(_tokenKey, token);
        await saved.setString(_serverKey, baseUrl);
      }
    } catch (_) {
      // Sin almacenamiento la sesión dura lo que dure la página abierta.
    }
  }

  /// Entra con una cuenta. Todos usan el mismo acceso: es el servidor quien
  /// dice si la cuenta es de administrador o de pasajero.
  Future<Session> signIn({String? server, required String email, required String password}) {
    return _open(server, '/api/auth/login', {'email': email.trim(), 'password': password}, email);
  }

  /// El desafío de un solo uso con el que se firma un acceso con Google.
  Future<String> googleChallenge() async {
    final data = await _send('POST', '/api/auth/google/challenge');
    final nonce = data['nonce'] as String?;
    if (nonce == null) throw ApiException('El acceso con Google no está disponible ahora.');
    return nonce;
  }

  /// Entra con la credencial que dio Google. Si el correo ya tenía cuenta con
  /// contraseña, el servidor pide esa contraseña ([existingPassword]) para
  /// unirlas.
  Future<Session> signInWithGoogle({required String idToken, required String nonce, String? existingPassword}) {
    return _open(
      null,
      '/api/auth/google',
      {
        'idToken': idToken,
        'nonce': nonce,
        if (existingPassword != null && existingPassword.isNotEmpty) 'existingPassword': existingPassword,
      },
      'Tu cuenta',
    );
  }

  /// Pide una cuenta de pasajero. Por aquí nunca sale una cuenta de
  /// administrador: esas las da el administrador principal.
  ///
  /// Devuelve `null` si el servidor mandó un código al correo: la cuenta se
  /// crea al confirmarlo con [confirmRegistration]. Si no pide confirmar,
  /// devuelve la sesión ya abierta.
  Future<Session?> register({
    String? server,
    required String displayName,
    required String email,
    required String password,
  }) async {
    if (server != null) baseUrl = normalizeServerUrl(server);
    _token = null;
    final opened = await _send(
      'POST',
      '/api/auth/register',
      body: {'displayName': displayName.trim(), 'email': email.trim(), 'password': password},
    );
    if (opened['verificationRequired'] == true) return null;
    return _adopt(opened, email);
  }

  /// El segundo paso del registro: con el código que llegó al correo, la
  /// cuenta se crea y se entra con ella.
  Future<Session> confirmRegistration({required String email, required String code}) {
    return _open(null, '/api/auth/register/verify', {'email': email.trim(), 'code': code.trim()}, email);
  }

  Future<Session> _open(String? server, String path, Map<String, dynamic> body, String email) async {
    if (server != null) baseUrl = normalizeServerUrl(server);
    _token = null;
    return _adopt(await _send('POST', path, body: body), email);
  }

  /// Se queda con la sesión que abrió el servidor y mira de qué tipo es la cuenta.
  Future<Session> _adopt(Map<String, dynamic> opened, String email) async {
    try {
      _token = opened['token'] as String?;
      if (_token == null) throw ApiException('El servidor no devolvió una sesión. Revisa su dirección.');
      final user = opened['user'];
      final passenger = Session.of(
        user is Map ? {...user, 'role': 'client'} : const {},
        fallback: Session(email.trim(), isAdmin: false, email: email.contains('@') ? email.trim() : ''),
      );
      Session session;
      try {
        final admin = await _send('GET', '/api/admin/me');
        session = Session.of(
          admin,
          fallback: Session(passenger.name, isAdmin: true, email: passenger.email, avatarUrl: passenger.avatarUrl),
        );
      } on ApiException catch (error) {
        // Que no sea administrador no es un fallo: es un pasajero.
        if (!error.forbidden) rethrow;
        session = passenger;
      }
      await _remember();
      return session;
    } catch (_) {
      _token = null;
      rethrow;
    }
  }

  void signOut() {
    _token = null;
    _remember();
  }

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

  /// Cambia el estado de una sugerencia y, si se quiere, la contesta.
  Future<void> reviewFeedback(String id, SuggestionStatus status, String? reply) => _send(
        'PUT',
        '/api/admin/feedback/${Uri.encodeComponent(id)}',
        body: {'status': status.code, 'reply': reply},
      );

  /// Las alertas vigentes, también las de cuentas bloqueadas.
  Future<List<ModerationAlert>> moderationAlerts() async {
    final data = await _send('GET', '/api/admin/alerts');
    return [
      for (final alert in data['alerts'] as List? ?? const []) ModerationAlert.fromJson(alert as Map<String, dynamic>),
    ];
  }

  Future<void> deleteAlert(String id) => _send('DELETE', '/api/admin/alerts/${Uri.encodeComponent(id)}');

  /// Bloquea o desbloquea una cuenta: bloqueada no publica y lo suyo deja de verse.
  Future<void> blockUser(String id, {required bool blocked}) =>
      _send('POST', '/api/admin/users/${Uri.encodeComponent(id)}/block', body: {'blocked': blocked});

  Future<Stats> stats() async => Stats.fromJson(await _send('GET', '/api/admin/stats'));

  /// Los administradores, con el principal primero. Solo los ve el principal.
  Future<List<Admin>> admins() async {
    final data = await _send('GET', '/api/admin/admins');
    return [for (final user in data['admins'] as List? ?? const []) Admin.fromJson(user as Map<String, dynamic>)];
  }

  /// Da acceso al panel a una cuenta que ya existe.
  Future<Admin> addAdmin(String email) async =>
      Admin.fromJson(await _send('POST', '/api/admin/admins', body: {'email': email.trim()}));

  /// Quita el acceso: la cuenta sigue ahí, como la de cualquier pasajero.
  Future<void> removeAdmin(String id) => _send('DELETE', '/api/admin/admins/${Uri.encodeComponent(id)}');

  /// Sube una foto y devuelve su dirección pública.
  Future<String> uploadImage(Uint8List bytes, String contentType) async {
    final data = await _send('POST', '/api/admin/images', bytes: bytes, contentType: contentType);
    final url = data['url'] as String?;
    if (url == null) throw ApiException('El servidor no devolvió la dirección de la foto.');
    return url;
  }

  /// Cambia el nombre de la cuenta con la que se entró. Devuelve la sesión al día.
  Future<Session> rename(Session session, String name) async =>
      Session.of(await _send('POST', '/api/me', body: {'displayName': name.trim()}), fallback: session);

  /// Sube una foto de perfil, que el servidor revisa, y la pone en la cuenta.
  Future<Session> changeAvatar(Session session, Uint8List bytes, String contentType) async {
    final uploaded = await _send('POST', '/api/images?purpose=avatar', bytes: bytes, contentType: contentType);
    final url = uploaded['url'] as String?;
    if (url == null) throw ApiException('El servidor no devolvió la dirección de la foto.');
    return Session.of(await _send('POST', '/api/me/avatar', body: {'avatarUrl': url}), fallback: session);
  }

  Future<Session> removeAvatar(Session session) async =>
      Session.of(await _send('POST', '/api/me/avatar', body: {'avatarUrl': null}), fallback: session);

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
      if (expired) {
        _token = null;
        _remember();
      }
      throw ApiException(
        data['message'] as String? ?? 'No se pudo completar la operación (${response.statusCode}).',
        unauthorized: expired,
        forbidden: response.statusCode == 403,
      );
    }
    return data;
  }
}
