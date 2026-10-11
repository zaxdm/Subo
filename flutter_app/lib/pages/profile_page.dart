import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';

/// Mi perfil: la cuenta con la que se entró, sea de pasajero o de
/// administrador. Deja cambiar el nombre y la foto, y cerrar sesión.
class ProfilePage extends StatefulWidget {
  const ProfilePage({
    super.key,
    required this.api,
    required this.session,
    required this.onChanged,
    required this.onSignOut,
    required this.onError,
    this.centered = false,
  });

  final ApiClient api;
  final Session session;

  /// La cuenta cambió: quien enseña la página se queda con la sesión nueva.
  final void Function(Session session) onChanged;
  final VoidCallback onSignOut;
  final void Function(Object error) onError;

  /// En la web de los pasajeros va centrada; en el panel, a la izquierda como
  /// el resto de secciones.
  final bool centered;

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  static const _maxImageBytes = 5 * 1024 * 1024;

  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.session.name);
  bool _busy = false;

  /// Lo último que salió bien, para decirlo junto al botón.
  String? _done;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  /// Hace un cambio en la cuenta y, si sale bien, avisa de la sesión nueva.
  Future<void> _change(Future<Session> Function() change, String done) async {
    setState(() {
      _busy = true;
      _done = null;
    });
    try {
      final session = await change();
      if (!mounted) return;
      widget.onChanged(session);
      setState(() => _done = done);
    } catch (error) {
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _saveName() async {
    if (_busy || !_form.currentState!.validate()) return;
    await _change(() => widget.api.rename(widget.session, _name.text), 'Nombre guardado.');
  }

  Future<void> _pickPhoto() async {
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
    await _change(() => widget.api.changeAvatar(widget.session, bytes, contentType), 'Foto actualizada.');
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

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final session = widget.session;
    final avatar = session.avatarUrl;
    final initial = session.name.trim().isEmpty ? '?' : session.name.trim()[0].toUpperCase();
    final role = session.isSuperAdmin
        ? 'Administrador principal'
        : session.isAdmin
            ? 'Administrador'
            : 'Pasajero';

    final account = Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Row(
          children: [
            CircleAvatar(
              radius: 34,
              backgroundColor: Palette.accentSoft,
              foregroundImage: avatar == null ? null : NetworkImage(avatar),
              child: Text(initial,
                  style: const TextStyle(color: Palette.accentText, fontSize: 26, fontWeight: FontWeight.w700)),
            ),
            const SizedBox(width: 20),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(session.name, style: text.titleMedium, overflow: TextOverflow.ellipsis),
                  if (session.email.isNotEmpty)
                    Text(session.email, style: text.bodySmall?.copyWith(fontSize: 13), overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 8),
                  Text(role, style: text.labelSmall?.copyWith(color: Palette.accentText)),
                ],
              ),
            ),
          ],
        ),
      ),
    );

    final edit = Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Tus datos', style: text.titleMedium),
              const SizedBox(height: 16),
              TextFormField(
                controller: _name,
                enabled: !_busy,
                decoration: const InputDecoration(labelText: 'Nombre'),
                validator: (value) => (value ?? '').trim().isEmpty ? 'Escribe tu nombre' : null,
                onFieldSubmitted: (_) => _saveName(),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 10,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  FilledButton(onPressed: _busy ? null : _saveName, child: const Text('Guardar nombre')),
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _pickPhoto,
                    icon: const Icon(Icons.photo_camera_outlined, size: 18),
                    label: Text(avatar == null ? 'Poner foto' : 'Cambiar foto'),
                  ),
                  if (avatar != null)
                    TextButton(
                      onPressed: _busy
                          ? null
                          : () => _change(() => widget.api.removeAvatar(widget.session), 'Foto quitada.'),
                      child: const Text('Quitar foto'),
                    ),
                  if (_busy) const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                  if (_done != null) Text(_done!, style: text.bodySmall?.copyWith(color: Palette.success)),
                ],
              ),
            ],
          ),
        ),
      ),
    );

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 32),
      child: Align(
        alignment: widget.centered ? Alignment.topCenter : Alignment.topLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('TU CUENTA', style: text.labelSmall?.copyWith(color: Palette.accentText)),
              const SizedBox(height: 8),
              Text('Mi perfil', style: text.headlineMedium),
              const SizedBox(height: 24),
              account,
              const SizedBox(height: 16),
              edit,
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: widget.onSignOut,
                icon: const Icon(Icons.logout, size: 18),
                label: const Text('Cerrar sesión en este navegador'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
