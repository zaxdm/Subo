import 'package:flutter/material.dart';

import 'api.dart';
import 'pages/alerts_page.dart';
import 'pages/catalog_page.dart';
import 'pages/editor_page.dart';
import 'pages/feedback_page.dart';
import 'pages/home_page.dart';
import 'pages/info_pages.dart';
import 'pages/login_page.dart';
import 'pages/stats_page.dart';
import 'pages/team_page.dart';
import 'theme.dart';
import 'widgets/shell.dart';
import 'widgets/site_shell.dart';

void main() => runApp(AdminApp(sharedLine: sharedLineOf(Uri.base)));

/// La línea a la que apunta un enlace compartido desde la app
/// (`https://…/ruta/<id>`), o `null` si la dirección es otra.
String? sharedLineOf(Uri address) {
  final segments = address.pathSegments;
  if (segments.length != 2 || segments[0] != 'ruta' || segments[1].isEmpty) return null;
  return segments[1];
}

class AdminApp extends StatelessWidget {
  const AdminApp({super.key, this.api, this.sharedLine});

  /// Las pruebas pasan un cliente de mentira.
  final ApiClient? api;

  /// La línea con la que se abre la web, si se llegó por un enlace compartido.
  final String? sharedLine;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Subo',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: AdminHome(api: api ?? ApiClient(), sharedLine: sharedLine),
    );
  }
}

/// Decide qué se ve.
///
/// Cualquiera entra directo a la web de los pasajeros: el mapa con las líneas
/// y las páginas de información. Con una cuenta de administrador se pasa al
/// panel: el catálogo, el editor de una ruta o el buzón.
class AdminHome extends StatefulWidget {
  const AdminHome({super.key, required this.api, this.sharedLine});

  final ApiClient api;

  /// La línea que hay que enseñar al abrir, si se llegó por un enlace compartido.
  final String? sharedLine;

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
  /// Se abre por Inicio; quien llega por el enlace de una línea va al mapa.
  late SitePage _page = widget.sharedLine == null ? SitePage.about : SitePage.map;

  /// La línea abierta en el mapa.
  String? _openLine;

  /// La línea que alguien quiso abrir sin sesión: se abre al entrar.
  String? _wantedLine;

  /// El enlace compartido solo se atiende una vez, al abrir la web.
  bool _sharedLineOpened = false;

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
      _openSharedLine();
    } catch (error) {
      if (!mounted) return;
      setState(() => _linesError = error is ApiException ? error.message : 'No se pudieron cargar las líneas.');
    } finally {
      if (mounted) setState(() => _loadingLines = false);
    }
  }

  /// Quien llega por un enlace compartido va directo a esa línea, como si la
  /// hubiera tocado. Si ya no existe, se queda en el mapa con todas.
  void _openSharedLine() {
    final shared = widget.sharedLine;
    if (shared == null || _sharedLineOpened || _isAdmin) return;
    _sharedLineOpened = true;
    if (_lines.any((line) => line.id == shared)) _selectLine(shared);
  }

  void _onSignedIn(Session session, List<BusRoute> routes) {
    setState(() {
      _session = session;
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
      _page = SitePage.about;
    });
    // Lo que cambió el administrador ya se ve en el mapa.
    _loadLines();
  }

  /// Abrir el recorrido de una línea pide sesión.
  void _selectLine(String? id) {
    if (id != null && _session == null) {
      _wantedLine = id;
      _openLogin();
      return;
    }
    setState(() => _openLine = id);
  }

  /// El acceso es una ventana sobre la página: al cerrarla se sigue donde se
  /// estaba.
  Future<void> _openLogin() async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => Dialog(
        backgroundColor: Colors.transparent,
        elevation: 0,
        insetPadding: const EdgeInsets.all(16),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: SingleChildScrollView(
            child: LoginPage(
              api: _api,
              reason: _wantedLine == null ? null : 'Entra para ver el recorrido de la línea.',
              onClose: () => Navigator.of(dialogContext).pop(),
              onSignedIn: (session, routes) {
                Navigator.of(dialogContext).pop();
                _onSignedIn(session, routes);
              },
            ),
          ),
        ),
      ),
    );
    // Si cerró sin entrar, se olvida la línea que quería ver.
    if (mounted && _session == null) _wantedLine = null;
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
    void goTo(SitePage page) => setState(() => _page = page);

    final page = switch (_page) {
      SitePage.map => HomePage(
          api: _api,
          routes: _lines,
          loading: _loadingLines,
          loadError: _linesError,
          onRetry: _loadLines,
          session: session,
          selectedId: _openLine,
          onSelect: _selectLine,
          onSignIn: _openLogin,
          onError: _onError,
        ),
      SitePage.about => AboutPage(routes: _lines, onPage: goTo),
      SitePage.download => DownloadPage(onPage: goTo),
      SitePage.privacy => PrivacyPage(onPage: goTo),
      SitePage.terms => TermsPage(onPage: goTo),
      SitePage.contact => ContactPage(
          api: _api,
          signedIn: session != null,
          onSignIn: _openLogin,
          onError: _onError,
          onPage: goTo,
        ),
    };

    return SiteShell(
      page: _page,
      onPage: goTo,
      accountName: session?.name,
      onSignIn: _openLogin,
      onSignOut: session == null ? null : _signOut,
      child: page,
    );
  }

  Widget _buildPanel() {
    final editing = _editing;

    final Widget page;
    if (_section == AdminSection.feedback) {
      page = FeedbackPage(api: _api, onError: _onError);
    } else if (_section == AdminSection.alerts) {
      page = AlertsPage(api: _api, onError: _onError);
    } else if (_section == AdminSection.stats) {
      page = StatsPage(api: _api, onError: _onError);
    } else if (_section == AdminSection.team) {
      page = TeamPage(api: _api, onError: _onError);
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
