// Compilación propia, separada de la app Android: cambiar el servidor nunca
// rompe el build de la app, y cada uno usa la versión de Kotlin que le conviene.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "expresdm-backend"
