/// Los datos de contacto de Subo que se enseñan en la web.
///
/// Un dato vacío se muestra como «Próximamente»: basta escribirlo aquí para
/// que aparezca en Contacto y en el pie de página.
abstract final class SiteInfo {
  /// Correo al que escribe la gente.
  static const email = '';

  /// WhatsApp con el código del país y sin espacios ni signos: 51987654321.
  static const whatsapp = '';

  /// Direcciones completas de las redes (https://…).
  static const facebook = '';
  static const instagram = '';
  static const tiktok = '';

  /// Dónde se descarga la app de Android. Lo más simple: copiar el archivo a
  /// `web/descargas/subo.apk` y poner aquí `descargas/subo.apk`. También vale
  /// una dirección completa (https://…). Vacío: «Disponible pronto».
  static const apkUrl = 'descargas/subo.apk';

  /// Lo que se enseña junto al botón, por ejemplo `1.0` y `18 MB`.
  static const apkVersion = '1.0';
  static const apkSize = '4 MB';
}
