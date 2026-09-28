package com.tailorsfit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tailorsfit.app.ui.screens.CatalogScreen
import com.tailorsfit.app.ui.screens.CustomersScreen
import com.tailorsfit.app.ui.screens.HomeScreen
import com.tailorsfit.app.ui.screens.MeasurementScreen
import com.tailorsfit.app.ui.screens.PatternScreen
import com.tailorsfit.app.ui.screens.ProjectorScreen
import com.tailorsfit.app.ui.theme.TailorsFitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TailorsFitTheme {
                TailorsFitApp()
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val CUSTOMERS = "customers"
    fun catalog(categoryId: String) = "catalog/$categoryId"
    fun measure(modelId: String) = "measure/$modelId"
    fun pattern(modelId: String) = "pattern/$modelId"
    fun projector(modelId: String) = "projector/$modelId"
}

@Composable
fun TailorsFitApp() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val back: () -> Unit = { nav.popBackStack() }
    val modelArg = listOf(navArgument("modelId") { type = NavType.StringType })

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onCategory = { nav.navigate(Routes.catalog(it)) },
                onCustomers = { nav.navigate(Routes.CUSTOMERS) },
            )
        }
        composable(Routes.CUSTOMERS) {
            CustomersScreen(
                vm = vm,
                onBack = back,
                onOpen = { customer ->
                    vm.selectCustomer(customer)
                    nav.navigate(Routes.catalog("blouse"))
                },
            )
        }
        composable("catalog/{categoryId}", listOf(navArgument("categoryId") { type = NavType.StringType })) { entry ->
            CatalogScreen(
                categoryId = entry.arguments?.getString("categoryId") ?: "blouse",
                onBack = back,
                onModel = { nav.navigate(Routes.measure(it)) },
            )
        }
        composable("measure/{modelId}", modelArg) { entry ->
            val modelId = entry.arguments?.getString("modelId") ?: return@composable
            MeasurementScreen(vm = vm, modelId = modelId, onBack = back, onGenerate = { nav.navigate(Routes.pattern(modelId)) })
        }
        composable("pattern/{modelId}", modelArg) { entry ->
            val modelId = entry.arguments?.getString("modelId") ?: return@composable
            PatternScreen(vm = vm, modelId = modelId, onBack = back, onProject = { nav.navigate(Routes.projector(modelId)) })
        }
        composable("projector/{modelId}", modelArg) { entry ->
            val modelId = entry.arguments?.getString("modelId") ?: return@composable
            ProjectorScreen(vm = vm, modelId = modelId, onBack = back)
        }
    }
}
