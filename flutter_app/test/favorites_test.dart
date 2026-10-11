import 'package:flutter_test/flutter_test.dart';
import 'package:ruta_norte_admin/favorites.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  FavoritePlace place(FavoriteKind kind) => FavoritePlace(kind, '', -8.11, -79.03);

  test('solo caben cuatro favoritos', () {
    final favorites = FavoritePlaces();
    for (final kind in [FavoriteKind.home, FavoriteKind.work, FavoriteKind.study, FavoriteKind.gym]) {
      expect(favorites.add(place(kind)), isTrue);
    }
    expect(favorites.isFull, isTrue);
    expect(favorites.add(place(FavoriteKind.other)), isFalse);
    expect(favorites.items, hasLength(4));

    // Al quitar uno vuelve a haber sitio.
    favorites.remove(favorites.items.first);
    expect(favorites.add(place(FavoriteKind.other)), isTrue);
  });

  test('los favoritos siguen ahí al volver a abrir', () async {
    final favorites = FavoritePlaces()
      ..add(const FavoritePlace(FavoriteKind.home, '', -8.1, -79.0))
      ..add(const FavoritePlace(FavoriteKind.other, 'La abuela', -8.2, -79.1));
    await pumpEventQueue();

    final again = await FavoritePlaces.load();
    expect([for (final item in again.items) item.label], ['Casa', 'La abuela']);
    expect(again.items.last.latitude, -8.2);
    expect(favorites.items, hasLength(2));
  });
}
