import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Qué es el lugar para quien lo guarda: decide el nombre y el icono con que
/// se muestra. Los códigos son los mismos que usa la app Android.
enum FavoriteKind {
  home('HOME', 'Casa', Icons.home_outlined),
  work('WORK', 'Trabajo', Icons.work_outline),
  study('STUDY', 'Estudio', Icons.school_outlined),
  partner('PARTNER', 'Pareja', Icons.favorite_border),
  family('FAMILY', 'Familia', Icons.family_restroom),
  gym('GYM', 'Gimnasio', Icons.fitness_center),
  other('OTHER', 'Otro', Icons.star_border);

  const FavoriteKind(this.code, this.label, this.icon);

  final String code;
  final String label;
  final IconData icon;

  /// Un tipo que esta versión no conoce no se pierde: pasa a "otro".
  static FavoriteKind fromCode(String? code) =>
      values.firstWhere((kind) => kind.code == code, orElse: () => FavoriteKind.other);
}

/// Un lugar favorito: casa, trabajo, la pareja…
class FavoritePlace {
  const FavoritePlace(this.kind, this.name, this.latitude, this.longitude);

  final FavoriteKind kind;

  /// El nombre que le puso quien lo guardó; vacío si se queda con el del tipo.
  final String name;
  final double latitude;
  final double longitude;

  String get label => name.isEmpty ? kind.label : name;

  factory FavoritePlace.fromJson(Map<String, dynamic> json) => FavoritePlace(
        FavoriteKind.fromCode(json['kind'] as String?),
        json['name'] as String? ?? '',
        (json['latitude'] as num).toDouble(),
        (json['longitude'] as num).toDouble(),
      );

  Map<String, dynamic> toJson() => {'kind': kind.code, 'name': name, 'latitude': latitude, 'longitude': longitude};
}

/// Los lugares favoritos, en el orden en que se añadieron. Son pocos a
/// propósito: los de todos los días.
///
/// Se guardan en este navegador o teléfono y no salen de él.
class FavoritePlaces {
  FavoritePlaces([List<FavoritePlace> items = const []]) : _items = items.take(max).toList();

  /// Más que estos dejan de ser "los de siempre" y vuelven a ser una lista que recorrer.
  static const max = 4;
  static const _key = 'favorite_places';

  final List<FavoritePlace> _items;

  List<FavoritePlace> get items => List.unmodifiable(_items);

  bool get isFull => _items.length >= max;

  /// Los que hay guardados; ninguno si no hay o no se pueden leer.
  static Future<FavoritePlaces> load() async {
    try {
      final raw = (await SharedPreferences.getInstance()).getString(_key);
      if (raw == null) return FavoritePlaces();
      return FavoritePlaces([
        for (final item in jsonDecode(raw) as List) FavoritePlace.fromJson(item as Map<String, dynamic>),
      ]);
    } catch (_) {
      return FavoritePlaces();
    }
  }

  /// Añade [place] al final. Devuelve `false` si ya hay [max] y no cabe.
  bool add(FavoritePlace place) {
    if (isFull) return false;
    _items.add(place);
    _store();
    return true;
  }

  void remove(FavoritePlace place) {
    _items.remove(place);
    _store();
  }

  Future<void> _store() async {
    try {
      final saved = await SharedPreferences.getInstance();
      await saved.setString(_key, jsonEncode([for (final item in _items) item.toJson()]));
    } catch (_) {
      // Sin almacenamiento los favoritos duran lo que dure la página abierta.
    }
  }
}
