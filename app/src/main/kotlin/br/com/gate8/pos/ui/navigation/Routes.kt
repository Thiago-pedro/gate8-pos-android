package br.com.gate8.pos.ui.navigation

import androidx.navigation.NavController

object Routes {
    const val Login = "login"
    const val LoginPending = "login_pending"
    const val Setup = "setup"
    const val Home = "home"
    const val Pdv = "pdv"
    const val Products = "products"
    const val Checkin = "checkin"
    const val Refund = "refund"
    const val Reports = "reports"
    const val Cashier = "cashier"
    const val Cashless = "cashless"
    const val Pending = "pending"
    const val Kitchen = "kitchen"
}

fun NavController.goHome() {
    val atHome = popBackStack(Routes.Home, inclusive = false)
    if (!atHome) {
        navigate(Routes.Home) { launchSingleTop = true }
    }
}

fun NavController.goLogin() {
    navigate(Routes.Login) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

