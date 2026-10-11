import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../theme.dart';

/// El azul con el que los mapas señalan «estás aquí».
const mapBlue = Color(0xFF1A73E8);

/// El fondo del mapa: calles o foto desde el aire. Ninguno pide clave.
enum MapBase {
  /// El mapa de calles de OpenStreetMap. Los que más se parecen a Google Maps
  /// (CARTO, MapTiler, el propio Google) piden clave: sin ella, CARTO devuelve
  /// las teselas con un «API KEY REQUIRED» encima.
  streets(
    'Mapa',
    'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    '© OpenStreetMap',
    maxNativeZoom: 19,
  ),

  /// World Imagery, de Esri.
  satellite(
    'Satélite',
    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    'Imágenes © Esri, Maxar, Earthstar Geographics',
    maxNativeZoom: 18,
  );

  const MapBase(this.label, this.url, this.credit, {required this.maxNativeZoom});

  final String label;
  final String url;

  /// A quién hay que nombrar, a la vista, por usar sus imágenes.
  final String credit;
  final int maxNativeZoom;

  TileLayer get layer => TileLayer(
        // Al cambiar de fondo no se aprovecha nada del anterior.
        key: ValueKey(this),
        urlTemplate: url,
        userAgentPackageName: 'pe.subo.web',
        maxNativeZoom: maxNativeZoom,
      );
}

/// Lleva el mapa de un sitio a otro deslizándolo, en vez de dar un salto.
class MapGlider {
  MapGlider(this._map, TickerProvider vsync)
      : _controller = AnimationController(vsync: vsync, duration: const Duration(milliseconds: 450));

  final MapController _map;
  final AnimationController _controller;
  VoidCallback? _step;

  void to(LatLng center, double zoom) {
    final from = _map.camera;
    final start = from.center;
    final startZoom = from.zoom;
    if (_step case final old?) _controller.removeListener(old);
    void step() {
      final t = Curves.easeInOutCubic.transform(_controller.value);
      _map.move(
        LatLng(
          start.latitude + (center.latitude - start.latitude) * t,
          start.longitude + (center.longitude - start.longitude) * t,
        ),
        startZoom + (zoom - startZoom) * t,
      );
    }

    _step = step;
    _controller
      ..addListener(step)
      ..forward(from: 0);
  }

  /// Encuadra lo que pida [fit].
  void fit(CameraFit fit) {
    final target = fit.fit(_map.camera);
    to(target.center, target.zoom);
  }

  /// Si alguien coge el mapa a mitad de camino, manda su mano.
  void stop() => _controller.stop();

  void dispose() => _controller.dispose();
}

/// Un botón redondo de los que flotan sobre el mapa.
class MapButton extends StatelessWidget {
  const MapButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onTap,
    this.active = false,
    this.busy = false,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  /// Lo que enciende el botón está a la vista.
  final bool active;

  /// Está esperando una respuesta.
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Material(
        color: Palette.surface,
        elevation: 3,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: busy ? null : onTap,
          child: SizedBox(
            width: 42,
            height: 42,
            child: busy
                ? const Padding(
                    padding: EdgeInsets.all(12),
                    child: CircularProgressIndicator(strokeWidth: 2, color: mapBlue),
                  )
                : Icon(icon, size: 21, color: active ? mapBlue : Palette.ink),
          ),
        ),
      ),
    );
  }
}

/// El más y el menos, juntos en una pastilla.
class MapZoomButtons extends StatelessWidget {
  const MapZoomButtons({super.key, required this.onZoom});

  /// Recibe +1 para acercar y -1 para alejar.
  final void Function(double change) onZoom;

  @override
  Widget build(BuildContext context) {
    Widget half(IconData icon, String tooltip, double change) => Tooltip(
          message: tooltip,
          child: InkWell(
            onTap: () => onZoom(change),
            child: SizedBox(width: 42, height: 40, child: Icon(icon, size: 21, color: Palette.ink)),
          ),
        );
    return Material(
      color: Palette.surface,
      elevation: 3,
      borderRadius: BorderRadius.circular(21),
      clipBehavior: Clip.antiAlias,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          half(Icons.add, 'Acercar', 1),
          const SizedBox(width: 26, child: Divider()),
          half(Icons.remove, 'Alejar', -1),
        ],
      ),
    );
  }
}

/// El punto azul de «estás aquí».
class MyLocationDot extends StatelessWidget {
  const MyLocationDot({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: mapBlue,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 3),
        boxShadow: const [BoxShadow(color: Color(0x55000000), blurRadius: 4, offset: Offset(0, 1))],
      ),
    );
  }
}

/// De quién son las imágenes del mapa. Sus licencias piden nombrarlos a la
/// vista, y sobre la foto aérea el texto suelto no se leería.
class MapCredit extends StatelessWidget {
  const MapCredit(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(6),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: const Color(0xD9FFFFFF), borderRadius: BorderRadius.circular(20)),
      child: Text(text, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 10, color: Palette.ink)),
    );
  }
}
