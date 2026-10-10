import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/page_frame.dart';

/// Cómo se está usando Subo: cuánta gente hay, qué se reporta y cuándo, y qué
/// rutas se miran más.
class StatsPage extends StatefulWidget {
  const StatsPage({super.key, required this.api, required this.onError});

  final ApiClient api;
  final void Function(Object error) onError;

  @override
  State<StatsPage> createState() => _StatsPageState();
}

class _StatsPageState extends State<StatsPage> {
  /// `null` mientras llega la primera respuesta, o si falló.
  Stats? _stats;
  bool _loading = false;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final stats = await widget.api.stats();
      if (mounted) {
        setState(() {
          _stats = stats;
          _failed = false;
        });
      }
    } catch (error) {
      if (mounted) setState(() => _failed = _stats == null);
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  static const _typeLabels = {
    'TRAFFIC': 'Tráfico',
    'ACCIDENT': 'Accidente',
    'OVERCHARGE': 'Cobro de más',
    'ROADWORK': 'Obras',
    'CROWDED': 'Micro lleno',
    'DANGER': 'Zona peligrosa',
    'OTHER': 'Otro',
  };

  @override
  Widget build(BuildContext context) {
    final stats = _stats;
    return PageFrame(
      label: 'RESUMEN',
      title: 'Estadísticas',
      subtitle: 'Cómo se está usando la app.',
      loading: _loading,
      onRefresh: _load,
      ready: stats != null || _failed,
      emptyMessage: stats == null && _failed ? 'No se pudieron cargar las estadísticas.' : null,
      slivers: (padding) {
        final data = stats!;
        final byType = data.alertsThisWeek.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
        return [
          SliverPadding(
            padding: padding,
            sliver: SliverToBoxAdapter(
              child: Wrap(
                spacing: 16,
                runSpacing: 16,
                children: [
                  _Metric('Cuentas', data.users, note: '+${data.newUsersThisWeek} esta semana'),
                  _Metric('Alertas vigentes', data.activeAlerts),
                  _Metric('Calificaciones', data.ratings),
                  _Metric('Rutas por aprobar', data.pendingRoutes),
                  _Metric('Sugerencias nuevas', data.newFeedback),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: padding.copyWith(top: 24),
            sliver: SliverToBoxAdapter(
              child: _Section(
                title: 'Rutas más consultadas',
                note: 'Veces que se abrió su ficha en los últimos 30 días.',
                empty: data.topRoutes.isEmpty ? 'Todavía nadie ha abierto la ficha de una ruta.' : null,
                children: [
                  for (final route in data.topRoutes)
                    _Bar(
                      label: '${route.letter} · ${route.name}',
                      value: route.views,
                      max: data.topRoutes.first.views,
                    ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: padding.copyWith(top: 16),
            sliver: SliverToBoxAdapter(
              child: _Section(
                title: 'Alertas de la semana, por tipo',
                empty: byType.isEmpty ? 'No se publicó ninguna alerta esta semana.' : null,
                children: [
                  for (final entry in byType)
                    _Bar(label: _typeLabels[entry.key] ?? entry.key, value: entry.value, max: byType.first.value),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: padding.copyWith(top: 16, bottom: 40),
            sliver: SliverToBoxAdapter(
              child: _Section(
                title: 'A qué hora se reporta',
                note: 'Alertas de los últimos 30 días, por hora del día.',
                empty: data.alertsByHour.every((count) => count == 0) ? 'Aún no hay alertas que contar.' : null,
                children: [_Hours(data.alertsByHour)],
              ),
            ),
          ),
        ];
      },
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric(this.label, this.value, {this.note});

  final String label;
  final int value;
  final String? note;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return SizedBox(
      width: 190,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: text.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 8),
              Text('$value', style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w700)),
              // El hueco se guarda aunque no haya nota, para que las tarjetas midan lo mismo.
              Text(note ?? '', style: text.bodySmall),
            ],
          ),
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children, this.note, this.empty});

  final String title;
  final String? note;

  /// Qué decir si no hay datos; `null` si los hay.
  final String? empty;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: text.titleMedium),
            if (note != null) Text(note!, style: text.bodySmall),
            const SizedBox(height: 14),
            if (empty != null) Text(empty!, style: text.bodyMedium?.copyWith(color: Palette.muted)) else ...children,
          ],
        ),
      ),
    );
  }
}

/// Una barra horizontal con su etiqueta y su número.
class _Bar extends StatelessWidget {
  const _Bar({required this.label, required this.value, required this.max});

  final String label;
  final int value;
  final int max;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        children: [
          SizedBox(width: 160, child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis)),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: max > 0 ? value / max : 0,
                minHeight: 12,
                backgroundColor: Palette.paper,
                color: Palette.accent,
              ),
            ),
          ),
          SizedBox(
            width: 48,
            child: Text('$value', textAlign: TextAlign.end, style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}

/// Las 24 horas del día como columnas: cuanto más alta, más alertas a esa hora.
class _Hours extends StatelessWidget {
  const _Hours(this.counts);

  final List<int> counts;

  @override
  Widget build(BuildContext context) {
    final max = counts.fold(0, (a, b) => a > b ? a : b);
    return SizedBox(
      height: 130,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var hour = 0; hour < counts.length; hour++)
            Expanded(
              child: Tooltip(
                message: '$hour:00 · ${counts[hour]} alertas',
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Container(
                      height: max > 0 ? 100 * counts[hour] / max : 0,
                      margin: const EdgeInsets.symmetric(horizontal: 2),
                      decoration: BoxDecoration(color: Palette.accent, borderRadius: BorderRadius.circular(3)),
                    ),
                    const SizedBox(height: 6),
                    // Una de cada tres, para que quepan.
                    Text(hour % 3 == 0 ? '$hour' : '', style: const TextStyle(fontSize: 10, color: Palette.muted)),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
