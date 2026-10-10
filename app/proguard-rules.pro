# osmdroid usa reflexion para cargar proveedores de teselas
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Fragments instanciados por nombre desde el grafo de navegacion
-keep class com.peru.expresdm.ui.** extends androidx.fragment.app.Fragment
