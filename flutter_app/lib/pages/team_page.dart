import 'package:flutter/material.dart';

import '../api.dart';
import '../theme.dart';
import '../widgets/page_frame.dart';

/// Colaboradores: cuentas que pueden grabar rutas desde la app.
///
/// Lo que graban no se publica solo: queda como «Pendiente» en el catálogo
/// hasta que un administrador lo revisa y lo publica.
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
  List<Contributor>? _people;
  bool _loading = false;
  bool _adding = false;

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
      final people = await widget.api.contributors();
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
    if (email.isEmpty) return;
    setState(() => _adding = true);
    try {
      final added = await widget.api.addContributor(email);
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

  Future<void> _remove(Contributor person) async {
    try {
      await widget.api.removeContributor(person.id);
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
      title: 'Colaboradores',
      subtitle: 'Graban rutas desde la app; tú las revisas y las publicas desde el catálogo.',
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
                    Text('Añadir colaborador', style: text.titleMedium),
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
                            decoration: const InputDecoration(hintText: 'Correo de su cuenta'),
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
                  child: Text('Todavía no hay colaboradores.', style: text.bodyMedium?.copyWith(color: Palette.muted)),
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
                        trailing: TextButton(onPressed: () => _remove(person), child: const Text('Quitar')),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
