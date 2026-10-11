import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api.dart';
import '../favorites.dart';
import '../locate.dart';
import '../nearby.dart';
import '../theme.dart';
import '../widgets/map_kit.dart';
import '../widgets/page_frame.dart';

/// La portada, como en la app: el mapa de la ciudad con todas las líneas y, al
/// lado, el panel con las líneas, el asistente y las alertas.
///
/// Mirar el mapa es libre; abrir el recorrido de una línea o preguntarle al
/// asistente pide sesión.
class HomePage extends StatefulWidget {
  const HomePage({
    super.key,
    required this.api,
    required this.routes,
    required this.loading,
    required this.loadError,
    required this.onRetry,
    required this.session,
    required this.selectedId,
    required this.onSelect,
    required this.onSignIn,
    required this.onError,
  });

  final ApiClient api;
  final List<BusRoute> routes;

  /// Las líneas todavía se están pidiendo.
  final bool loading;

  /// Por qué no se pudieron cargar las líneas; `null` si todo fue bien.
  final String? loadError;
  final VoidCallback onRetry;

  /// `null` sin sesión.
  final Session? session;

  /// La línea abierta; `null` si se ven todas.
  final String? selectedId;

  /// Abre una línea (o vuelve a todas con `null`). Sin sesión, quien recibe el
  /// aviso manda a iniciar sesión.
  final void Function(String? routeId) onSelect;
  final VoidCallback onSignIn;
  final void Function(Object error) onError;

  @override
  State<HomePage> createState() => _HomePageState();
}

enum _Tab {
  lines('Líneas', Icons.alt_route),
  assistant('Asistente', Icons.auto_awesome_outlined),
  alerts('Alertas', Icons.warning_amber_rounded);

  const _Tab(this.label, this.icon);

  final String label;
  final IconData icon;
}

/// Un mensaje de la conversación con el asistente.
class _Message {
  const _Message(this.text, {this.mine = false, this.routeId});

  final String text;
  final bool mine;

  /// La línea que recomienda, para abrirla con un toque.
  final String? routeId;
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  static const _trujillo = LatLng(-8.112, -79.0288);

  // Más lejos que el mínimo ya no se distingue la ciudad.
  static const _minZoom = 10.0;
  static const _maxZoom = 19.0;

  /// Hasta dónde se anda, más o menos, para coger un micro.
  static const _nearMeters = 300.0;

  /// Un color por línea, para distinguirlas en el mapa.
  static const _lineColors = [
    Color(0xFFE0A800),
    Color(0xFF1F7A8C),
    Color(0xFFC8553D),
    Color(0xFF5B5F97),
    Color(0xFF2E7D4F),
    Color(0xFFB5459B),
    Color(0xFF3F6FD1),
    Color(0xFF8A5A2B),
  ];

  final _map = MapController();
  final _search = TextEditingController();
  final _question = TextEditingController();
  final _messages = <_Message>[];

  bool _mapReady = false;
  late final MapGlider _glider;
  MapBase _base = MapBase.streets;

  /// Las líneas que hay bajo el puntero: el mapa lo apunta aquí.
  final LayerHitNotifier<BusRoute> _hit = ValueNotifier(null);

  /// La línea sobre la que está el ratón y en qué punto del mapa. Van aparte
  /// del resto de la pantalla para que pasar el ratón no la repinte entera.
  final _hoverRoute = ValueNotifier<BusRoute?>(null);
  final _hoverAt = ValueNotifier<Offset?>(null);

  /// Dónde está quien mira el mapa, si lo ha preguntado.
  Fix? _me;
  bool _locating = false;

  /// El sitio que alguien marcó tocando el mapa, para ver su calle.
  LatLng? _pin;
  FavoritePlaces _favorites = FavoritePlaces();
  _Tab _tab = _Tab.lines;
  List<CityAlert> _alerts = [];
  bool _asking = false;

  @override
  void initState() {
    super.initState();
    _glider = MapGlider(_map, this);
    _loadAlerts();
    FavoritePlaces.load().then((favorites) {
      if (mounted) setState(() => _favorites = favorites);
    });
  }

  @override
  void didUpdateWidget(HomePage old) {
    super.didUpdateWidget(old);
    if (old.selectedId != widget.selectedId || old.routes.length != widget.routes.length) {
      if (widget.selectedId != null) _tab = _Tab.lines;
      WidgetsBinding.instance.addPostFrameCallback((_) => _frame());
    }
    // Al salir de la cuenta, la conversación se va con ella.
    if (old.session != null && widget.session == null) _messages.clear();
  }

  @override
  void dispose() {
    _search.dispose();
    _question.dispose();
    _glider.dispose();
    _hit.dispose();
    _hoverRoute.dispose();
    _hoverAt.dispose();
    _map.dispose();
    super.dispose();
  }

  Future<void> _loadAlerts() async {
    try {
      final alerts = await widget.api.alerts();
      if (mounted) setState(() => _alerts = alerts);
    } catch (_) {
      // Las alertas son un extra: sin ellas el mapa sigue sirviendo.
    }
  }

  BusRoute? get _selected {
    final id = widget.selectedId;
    if (id == null) return null;
    for (final route in widget.routes) {
      if (route.id == id) return route;
    }
    return null;
  }

  Color _colorOf(BusRoute route) => _lineColors[widget.routes.indexOf(route) % _lineColors.length];

  /// Las líneas ya convertidas: con el catálogo de una ciudad son miles de
  /// puntos, y rehacerlas en cada repintado frena el mapa.
  final _linePoints = Expando<List<LatLng>>();

  List<LatLng> _lineOf(BusRoute route) => _linePoints[route] ??= () {
        // Sin trazado dibujado, la línea une los puntos de referencia.
        final points = route.path.length >= 2 ? route.path : route.stops;
        return [for (final point in points) LatLng(point.latitude, point.longitude)];
      }();

  /// El color de una línea que no es la abierta: apagado, pero opaco. Una línea
  /// translúcida obliga al mapa a pintarla en una capa aparte, y con más de cien
  /// el navegador se queda parado.
  static Color _dimmed(Color color) => Color.lerp(color, const Color(0xFFE9E6DF), 0.72)!;

  /// El encuadre de la línea abierta o, si no hay, de todas; `null` sin líneas.
  CameraFit? _fit() {
    final selected = _selected;
    final points = [
      for (final route in selected == null ? widget.routes : [selected]) ..._lineOf(route),
    ];
    if (points.length < 2) return null;
    return CameraFit.bounds(bounds: LatLngBounds.fromPoints(points), padding: const EdgeInsets.all(48));
  }

  /// Encuadra la línea abierta o, si no hay, todas.
  void _frame() {
    if (!mounted || !_mapReady) return;
    final fit = _fit();
    if (fit != null) _glider.fit(fit);
  }

  void _zoom(double change) {
    if (!_mapReady) return;
    final camera = _map.camera;
    _glider.to(camera.center, (camera.zoom + change).clamp(_minZoom, _maxZoom));
  }

  /// Lleva el mapa a donde está quien lo mira, si deja saberlo.
  Future<void> _locate() async {
    if (_locating || !_mapReady) return;
    setState(() => _locating = true);
    final fix = await currentLocation();
    if (!mounted) return;
    setState(() {
      _locating = false;
      _me = fix ?? _me;
    });
    if (fix == null) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(
          content: Text('No se pudo saber dónde estás. Revisa que el navegador tenga permiso de ubicación.'),
        ));
      return;
    }
    final zoom = _map.camera.zoom;
    _glider.to(LatLng(fix.latitude, fix.longitude), zoom < 16 ? 16 : zoom);
  }

  /// Apunta qué línea queda bajo el ratón, para resaltarla y decir cuál es.
  void _onLineHover() {
    final hit = _hit.value;
    // La primera es la que está pintada encima.
    _hoverRoute.value = hit?.hitValues.first;
    _hoverAt.value = hit?.point;
  }

  void _onLineExit() {
    _hoverRoute.value = null;
    _hoverAt.value = null;
  }

  LatLng? _nearPin;
  int _nearCount = -1;
  List<BusRoute> _nearRoutes = const [];

  /// Las líneas que pasan cerca de [pin], la más cercana primero.
  List<BusRoute> _near(LatLng pin) {
    if (!identical(pin, _nearPin) || _nearCount != widget.routes.length) {
      final found = [
        for (final route in widget.routes) (route, metersToLine(pin, _lineOf(route))),
      ].where((entry) => entry.$2 <= _nearMeters).toList()
        ..sort((a, b) => a.$2.compareTo(b.$2));
      _nearPin = pin;
      _nearCount = widget.routes.length;
      _nearRoutes = [for (final entry in found) entry.$1];
    }
    return _nearRoutes;
  }

  /// Las líneas que pasan cerca del sitio marcado, para abrirlas desde ahí.
  Widget _nearbyCard(LatLng pin) {
    final near = _near(pin);
    return Material(
      color: Palette.surface,
      elevation: 3,
      borderRadius: BorderRadius.circular(14),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              switch (near.length) {
                0 => 'Ninguna línea pasa a menos de ${_nearMeters.round()} m de aquí.',
                1 => 'Una línea pasa cerca de aquí',
                final count => '$count líneas pasan cerca de aquí',
              },
              style: Theme.of(context).textTheme.bodySmall,
            ),
            if (near.isNotEmpty)
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.only(top: 4),
                child: Row(
                  children: [
                    for (final route in near)
                      Padding(
                        padding: const EdgeInsets.only(right: 6),
                        child: ActionChip(
                          avatar: CircleAvatar(backgroundColor: _colorOf(route)),
                          label: Text(route.letter.isEmpty ? route.name : '${route.letter} · ${route.name}'),
                          backgroundColor: Palette.surface,
                          onPressed: () => widget.onSelect(route.id),
                        ),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// Abre la vista de calle de Google Maps en el sitio marcado o, sin marca, en
  /// el centro del mapa. Es un enlace: no pide clave ni cuenta de Google.
  Future<void> _openStreetView() async {
    if (!_mapReady) return;
    final point = _pin ?? _map.camera.center;
    final link = Uri.https('www.google.com', '/maps/@', {
      'api': '1',
      'map_action': 'pano',
      'viewpoint': '${point.latitude},${point.longitude}',
    });
    await launchUrl(link, mode: LaunchMode.externalApplication);
  }

  /// Lleva el mapa a un favorito y lo deja marcado.
  void _showFavorite(FavoritePlace favorite) {
    final point = LatLng(favorite.latitude, favorite.longitude);
    setState(() => _pin = point);
    if (_mapReady) _glider.to(point, 16);
  }

  /// Guarda el sitio marcado como favorito, preguntando antes qué lugar es.
  Future<void> _saveFavorite() async {
    final pin = _pin;
    if (pin == null) return;
    final picked = await showDialog<(FavoriteKind, String)>(
      context: context,
      builder: (_) => _FavoriteDialog(used: {for (final favorite in _favorites.items) favorite.kind}),
    );
    if (picked == null || !mounted) return;
    setState(() => _favorites.add(FavoritePlace(picked.$1, picked.$2, pin.latitude, pin.longitude)));
  }

  /// Los favoritos sobre el mapa y, con un sitio marcado, el botón de guardarlo.
  Widget _favoriteBar() {
    final pin = _pin;
    final favorites = _favorites.items;
    final pinIsFavorite = pin != null &&
        favorites.any((favorite) => favorite.latitude == pin.latitude && favorite.longitude == pin.longitude);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.all(10),
      child: Row(
        children: [
          for (final favorite in favorites)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: InputChip(
                avatar: Icon(favorite.kind.icon, size: 16, color: Palette.accentText),
                label: Text(favorite.label),
                backgroundColor: Palette.surface,
                elevation: 2,
                onPressed: () => _showFavorite(favorite),
                onDeleted: () => setState(() => _favorites.remove(favorite)),
                deleteButtonTooltipMessage: 'Quitar de favoritos',
              ),
            ),
          if (pin != null && !pinIsFavorite)
            ActionChip(
              avatar: const Icon(Icons.star_border, size: 16, color: Palette.accentText),
              label: Text(_favorites.isFull ? 'Máximo ${FavoritePlaces.max} favoritos: quita uno' : 'Guardar aquí'),
              backgroundColor: Palette.surface,
              elevation: 2,
              onPressed: _favorites.isFull ? null : _saveFavorite,
            )
          else if (favorites.isEmpty)
            const Chip(
              avatar: Icon(Icons.star_border, size: 16, color: Palette.accentText),
              label: Text('Toca el mapa para guardar tu casa, tu trabajo…'),
              backgroundColor: Palette.surface,
              elevation: 2,
            ),
        ],
      ),
    );
  }

  List<BusRoute> get _visible {
    final query = _search.text.trim().toLowerCase();
    return widget.routes.where((route) {
      final haystack = [
        route.name,
        route.vehicleName,
        route.origin,
        route.destination,
        route.letter,
        for (final stop in route.stops) stop.name,
      ].join(' ').toLowerCase();
      return haystack.contains(query);
    }).toList();
  }

  Future<void> _ask([String? preset]) async {
    final question = (preset ?? _question.text).trim();
    if (question.isEmpty || _asking) return;
    setState(() {
      _messages.add(_Message(question, mine: true));
      _question.clear();
      _asking = true;
    });
    try {
      final answer = await widget.api.ask(question);
      if (!mounted) return;
      setState(() => _messages.add(_Message(answer.text, routeId: answer.routeId)));
    } catch (error) {
      if (!mounted) return;
      final message = error is ApiException ? error.message : 'El asistente no respondió. Inténtalo de nuevo.';
      setState(() => _messages.add(_Message(message)));
      if (error is ApiException && error.unauthorized) widget.onError(error);
    } finally {
      if (mounted) setState(() => _asking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 860;
        // En el móvil se acerca con dos dedos, y los botones taparían el mapa.
        final map = _buildMap(zoomButtons: wide);
        final panel = Material(color: Palette.surface, child: _buildPanel(context));
        if (wide) {
          return Row(
            children: [
              Expanded(child: map),
              const VerticalDivider(width: 1),
              SizedBox(width: 400, child: panel),
            ],
          );
        }
        return Column(
          children: [
            SizedBox(height: constraints.maxHeight * 0.42, child: map),
            const Divider(),
            Expanded(child: panel),
          ],
        );
      },
    );
  }

  Widget _buildMap({required bool zoomButtons}) {
    final selected = _selected;
    final satellite = _base == MapBase.satellite;
    return FlutterMap(
      mapController: _map,
      options: MapOptions(
        initialCenter: _trujillo,
        initialZoom: 12.5,
        // El mapa nace ya encuadrado: moverlo nada más crearlo dejaba el
        // fondo de calles sin cargar hasta que alguien lo tocaba.
        initialCameraFit: _fit(),
        minZoom: _minZoom,
        maxZoom: _maxZoom,
        onMapReady: () => _mapReady = true,
        onTap: (_, point) => setState(() => _pin = point),
        onPositionChanged: (_, hasGesture) {
          if (hasGesture) _glider.stop();
        },
      ),
      children: [
        _base.layer,
        // Sobre una línea el ratón cambia a la mano y sale su nombre.
        MouseRegion(
          hitTestBehavior: HitTestBehavior.deferToChild,
          cursor: SystemMouseCursors.click,
          onHover: (_) => _onLineHover(),
          onExit: (_) => _onLineExit(),
          child: PolylineLayer<BusRoute>(
            hitNotifier: _hit,
            polylines: [
              // La abierta se pinta la última, encima de las demás.
              for (final route in [...widget.routes.where((route) => route != selected), ?selected])
                if (_lineOf(route).length >= 2)
                  Polyline(
                    points: _lineOf(route),
                    strokeWidth: route == selected ? 6 : 4,
                    color: selected == null || route == selected ? _colorOf(route) : _dimmed(_colorOf(route)),
                    borderStrokeWidth: route == selected ? 1.5 : 0,
                    borderColor: Palette.ink,
                    hitValue: route,
                  ),
            ],
          ),
        ),
        // La línea bajo el ratón, repintada encima y más gruesa. No atiende al
        // puntero: si lo hiciera, taparía a las de debajo y parpadearía.
        IgnorePointer(
          child: ValueListenableBuilder(
            valueListenable: _hoverRoute,
            builder: (context, hovered, _) => PolylineLayer(
              polylines: [
                if (hovered != null && hovered != selected)
                  Polyline(
                    points: _lineOf(hovered),
                    strokeWidth: 6,
                    color: _colorOf(hovered),
                    borderStrokeWidth: 1.5,
                    borderColor: Palette.ink,
                  ),
              ],
            ),
          ),
        ),
        // El margen de error de la ubicación. Con uno muy grande (un ordenador
        // sin GPS) el círculo taparía media ciudad.
        if (_me case final me? when me.accuracy < 1500)
          IgnorePointer(
            child: CircleLayer(
              circles: [
                CircleMarker(
                  point: LatLng(me.latitude, me.longitude),
                  radius: me.accuracy,
                  useRadiusInMeter: true,
                  color: mapBlue.withValues(alpha: 0.12),
                  borderColor: mapBlue.withValues(alpha: 0.45),
                  borderStrokeWidth: 1,
                ),
              ],
            ),
          ),
        MarkerLayer(
          markers: [
            for (final alert in _alerts)
              Marker(
                point: LatLng(alert.latitude, alert.longitude),
                width: 30,
                height: 30,
                child: Tooltip(
                  message: [alert.label, ?alert.placeName].join(' · '),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Palette.warningSoft,
                      shape: BoxShape.circle,
                      border: Border.all(color: Palette.warning, width: 1.5),
                    ),
                    child: const Icon(Icons.warning_amber_rounded, size: 17, color: Palette.warning),
                  ),
                ),
              ),
            if (selected != null)
              for (var i = 0; i < selected.stops.length; i++)
                Marker(
                  point: LatLng(selected.stops[i].latitude, selected.stops[i].longitude),
                  width: 20,
                  height: 20,
                  child: Tooltip(
                    message: selected.stops[i].name,
                    child: Container(
                      decoration: BoxDecoration(
                        color: i == 0 || i == selected.stops.length - 1 ? Palette.ink : Colors.white,
                        shape: BoxShape.circle,
                        border: Border.all(color: Palette.ink, width: 2.5),
                      ),
                    ),
                  ),
                )
            else
              // Sin línea abierta, cada una lleva su letra donde empieza.
              for (final route in widget.routes)
                if (route.stops.isNotEmpty && route.letter.isNotEmpty)
                  Marker(
                    point: LatLng(route.stops.first.latitude, route.stops.first.longitude),
                    width: 28,
                    height: 28,
                    child: GestureDetector(
                      onTap: () => widget.onSelect(route.id),
                      child: _Letter(route.letter, color: _colorOf(route), round: true),
                    ),
                  ),
            for (final favorite in _favorites.items)
              Marker(
                point: LatLng(favorite.latitude, favorite.longitude),
                width: 30,
                height: 30,
                child: GestureDetector(
                  onTap: () => _showFavorite(favorite),
                  child: Tooltip(
                    message: favorite.label,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Palette.accent,
                        shape: BoxShape.circle,
                        border: Border.all(color: Palette.ink, width: 1.5),
                      ),
                      child: Icon(favorite.kind.icon, size: 16, color: Palette.ink),
                    ),
                  ),
                ),
              ),
            if (_me case final me?)
              Marker(
                point: LatLng(me.latitude, me.longitude),
                width: 20,
                height: 20,
                child: const Tooltip(message: 'Estás aquí', child: MyLocationDot()),
              ),
            if (_pin case final pin?)
              Marker(
                point: pin,
                width: 36,
                height: 36,
                // La punta del alfiler, no su centro, señala el sitio.
                alignment: Alignment.topCenter,
                child: GestureDetector(
                  onTap: () => setState(() => _pin = null),
                  child: const Tooltip(
                    message: 'Quitar la marca',
                    child: Icon(Icons.location_on, size: 36, color: Palette.danger),
                  ),
                ),
              ),
          ],
        ),
        Align(alignment: Alignment.topLeft, child: _favoriteBar()),
        Align(
          alignment: Alignment.bottomLeft,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(10, 10, 10, 30),
            child: _StreetViewButton(onTap: _openStreetView),
          ),
        ),
        if (_pin case final pin?)
          Align(
            alignment: Alignment.bottomCenter,
            child: Padding(
              // Entre el botón de la calle y los del mapa.
              padding: const EdgeInsets.fromLTRB(84, 10, 62, 34),
              child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 460), child: _nearbyCard(pin)),
            ),
          ),
        Align(
          alignment: Alignment.bottomRight,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(10, 10, 10, 34),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                MapButton(
                  icon: Icons.layers_outlined,
                  tooltip: satellite ? 'Ver el mapa de calles' : 'Ver la foto de satélite',
                  active: satellite,
                  onTap: () => setState(() => _base = satellite ? MapBase.streets : MapBase.satellite),
                ),
                const SizedBox(height: 8),
                MapButton(
                  icon: Icons.zoom_out_map,
                  tooltip: selected == null ? 'Ver todas las líneas' : 'Ver la línea entera',
                  onTap: _frame,
                ),
                if (locationAvailable) ...[
                  const SizedBox(height: 8),
                  MapButton(
                    icon: _me == null ? Icons.location_searching : Icons.my_location,
                    tooltip: 'Mi ubicación',
                    active: _me != null,
                    busy: _locating,
                    onTap: _locate,
                  ),
                ],
                if (zoomButtons) ...[
                  const SizedBox(height: 8),
                  MapZoomButtons(onZoom: _zoom),
                ],
              ],
            ),
          ),
        ),
        Scalebar(
          alignment: Alignment.bottomRight,
          padding: const EdgeInsets.fromLTRB(10, 10, 14, 8),
          length: ScalebarLength.s,
          strokeWidth: 1.5,
          lineColor: satellite ? Colors.white : Palette.ink,
          textStyle: TextStyle(fontSize: 11, color: satellite ? Colors.white : Palette.ink),
        ),
        Align(alignment: Alignment.bottomLeft, child: MapCredit(_base.credit)),
        // El nombre de la línea bajo el ratón, a su lado.
        IgnorePointer(
          child: ValueListenableBuilder(
            valueListenable: _hoverAt,
            builder: (context, at, _) {
              final route = _hoverRoute.value;
              if (at == null || route == null) return const SizedBox.shrink();
              return Align(
                alignment: Alignment.topLeft,
                child: Padding(
                  padding: EdgeInsets.only(left: at.dx + 14, top: at.dy + 14),
                  child: Material(
                    color: Palette.ink,
                    borderRadius: BorderRadius.circular(8),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: Text(
                        route.letter.isEmpty ? route.name : '${route.letter} · ${route.name}',
                        style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildPanel(BuildContext context) {
    final selected = _selected;
    if (selected != null) return _detail(context, selected);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Las tres pestañas van en una sola pieza: la elegida es la blanca.
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Container(
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(color: Palette.chip, borderRadius: BorderRadius.circular(12)),
            child: Row(
              children: [
                for (final tab in _Tab.values) ...[
                  Expanded(
                    child: _TabButton(tab: tab, active: tab == _tab, onTap: () => setState(() => _tab = tab)),
                  ),
                  if (tab != _Tab.values.last) const SizedBox(width: 4),
                ],
              ],
            ),
          ),
        ),
        Expanded(
          child: switch (_tab) {
            _Tab.lines => _lines(context),
            _Tab.assistant => _assistant(context),
            _Tab.alerts => _alertList(context),
          },
        ),
      ],
    );
  }

  Widget _lines(BuildContext context) {
    final text = Theme.of(context).textTheme;
    if (widget.loading) return const Center(child: CircularProgressIndicator());
    final loadError = widget.loadError;
    if (loadError != null) {
      return _Empty(
        icon: Icons.cloud_off_outlined,
        message: loadError,
        action: OutlinedButton(onPressed: widget.onRetry, child: const Text('Reintentar')),
      );
    }
    final visible = _visible;
    final name = widget.session?.name.trim().split(' ').first;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name == null || name.isEmpty ? '¿Qué micro tomas hoy?' : 'Hola, $name. ¿Qué micro tomas hoy?',
                  style: text.titleLarge?.copyWith(fontSize: 22, letterSpacing: 0)),
              const SizedBox(height: 12),
              TextField(
                controller: _search,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  hintText: 'Línea, zona o punto de referencia',
                  prefixIcon: const Icon(Icons.search, size: 20, color: Palette.muted),
                  fillColor: Palette.surface,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Palette.fieldLine),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(color: Palette.fieldLine),
                  ),
                ),
              ),
              // Sin cuenta se ve la lista, pero el recorrido pide entrar.
              if (widget.session == null) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
                  decoration: BoxDecoration(color: Palette.accentWash, borderRadius: BorderRadius.circular(12)),
                  child: Row(
                    children: [
                      const Expanded(
                        child: Text('Entra para ver el recorrido completo de cada línea.',
                            style: TextStyle(fontSize: 14, height: 1.4, color: Palette.body)),
                      ),
                      const SizedBox(width: 12),
                      FilledButton(
                        onPressed: widget.onSignIn,
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                          minimumSize: Size.zero,
                          textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                        ),
                        child: const Text('Entrar'),
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 18),
              const Row(
                children: [
                  Expanded(
                    child: Text('Línea y sentido',
                        overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 13, color: Palette.faint)),
                  ),
                  SizedBox(width: 8),
                  Text('Pasaje · tiempo', style: TextStyle(fontSize: 13, color: Palette.faint)),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: visible.isEmpty
              ? _Empty(
                  icon: Icons.search_off,
                  message: widget.routes.isEmpty
                      ? 'Todavía no hay líneas publicadas. Vuelve pronto.'
                      : 'Ninguna línea coincide con lo que buscas.',
                )
              : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
                  itemCount: visible.length,
                  itemBuilder: (context, index) {
                    final route = visible[index];
                    return InkWell(
                      onTap: () => widget.onSelect(route.id),
                      borderRadius: BorderRadius.circular(12),
                      hoverColor: Palette.accentWash,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
                        child: Row(
                          children: [
                            _Letter(route.letter.isEmpty ? '·' : route.letter, color: _colorOf(route)),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(route.name,
                                      style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 15),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis),
                                  const SizedBox(height: 2),
                                  RouteEnds(route.origin, route.destination,
                                      style: const TextStyle(fontSize: 13, color: Palette.muted)),
                                ],
                              ),
                            ),
                            const SizedBox(width: 10),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Text('S/ ${route.fare.toStringAsFixed(2)}',
                                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                                if (route.durationMinutes > 0)
                                  Text('${route.durationMinutes} min',
                                      style: const TextStyle(fontSize: 13, color: Palette.muted)),
                              ],
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _detail(BuildContext context, BusRoute route) {
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: () => widget.onSelect(null),
            icon: const Icon(Icons.arrow_back, size: 16),
            label: const Text('Todas las líneas'),
            style: TextButton.styleFrom(padding: EdgeInsets.zero),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            if (route.letter.isNotEmpty) ...[_Letter(route.letter, color: _colorOf(route)), const SizedBox(width: 12)],
            Expanded(
              child: Text(route.name,
                  style: text.titleLarge?.copyWith(fontSize: 24), maxLines: 2, overflow: TextOverflow.ellipsis),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text('${route.origin} – ${route.destination}', style: text.bodyMedium?.copyWith(color: Palette.muted)),
        const SizedBox(height: 18),
        Row(
          children: [
            Expanded(child: _Fact(label: 'Pasaje', value: 'S/ ${route.fare.toStringAsFixed(2)}')),
            const SizedBox(width: 8),
            Expanded(
              child: _Fact(label: 'Demora', value: route.durationMinutes > 0 ? '${route.durationMinutes} min' : '—'),
            ),
            const SizedBox(width: 8),
            Expanded(child: _Fact(label: 'Puntos de referencia', value: '${route.stops.length}')),
          ],
        ),
        if (route.schedule.isNotEmpty) ...[
          const SizedBox(height: 14),
          Row(
            children: [
              const Icon(Icons.schedule, size: 15, color: Palette.muted),
              const SizedBox(width: 6),
              Expanded(child: Text('Horario: ${route.schedule}', style: const TextStyle(fontSize: 13))),
            ],
          ),
        ],
        if (route.vehicleName.isNotEmpty) ...[
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.directions_bus_outlined, size: 15, color: Palette.muted),
              const SizedBox(width: 6),
              Expanded(child: Text(route.vehicleName, style: const TextStyle(fontSize: 13))),
            ],
          ),
        ],
        if (route.description.isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(route.description, style: text.bodyMedium),
        ],
        const SizedBox(height: 24),
        Text('PARADEROS', style: text.labelSmall?.copyWith(color: Palette.accentText)),
        const SizedBox(height: 4),
        Text(
          route.bidirectional ? 'El micro hace este recorrido de ida y de vuelta.' : 'El micro va solo en este sentido.',
          style: text.bodySmall,
        ),
        const SizedBox(height: 10),
        for (var i = 0; i < route.stops.length; i++)
          // Tocar un paradero lleva el mapa hasta él.
          InkWell(
            borderRadius: BorderRadius.circular(8),
            onTap: () {
              if (_mapReady) _glider.to(LatLng(route.stops[i].latitude, route.stops[i].longitude), 17);
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 7),
              child: Row(
                children: [
                  Container(
                    width: 10,
                    height: 10,
                    decoration: BoxDecoration(
                      color: i == 0 || i == route.stops.length - 1 ? Palette.ink : Colors.white,
                      shape: BoxShape.circle,
                      border: Border.all(color: Palette.ink, width: 2),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(child: Text(route.stops[i].name, style: const TextStyle(fontSize: 14))),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _assistant(BuildContext context) {
    if (widget.session == null) {
      return _Empty(
        icon: Icons.auto_awesome_outlined,
        message: 'Pregúntale al asistente qué micro te lleva a donde vas. Para usarlo, entra con tu cuenta.',
        action: FilledButton(onPressed: widget.onSignIn, child: const Text('Entrar para preguntar')),
      );
    }
    const ideas = ['¿Qué micro va a Huanchaco?', '¿Cuál es el pasaje más barato?', '¿Qué línea pasa por el centro?'];
    return Column(
      children: [
        Expanded(
          child: _messages.isEmpty
              ? ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Text('Pregunta como le preguntarías a alguien en el punto de referencia.',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Palette.muted)),
                    const SizedBox(height: 14),
                    for (final idea in ideas)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: OutlinedButton(
                          onPressed: () => _ask(idea),
                          style: OutlinedButton.styleFrom(alignment: Alignment.centerLeft),
                          child: Text(idea),
                        ),
                      ),
                  ],
                )
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    for (final message in _messages) _bubble(message),
                    if (_asking)
                      const Padding(
                        padding: EdgeInsets.all(8),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
                        ),
                      ),
                  ],
                ),
        ),
        const Divider(),
        Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _question,
                  enabled: !_asking,
                  maxLength: 500,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => _ask(),
                  decoration: const InputDecoration(hintText: 'Escribe tu pregunta', counterText: ''),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Preguntar',
                onPressed: _asking ? null : _ask,
                icon: const Icon(Icons.arrow_upward, size: 18),
                style: IconButton.styleFrom(backgroundColor: Palette.accent, foregroundColor: Palette.ink),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _bubble(_Message message) {
    BusRoute? route;
    for (final candidate in widget.routes) {
      if (candidate.id == message.routeId) route = candidate;
    }
    return Align(
      alignment: message.mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 300),
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: message.mine ? Palette.accentSoft : Palette.paper,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Palette.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message.text, style: const TextStyle(fontSize: 14, height: 1.45)),
            if (route != null) ...[
              const SizedBox(height: 8),
              TextButton.icon(
                onPressed: () => widget.onSelect(route!.id),
                icon: const Icon(Icons.alt_route, size: 16),
                label: Text('Ver ${route.name} en el mapa'),
                style: TextButton.styleFrom(padding: EdgeInsets.zero),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _alertList(BuildContext context) {
    final text = Theme.of(context).textTheme;
    if (_alerts.isEmpty) {
      return const _Empty(
        icon: Icons.check_circle_outline,
        message: 'No hay alertas ahora mismo. Los avisos de tráfico, obras o accidentes se envían desde la app.',
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: _alerts.length,
      separatorBuilder: (_, _) => const Divider(indent: 16, endIndent: 16),
      itemBuilder: (context, index) {
        final alert = _alerts[index];
        return InkWell(
          onTap: () {
            if (_mapReady) _glider.to(LatLng(alert.latitude, alert.longitude), 16);
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.warning_amber_rounded, size: 20, color: Palette.warning),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(alert.label, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                      if ((alert.placeName ?? '').isNotEmpty) Text(alert.placeName!, style: text.bodySmall),
                      if ((alert.comment ?? '').isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(alert.comment!, style: const TextStyle(fontSize: 13)),
                        ),
                      if (alert.confirmations > 0)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text('${alert.confirmations} dicen que sigue ahí', style: text.bodySmall),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _TabButton extends StatelessWidget {
  const _TabButton({required this.tab, required this.active, required this.onTap});

  final _Tab tab;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: active ? Palette.surface : Colors.transparent,
      borderRadius: BorderRadius.circular(9),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
          child: Text(tab.label,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 14,
                fontWeight: active ? FontWeight.w500 : FontWeight.w400,
                color: active ? Palette.ink : Palette.muted,
              )),
        ),
      ),
    );
  }
}

/// El cuadradito del mapa que lleva a la vista de calle, como el de Google Maps.
class _StreetViewButton extends StatelessWidget {
  const _StreetViewButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: 'Ver la calle en Google Street View',
      child: Material(
        color: Palette.surface,
        elevation: 3,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: const SizedBox(
            width: 64,
            height: 64,
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.streetview, size: 28, color: Palette.accentText),
                SizedBox(height: 2),
                Text('Ver calle', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: Palette.ink)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Pregunta qué lugar es el que se va a guardar y, si se quiere, su nombre.
class _FavoriteDialog extends StatefulWidget {
  const _FavoriteDialog({required this.used});

  /// Los tipos que ya tienen favorito: no se ofrecen otra vez, salvo "otro".
  final Set<FavoriteKind> used;

  @override
  State<_FavoriteDialog> createState() => _FavoriteDialogState();
}

class _FavoriteDialogState extends State<_FavoriteDialog> {
  final _name = TextEditingController();
  FavoriteKind? _kind;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final kind = _kind;
    return AlertDialog(
      title: const Text('¿Qué lugar es?'),
      content: SizedBox(
        width: 340,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final option in FavoriteKind.values)
                  if (option == FavoriteKind.other || !widget.used.contains(option))
                    ChoiceChip(
                      avatar: Icon(option.icon, size: 16, color: Palette.accentText),
                      label: Text(option.label),
                      showCheckmark: false,
                      selected: option == kind,
                      selectedColor: Palette.accentSoft,
                      onSelected: (_) => setState(() => _kind = option),
                    ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              controller: _name,
              maxLength: 24,
              decoration: const InputDecoration(labelText: 'Nombre (opcional)', counterText: ''),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
        FilledButton(
          onPressed: kind == null ? null : () => Navigator.pop(context, (kind, _name.text.trim())),
          child: const Text('Guardar'),
        ),
      ],
    );
  }
}

/// La letra con la que se conoce a la línea en la calle, en su color.
class _Letter extends StatelessWidget {
  const _Letter(this.letter, {required this.color, this.round = false});

  final String letter;
  final Color color;
  final bool round;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(minWidth: round ? 28 : 38),
      height: round ? 28 : 38,
      padding: const EdgeInsets.symmetric(horizontal: 6),
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(round ? 14 : 10),
        border: round ? Border.all(color: Colors.white, width: 2) : null,
      ),
      child: Text(letter,
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: round ? 12 : 14)),
    );
  }
}

/// Un dato corto de la línea, con su nombre encima.
class _Fact extends StatelessWidget {
  const _Fact({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Palette.paper,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(fontSize: 11)),
          const SizedBox(height: 2),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15), maxLines: 1),
        ],
      ),
    );
  }
}

/// Lo que se ve cuando una pestaña no tiene nada que enseñar.
class _Empty extends StatelessWidget {
  const _Empty({required this.icon, required this.message, this.action});

  final IconData icon;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 30, color: Palette.muted),
            const SizedBox(height: 12),
            Text(message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Palette.muted)),
            if (action != null) ...[const SizedBox(height: 16), action!],
          ],
        ),
      ),
    );
  }
}
