import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/route_photo.dart';

/// El catálogo: cuántas rutas hay, un buscador con filtro por estado y una
/// tarjeta por ruta.
class CatalogPage extends StatefulWidget {
  const CatalogPage({
    super.key,
    required this.routes,
    required this.onRefresh,
    required this.onNew,
    required this.onEdit,
  });

  final List<BusRoute> routes;
  final Future<void> Function() onRefresh;
  final VoidCallback onNew;
  final void Function(BusRoute route) onEdit;

  @override
  State<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends State<CatalogPage> {
  final _search = TextEditingController();

  /// `null`: todos los estados.
  RouteStatus? _status;
  bool _refreshing = false;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  List<BusRoute> get _visible {
    final query = _search.text.trim().toLowerCase();
    return widget.routes.where((route) {
      if (_status != null && route.status != _status) return false;
      final haystack =
          [route.name, route.vehicleName, route.origin, route.destination, route.letter].join(' ').toLowerCase();
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
    final text = Theme.of(context).textTheme;
    final routes = widget.routes;
    final visible = _visible;

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 720;
        final padding = EdgeInsets.symmetric(horizontal: narrow ? 20 : 0);

        final actions = Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            OutlinedButton.icon(
              onPressed: _refreshing ? null : _refresh,
              icon: _refreshing
                  ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.refresh, size: 18),
              label: const Text('Actualizar'),
            ),
            FilledButton.icon(
              onPressed: widget.onNew,
              icon: const Icon(Icons.add, size: 18),
              label: const Text('Nueva ruta'),
            ),
          ],
        );
        final heading = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('CATÁLOGO', style: text.labelSmall?.copyWith(color: Palette.accentText)),
            const SizedBox(height: 8),
            Text('Rutas y micros', style: narrow ? text.headlineMedium : text.headlineLarge),
            const SizedBox(height: 6),
            Text('Lo que publiques aquí es lo que ven los pasajeros en la app.',
                style: text.bodyMedium?.copyWith(color: Palette.muted)),
          ],
        );

        return CustomScrollView(
          slivers: [
            SliverPadding(
              padding: padding.copyWith(top: 32, bottom: 20),
              sliver: SliverToBoxAdapter(
                child: narrow
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [heading, const SizedBox(height: 16), actions],
                      )
                    : Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [Expanded(child: heading), actions],
                      ),
              ),
            ),
            SliverPadding(
              padding: padding,
              sliver: SliverToBoxAdapter(
                child: Row(
                  children: [
                    Expanded(child: _Metric(label: 'Rutas en total', value: routes.length)),
                    SizedBox(width: narrow ? 8 : 18),
                    Expanded(
                      child: _Metric(
                        label: 'Publicadas',
                        value: routes.where((route) => route.status == RouteStatus.approved).length,
                      ),
                    ),
                    SizedBox(width: narrow ? 8 : 18),
                    Expanded(
                      child: _Metric(
                        label: 'Con foto',
                        value: routes.where((route) => route.safeImageUrl != null).length,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: padding.copyWith(top: 28, bottom: 22),
              sliver: SliverToBoxAdapter(
                child: Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    SizedBox(
                      width: narrow ? double.infinity : 420,
                      child: TextField(
                        controller: _search,
                        onChanged: (_) => setState(() {}),
                        decoration: const InputDecoration(
                          hintText: 'Buscar por nombre, micro, origen o destino',
                          prefixIcon: Icon(Icons.search, size: 20),
                        ),
                      ),
                    ),
                    SizedBox(
                      width: 190,
                      child: DropdownButtonFormField<RouteStatus?>(
                        initialValue: _status,
                        isExpanded: true,
                        decoration: const InputDecoration(),
                        items: [
                          const DropdownMenuItem(value: null, child: Text('Todos los estados')),
                          for (final status in RouteStatus.values)
                            DropdownMenuItem(value: status, child: Text(status.label)),
                        ],
                        onChanged: (value) => setState(() => _status = value),
                      ),
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
                      routes.isEmpty
                          ? 'Todavía no hay rutas. Crea la primera con «Nueva ruta».'
                          : 'Ninguna ruta coincide con la búsqueda.',
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
                    mainAxisExtent: 372,
                    crossAxisSpacing: 22,
                    mainAxisSpacing: 22,
                  ),
                  itemCount: visible.length,
                  itemBuilder: (context, index) =>
                      _RouteCard(route: visible[index], onEdit: () => widget.onEdit(visible[index])),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final int value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
            const SizedBox(height: 8),
            Text('$value', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }
}

class _RouteCard extends StatelessWidget {
  const _RouteCard({required this.route, required this.onEdit});

  final BusRoute route;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(height: 150, child: RoutePhoto(url: route.safeImageUrl, letter: route.letter)),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  StatusBadge(status: route.status),
                  const SizedBox(height: 10),
                  Text(route.name, style: text.titleMedium?.copyWith(fontSize: 18), maxLines: 1, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 2),
                  Text('${route.origin} → ${route.destination}',
                      style: text.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
                  const Spacer(),
                  const Divider(),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Text('S/ ${route.fare.toStringAsFixed(2)}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
                      const SizedBox(width: 14),
                      Text('${route.durationMinutes} min', style: const TextStyle(fontSize: 12)),
                      const SizedBox(width: 14),
                      Flexible(
                        child: Text('${route.stops.length} paraderos',
                            style: const TextStyle(fontSize: 12), overflow: TextOverflow.ellipsis),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: onEdit,
                      icon: const Icon(Icons.edit_outlined, size: 16),
                      label: const Text('Editar ruta'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Pastilla con el estado de una ruta. El color tiene significado propio:
/// verde publicada, ámbar pendiente, rojo oculta.
class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.status});

  final RouteStatus status;

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = switch (status) {
      RouteStatus.approved => (Palette.successSoft, Palette.success),
      RouteStatus.pending => (Palette.warningSoft, Palette.warning),
      RouteStatus.rejected => (const Color(0xFFF2E5E2), const Color(0xFF945244)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(30)),
      child: Text(status.label, style: TextStyle(color: foreground, fontSize: 10, fontWeight: FontWeight.w700)),
    );
  }
}
