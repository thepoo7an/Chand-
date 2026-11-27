package ir.thepoo7an.chand.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface ChandRoute {
    @Serializable
    data object Market : ChandRoute

    @Serializable
    data object Converter : ChandRoute

    @Serializable
    data object Alerts : ChandRoute
}
