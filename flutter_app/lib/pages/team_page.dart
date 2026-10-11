import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/page_frame.dart';

/// Administradores: cuentas que entran al panel y graban rutas desde la app.
///
/// Solo la ve el administrador principal, que es quien da y quita el acceso.
class TeamPage extends StatefulWidget {
  const TeamPage({super.key, required this.api, required this.onError});

  final ApiClient api;
  final void Function(Object error) onError;

  @override
  State<TeamPage> createState() => _TeamPageState();
}

class _TeamPageState extends State<TeamPage> {
  final _email = TextEditingController();

  /// `null` mientras llega la primera respuesta.
  List<Admin>? _people;
  bool _loading = false;
  bool _adding = false;

  /// Lo que le falta al correo escrito; `null` si no hay nada que decir.
  String? _emailProblem;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final people = await widget.api.admins();
      if (mounted) setState(() => _people = people);
    } catch (error) {
      if (mounted) setState(() => _people ??= []);
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _add() async {
    final email = _email.text.trim();
    if (!email.contains('@')) {
      setState(() => _emailProblem = 'Escribe el correo de la cuenta');
      return;
    }
    setState(() {
      _emailProblem = null;
      _adding = true;
    });
    try {
      final added = await widget.api.addAdmin(email);
      if (!mounted) return;
      setState(() {
        _people = [...?_people?.where((person) => person.id != added.id), added];
        _email.clear();
      });
    } catch (error) {
      widget.onError(error);
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  Future<void> _remove(Admin person) async {
    try {
      await widget.api.removeAdmin(person.id);
      if (mounted) setState(() => _people = _people?.where((other) => other.id != person.id).toList());
    } catch (error) {
      widget.onError(error);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final people = _people;
    return PageFrame(
      label: 'EQUIPO',
      title: 'Administradores',
      subtitle: 'Entran a este panel y graban rutas desde la app, que salen publicadas.',
      loading: _loading,
      onRefresh: _load,
      ready: people != null,
      slivers: (padding) => [
        SliverPadding(
          padding: padding,
          sliver: SliverToBoxAdapter(
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Añadir administrador', style: text.titleMedium),
                    const SizedBox(height: 4),
                    Text('Tiene que tener ya una cuenta en la app.', style: text.bodySmall),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 12,
                      runSpacing: 12,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        SizedBox(
                          width: 320,
                          child: TextField(
                            controller: _email,
                            keyboardType: TextInputType.emailAddress,
                            onSubmitted: (_) => _add(),
                            decoration: InputDecoration(hintText: 'Correo de su cuenta', errorText: _emailProblem),
                          ),
                        ),
                        FilledButton.icon(
                          onPressed: _adding ? null : _add,
                          icon: const Icon(Icons.person_add_alt, size: 18),
                          label: const Text('Añadir'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: padding.copyWith(top: 16, bottom: 40),
          sliver: people!.isEmpty
              ? SliverToBoxAdapter(
                  child: Text('Todavía no hay administradores.', style: text.bodyMedium?.copyWith(color: Palette.muted)),
                )
              : SliverList.separated(
                  itemCount: people.length,
                  separatorBuilder: (context, index) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final person = people[index];
                    return Card(
                      child: ListTile(
                        title: Text(person.name.isEmpty ? person.email : person.name),
                        subtitle: Text(person.email),
                        // Al principal no se le quita el acceso: el panel no se queda sin dueño.
                        trailing: person.isPrincipal
                            ? Text('Principal', style: text.bodySmall?.copyWith(color: Palette.muted))
                            : TextButton(onPressed: () => _remove(person), child: const Text('Quitar')),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
