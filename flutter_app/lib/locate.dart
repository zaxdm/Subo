// Dónde está quien mira el mapa. Solo se sabe en el navegador, que es quien
// pide el permiso; en cualquier otro sitio (y en las pruebas) no hay ubicación.
export 'locate_stub.dart' if (dart.library.js_interop) 'locate_web.dart';
