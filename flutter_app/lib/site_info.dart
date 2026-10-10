/// Los datos de contacto de Subo que se enseñan en la web.
///
/// Un dato vacío se muestra como «Próximamente»: basta escribirlo aquí para
/// que aparezca en Contacto y en el pie de página.
abstract final class SiteInfo {
  /// El cliente OAuth de tipo Web de Google, el mismo que usa la app. No es
  /// un secreto: va en la página. En Google Cloud tiene que tener autorizada
  /// la dirección de esta web como «origen de JavaScript».
  static const googleClientId = String.fromEnvironment(
    'GOOGLE_CLIENT_ID',
    defaultValue: '608487070231-84hnjf4mr3rollfpdat0ev4vqa5ud6ot.apps.googleusercontent.com',
  );

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
