package com.peru.expresdm.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.peru.expresdm.R
import com.peru.expresdm.data.SavedPlaceStore

/** Icono y nombre de cada tipo de lugar favorito, iguales en el mapa y en "¿A dónde vas?". */
object SavedPlaceStyle {

    @StringRes
    fun label(kind: SavedPlaceStore.Kind): Int = when (kind) {
        SavedPlaceStore.Kind.HOME -> R.string.saved_place_home
        SavedPlaceStore.Kind.WORK -> R.string.saved_place_work
        SavedPlaceStore.Kind.STUDY -> R.string.saved_place_study
        SavedPlaceStore.Kind.PARTNER -> R.string.saved_place_partner
        SavedPlaceStore.Kind.FAMILY -> R.string.saved_place_family
        SavedPlaceStore.Kind.GYM -> R.string.saved_place_gym
        SavedPlaceStore.Kind.OTHER -> R.string.saved_place_other
    }

    @DrawableRes
    fun icon(kind: SavedPlaceStore.Kind): Int = when (kind) {
        SavedPlaceStore.Kind.HOME -> R.drawable.ic_fav_home
        SavedPlaceStore.Kind.WORK -> R.drawable.ic_fav_work
        SavedPlaceStore.Kind.STUDY -> R.drawable.ic_fav_study
        SavedPlaceStore.Kind.PARTNER -> R.drawable.ic_fav_partner
        SavedPlaceStore.Kind.FAMILY -> R.drawable.ic_fav_family
        SavedPlaceStore.Kind.GYM -> R.drawable.ic_fav_gym
        SavedPlaceStore.Kind.OTHER -> R.drawable.ic_fav_other
    }

    /** Pregunta qué es el lugar que se va a guardar: de ahí salen su nombre y su icono. */
    fun pickKind(
        context: Context,
        favorites: List<SavedPlaceStore.Favorite>,
        onPicked: (SavedPlaceStore.Kind) -> Unit
    ) {
        // Una sola casa, un solo trabajo…; "otro" sirve las veces que haga falta.
        val used = favorites.map { it.kind }.toSet()
        val kinds = SavedPlaceStore.Kind.entries.filter { it == SavedPlaceStore.Kind.OTHER || it !in used }
        val iconGap = (ICON_GAP_DP * context.resources.displayMetrics.density).toInt()
        val adapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.select_dialog_item,
            kinds.map { context.getString(label(it)) }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon(kinds[position]), 0, 0, 0)
                    compoundDrawablePadding = iconGap
                }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.saved_place_kind_title)
            .setAdapter(adapter) { _, which -> onPicked(kinds[which]) }
            .show()
    }

    private const val ICON_GAP_DP = 16
}
