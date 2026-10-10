package com.peru.expresdm.backend.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distancias sobre el mapa.
 *
 * Es la misma fórmula que usa la app (haversine), repetida aquí a propósito:
 * el servidor no depende del código Android, y las dos partes tienen que medir
 * igual para que "a menos de 500 m" signifique lo mismo en ambos lados.
 */
object Geo {

    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val METERS_PER_DEGREE_LAT = 110_540.0
    private const val METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val a = sinLat * sinLat + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Caja que contiene todo lo que está a [radiusMeters] de un punto.
     *
     * La base de datos filtra primero por esta caja, que usa los índices y es
     * instantánea; la distancia exacta se calcula después sobre esas pocas filas.
     */
    fun boundingBox(latitude: Double, longitude: Double, radiusMeters: Double): BoundingBox {
        val latSpan = radiusMeters / METERS_PER_DEGREE_LAT
        // Cerca de los polos un grado de longitud son pocos metros: se acota para
        // no dividir por casi cero.
        val metersPerDegreeLon = max(1.0, METERS_PER_DEGREE_LON_AT_EQUATOR * cos(Math.toRadians(latitude)))
        val lonSpan = radiusMeters / metersPerDegreeLon
        return BoundingBox(
            minLatitude = latitude - latSpan,
            maxLatitude = latitude + latSpan,
            minLongitude = longitude - lonSpan,
            maxLongitude = longitude + lonSpan
        )
    }

    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && abs(latitude) <= 90.0 && abs(longitude) <= 180.0
}

data class BoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)
