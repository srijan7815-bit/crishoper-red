package red.crishoper.agent

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import red.crishoper.agent.ui.ChatScreen
import red.crishoper.agent.ui.OnboardingScreen
import red.crishoper.agent.ui.OperationsDrawer
import red.crishoper.agent.ui.SettingsScreen
import red.crishoper.agent.ui.TerminalScreen
import red.crishoper.agent.ui.theme.LocalRedColors
import red.crishoper.agent.ui.theme.RedTheme

class RedApp : Application()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // A single, system-drawn splash. It is shown once per cold start and
        // never re-entered: there is no in-app splash loop to reproduce.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val vm: RedViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()

            RedTheme(usePaper = ui.settings.appearance.theme == "paper") {
                Surface(color = LocalRedColors.current.background, modifier = Modifier.fillMaxSize()) {
                    if (!ui.ready) {
                        OnboardingScreen(ui.settings.profile.name) { name, keys, demo ->
                            vm.finishOnboarding(name, keys, demo)
                            haptic()
                        }
                    } else {
                        AppScaffold(vm, ui)
                    }
                }
            }
        }
    }

    private fun haptic() {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

@Composable
private fun AppScaffold(vm: RedViewModel, ui: UiState) {
    val c = LocalRedColors.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            OperationsDrawer(
                conversations = ui.conversations,
                activeId = ui.activeId,
                userName = ui.settings.profile.name.ifBlank { "Operator" },
                demo = ui.settings.demo,
                onOpen = { vm.open(it); scope.launch { drawerState.close() } },
                onNew = { vm.newConversation(); scope.launch { drawerState.close() } },
                onDelete = { vm.delete(it) },
                onSettings = { scope.launch { drawerState.close() }; vm.go(Screen.Settings) },
                onTerminal = { scope.launch { drawerState.close() }; vm.go(Screen.Terminal) },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
            when (ui.screen) {
                Screen.Chats -> ChatScreen(
                    title = ui.active?.title ?: "New operation",
                    subtitle = "${ui.settings.model.id.substringAfterLast('/')} · ${ui.active?.messages?.size ?: 0} msgs",
                    messages = ui.active?.messages.orEmpty(),
                    streaming = ui.streaming,
                    model = ui.settings.model.id,
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = { vm.newConversation() },
                    onTerminal = { vm.go(Screen.Terminal) },
                    onSearch = { scope.launch { drawerState.open() } },
                    onMore = { vm.go(Screen.Settings) },
                    onSend = { vm.send(it) },
                    onStop = { vm.stop() },
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                    onRetry = { vm.retry(it) },
                    onUsePrompt = { vm.usePrompt(it) },
                )
                Screen.Settings -> SettingsScreen(
                    settings = ui.settings,
                    memory = ui.memory,
                    secretsSet = ui.secrets,
                    onBack = { vm.go(Screen.Chats) },
                    onPatch = { block -> vm.patchSettings(block) },
                    onSecret = { k, v -> vm.setSecret(k, v) },
                    onForget = { vm.forget(it) },
                    onClearMemory = { vm.clearMemory() },
                    onRemember = { vm.remember(it) },
                    onClearChats = { vm.clearConversations() },
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                    onToast = { vm.toast(it) },
                    testKey = { vm.testKey(it) },
                )
                Screen.Terminal -> TerminalScreen(
                    domain = "sandbox",
                    status = ui.termStatus,
                    lines = ui.termLines,
                    busy = ui.termBusy,
                    onBack = { vm.go(Screen.Chats) },
                    onRun = { vm.runCommand(it) },
                    onNewSandbox = { vm.killSandbox() },
                    onClear = { vm.clearTerminal() },
                )
            }

            ToastHost(ui.toast)
        }
    }
}

@Composable
private fun ToastHost(message: String?) {
    val c = LocalRedColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = c.surfaceAlt,
                border = androidx.compose.foundation.BorderStroke(1.dp, c.stroke),
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
    }
}
