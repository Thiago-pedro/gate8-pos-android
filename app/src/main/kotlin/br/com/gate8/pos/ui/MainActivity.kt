package br.com.gate8.pos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import br.com.gate8.pos.R
import br.com.gate8.pos.core.session.SessionEvents
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.ui.cashier.CashierScreen
import br.com.gate8.pos.ui.cashless.CashlessScreen
import br.com.gate8.pos.ui.checkin.CheckinScreen
import br.com.gate8.pos.ui.common.Gate8SplashHost
import br.com.gate8.pos.ui.config.SetupScreen
import br.com.gate8.pos.ui.home.HomeScreen
import br.com.gate8.pos.ui.login.LoginPendingScreen
import br.com.gate8.pos.ui.login.LoginScreen
import br.com.gate8.pos.ui.navigation.Routes
import br.com.gate8.pos.ui.pending.PendingScreen
import br.com.gate8.pos.ui.pdv.PdvScreen
import br.com.gate8.pos.ui.products.ProductsScreen
import br.com.gate8.pos.ui.refund.RefundScreen
import br.com.gate8.pos.ui.reports.ReportsScreen
import br.com.gate8.pos.ui.theme.Gate8Colors
import br.com.gate8.pos.ui.theme.Gate8Theme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val configStore: DeviceConfigStore by inject()
    private val sessionEvents: SessionEvents by inject()
    private val keepSystemSplash = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSystemSplash.get() }
        super.onCreate(savedInstanceState)
        configStore.ensureDefaultBaseUrl()

        setContent {
            Gate8SplashHost(
                onSplashVisible = { visible -> keepSystemSplash.set(visible) },
                onSplashFinished = { setTheme(R.style.Theme_Gate8POS) },
            ) {
                Gate8Theme {
                    val nav = rememberNavController()
                    val start = if (configStore.isLoggedIn()) Routes.Home else Routes.Login

                    LaunchedEffect(Unit) {
                        sessionEvents.unauthorized.collect {
                            nav.navigate(Routes.Login) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        Surface(
                            Modifier
                                .fillMaxSize()
                                .background(Gate8Colors.Background),
                        ) {
                            NavHost(navController = nav, startDestination = start) {
                                composable(Routes.Login) {
                                    LoginScreen(
                                        onHome = {
                                            nav.navigate(Routes.Home) {
                                                popUpTo(Routes.Login) { inclusive = true }
                                            }
                                        },
                                        onPending = { nav.navigate(Routes.LoginPending) },
                                    )
                                }
                                composable(Routes.LoginPending) {
                                    LoginPendingScreen(
                                        onBackToLogin = { nav.popBackStack() },
                                        onHome = {
                                            nav.navigate(Routes.Home) {
                                                popUpTo(Routes.Login) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable(Routes.Home) {
                                    HomeScreen(
                                        onPdv = { nav.navigate(Routes.Pdv) },
                                        onProducts = { nav.navigate(Routes.Products) },
                                        onCashier = { nav.navigate(Routes.Cashier) },
                                        onCashless = { nav.navigate(Routes.Cashless) },
                                        onRefund = { nav.navigate(Routes.Refund) },
                                        onReports = { nav.navigate(Routes.Reports) },
                                        onSetup = { nav.navigate(Routes.Setup) },
                                    )
                                }
                                composable(Routes.Setup) {
                                    SetupScreen(
                                        onDone = { nav.popBackStack() },
                                        onLogout = {
                                            nav.navigate(Routes.Login) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable(Routes.Pdv) { PdvScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Products) { ProductsScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Checkin) { CheckinScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Refund) { RefundScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Reports) { ReportsScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Cashier) { CashierScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Cashless) { CashlessScreen(onBack = { nav.popBackStack() }) }
                                composable(Routes.Pending) { PendingScreen(onBack = { nav.popBackStack() }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
