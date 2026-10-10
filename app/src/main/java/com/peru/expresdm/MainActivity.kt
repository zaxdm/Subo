package com.peru.expresdm

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.peru.expresdm.databinding.ActivityMainBinding

/**
 * Única Activity de la app: aloja el grafo de navegación y la barra inferior.
 *
 * Con NavigationUI, cada pestaña reutiliza su destino en lugar de apilar
 * pantallas nuevas, así que el botón atrás se comporta como se espera.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNav.setupWithNavController(navController)

        // Las pantallas secundarias (ajustes, soporte, ficha del micro, nueva ruta) ocupan todo el alto.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val visibility = if (destination.id in TOP_LEVEL_DESTINATIONS) View.VISIBLE else View.GONE
            binding.bottomNav.visibility = visibility
            binding.navDivider.visibility = visibility
        }

        applyWindowInsets()
    }

    /** Deja libres la barra de estado y la de gestos. */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private companion object {
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.routesFragment,
            R.id.reportsFragment,
            R.id.profileFragment
        )
    }
}
