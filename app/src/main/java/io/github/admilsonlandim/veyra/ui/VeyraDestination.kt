package io.github.admilsonlandim.veyra.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.admilsonlandim.veyra.R

/** Top-level sections of the app. Navigation is a plain enum held in saveable state. */
enum class VeyraDestination(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Library(R.string.nav_library, R.drawable.ic_nav_library),
    Playlists(R.string.nav_playlists, R.drawable.ic_nav_playlists),
    Settings(R.string.nav_settings, R.drawable.ic_nav_settings),
}
