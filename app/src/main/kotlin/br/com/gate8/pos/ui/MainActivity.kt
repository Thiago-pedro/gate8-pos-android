package br.com.gate8.pos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.gate8.pos.ui.checkin.CheckinScreen
import br.com.gate8.pos.ui.config.SetupScreen
import br.com.gate8.pos.ui.config.SetupViewModel
import br.com.gate8.pos.ui.home.HomeScreen
import br.com.gate8.pos.ui.navigation.Routes
import br.com.gate8.pos.ui.pending.PendingScreen
import br.com.gate8.pos.ui.pdv.PdvScreen
import br.com.gate8.pos.ui.products.ProductsScreen
import br.com.gate8.pos.ui.theme.Gate8Theme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gate8Theme {
                val nav = rememberNavController()
                val setupVm: SetupViewModel = koinViewModel()
                val start = if (setupVm.isConfigured()) Routes.Home else Routes.Setup

                Surface(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = start) {
                        composable(Routes.Setup) {
                            SetupScreen(onDone = {
                                nav.navigate(Routes.Home) {
                                    popUpTo(Routes.Setup) { inclusive = true }
                                }
                            })
                        }
                        composable(Routes.Home) {
                            HomeScreen(
                                onPdv = { nav.navigate(Routes.Pdv) },
                                onProducts = { nav.navigate(Routes.Products) },
                                onCheckin = { nav.navigate(Routes.Checkin) },
                                onPending = { nav.navigate(Routes.Pending) },
                                onSetup = { nav.navigate(Routes.Setup) },
                            )
                        }
                        composable(Routes.Pdv) { PdvScreen(onBack = { nav.popBackStack() }) }
                        composable(Routes.Products) { ProductsScreen(onBack = { nav.popBackStack() }) }
                        composable(Routes.Checkin) { CheckinScreen(onBack = { nav.popBackStack() }) }
                        composable(Routes.Pending) { PendingScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }
}
