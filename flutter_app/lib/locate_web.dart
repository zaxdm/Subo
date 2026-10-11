import 'dart:async';
import 'dart:js_interop';

import 'package:web/web.dart' as web;

/// Un sitio y el margen de error, en metros, con el que se conoce.
typedef Fix = ({double latitude, double longitude, double accuracy});

const locationAvailable = true;

/// Pregunta al navegador dónde está el dispositivo. `null` si la persona no da
/// permiso o el navegador no lo averigua a tiempo.
Future<Fix?> currentLocation() {
  final done = Completer<Fix?>();
  void finish(Fix? fix) {
    if (!done.isCompleted) done.complete(fix);
  }

  try {
    web.window.navigator.geolocation.getCurrentPosition(
      (web.GeolocationPosition position) {
        final coords = position.coords;
        finish((
          latitude: coords.latitude.toDouble(),
          longitude: coords.longitude.toDouble(),
          accuracy: coords.accuracy.toDouble(),
        ));
      }.toJS,
      (web.GeolocationPositionError _) {
        finish(null);
      }.toJS,
      web.PositionOptions(enableHighAccuracy: true, timeout: 15000, maximumAge: 30000),
    );
  } catch (_) {
    finish(null);
  }
  return done.future;
}
