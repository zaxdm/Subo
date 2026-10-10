import 'package:flutter/material.dart';

import 'api.dart';
import 'pages/catalog_page.dart';
import 'pages/editor_page.dart';
import 'pages/feedback_page.dart';
import 'pages/login_page.dart';
import 'theme.dart';
import 'widgets/shell.dart';

void main() => runApp(const AdminApp());

class AdminApp extends StatelessWidget {
  const AdminApp({super.key, this.api});

  /// Las pruebas pasan un cliente de mentira.
  final ApiClient? api;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Subo · Administración',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: AdminHome(api: api ?? ApiClient()),
    );
  }
}

/// Decide qué se ve: el acceso, el catálogo, el editor de una ruta o el buzón.
///
/// El catálogo se guarda aquí para que al volver del editor no haya que
/// pedirlo otra vez al servidor.
class AdminHome extends StatefulWidget {
  const AdminHome({super.key, required this.api});

  final ApiClient api;

  @override
  State<AdminHome> createState() => _AdminHomeState();
}

class _AdminHomeState extends State<AdminHome> {
  String? _accountName;
  List<BusRoute> _routes = [];

  /// La ruta abierta en el editor; `null` si se ve el catálogo.
  BusRoute? _editing;

  AdminSection _section = AdminSection.routes;

  ApiClient get _api => widget.api;

  void _onSignedIn(String name, List<BusRoute> routes) {
    setState(() {
      _accountName = name;
      _routes = routes;
    });
    _notify('Sesión iniciada. El catálogo está actualizado.');
  }

  void _signOut() {
    _api.signOut();
    setState(() {
      _accountName = null;
      _routes = [];
      _editing = null;
      _section = AdminSection.routes;
    });
  }

  /// Enseña el fallo y, si la sesión caducó, vuelve a la pantalla de acceso.
  void _onError(Object error) {
    _notify(error is ApiException ? error.message : 'Algo falló. Inténtalo de nuevo.', error: true);
    if (error is ApiException && error.unauthorized) _signOut();
  }

  void _notify(String message, {bool error = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: error ? Palette.danger : Palette.success,
          duration: Duration(seconds: error ? 8 : 5),
          showCloseIcon: true,
        ),
      );
  }

  Future<void> _refresh() async {
    try {
      final routes = await _api.routes();
      if (!mounted) return;
      setState(() => _routes = routes);
      _notify('Catálogo actualizado.');
    } catch (error) {
      _onError(error);
    }
  }

  void _onSaved(BusRoute saved) {
    setState(() {
      _routes = [saved, ..._routes.where((route) => route.id != saved.id)];
      _editing = null;
    });
    _notify('Ruta guardada. Las publicadas llegan a la app cuando actualice su catálogo.');
  }

  void _onDeleted(String id) {
    setState(() {
      _routes = _routes.where((route) => route.id != id).toList();
      _editing = null;
    });
    _notify('Ruta eliminada.');
  }

  @override
  Widget build(BuildContext context) {
    final name = _accountName;
    final editing = _editing;

    final Widget page;
    if (name == null) {
      page = LoginPage(api: _api, onSignedIn: _onSignedIn);
    } else if (_section == AdminSection.feedback) {
      page = FeedbackPage(api: _api, onError: _onError);
    } else if (editing != null) {
      page = EditorPage(
        // Una ruta distinta es un editor distinto: no se mezclan sus campos.
        key: ValueKey(editing.id ?? identityHashCode(editing)),
        api: _api,
        route: editing,
        onSaved: _onSaved,
        onDeleted: _onDeleted,
        onCancel: () => setState(() => _editing = null),
        onError: _onError,
      );
    } else {
      page = CatalogPage(
        routes: _routes,
        onRefresh: _refresh,
        onNew: () => setState(() => _editing = BusRoute()),
        onEdit: (route) => setState(() => _editing = route.copy()),
      );
    }

    return AdminShell(
      accountName: name,
      onSignOut: name == null ? null : _signOut,
      section: _section,
      // Una ruta a medio editar se queda esperando al volver de Sugerencias.
      onSection: name == null ? null : (section) => setState(() => _section = section),
      child: page,
    );
  }
}
