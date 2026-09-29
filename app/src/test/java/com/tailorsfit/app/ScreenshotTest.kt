package com.tailorsfit.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.tailorsfit.app.ui.components.AppDrawerSheet
import com.tailorsfit.app.ui.components.DrawerDestination
import com.tailorsfit.app.ui.screens.CatalogScreen
import com.tailorsfit.app.ui.screens.MeasurementGuideScreen
import com.tailorsfit.app.ui.screens.CustomersScreen
import com.tailorsfit.app.ui.screens.HomeScreen
import com.tailorsfit.app.ui.screens.MeasurementScreen
import com.tailorsfit.app.ui.screens.PatternScreen
import com.tailorsfit.app.ui.screens.ProjectorScreen
import com.tailorsfit.app.ui.theme.TailorsFitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each screen and saves a PNG to app/build/screenshots. Also a smoke test: a screen
 * that throws while composing fails the build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val model = "blouse_round_classic"

    private fun vm() = AppViewModel(ApplicationProvider.getApplicationContext()).apply { customerName = "Lakshmi" }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TailorsFitTheme { content() } }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        // Draw the window's view hierarchy directly; captureToImage() waits for a frame
        // callback that Robolectric never delivers.
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val root = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun homeContent(vm: AppViewModel): @Composable () -> Unit = {
        HomeScreen(vm, onMenu = {}, onCategory = {}, onModel = {}, onCustomers = {}, onCustomer = {})
    }

    @Test
    fun home() {
        val vm = vm()
        vm.saveCustomer()
        show(homeContent(vm))
        save("01-home")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Featured designs"))
        save("01b-home-collections")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Measure twice, cut once."))
        save("01c-home-bottom")
    }

    @Test
    fun drawer() {
        val vm = vm()
        show {
            ModalNavigationDrawer(
                drawerState = rememberDrawerState(DrawerValue.Open),
                drawerContent = { AppDrawerSheet(selected = DrawerDestination.HOME, onSelect = {}) },
            ) { homeContent(vm)() }
        }
        save("01d-side-menu")
    }

    @Test
    fun guide() {
        show { MeasurementGuideScreen(onBack = {}) }
        save("10-measuring-guide")
    }

    @Test
    fun catalog() {
        show { CatalogScreen("blouse", onBack = {}, onModel = {}) }
        save("02-catalog")
    }

    @Test
    fun measurements() {
        val vm = vm()
        show { MeasurementScreen(vm, model, onBack = {}, onGenerate = {}) }
        save("03-measurements")
        compose.onNodeWithText("Generate pattern").performScrollTo()
        save("04-measurements-bottom")
    }

    @Test
    fun pattern() {
        val vm = vm()
        show { PatternScreen(vm, model, onBack = {}, onProject = {}) }
        save("05-pattern")
        compose.onNodeWithText("Details").performScrollTo()
        save("06-pattern-details")
    }

    @Test
    fun patternSweetheart() {
        val vm = vm()
        show { PatternScreen(vm, "blouse_sweetheart", onBack = {}, onProject = {}) }
        save("07-pattern-sweetheart")
    }

    @Test
    fun patternPrincess() {
        val vm = vm()
        show { PatternScreen(vm, "blouse_princess_round", onBack = {}, onProject = {}) }
        save("07b-pattern-princess")
    }

    @Test
    fun catalogPrincess() {
        show { CatalogScreen("blouse", onBack = {}, onModel = {}) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Princess Cut, Round Neck"))
        save("02b-catalog-princess")
    }

    @Test
    fun projector() {
        val vm = vm()
        vm.projectorPan = com.tailorsfit.pattern.geom.Pt(0.0, 2.0)
        show { ProjectorScreen(vm, model, onBack = {}) }
        save("08-projector")
    }

    @Test
    fun customers() {
        val vm = vm()
        vm.saveCustomer()
        show { CustomersScreen(vm, onBack = {}, onOpen = {}) }
        save("09-customers")
    }
}
