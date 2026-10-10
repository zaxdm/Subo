import 'package:flutter/material.dart';

import 'api.dart';
import 'pages/catalog_page.dart';
import 'pages/editor_page.dart';
import 'pages/feedback_page.dart';
import 'pages/home_page.dart';
import 'pages/info_pages.dart';
import 'pages/login_page.dart';
import 'theme.dart';
import 'widgets/shell.dart';
import 'widgets/site_shell.dart';

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

/// Decide qué se ve.
///
/// Cualquiera entra directo a la web de los pasajeros: el mapa con las líneas
/// y las páginas de información. Con una cuenta de administrador se pasa al
/// panel: el catálogo, el editor de una ruta o el buzón.
class AdminHome extends StatefulWidget {
  const AdminHome({super.key, required this.api});

  final ApiClient api;

  @override
  State<AdminHome> createState() => _AdminHomeState();
}

class _AdminHomeState extends State<AdminHome> {
  /// Quién entró; `null` sin sesión.
  Session? _session;

  // --- La web de los pasajeros ---

  /// Las líneas publicadas, las que ve todo el mundo.
  List<BusRoute> _lines = [];
  bool _loadingLines = true;
  String? _linesError;
  SitePage _page = SitePage.map;

  /// Se está viendo el acceso en lugar de una página.
  bool _signingIn = false;

  /// La línea abierta en el mapa.
  String? _openLine;

  /// La línea que alguien quiso abrir sin sesión: se abre al entrar.
  String? _wantedLine;

  // --- El panel de administración ---

  /// El catálogo completo, con las rutas pendientes y ocultas.
  List<BusRoute> _routes = [];

  /// La ruta abierta en el editor; `null` si se ve el catálogo.
  BusRoute? _editing;

  AdminSection _section = AdminSection.routes;

  ApiClient get _api => widget.api;

  bool get _isAdmin => _session?.isAdmin ?? false;

  @override
  void initState() {
    super.initState();
    _loadLines();
  }

  Future<void> _loadLines() async {
    setState(() {
      _loadingLines = true;
      _linesError = null;
    });
    try {
      final lines = await _api.publicRoutes();
      if (!mounted) return;
      setState(() => _lines = lines);
    } catch (error) {
      if (!mounted) return;
      setState(() => _linesError = error is ApiException ? error.message : 'No se pudieron cargar las líneas.');
    } finally {
      if (mounted) setState(() => _loadingLines = false);
    }
  }

  void _onSignedIn(Session session, List<BusRoute> routes) {
    setState(() {
      _session = session;
      _signingIn = false;
      if (session.isAdmin) {
        _routes = routes;
      } else {
        _lines = routes;
        _linesError = null;
        // Vuelve a donde estaba, con la línea que quería ver ya abierta.
        if (_wantedLine != null) {
          _openLine = _wantedLine;
          _page = SitePage.map;
        }
      }
      _wantedLine = null;
    });
    if (session.isAdmin) _notify('Sesión iniciada. El catálogo está actualizado.');
  }

  void _signOut() {
    _api.signOut();
    setState(() {
      _session = null;
      _routes = [];
      _editing = null;
      _section = AdminSection.routes;
      _openLine = null;
      _wantedLine = null;
      _page = SitePage.map;
    });
    // Lo que cambió el administrador ya se ve en el mapa.
    _loadLines();
  }

  /// Abrir el recorrido de una línea pide sesión.
  void _selectLine(String? id) {
    if (id != null && _session == null) {
      setState(() {
        _wantedLine = id;
        _signingIn = true;
      });
      return;
    }
    setState(() => _openLine = id);
  }

  /// Enseña el fallo y, si la sesión caducó, vuelve a pedir que se entre.
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
  Widget build(BuildContext context) => _isAdmin ? _buildPanel() : _buildSite();

  Widget _buildSite() {
    final session = _session;
    void signIn() => setState(() => _signingIn = true);

    final Widget page;
    if (_signingIn) {
      page = LoginPage(
        api: _api,
        onSignedIn: _onSignedIn,
        reason: _wantedLine == null ? null : 'Entra para ver el recorrido de la línea.',
      );
    } else {
      page = switch (_page) {
        SitePage.map => HomePage(
            api: _api,
            routes: _lines,
            loading: _loadingLines,
            loadError: _linesError,
            onRetry: _loadLines,
            session: session,
            selectedId: _openLine,
            onSelect: _selectLine,
            onSignIn: signIn,
            onError: _onError,
          ),
        SitePage.about => const AboutPage(),
        SitePage.privacy => const PrivacyPage(),
        SitePage.terms => const TermsPage(),
        SitePage.contact => ContactPage(api: _api, signedIn: session != null, onSignIn: signIn, onError: _onError),
      };
    }

    return SiteShell(
      page: _signingIn ? null : _page,
      onPage: (page) => setState(() {
        _page = page;
        _signingIn = false;
        _wantedLine = null;
      }),
      accountName: session?.name,
      onSignIn: signIn,
      onSignOut: session == null ? null : _signOut,
      child: page,
    );
  }

  Widget _buildPanel() {
    final editing = _editing;

    final Widget page;
    if (_section == AdminSection.feedback) {
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
      accountName: _session?.name,
      onSignOut: _signOut,
      admin: true,
      section: _section,
      // Una ruta a medio editar se queda esperando al volver de Sugerencias.
      onSection: (section) => setState(() => _section = section),
      child: page,
    );
  }
}
