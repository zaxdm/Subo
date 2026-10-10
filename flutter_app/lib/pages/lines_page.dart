import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/route_photo.dart';

/// Lo que ve un pasajero: las líneas publicadas, con un buscador, y el
/// recorrido de la que elija.
class LinesPage extends StatefulWidget {
  const LinesPage({super.key, required this.routes, required this.onRefresh, this.greeting});

  final List<BusRoute> routes;
  final Future<void> Function() onRefresh;

  /// Nombre de quien entró; `null` si mira sin cuenta.
  final String? greeting;

  @override
  State<LinesPage> createState() => _LinesPageState();
}

class _LinesPageState extends State<LinesPage> {
  final _search = TextEditingController();
  bool _refreshing = false;

  /// La línea cuyo recorrido se está viendo; `null` en la lista.
  BusRoute? _open;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
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

  Future<void> _refresh() async {
    setState(() => _refreshing = true);
    await widget.onRefresh();
    if (mounted) setState(() => _refreshing = false);
  }

  @override
  Widget build(BuildContext context) {
    final open = _open;
    if (open != null) return _LineDetail(route: open, onBack: () => setState(() => _open = null));

    final text = Theme.of(context).textTheme;
    final visible = _visible;
    final name = widget.greeting?.trim().split(' ').first;

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 720;
        final padding = EdgeInsets.symmetric(horizontal: narrow ? 20 : 0);
        return CustomScrollView(
          slivers: [
            SliverPadding(
              padding: padding.copyWith(top: 32, bottom: 22),
              sliver: SliverToBoxAdapter(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(name == null || name.isEmpty ? 'LÍNEAS DE TRUJILLO' : 'HOLA, ${name.toUpperCase()}',
                        style: text.labelSmall?.copyWith(color: Palette.accentText)),
                    const SizedBox(height: 8),
                    Text('¿Qué micro tomas hoy?', style: narrow ? text.headlineMedium : text.headlineLarge),
                    const SizedBox(height: 6),
                    Text('Busca por línea, por zona o por paradero y mira su recorrido.',
                        style: text.bodyMedium?.copyWith(color: Palette.muted)),
                    const SizedBox(height: 22),
                    Row(
                      children: [
                        Expanded(
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 520),
                            child: TextField(
                              controller: _search,
                              onChanged: (_) => setState(() {}),
                              decoration: const InputDecoration(
                                hintText: 'Huanchaco, Av. América, California…',
                                prefixIcon: Icon(Icons.search, size: 20),
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        IconButton.outlined(
                          tooltip: 'Actualizar las líneas',
                          onPressed: _refreshing ? null : _refresh,
                          icon: _refreshing
                              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                              : const Icon(Icons.refresh, size: 20),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            if (visible.isEmpty)
              SliverFillRemaining(
                hasScrollBody: false,
                child: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(
                      widget.routes.isEmpty
                          ? 'Todavía no hay líneas publicadas. Vuelve pronto.'
                          : 'Ninguna línea coincide con lo que buscas.',
                      textAlign: TextAlign.center,
                      style: text.bodyMedium?.copyWith(color: Palette.muted),
                    ),
                  ),
                ),
              )
            else
              SliverPadding(
                padding: padding.copyWith(bottom: 40),
                sliver: SliverGrid.builder(
                  gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 340,
                    mainAxisExtent: 318,
                    crossAxisSpacing: 22,
                    mainAxisSpacing: 22,
                  ),
                  itemCount: visible.length,
                  itemBuilder: (context, index) =>
                      _LineCard(route: visible[index], onOpen: () => setState(() => _open = visible[index])),
                ),
              ),
          ],
        );
      },
    );
  }
}

/// Pasaje, duración y horario de una línea, en una fila de datos cortos.
class _Facts extends StatelessWidget {
  const _Facts({required this.route});

  final BusRoute route;

  @override
  Widget build(BuildContext context) {
    Widget fact(IconData icon, String label) => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 14, color: Palette.muted),
            const SizedBox(width: 5),
            Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
          ],
        );
    return Wrap(
      spacing: 14,
      runSpacing: 6,
      children: [
        fact(Icons.payments_outlined, 'S/ ${route.fare.toStringAsFixed(2)}'),
        if (route.durationMinutes > 0) fact(Icons.schedule, '${route.durationMinutes} min'),
        if (route.schedule.isNotEmpty) fact(Icons.wb_sunny_outlined, route.schedule),
      ],
    );
  }
}

class _LineCard extends StatelessWidget {
  const _LineCard({required this.route, required this.onOpen});

  final BusRoute route;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onOpen,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(height: 140, child: RoutePhoto(url: route.safeImageUrl, letter: route.letter)),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (route.letter.isNotEmpty) ...[_Letter(route.letter), const SizedBox(width: 10)],
                        Expanded(
                          child: Text(route.name,
                              style: text.titleMedium?.copyWith(fontSize: 18),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text('${route.origin} → ${route.destination}',
                        style: text.bodySmall?.copyWith(fontSize: 13), maxLines: 1, overflow: TextOverflow.ellipsis),
                    const Spacer(),
                    _Facts(route: route),
                    const SizedBox(height: 12),
                    const Row(
                      children: [
                        Text('Ver recorrido',
                            style: TextStyle(color: Palette.accentText, fontWeight: FontWeight.w700, fontSize: 13)),
                        SizedBox(width: 4),
                        Icon(Icons.arrow_forward, size: 15, color: Palette.accentText),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// La letra con la que se conoce a la línea en la calle.
class _Letter extends StatelessWidget {
  const _Letter(this.letter);

  final String letter;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 28),
      height: 28,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      alignment: Alignment.center,
      decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(8)),
      child: Text(letter, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
    );
  }
}

/// El recorrido de una línea: el mapa con su trazado y la lista de paraderos.
class _LineDetail extends StatelessWidget {
  const _LineDetail({required this.route, required this.onBack});

  final BusRoute route;
  final VoidCallback onBack;

  static const _trujillo = LatLng(-8.112, -79.0288);

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final stops = [for (final stop in route.stops) LatLng(stop.latitude, stop.longitude)];
    // Sin trazado dibujado, la línea une los paraderos.
    final line = route.path.length >= 2 ? [for (final point in route.path) LatLng(point.latitude, point.longitude)] : stops;
    final all = [...line, ...stops];

    final map = ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: FlutterMap(
        options: MapOptions(
          initialCenter: _trujillo,
          initialZoom: 13,
          initialCameraFit: all.length >= 2
              ? CameraFit.bounds(bounds: LatLngBounds.fromPoints(all), padding: const EdgeInsets.all(40))
              : null,
        ),
        children: [
          TileLayer(
            urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            userAgentPackageName: 'pe.subo.web',
          ),
          if (line.length >= 2)
            PolylineLayer(
              polylines: [
                Polyline(points: line, strokeWidth: 5, color: Palette.accentDark, borderStrokeWidth: 1.5, borderColor: Palette.ink),
              ],
            ),
          MarkerLayer(
            markers: [
              for (var i = 0; i < stops.length; i++)
                Marker(
                  point: stops[i],
                  width: 22,
                  height: 22,
                  child: Tooltip(
                    message: route.stops[i].name,
                    child: Container(
                      decoration: BoxDecoration(
                        color: i == 0 || i == stops.length - 1 ? Palette.ink : Colors.white,
                        shape: BoxShape.circle,
                        border: Border.all(color: Palette.ink, width: 2.5),
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const Align(
            alignment: Alignment.bottomRight,
            child: Padding(
              padding: EdgeInsets.all(6),
              child: Text('© OpenStreetMap', style: TextStyle(fontSize: 10, color: Palette.ink)),
            ),
          ),
        ],
      ),
    );

    final info = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Facts(route: route),
        if (route.vehicleName.isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(route.vehicleName, style: text.bodySmall?.copyWith(fontSize: 13)),
        ],
        if (route.description.isNotEmpty) ...[
          const SizedBox(height: 10),
          Text(route.description, style: text.bodyMedium),
        ],
        const SizedBox(height: 22),
        Text('PARADEROS', style: text.labelSmall?.copyWith(color: Palette.accentText)),
        const SizedBox(height: 4),
        Text(
          route.bidirectional ? 'El micro hace este recorrido de ida y de vuelta.' : 'El micro va solo en este sentido.',
          style: text.bodySmall,
        ),
        const SizedBox(height: 12),
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

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 860;
        final heading = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextButton.icon(
              onPressed: onBack,
              icon: const Icon(Icons.arrow_back, size: 16),
              label: const Text('Todas las líneas'),
              style: TextButton.styleFrom(padding: EdgeInsets.zero),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (route.letter.isNotEmpty) ...[_Letter(route.letter), const SizedBox(width: 12)],
                Expanded(
                  child: Text(route.name,
                      style: narrow ? text.headlineMedium : text.headlineLarge,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text('${route.origin} → ${route.destination}', style: text.bodyMedium?.copyWith(color: Palette.muted)),
          ],
        );
        if (narrow) {
          return ListView(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 40),
            children: [heading, const SizedBox(height: 18), SizedBox(height: 320, child: map), const SizedBox(height: 22), info],
          );
        }
        return Padding(
          padding: const EdgeInsets.only(top: 24, bottom: 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              heading,
              const SizedBox(height: 22),
              Expanded(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(flex: 7, child: map),
                    const SizedBox(width: 32),
                    Expanded(flex: 4, child: SingleChildScrollView(child: info)),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
