package com.tailorsfit.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.tailorsfit.app.ui.screens.CatalogScreen
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
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home() {
        show { HomeScreen(onCategory = {}, onCustomers = {}) }
        save("01-home")
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
