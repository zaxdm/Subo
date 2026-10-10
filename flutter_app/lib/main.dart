import 'package:flutter/material.dart';

import 'api.dart';
import 'pages/catalog_page.dart';
import 'pages/editor_page.dart';
import 'pages/feedback_page.dart';
import 'pages/lines_page.dart';
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
      title: 'Subo',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: AdminHome(api: api ?? ApiClient()),
    );
  }
}

/// Decide qué se ve. Sin sesión, el acceso. Un pasajero (o quien mira sin
/// cuenta) ve las líneas; un administrador, el panel: el catálogo, el editor
/// de una ruta o el buzón.
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
  /// Quién entró; `null` sin sesión.
  Session? _session;

  /// Mirando las líneas sin cuenta.
  bool _guest = false;
  List<BusRoute> _routes = [];

  /// La ruta abierta en el editor; `null` si se ve el catálogo.
  BusRoute? _editing;

  AdminSection _section = AdminSection.routes;

  ApiClient get _api => widget.api;

  bool get _isAdmin => _session?.isAdmin ?? false;

  void _onSignedIn(Session session, List<BusRoute> routes) {
    setState(() {
      _session = session;
      _guest = false;
      _routes = routes;
    });
    if (session.isAdmin) _notify('Sesión iniciada. El catálogo está actualizado.');
  }

  void _onGuest(List<BusRoute> routes) {
    setState(() {
      _guest = true;
      _routes = routes;
    });
  }

  void _signOut() {
    _api.signOut();
    setState(() {
      _session = null;
      _guest = false;
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
      final routes = _isAdmin ? await _api.routes() : await _api.publicRoutes();
      if (!mounted) return;
      setState(() => _routes = routes);
      _notify(_isAdmin ? 'Catálogo actualizado.' : 'Líneas actualizadas.');
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
    final session = _session;
    final editing = _editing;
    final inside = session != null || _guest;

    final Widget page;
    if (!inside) {
      page = LoginPage(api: _api, onSignedIn: _onSignedIn, onGuest: _onGuest);
    } else if (!_isAdmin) {
      page = LinesPage(routes: _routes, onRefresh: _refresh, greeting: session?.name);
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
      accountName: session?.name,
      onSignOut: inside ? _signOut : null,
      signOutLabel: _guest ? 'Iniciar sesión' : 'Cerrar sesión',
      admin: _isAdmin,
      section: _section,
      // Una ruta a medio editar se queda esperando al volver de Sugerencias.
      onSection: _isAdmin ? (section) => setState(() => _section = section) : null,
      child: page,
    );
  }
}
