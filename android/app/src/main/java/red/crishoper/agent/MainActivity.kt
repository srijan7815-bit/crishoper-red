package red.crishoper.agent

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import red.crishoper.agent.ui.ChatScreen
import red.crishoper.agent.ui.Dot
import red.crishoper.agent.ui.MonoLabel
import red.crishoper.agent.ui.OnboardingScreen
import red.crishoper.agent.ui.OperationsScreen
import red.crishoper.agent.ui.SettingsScreen
import red.crishoper.agent.ui.TerminalScreen
import red.crishoper.agent.ui.theme.Haptics
import red.crishoper.agent.ui.theme.LocalHaptics
import red.crishoper.agent.ui.theme.LocalMono
import red.crishoper.agent.ui.theme.MonoType
import red.crishoper.agent.ui.theme.RedTheme

class RedApp : Application()

class MainActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // One system splash per cold start. There is deliberately no in-app
        // splash screen to replay on resume — that was the old bug.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()

        setContent {
            val vm: RedViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val paper = ui.settings.appearance.theme == "paper"

            RedTheme(paper = paper) {
                val hapticsOn = ui.settings.appearance.haptics
                val haptics = remember(hapticsOn) {
                    Haptics { ms ->
                        if (hapticsOn) {
                            runCatching {
                                vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    Root(vm, ui)
                }
            }
        }
    }
}

@Composable
private fun Root(vm: RedViewModel, ui: UiState) {
    val c = LocalMono.current
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        if (!ui.ready) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                OnboardingScreen(
                    initialName = ui.settings.profile.name,
                    onSaveKey = { k, v -> vm.setSecret(k, v) },
                    onTestKey = { vm.testKey(it) },
                    onDone = { name, keys, demo -> vm.finishOnboarding(name, keys, demo) },
                )
            }
        } else {
            AppScaffold(vm, ui)
        }
        Toast(
            message = ui.toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (ui.ready) 92.dp else 22.dp)
                .padding(horizontal = 22.dp),
        )
    }
}

private fun routeOf(ui: UiState, openOp: Boolean): String = when {
    ui.screen == Screen.Settings -> "settings"
    ui.screen == Screen.Terminal -> "console"
    openOp -> "chat"
    else -> "ops"
}

@Composable
private fun AppScaffold(vm: RedViewModel, ui: UiState) {
    val uriHandler = LocalUriHandler.current
    var openOp by rememberSaveable { mutableStateOf(false) }
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1100); intro = false }

    BackHandler(enabled = openOp || ui.screen != Screen.Chats) {
        if (openOp) openOp = false else vm.back()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = routeOf(ui, openOp),
            transitionSpec = { fadeIn(tween(210)) togetherWith fadeOut(tween(140)) },
            label = "route",
            modifier = Modifier.weight(1f),
        ) { route ->
            when (route) {
                "ops" -> OperationsScreen(
                    conversations = ui.conversations,
                    activeId = ui.activeId,
                    streaming = ui.streaming,
                    userName = ui.settings.profile.name.ifBlank { "Operator" },
                    model = ui.settings.model.id,
                    intro = intro,
                    onOpen = { id -> vm.open(id); openOp = true },
                    onNew = { vm.newConversation(); openOp = true },
                    onDelete = { vm.delete(it) },
                )
                "chat" -> ChatScreen(
                    conversation = ui.active,
                    streaming = ui.streaming,
                    model = ui.settings.model.id,
                    onBack = { openOp = false },
                    onSend = { vm.send(it) },
                    onStop = { vm.stop() },
                    onRetry = { vm.retry(it) },
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                    onDelete = { vm.delete(ui.activeId ?: ""); openOp = false },
                )
                "console" -> TerminalScreen(
                    domain = "sandbox",
                    status = ui.termStatus,
                    lines = ui.termLines,
                    busy = ui.termBusy,
                    onBack = { vm.go(Screen.Chats) },
                    onRun = { vm.runCommand(it) },
                    onNewSandbox = { vm.killSandbox() },
                    onClear = { vm.clearTerminal() },
                )
                else -> SettingsScreen(
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
            }
        }
        Dock(current = ui.screen) { screen ->
            openOp = false
            vm.go(screen)
        }
    }
}

@Composable
private fun Dock(current: Screen, onSelect: (Screen) -> Unit) {
    val c = LocalMono.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(CircleShape)
                .background(c.surface)
                .border(1.dp, c.hairline, CircleShape)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockItem("operations", Icons.Rounded.Layers, current == Screen.Chats) { onSelect(Screen.Chats) }
            DockItem("console", Icons.Rounded.Terminal, current == Screen.Terminal) { onSelect(Screen.Terminal) }
            DockItem("settings", Icons.Rounded.Settings, current == Screen.Settings) { onSelect(Screen.Settings) }
        }
    }
}

@Composable
private fun RowScope.DockItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalMono.current
    val h = LocalHaptics.current
    val fill by animateFloatAsState(if (selected) 1f else 0f, tween(230), label = "dock")
    val fg by animateColorAsState(if (selected) c.inverseInk else c.muted, tween(180), label = "dockf")

    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable { h.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(c.solid.copy(alpha = fill)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            MonoLabel(label, color = fg, size = 8.sp)
        }
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier = Modifier) {
    val c = LocalMono.current
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 3 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.surfaceHi)
                .border(1.dp, c.hairlineStrong, CircleShape)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(c.ink, 5.dp)
            Spacer(Modifier.width(10.dp))
            Text(last, style = MonoType.bodySm, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
