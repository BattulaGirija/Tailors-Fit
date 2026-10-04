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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tailorsfit.app.ui.components.LanguageDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tailorsfit.app.ui.components.AppDrawerSheet
import com.tailorsfit.app.ui.components.DrawerDestination
import androidx.compose.runtime.remember
import com.tailorsfit.app.ui.screens.AboutScreen
import com.tailorsfit.app.ui.screens.AdminHomeScreen
import com.tailorsfit.app.ui.screens.AdminLoginScreen
import com.tailorsfit.app.ui.screens.AdminTailorScreen
import com.tailorsfit.app.ui.screens.AuthScreen
import com.tailorsfit.app.ui.screens.ForgotPinScreen
import com.tailorsfit.app.ui.screens.CustomizeScreen
import com.tailorsfit.app.ui.screens.CutPatternsScreen
import com.tailorsfit.app.ui.screens.DesignEditorScreen
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
    const val AUTH = "auth"
    const val ADMIN_LOGIN = "admin_login"
    /** "Design your own" starts the customise screen from a plain blouse. */
    const val NEW_DESIGN = "new"
    const val FORGOT = "forgot?login={login}"
    fun forgot(login: String) = "forgot?login=${android.net.Uri.encode(login)}"
    const val ADMIN = "admin"
    const val ADMIN_NEW_DESIGN = "admin/design/new"
    fun adminDesign(id: String) = "admin/design/$id"
    fun adminTailor(id: String) = "admin/tailor/$id"
    fun catalog(categoryId: String) = "catalog/$categoryId"
    fun customize(modelId: String) = "customize/$modelId"
    fun cut(modelId: String) = "cut/$modelId"
    fun measure(modelId: String) = "measure/$modelId"
    fun pattern(modelId: String) = "pattern/$modelId"
    fun projector(modelId: String) = "projector/$modelId"
}

@Composable
fun TailorsFitApp(startRoute: String? = null, customer: String? = null) {
    val vm: AppViewModel = viewModel()
    // Changing the language rebuilds every screen so all texts are looked up again.
    key(vm.language) { AppContent(vm, startRoute, customer) }
}

@Composable
private fun AppContent(vm: AppViewModel, startRoute: String?, customer: String?) {
    val nav = rememberNavController()
    var showLanguage by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!customer.isNullOrBlank()) vm.customerName = customer
        if (!startRoute.isNullOrBlank()) runCatching { nav.navigate(startRoute) }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val modelArg = listOf(navArgument("modelId") { type = NavType.StringType })
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    fun toAuth() = nav.navigate(Routes.AUTH) { popUpTo(nav.graph.id) { inclusive = true } }
    fun toHome() = nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
    fun toAdmin() = nav.navigate(Routes.ADMIN) { popUpTo(nav.graph.id) { inclusive = true } }

    fun go(d: DrawerDestination) {
        scope.launch { drawer.close() }
        when (d) {
            DrawerDestination.LOG_OUT -> {
                vm.logOut()
                toAuth()
            }
            DrawerDestination.HOME -> nav.popBackStack(Routes.HOME, inclusive = false)
            DrawerDestination.DESIGNS -> nav.navigate(Routes.catalog("blouse"))
            DrawerDestination.CUSTOMERS -> nav.navigate(Routes.CUSTOMERS)
            DrawerDestination.GUIDE -> nav.navigate(Routes.GUIDE)
            DrawerDestination.ABOUT -> nav.navigate(Routes.ABOUT)
            DrawerDestination.LANGUAGE -> showLanguage = true
        }
    }

    if (showLanguage) {
        LanguageDialog(
            current = vm.language,
            onPick = { showLanguage = false; vm.changeLanguage(it) },
            onDismiss = { showLanguage = false },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = route == Routes.HOME || drawer.isOpen,
        drawerContent = {
            AppDrawerSheet(
                selected = DrawerDestination.HOME.takeIf { route == Routes.HOME },
                onSelect = ::go,
                userName = vm.currentUser?.name,
                shopName = vm.currentUser?.shopName,
            )
        },
    ) {
        val start = remember { if (vm.currentUser != null) Routes.HOME else Routes.AUTH }
        NavHost(navController = nav, startDestination = start) {
            composable(Routes.AUTH) {
                AuthScreen(
                    vm,
                    onLoggedIn = ::toHome,
                    onAdmin = { nav.navigate(Routes.ADMIN_LOGIN) },
                    onForgot = { nav.navigate(Routes.forgot(it)) },
                    onLanguage = vm::changeLanguage,
                )
            }
            composable(Routes.FORGOT, listOf(navArgument("login") { type = NavType.StringType; defaultValue = "" })) {
                ForgotPinScreen(vm, it.arguments?.getString("login") ?: "", onSent = ::toAuth, onBack = back)
            }
            composable(Routes.ADMIN_LOGIN) {
                AdminLoginScreen(vm, onLoggedIn = ::toAdmin, onBack = back)
            }
            composable(Routes.ADMIN) {
                if (!vm.isAdmin) {
                    LaunchedEffect(Unit) { toAuth() }
                } else {
                    AdminHomeScreen(
                        vm,
                        onTailor = { nav.navigate(Routes.adminTailor(it)) },
                        onNewDesign = { nav.navigate(Routes.ADMIN_NEW_DESIGN) },
                        onEditDesign = { nav.navigate(Routes.adminDesign(it)) },
                        onLogOut = ::toAuth,
                    )
                }
            }
            composable(Routes.ADMIN_NEW_DESIGN) {
                if (vm.isAdmin) DesignEditorScreen(vm, designId = null, onDone = back)
            }
            composable("admin/design/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                if (vm.isAdmin) DesignEditorScreen(vm, designId = entry.arguments?.getString("id"), onDone = back)
            }
            composable("admin/tailor/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                if (vm.isAdmin) AdminTailorScreen(vm, id, onBack = back)
            }
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onMenu = { scope.launch { drawer.open() } },
                    onCategory = { nav.navigate(Routes.catalog(it)) },
                    onModel = { nav.navigate(Routes.customize(it)) },
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
                    onModel = { nav.navigate(Routes.customize(it)) },
                    onDesignOwn = { nav.navigate(Routes.customize(Routes.NEW_DESIGN)) },
                )
            }
            composable("customize/{modelId}", modelArg) { entry ->
                val modelId = entry.arguments?.getString("modelId") ?: return@composable
                CustomizeScreen(vm, modelId, onBack = back, onNext = { nav.navigate(Routes.measure(it)) })
            }
            composable("measure/{modelId}", modelArg) { entry ->
                val modelId = entry.arguments?.getString("modelId") ?: return@composable
                MeasurementScreen(
                    vm = vm, modelId = modelId, onBack = back,
                    onGenerate = { nav.navigate(Routes.pattern(modelId)) },
                    onGuide = { nav.navigate(Routes.GUIDE) },
                )
            }
            composable("pattern/{modelId}", modelArg) { entry ->
                val modelId = entry.arguments?.getString("modelId") ?: return@composable
                PatternScreen(vm = vm, modelId = modelId, onBack = back, onCut = { nav.navigate(Routes.cut(modelId)) })
            }
            composable("cut/{modelId}", modelArg) { entry ->
                val modelId = entry.arguments?.getString("modelId") ?: return@composable
                CutPatternsScreen(vm = vm, modelId = modelId, onBack = back, onProject = { nav.navigate(Routes.projector(modelId)) })
            }
            composable("projector/{modelId}", modelArg) { entry ->
                val modelId = entry.arguments?.getString("modelId") ?: return@composable
                ProjectorScreen(vm = vm, modelId = modelId, onBack = back)
            }
        }
    }
}
