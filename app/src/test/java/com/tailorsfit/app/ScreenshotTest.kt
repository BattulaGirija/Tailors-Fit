package com.tailorsfit.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import com.tailorsfit.app.ui.components.AppDrawerSheet
import com.tailorsfit.app.ui.components.DrawerDestination
import androidx.compose.ui.test.performClick
import com.tailorsfit.app.ui.screens.AdminHomeScreen
import com.tailorsfit.app.ui.screens.AdminLoginScreen
import com.tailorsfit.app.ui.screens.AuthScreen
import com.tailorsfit.app.ui.screens.ForgotPinScreen
import com.tailorsfit.app.ui.screens.CustomizeScreen
import com.tailorsfit.app.ui.screens.CutPatternsScreen
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import com.tailorsfit.app.ui.screens.AdminTailorScreen
import kotlinx.coroutines.runBlocking
import com.tailorsfit.app.ui.screens.DesignEditorScreen
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

    private val model = "blouse_deep_back_u"

    private fun vm(): AppViewModel {
        com.tailorsfit.app.data.Backends.forceLocal = true
        return AppViewModel(ApplicationProvider.getApplicationContext()).apply { customerName = "Lakshmi" }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TailorsFitTheme { content() } }
        compose.waitForIdle()
    }

    /** Waits until background work (drafting and nesting) has put [text] on screen. */
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
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
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Featured designs"))
        save("01b-home-collections")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Measure twice, cut once."))
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
    fun login() {
        val vm = vm()
        show { AuthScreen(vm, onLoggedIn = {}, onAdmin = {}) }
        save("00-login")
        compose.onNodeWithText("Sign up").performClick()
        save("00b-signup")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Create account"))
        save("00c-signup-pin")
    }

    @Test
    fun forgotPin() {
        val vm = vm()
        runBlocking { vm.signUp("Ravi Kumar", "Ravi Tailors", "9876543210", "ravi@tailors.in", "1234") }
        vm.logOut()
        show { ForgotPinScreen(vm, vm.lastLogin, onSent = {}, onBack = {}) }
        save("00d-forgot-pin")
    }

    @Test
    fun loginRemembersEmail() {
        val vm = vm()
        runBlocking { vm.signUp("Ravi Kumar", "Ravi Tailors", "9876543210", "ravi@tailors.in", "1234") }
        vm.logOut()
        vm.authNotice = com.tailorsfit.pattern.i18n.tr("forgot.sent", vm.lastLogin)
        show { AuthScreen(vm, onLoggedIn = {}, onAdmin = {}) }
        awaitText("ravi@tailors.in")
        save("00e-login-after-reset-link")
    }

    @Test
    fun admin() {
        val vm = vm()
        runBlocking { vm.signUp("Ravi Kumar", "Ravi Tailors", "9876543210", "ravi@tailors.in", "1234") }
        vm.saveCustomer()
        show { AdminLoginScreen(vm, onLoggedIn = {}, onBack = {}) }
        save("11-admin-login")
    }

    @Test
    fun adminHome() {
        val vm = vm()
        runBlocking {
            vm.signUp("Ravi Kumar", "Ravi Tailors", "9876543210", "ravi@tailors.in", "1234")
            vm.customerName = "Lakshmi"
            vm.saveCustomer()
            vm.customerName = "Padma"
            vm.saveCustomer()
            vm.signUp("Meena", "Meena Boutique", "", "meena@boutique.in", "1234")
            vm.adminLogIn("", "admin-pass")
        }
        show { AdminHomeScreen(vm, onTailor = {}, onNewDesign = {}, onEditDesign = {}, onLogOut = {}) }
        awaitText("Meena")
        save("12-admin-tailors")
        compose.onNode(hasText("Designs") and hasClickAction()).performClick()
        save("13-admin-designs")
    }

    @Test
    fun adminTailor() {
        val vm = vm()
        val id = runBlocking {
            vm.signUp("Ravi Kumar", "Ravi Tailors", "9876543210", "ravi@tailors.in", "1234")
            // Logging in starts a new, empty customer.
            vm.customerName = "Lakshmi"
            vm.saveCustomer()
            vm.adminLogIn("", "admin-pass")
            vm.tailors().first().id
        }
        show { AdminTailorScreen(vm, id, onBack = {}) }
        awaitText("Lakshmi")
        save("15-admin-tailor")
    }

    @Test
    fun customizeBlouse() {
        val vm = vm()
        show { CustomizeScreen(vm, "blouse_round_classic", onBack = {}, onNext = {}) }
        awaitText("Katori")
        save("40-customize-blouse")
        compose.onNodeWithText("Katori").performClick()
        compose.onNodeWithText("Sleeve").performClick()
        save("41-customize-sleeve")
    }

    @Test
    fun customizeNeck() {
        val vm = vm()
        show { CustomizeScreen(vm, "blouse_sabyasachi_square", onBack = {}, onNext = {}) }
        compose.onNodeWithText("Neck").performClick()
        save("42-customize-front-neck")
        compose.onNode(hasText("Back") and hasClickAction()).performClick()
        compose.onNodeWithText("Pot (matka)").performClick()
        save("43-customize-back-neck")
        // The chosen design opens like any other.
        val model = vm.model(vm.spec.toModel().id)!!
        assert(model.name.contains("Pot"))
    }

    @Test
    fun catalogTabs() {
        show { CatalogScreen("blouse", onBack = {}, onModel = {}) }
        save("44-catalog-all")
        compose.onNodeWithText("Katori").performClick()
        save("44b-catalog-katori")
        compose.onNodeWithText("All").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("boat")
        save("44c-catalog-search")
    }

    @Test
    fun cutPatterns() {
        val vm = vm()
        show { CutPatternsScreen(vm, "blouse_katori_sweetheart", onBack = {}, onProject = {}) }
        awaitText("Tap a piece")
        save("45-cut-patterns")
        compose.onNodeWithText("Cloth needed", substring = true).performScrollTo()
        save("45b-cut-cloth")
    }

    @Test
    fun measurementHelp() {
        val vm = vm()
        show { MeasurementScreen(vm, model, onBack = {}, onGenerate = {}) }
        compose.onAllNodes(hasContentDescription("How to measure")).onFirst().performScrollTo().performClick()
        save("46-measure-help")
    }

    @Test
    fun measurementAdjustments() {
        val vm = vm()
        show { MeasurementScreen(vm, model, onBack = {}, onGenerate = {}) }
        compose.onNodeWithText("Customization details").performScrollTo().performClick()
        compose.onNodeWithText("Hook dart distance").performScrollTo()
        save("47-measure-customization")
    }

    @Test
    fun designEditor() {
        val vm = vm()
        runBlocking { vm.adminLogIn("", "admin-pass") }
        show { DesignEditorScreen(vm, designId = null, onDone = {}) }
        save("14-admin-new-design")
    }

    @org.junit.After
    fun resetLanguage() {
        com.tailorsfit.pattern.i18n.I18n.language = com.tailorsfit.pattern.i18n.Language.EN
    }

    private fun trendyPreview(id: String, name: String) {
        val vm = vm()
        show { MeasurementScreen(vm, id, onBack = {}, onGenerate = {}) }
        save(name)
    }

    @Test fun trendyPuff() = trendyPreview("blouse_puff_sweetheart", "30-trendy-puff")

    @Test fun trendyKeyhole() = trendyPreview("blouse_keyhole_back", "31-trendy-keyhole")

    @Test fun trendyDori() = trendyPreview("blouse_dori_back", "32-trendy-dori")

    @Test fun trendyCollar() = trendyPreview("blouse_mandarin_collar", "33-trendy-collar")

    @Test
    fun trendyCatalog() {
        show { CatalogScreen("blouse", onBack = {}, onModel = {}) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paan (Leaf) Back"))
        save("34-catalog-trendy")
    }

    @Test
    fun telugu() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.TE)
        show { AuthScreen(vm, onLoggedIn = {}, onAdmin = {}) }
        save("20-te-login")
    }

    @Test
    fun teluguHome() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.TE)
        show(homeContent(vm))
        save("21-te-home")
    }

    @Test
    fun teluguMeasurements() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.TE)
        show { MeasurementScreen(vm, model, onBack = {}, onGenerate = {}) }
        compose.onNodeWithText("ఛాతీ (బస్ట్)").performScrollTo()
        save("22-te-measurements")
    }

    @Test
    fun teluguPattern() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.TE)
        show { PatternScreen(vm, model, onBack = {}, onCut = {}) }
        awaitText("జూమ్")
        save("23-te-pattern")
    }

    @Test
    fun hindiHome() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.HI)
        show(homeContent(vm))
        save("24-hi-home")
    }

    @Test
    fun hindiMeasurements() {
        val vm = vm()
        vm.changeLanguage(com.tailorsfit.pattern.i18n.Language.HI)
        show { MeasurementScreen(vm, model, onBack = {}, onGenerate = {}) }
        save("25-hi-measurements")
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
        compose.onNodeWithText("Gold buttis").performClick()
        compose.onNodeWithText("L (38)").performScrollTo().performClick()
        compose.onNodeWithText("Bust (chest round)").performScrollTo()
        save("03b-measurements-size-38")
        compose.onNodeWithText("Size L (38) selected", substring = true).assertExists()
        compose.onNodeWithText("Generate pattern").performScrollTo()
        save("04-measurements-bottom")
    }

    @Test
    fun pattern() {
        val vm = vm()
        show { PatternScreen(vm, model, onBack = {}, onCut = {}) }
        awaitText("Pinch to zoom")
        save("05-pattern")
        compose.onNodeWithText("Sleeve").performClick()
        save("06-pattern-sleeve")
        compose.onNodeWithText("Details").performScrollTo()
        save("06b-pattern-details")
    }

    @Test
    fun patternSweetheart() {
        val vm = vm()
        show { PatternScreen(vm, "blouse_sweetheart", onBack = {}, onCut = {}) }
        awaitText("Pinch to zoom")
        save("07-pattern-sweetheart")
    }

    @Test
    fun patternPrincess() {
        val vm = vm()
        show { PatternScreen(vm, "blouse_princess_round", onBack = {}, onCut = {}) }
        awaitText("Pinch to zoom")
        save("07b-pattern-princess")
    }

    @Test
    fun catalogPrincess() {
        show { CatalogScreen("blouse", onBack = {}, onModel = {}) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Princess Cut, Round Neck"))
        save("02b-catalog-princess")
    }

    @Test
    fun projector() {
        val vm = vm()
        vm.projectorPan = com.tailorsfit.pattern.geom.Pt(0.0, 2.0)
        show { ProjectorScreen(vm, model, onBack = {}) }
        awaitText("Line width")
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
