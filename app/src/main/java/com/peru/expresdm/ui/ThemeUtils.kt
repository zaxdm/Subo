package com.peru.expresdm.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

/**
 * Resuelve un color del tema activo (claro u oscuro) desde código.
 *
 * Evita referenciar `@color/...` directamente en los fragments, que es lo que
 * rompía el modo oscuro en la versión anterior.
 */
@ColorInt
fun Context.themeColor(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}
