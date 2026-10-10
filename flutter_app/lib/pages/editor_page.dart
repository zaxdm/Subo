import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/route_photo.dart';

/// Qué añade un clic en el mapa.
enum _MapMode {
  stops('Añadir puntos de referencia'),
  path('Dibujar recorrido');

  const _MapMode(this.label);
  final String label;
}

/// Crea o edita una ruta: los datos del micro, su foto, los puntos de referencia en orden
/// y, si hace falta, el recorrido exacto dibujado sobre el mapa.
class EditorPage extends StatefulWidget {
  const EditorPage({
    super.key,
    required this.api,
    required this.route,
    required this.onSaved,
    required this.onDeleted,
    required this.onCancel,
    required this.onError,
  });

  final ApiClient api;

  /// Copia de trabajo: se puede modificar sin tocar el catálogo.
  final BusRoute route;
  final void Function(BusRoute saved) onSaved;
  final void Function(String id) onDeleted;
  final VoidCallback onCancel;
  final void Function(Object error) onError;

  @override
  State<EditorPage> createState() => _EditorPageState();
}

class _EditorPageState extends State<EditorPage> {
  static const _maxStops = 300;
  static const _maxPathPoints = 20000;
  static const _maxImageBytes = 5 * 1024 * 1024;
  static const _trujillo = LatLng(-8.112, -79.0288);

  final _form = GlobalKey<FormState>();
  final _mapKey = GlobalKey();
  final _map = MapController();

  late final BusRoute _route = widget.route;
  late final _name = TextEditingController(text: _route.name);
  late final _letter = TextEditingController(text: _route.letter);
  late final _vehicle = TextEditingController(text: _route.vehicleName);
  late final _origin = TextEditingController(text: _route.origin);
  late final _destination = TextEditingController(text: _route.destination);
  late final _fare = TextEditingController(text: _route.fare > 0 ? _route.fare.toStringAsFixed(2) : '');
  late final _fareSchool = TextEditingController(text: _route.fareSchool?.toStringAsFixed(2) ?? '');
  late final _fareUniversity = TextEditingController(text: _route.fareUniversity?.toStringAsFixed(2) ?? '');
  late final _frequency = TextEditingController(text: _route.frequencyMinutes?.toString() ?? '');
  late final _duration =
      TextEditingController(text: _route.durationMinutes > 0 ? '${_route.durationMinutes}' : '');
  late final _schedule = TextEditingController(text: _route.schedule);
  late final _description = TextEditingController(text: _route.description);
  late final _imageUrl = TextEditingController(text: _route.imageUrl ?? '');

  _MapMode _mode = _MapMode.stops;
  bool _busy = false;
  bool _uploading = false;
  bool _dirty = false;

  bool get _isNew => _route.id == null;
  bool get _locked => _busy || _uploading;

  @override
  void dispose() {
    for (final controller in [
      _name, _letter, _vehicle, _origin, _destination, _fare, _duration, _schedule, _description, _imageUrl, //
      _fareSchool, _fareUniversity, _frequency,
    ]) {
      controller.dispose();
    }
    _map.dispose();
    super.dispose();
  }

  void _change(VoidCallback update) => setState(() {
        update();
        _dirty = true;
      });

  // --- Mapa ---

  void _onMapReady() {
    if (_route.stops.length < 2) return;
    _map.fitCamera(
      CameraFit.bounds(
        bounds: LatLngBounds.fromPoints([for (final stop in _route.stops) LatLng(stop.latitude, stop.longitude)]),
        padding: const EdgeInsets.all(40),
        maxZoom: 15,
      ),
    );
  }

  void _onMapTap(LatLng point) {
    if (_locked) return;
    if (_mode == _MapMode.path) {
      if (_route.path.length >= _maxPathPoints) return;
      _change(() => _route.path.add(GeoPoint(point.latitude, point.longitude)));
    } else {
      if (_route.stops.length >= _maxStops) return;
      _change(() => _route.stops
          .add(RouteStop('Punto ${_route.stops.length + 1}', point.latitude, point.longitude)));
    }
  }

  /// Arrastrar un punto de referencia lo mueve: la posición del dedo se traduce a un punto del mapa.
  void _onStopDragged(RouteStop stop, Offset globalPosition) {
    if (_locked) return;
    final box = _mapKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;
    final point = _map.camera.screenOffsetToLatLng(box.globalToLocal(globalPosition));
    _change(() {
      stop.latitude = point.latitude;
      stop.longitude = point.longitude;
    });
  }

  void _undo() {
    final points = _mode == _MapMode.path ? _route.path : _route.stops;
    if (points.isEmpty) return;
    _change(points.removeLast);
  }

  Future<void> _clearPath() async {
    if (_route.path.isEmpty) return;
    final confirmed = await _confirm(
      title: '¿Borrar el trazado dibujado?',
      message: 'Los puntos de referencia se conservan.',
      action: 'Borrar trazado',
    );
    if (confirmed) _change(_route.path.clear);
  }

  void _swapStops(int a, int b) => _change(() {
        final stop = _route.stops[a];
        _route.stops[a] = _route.stops[b];
        _route.stops[b] = stop;
      });

  // --- Foto ---

  Future<void> _pickImage() async {
    const types = XTypeGroup(
      label: 'Imágenes',
      extensions: ['jpg', 'jpeg', 'png', 'webp'],
      mimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
    );
    final file = await openFile(acceptedTypeGroups: const [types]);
    if (file == null) return;

    final contentType = _imageType(file);
    final bytes = await file.readAsBytes();
    if (contentType == null || bytes.length > _maxImageBytes) {
      widget.onError(ApiException('Usa JPG, PNG o WebP de hasta 5 MB.'));
      return;
    }

    setState(() => _uploading = true);
    try {
      final url = await widget.api.uploadImage(bytes, contentType);
      if (!mounted) return;
      _change(() => _imageUrl.text = url);
      _message('Foto subida. Guarda la ruta para publicarla.');
    } catch (error) {
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  String? _imageType(XFile file) {
    const allowed = {'image/jpeg', 'image/png', 'image/webp'};
    if (allowed.contains(file.mimeType)) return file.mimeType;
    final name = file.name.toLowerCase();
    if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg';
    if (name.endsWith('.png')) return 'image/png';
    if (name.endsWith('.webp')) return 'image/webp';
    return null;
  }

  // --- Guardar, borrar, salir ---

  Future<void> _save() async {
    if (_locked) return;
    if (!_form.currentState!.validate()) {
      widget.onError(ApiException('Revisa los campos marcados en rojo.'));
      return;
    }
    if (_route.stops.length < 2) {
      widget.onError(ApiException('Añade al menos dos puntos de referencia en el mapa.'));
      return;
    }
    if (_route.stops.any((stop) => stop.name.trim().isEmpty)) {
      widget.onError(ApiException('Cada punto de referencia necesita un nombre.'));
      return;
    }

    _route
      ..name = _name.text
      ..letter = _letter.text
      ..vehicleName = _vehicle.text
      ..origin = _origin.text
      ..destination = _destination.text
      ..fare = _parseNumber(_fare.text) ?? 0
      // Vacíos se mandan como "no se sabe": la app no los enseña.
      ..fareSchool = _parseNumber(_fareSchool.text)
      ..fareUniversity = _parseNumber(_fareUniversity.text)
      ..frequencyMinutes = int.tryParse(_frequency.text.trim())
      ..durationMinutes = int.tryParse(_duration.text.trim()) ?? 0
      ..schedule = _schedule.text
      ..description = _description.text
      ..imageUrl = _imageUrl.text.trim().isEmpty ? null : _imageUrl.text.trim();

    setState(() => _busy = true);
    try {
      final saved = await widget.api.saveRoute(_route);
      if (mounted) widget.onSaved(saved);
    } catch (error) {
      widget.onError(error);
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delete() async {
    final id = _route.id;
    if (id == null || _locked) return;
    final confirmed = await _confirm(
      title: '¿Eliminar esta ruta del catálogo?',
      message: 'Esta acción no se puede deshacer.',
      action: 'Eliminar ruta',
      destructive: true,
    );
    if (!confirmed) return;

    setState(() => _busy = true);
    try {
      await widget.api.deleteRoute(id);
      if (mounted) widget.onDeleted(id);
    } catch (error) {
      widget.onError(error);
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _back() async {
    if (_locked) return;
    if (_dirty) {
      final leave = await _confirm(
        title: '¿Volver al catálogo?',
        message: 'Los cambios que no guardaste se perderán.',
        action: 'Salir sin guardar',
      );
      if (!leave) return;
    }
    widget.onCancel();
  }

  Future<bool> _confirm({
    required String title,
    required String message,
    required String action,
    bool destructive = false,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(
            style: destructive
                ? FilledButton.styleFrom(backgroundColor: Palette.danger, foregroundColor: Colors.white)
                : null,
            onPressed: () => Navigator.pop(context, true),
            child: Text(action),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  void _message(String text) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text), backgroundColor: Palette.success, showCloseIcon: true));
  }

  static double? _parseNumber(String text) => double.tryParse(text.trim().replaceAll(',', '.'));

  /// Un pasaje reducido puede quedar vacío, pero nunca costar más que el regular.
  String? _reducedFare(String? value) {
    if ((value ?? '').trim().isEmpty) return null;
    final reduced = _parseNumber(value!);
    final regular = _parseNumber(_fare.text) ?? 50;
    return reduced == null || reduced <= 0 || reduced > regular ? 'No mayor que el regular' : null;
  }

  // --- Pantalla ---

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return PopScope(
      canPop: false,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 1000;
          final narrow = constraints.maxWidth < 720;

          final heading = Wrap(
            spacing: 12,
            runSpacing: 12,
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextButton.icon(
                    onPressed: _locked ? null : _back,
                    icon: const Icon(Icons.arrow_back, size: 16),
                    label: const Text('Volver al catálogo'),
                    style: TextButton.styleFrom(padding: EdgeInsets.zero),
                  ),
                  Text(_isNew ? 'Nueva ruta' : 'Editar ruta', style: narrow ? text.headlineMedium : text.headlineLarge),
                ],
              ),
              FilledButton.icon(
                onPressed: _locked ? null : _save,
                icon: _busy
                    ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.check, size: 18),
                label: Text(_busy ? 'Guardando…' : 'Guardar ruta'),
              ),
            ],
          );

          final details = Column(
            children: [
              _detailsPanel(),
              const SizedBox(height: 20),
              _photoPanel(),
              if (!_isNew) ...[
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: _locked ? null : _delete,
                    icon: const Icon(Icons.delete_outline, size: 18),
                    label: const Text('Eliminar ruta'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Palette.danger,
                      side: const BorderSide(color: Color(0xFFDEC2BC)),
                    ),
                  ),
                ),
              ],
            ],
          );
          final map = _mapPanel();

          return Form(
            key: _form,
            onChanged: () {
              if (!_dirty) setState(() => _dirty = true);
            },
            child: SingleChildScrollView(
              padding: EdgeInsets.fromLTRB(narrow ? 20 : 0, 24, narrow ? 20 : 0, 48),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  heading,
                  const SizedBox(height: 22),
                  if (wide)
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(flex: 4, child: details),
                        const SizedBox(width: 24),
                        Expanded(flex: 6, child: map),
                      ],
                    )
                  else ...[
                    details,
                    const SizedBox(height: 20),
                    map,
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _detailsPanel() {
    String? required(String? value) => (value ?? '').trim().isEmpty ? 'Obligatorio' : null;
    return _Panel(
      step: '1',
      title: 'Datos del micro',
      children: [
        _row([
          _field(_name, 'Nombre de la ruta', flex: 3, validator: required, maxLength: 80),
          _field(_letter, 'Letra', validator: required, maxLength: 3),
        ]),
        _field(_vehicle, 'Nombre del micro o empresa', hint: 'Como lo conoce la gente', maxLength: 80),
        _row([
          _field(_origin, 'Origen', validator: required, maxLength: 80),
          _field(_destination, 'Destino', validator: required, maxLength: 80),
        ]),
        _row([
          _field(
            _fare,
            'Pasaje (S/)',
            keyboard: const TextInputType.numberWithOptions(decimal: true),
            formatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
            validator: (value) {
              final fare = _parseNumber(value ?? '');
              return fare == null || fare <= 0 || fare > 50 ? 'Entre 0.10 y 50' : null;
            },
          ),
          _field(
            _duration,
            'Duración (min)',
            keyboard: TextInputType.number,
            formatters: [FilteringTextInputFormatter.digitsOnly],
            validator: (value) => (int.tryParse((value ?? '').trim()) ?? 0) > 0 ? null : 'Mayor que 0',
          ),
        ]),
        _row([
          _field(
            _fareSchool,
            'Pasaje escolar (S/)',
            hint: 'Opcional',
            keyboard: const TextInputType.numberWithOptions(decimal: true),
            formatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
            validator: _reducedFare,
          ),
          _field(
            _fareUniversity,
            'Pasaje universitario (S/)',
            hint: 'Opcional',
            keyboard: const TextInputType.numberWithOptions(decimal: true),
            formatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
            validator: _reducedFare,
          ),
          _field(
            _frequency,
            'Pasa cada (min)',
            hint: 'Opcional',
            keyboard: TextInputType.number,
            formatters: [FilteringTextInputFormatter.digitsOnly],
            validator: (value) {
              final text = (value ?? '').trim();
              if (text.isEmpty) return null;
              final minutes = int.tryParse(text) ?? 0;
              return minutes > 0 && minutes <= 240 ? null : 'Entre 1 y 240';
            },
          ),
        ]),
        _field(_schedule, 'Horario', hint: '05:00 – 22:30', maxLength: 60),
        _field(_description, 'Descripción', hint: 'Por dónde pasa, cómo reconocerlo…', maxLines: 3, maxLength: 400),
        DropdownButtonFormField<RouteStatus>(
          initialValue: _route.status,
          decoration: const InputDecoration(labelText: 'Estado'),
          items: [
            for (final status in RouteStatus.values) DropdownMenuItem(value: status, child: Text(status.label)),
          ],
          onChanged: _locked ? null : (value) => _change(() => _route.status = value ?? _route.status),
        ),
        CheckboxListTile(
          value: _route.bidirectional,
          onChanged: _locked ? null : (value) => _change(() => _route.bidirectional = value ?? true),
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          dense: true,
          title: const Text('Circula en ambos sentidos por el mismo recorrido'),
          subtitle: const Text('Si la vuelta usa otras calles, crea una ruta de regreso aparte.'),
        ),
      ],
    );
  }

  Widget _photoPanel() {
    final url = Uri.tryParse(_imageUrl.text.trim());
    final safeUrl = url != null && url.scheme == 'https' ? _imageUrl.text.trim() : null;
    return _Panel(
      step: '2',
      title: 'Foto del micro',
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: SizedBox(
            height: 170,
            child: RoutePhoto(url: safeUrl, letter: _letter.text, emptyHint: 'Una foto ayuda a reconocer el micro'),
          ),
        ),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            OutlinedButton.icon(
              onPressed: _locked ? null : _pickImage,
              icon: _uploading
                  ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.upload_outlined, size: 18),
              label: Text(_uploading ? 'Subiendo…' : 'Subir foto'),
            ),
            if (_imageUrl.text.isNotEmpty)
              TextButton(
                onPressed: _locked ? null : () => _change(_imageUrl.clear),
                child: const Text('Quitar foto'),
              ),
          ],
        ),
        _field(
          _imageUrl,
          'O pega la dirección de una imagen',
          hint: 'https://…',
          keyboard: TextInputType.url,
          onChanged: (_) => setState(() {}),
          validator: (value) {
            final text = (value ?? '').trim();
            if (text.isEmpty) return null;
            return Uri.tryParse(text)?.scheme == 'https' ? null : 'Tiene que empezar por https://';
          },
        ),
        Text('JPG, PNG o WebP de hasta 5 MB.', style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }

  Widget _mapPanel() {
    final text = Theme.of(context).textTheme;
    final stops = _route.stops;
    // Sin trazado dibujado, se enseña la unión de los puntos de referencia, como la estimará la app.
    final line = _route.path.length > 1 ? _route.path : stops;
    return _Panel(
      step: '3',
      title: 'Puntos de referencia y recorrido',
      children: [
        Text(
          _mode == _MapMode.stops
              ? 'Haz clic en el mapa para añadir puntos de referencia en el orden del recorrido. Arrástralos para corregirlos.'
              : 'Marca las esquinas y calles en el orden de circulación. Sin trazado, la app lo estima entre puntos de referencia.',
          style: text.bodySmall,
        ),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            SegmentedButton<_MapMode>(
              showSelectedIcon: false,
              segments: [for (final mode in _MapMode.values) ButtonSegment(value: mode, label: Text(mode.label))],
              selected: {_mode},
              onSelectionChanged: (selection) => setState(() => _mode = selection.first),
              style: SegmentedButton.styleFrom(
                selectedBackgroundColor: Palette.accentSoft,
                selectedForegroundColor: Palette.ink,
                textStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
              ),
            ),
            OutlinedButton(onPressed: _locked ? null : _undo, child: const Text('Deshacer último punto')),
            if (_route.path.isNotEmpty)
              OutlinedButton(onPressed: _locked ? null : _clearPath, child: const Text('Borrar trazado')),
          ],
        ),
        ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: SizedBox(
            key: _mapKey,
            height: 420,
            child: FlutterMap(
              mapController: _map,
              options: MapOptions(
                initialCenter: stops.isEmpty ? _trujillo : LatLng(stops.first.latitude, stops.first.longitude),
                initialZoom: 13,
                maxZoom: 19,
                onMapReady: _onMapReady,
                onTap: (_, point) => _onMapTap(point),
              ),
              children: [
                TileLayer(
                  urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                  userAgentPackageName: 'pe.rutanorte.admin',
                  maxNativeZoom: 19,
                ),
                if (line.length > 1)
                  PolylineLayer(
                    polylines: [
                      Polyline(
                        points: [for (final point in line) LatLng(point.latitude, point.longitude)],
                        strokeWidth: 5,
                        // Oscura: una línea amarilla se perdería entre las calles del mapa.
                        color: Palette.ink,
                      ),
                    ],
                  ),
                MarkerLayer(
                  markers: [
                    for (final (index, stop) in stops.indexed)
                      Marker(
                        point: LatLng(stop.latitude, stop.longitude),
                        width: 30,
                        height: 30,
                        child: _StopMarker(
                          number: index + 1,
                          name: stop.name,
                          onDrag: (position) => _onStopDragged(stop, position),
                        ),
                      ),
                  ],
                ),
                // La licencia de OpenStreetMap exige la atribución a la vista.
                Align(
                  alignment: Alignment.bottomRight,
                  child: Container(
                    margin: const EdgeInsets.all(6),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(color: const Color(0xD9FFFFFF), borderRadius: BorderRadius.circular(20)),
                    child: const Text('© OpenStreetMap contributors',
                        overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10, color: Palette.ink)),
                  ),
                ),
              ],
            ),
          ),
        ),
        Row(
          children: [
            Expanded(child: Text('Puntos de referencia', style: text.titleMedium)),
            Text('${stops.length} puntos de referencia', style: text.bodySmall),
          ],
        ),
        if (stops.length < 2) Text('Añade al menos dos puntos de referencia en el mapa.', style: text.bodySmall),
        for (final (index, stop) in stops.indexed)
          _StopRow(
            // Por identidad: al reordenar, cada campo sigue a su punto de referencia.
            key: ObjectKey(stop),
            number: index + 1,
            stop: stop,
            enabled: !_locked,
            onRenamed: (name) => _change(() => stop.name = name),
            onUp: index == 0 ? null : () => _swapStops(index, index - 1),
            onDown: index == stops.length - 1 ? null : () => _swapStops(index, index + 1),
            onRemove: () => _change(() => stops.removeAt(index)),
          ),
      ],
    );
  }

  Widget _row(List<Widget> fields) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (index, field) in fields.indexed) ...[
          if (index > 0) const SizedBox(width: 14),
          field is _FlexField ? Expanded(flex: field.flex, child: field) : Expanded(child: field),
        ],
      ],
    );
  }

  Widget _field(
    TextEditingController controller,
    String label, {
    String? hint,
    int flex = 1,
    int maxLines = 1,
    int? maxLength,
    TextInputType? keyboard,
    List<TextInputFormatter>? formatters,
    String? Function(String?)? validator,
    ValueChanged<String>? onChanged,
  }) {
    return _FlexField(
      flex: flex,
      child: TextFormField(
        controller: controller,
        enabled: !_locked,
        maxLines: maxLines,
        maxLength: maxLength,
        keyboardType: keyboard,
        inputFormatters: formatters,
        validator: validator,
        onChanged: onChanged,
        decoration: InputDecoration(labelText: label, hintText: hint, counterText: ''),
      ),
    );
  }
}

/// Un campo que recuerda cuánto ancho quiere dentro de una fila.
class _FlexField extends StatelessWidget {
  const _FlexField({required this.flex, required this.child});

  final int flex;
  final Widget child;

  @override
  Widget build(BuildContext context) => child;
}

/// Sección numerada del editor.
class _Panel extends StatelessWidget {
  const _Panel({required this.step, required this.title, required this.children});

  final String step;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  width: 29,
                  height: 29,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(color: Palette.accentSoft, borderRadius: BorderRadius.circular(8)),
                  child: Text(step,
                      style: const TextStyle(color: Color(0xFF7A5A00), fontWeight: FontWeight.w700, fontSize: 12)),
                ),
                const SizedBox(width: 12),
                Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontSize: 17)),
              ],
            ),
            for (final child in children) ...[const SizedBox(height: 16), child],
          ],
        ),
      ),
    );
  }
}

/// El número de un punto de referencia sobre el mapa; se puede arrastrar.
class _StopMarker extends StatelessWidget {
  const _StopMarker({required this.number, required this.name, required this.onDrag});

  final int number;
  final String name;
  final void Function(Offset globalPosition) onDrag;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: name,
      child: MouseRegion(
        cursor: SystemMouseCursors.grab,
        child: GestureDetector(
          onPanUpdate: (details) => onDrag(details.globalPosition),
          child: Container(
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: Palette.accent,
              shape: BoxShape.circle,
              border: Border.all(color: Palette.ink, width: 2),
              boxShadow: const [BoxShadow(color: Color(0x33000000), blurRadius: 5, offset: Offset(0, 1))],
            ),
            child: Text('$number',
                style: const TextStyle(color: Palette.ink, fontWeight: FontWeight.w700, fontSize: 12)),
          ),
        ),
      ),
    );
  }
}

/// Un punto de referencia en la lista: su número, su nombre editable y cómo moverlo o quitarlo.
class _StopRow extends StatelessWidget {
  const _StopRow({
    super.key,
    required this.number,
    required this.stop,
    required this.enabled,
    required this.onRenamed,
    required this.onUp,
    required this.onDown,
    required this.onRemove,
  });

  final int number;
  final RouteStop stop;
  final bool enabled;
  final ValueChanged<String> onRenamed;
  final VoidCallback? onUp;
  final VoidCallback? onDown;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            CircleAvatar(
              radius: 12,
              backgroundColor: Palette.accent,
              child: Text('$number',
                  style: const TextStyle(color: Palette.ink, fontSize: 11, fontWeight: FontWeight.w700)),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: TextFormField(
                initialValue: stop.name,
                enabled: enabled,
                maxLength: 120,
                onChanged: onRenamed,
                validator: (value) => (value ?? '').trim().isEmpty ? 'Ponle un nombre' : null,
                decoration: InputDecoration(
                  hintText: 'Nombre del punto de referencia $number',
                  counterText: '',
                  contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                ),
                style: const TextStyle(fontSize: 13),
              ),
            ),
            IconButton(
              tooltip: 'Subir punto de referencia',
              onPressed: enabled ? onUp : null,
              icon: const Icon(Icons.arrow_upward, size: 18),
            ),
            IconButton(
              tooltip: 'Bajar punto de referencia',
              onPressed: enabled ? onDown : null,
              icon: const Icon(Icons.arrow_downward, size: 18),
            ),
            IconButton(
              tooltip: 'Quitar punto de referencia',
              onPressed: enabled ? onRemove : null,
              icon: const Icon(Icons.close, size: 18),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 32, top: 2),
          child: Text(
            '${stop.latitude.toStringAsFixed(5)}, ${stop.longitude.toStringAsFixed(5)}',
            style: const TextStyle(fontSize: 10, color: Palette.muted),
          ),
        ),
      ],
    );
  }
}
