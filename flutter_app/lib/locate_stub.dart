/// Un sitio y el margen de error, en metros, con el que se conoce.
typedef Fix = ({double latitude, double longitude, double accuracy});

/// Fuera del navegador no se puede preguntar la ubicación.
const locationAvailable = false;

Future<Fix?> currentLocation() async => null;
