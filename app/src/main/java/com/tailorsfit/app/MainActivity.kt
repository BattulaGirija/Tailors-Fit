package com.tailorsfit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tailorsfit.app.ui.components.AppDrawerSheet
import com.tailorsfit.app.ui.components.DrawerDestination
import com.tailorsfit.app.ui.screens.AboutScreen
import com.tailorsfit.app.ui.screens.MeasurementGuideScreen
import kotlinx.coroutines.launch
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
    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_CUSTOMER = "customer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Optional launch arguments, e.g. `adb shell am start ... --es route pattern/blouse_v`.
        val startRoute = intent?.getStringExtra(EXTRA_ROUTE)
        val customer = intent?.getStringExtra(EXTRA_CUSTOMER)
        setContent {
            TailorsFitTheme {
                TailorsFitApp(startRoute, customer)
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val CUSTOMERS = "customers"
    const val GUIDE = "guide"
    const val ABOUT = "about"
    fun catalog(categoryId: String) = "catalog/$categoryId"
    fun measure(modelId: String) = "measure/$modelId"
    fun pattern(modelId: String) = "pattern/$modelId"
    fun projector(modelId: String) = "projector/$modelId"
}

@Composable
fun TailorsFitApp(startRoute: String? = null, customer: String? = null) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!customer.isNullOrBlank()) vm.customerName = customer
        if (!startRoute.isNullOrBlank()) runCatching { nav.navigate(startRoute) }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val modelArg = listOf(navArgument("modelId") { type = NavType.StringType })
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    fun go(d: DrawerDestination) {
        scope.launch { drawer.close() }
        when (d) {
            DrawerDestination.HOME -> nav.popBackStack(Routes.HOME, inclusive = false)
            DrawerDestination.DESIGNS -> nav.navigate(Routes.catalog("blouse"))
            DrawerDestination.CUSTOMERS -> nav.navigate(Routes.CUSTOMERS)
            DrawerDestination.GUIDE -> nav.navigate(Routes.GUIDE)
            DrawerDestination.ABOUT -> nav.navigate(Routes.ABOUT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = route == Routes.HOME || drawer.isOpen,
        drawerContent = { AppDrawerSheet(selected = DrawerDestination.HOME.takeIf { route == Routes.HOME }, onSelect = ::go) },
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onMenu = { scope.launch { drawer.open() } },
                    onCategory = { nav.navigate(Routes.catalog(it)) },
                    onModel = { nav.navigate(Routes.measure(it)) },
                    onCustomers = { nav.navigate(Routes.CUSTOMERS) },
                    onCustomer = { c ->
                        vm.selectCustomer(c)
                        nav.navigate(Routes.catalog("blouse"))
                    },
                )
            }
            composable(Routes.GUIDE) { MeasurementGuideScreen(onBack = back) }
            composable(Routes.ABOUT) { AboutScreen(onBack = back) }
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
}
