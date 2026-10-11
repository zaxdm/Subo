import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../api.dart';
import '../theme.dart';

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

class _HomePageState extends State<HomePage> {
  static const _trujillo = LatLng(-8.112, -79.0288);

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
  _Tab _tab = _Tab.lines;
  List<CityAlert> _alerts = [];
  bool _asking = false;

  @override
  void initState() {
    super.initState();
    _loadAlerts();
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
    if (fit != null) _map.fitCamera(fit);
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
        final map = _buildMap();
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

  Widget _buildMap() {
    final selected = _selected;
    return FlutterMap(
      mapController: _map,
      options: MapOptions(
        initialCenter: _trujillo,
        initialZoom: 12.5,
        // El mapa nace ya encuadrado: moverlo nada más crearlo dejaba el
        // fondo de calles sin cargar hasta que alguien lo tocaba.
        initialCameraFit: _fit(),
        // Más lejos que esto ya no se distingue la ciudad.
        minZoom: 10,
        maxZoom: 18,
        onMapReady: () => _mapReady = true,
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'pe.subo.web',
        ),
        PolylineLayer(
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
                ),
          ],
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
          ],
        ),
        const Align(
          alignment: Alignment.bottomLeft,
          child: Padding(
            padding: EdgeInsets.all(6),
            child: Text('© OpenStreetMap', style: TextStyle(fontSize: 10, color: Palette.ink)),
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
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
          child: Row(
            children: [
              for (final tab in _Tab.values) ...[
                Expanded(
                  child: _TabButton(tab: tab, active: tab == _tab, onTap: () => setState(() => _tab = tab)),
                ),
                if (tab != _Tab.values.last) const SizedBox(width: 6),
              ],
            ],
          ),
        ),
        const Divider(),
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
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name == null || name.isEmpty ? '¿Qué micro tomas hoy?' : 'Hola, $name. ¿Qué micro tomas hoy?',
                  style: text.titleMedium),
              const SizedBox(height: 12),
              TextField(
                controller: _search,
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(
                  hintText: 'Busca una línea, zona o punto de referencia',
                  prefixIcon: Icon(Icons.search, size: 20),
                ),
              ),
              if (widget.session == null) ...[
                const SizedBox(height: 10),
                Row(
                  children: [
                    const Icon(Icons.lock_outline, size: 14, color: Palette.muted),
                    const SizedBox(width: 6),
                    Expanded(child: Text('Inicia sesión para ver el recorrido de una línea.', style: text.bodySmall)),
                  ],
                ),
              ],
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
              : ListView.separated(
                  padding: const EdgeInsets.only(bottom: 16),
                  itemCount: visible.length,
                  separatorBuilder: (_, _) => const Divider(indent: 16, endIndent: 16),
                  itemBuilder: (context, index) {
                    final route = visible[index];
                    return InkWell(
                      onTap: () => widget.onSelect(route.id),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        child: Row(
                          children: [
                            _Letter(route.letter.isEmpty ? '·' : route.letter, color: _colorOf(route)),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(route.name,
                                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis),
                                  const SizedBox(height: 2),
                                  Text('${route.origin} – ${route.destination}',
                                      style: text.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                                ],
                              ),
                            ),
                            const SizedBox(width: 10),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Text('S/ ${route.fare.toStringAsFixed(2)}',
                                    style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                                if (route.durationMinutes > 0)
                                  Text('${route.durationMinutes} min', style: text.bodySmall),
                              ],
                            ),
                            const SizedBox(width: 6),
                            Icon(widget.session == null ? Icons.lock_outline : Icons.chevron_right,
                                size: 18, color: Palette.muted),
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
          Padding(
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
            if (_mapReady) _map.move(LatLng(alert.latitude, alert.longitude), 16);
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
      color: active ? Palette.accentSoft : Colors.transparent,
      borderRadius: BorderRadius.circular(10),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(tab.icon, size: 16, color: active ? Palette.accentText : Palette.muted),
              const SizedBox(width: 6),
              Flexible(
                child: Text(tab.label,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: active ? Palette.accentText : Palette.muted,
                    )),
              ),
            ],
          ),
        ),
      ),
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
      constraints: BoxConstraints(minWidth: round ? 28 : 34),
      height: round ? 28 : 34,
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
