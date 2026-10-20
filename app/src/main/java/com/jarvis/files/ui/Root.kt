package com.jarvis.files.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.files.core.Prefs
import com.jarvis.files.core.Storage
import com.jarvis.files.ui.components.ArcReactor
import com.jarvis.files.ui.components.GhostButton
import com.jarvis.files.ui.components.GlassCard
import com.jarvis.files.ui.components.GradientButton
import com.jarvis.files.ui.screens.AutomationsScreen
import com.jarvis.files.ui.screens.BatteryScreen
import com.jarvis.files.ui.screens.ChatScreen
import com.jarvis.files.ui.screens.CleanScreen
import com.jarvis.files.ui.screens.FindScreen
import com.jarvis.files.ui.screens.HomeScreen
import com.jarvis.files.ui.screens.MoreScreen
import com.jarvis.files.ui.screens.PeopleScreen
import com.jarvis.files.ui.screens.PhotosScreen
import com.jarvis.files.ui.screens.SecurityScreen
import com.jarvis.files.ui.screens.SettingsScreen
import com.jarvis.files.ui.screens.TrashScreen
import com.jarvis.files.ui.screens.VaultScreen
import com.jarvis.files.ui.theme.J

@Composable
fun JarvisRoot(vm: MainViewModel, locked: Boolean, onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    var hasAccess by remember { mutableStateOf(Storage.hasAllFilesAccess()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasAccess = Storage.hasAllFilesAccess()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    var onboarded by remember { mutableStateOf(Prefs(ctx).onboardingDone) }
    Box(Modifier.fillMaxSize().background(J.background)) {
        when {
            locked -> LockScreen(onUnlock)
            !hasAccess || !onboarded -> Onboarding(hasAccess) {
                Prefs(ctx).onboardingDone = true
                onboarded = true
                hasAccess = Storage.hasAllFilesAccess()
                vm.rescan()
            }
            else -> MainShell(vm)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ArcReactor(220.dp)
        Spacer(Modifier.height(32.dp))
        Text("Jarvis Files", style = MaterialTheme.typography.headlineMedium, color = J.Text)
        Spacer(Modifier.height(6.dp))
        Text("Identificazione richiesta, signore.", style = MaterialTheme.typography.bodyLarge, color = J.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        GradientButton("Sblocca", icon = Icons.Filled.Fingerprint, onClick = onUnlock)
    }
}

@Composable
private fun Onboarding(hasAccess: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ArcReactor(170.dp)
        Spacer(Modifier.height(20.dp))
        Text("Buongiorno, signore.", style = MaterialTheme.typography.headlineMedium, color = J.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sono Jarvis. Per mettere in ordine il telefono mi servono un paio di permessi. I file non lasciano il telefono, a meno che tu non scelga l'AI su internet.",
            color = J.Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GlassCard(Modifier.fillMaxWidth(), highlight = !hasAccess) {
            Text("1 · Accesso a tutti i file", style = MaterialTheme.typography.titleMedium, color = J.Text)
            Text("Per leggere, spostare, rinominare e cancellare i file in tutte le cartelle.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            if (hasAccess) Text("✓ Concesso", color = J.Green)
            else GradientButton("Concedi") {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                runCatching { ctx.startActivity(i) }.onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("2 · Notifiche e foto", style = MaterialTheme.typography.titleMedium, color = J.Text)
            Text("Per avvisi, resoconti e per leggere la posizione delle foto.", color = J.Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            GhostButton("Consenti") {
                notif.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION))
            }
        }
        Spacer(Modifier.height(24.dp))
        GradientButton("Iniziamo", Modifier.fillMaxWidth(), enabled = hasAccess, onClick = onDone)
    }
}

@Composable
private fun MainShell(vm: MainViewModel) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let { snack.showSnackbar(it); vm.toast = null }
    }
    BackHandler(enabled = !vm.voiceOpen && (vm.sub != Sub.NONE || vm.tab != Tab.HOME)) {
        if (vm.sub != Sub.NONE) vm.sub = Sub.NONE else vm.tab = Tab.HOME
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = { JarvisBottomBar(vm) },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                AnimatedContent(
                    vm.tab to vm.sub,
                    transitionSpec = {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.98f)) togetherWith fadeOut(tween(160))
                    },
                    label = "nav",
                ) { (tab, sub) ->
                    when (sub) {
                        Sub.SECURITY -> SecurityScreen(vm)
                        Sub.BATTERY -> BatteryScreen(vm)
                        Sub.VAULT -> VaultScreen(vm)
                        Sub.AUTOMATIONS -> AutomationsScreen(vm)
                        Sub.SETTINGS -> SettingsScreen(vm)
                        Sub.TRASH -> TrashScreen(vm)
                        Sub.PEOPLE -> PeopleScreen(vm)
                        Sub.FIND -> FindScreen(vm)
                        Sub.CATEGORY -> com.jarvis.files.ui.screens.CategoryScreen(vm)
                        Sub.NONE -> when (tab) {
                            Tab.HOME -> HomeScreen(vm)
                            Tab.FILES -> com.jarvis.files.ui.screens.FilesScreen(vm)
                            Tab.CHAT -> ChatScreen(vm)
                            Tab.PHOTOS -> PhotosScreen(vm)
                            Tab.CLEAN -> CleanScreen(vm)
                            Tab.MORE -> MoreScreen(vm)
                        }
                    }
                }
            }
        }
        if (vm.shared.isNotEmpty()) SharedFilesDialog(vm)
        androidx.compose.animation.AnimatedVisibility(vm.voiceOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            com.jarvis.files.ui.components.VoiceOverlay(
                onResult = { t -> vm.voiceOpen = false; vm.openChatWith(t) },
                onClose = { vm.voiceOpen = false },
            )
        }
    }
}

/** Barra in basso flottante: Home, File, Jarvis al centro (tocco = chat, pressione lunga = voce), Pulizia, Altro. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun JarvisBottomBar(vm: MainViewModel) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val current = when {
        vm.tab == Tab.PHOTOS -> Tab.FILES
        vm.sub == Sub.CATEGORY -> Tab.FILES
        else -> vm.tab
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().height(72.dp)
                .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x33000000), spotColor = Color(0x331E2A4A))
                .clip(RoundedCornerShape(30.dp)).background(J.Card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(vm, Tab.HOME, "Home", Icons.Filled.Home, current, Modifier.weight(1f))
            NavItem(vm, Tab.FILES, "File", Icons.Filled.Description, current, Modifier.weight(1f))
            Spacer(Modifier.weight(1.1f))
            NavItem(vm, Tab.CLEAN, "Pulizia", Icons.Filled.CleaningServices, current, Modifier.weight(1f))
            NavItem(vm, Tab.MORE, "Altro", Icons.Filled.GridView, current, Modifier.weight(1f))
        }
        // Jarvis al centro, rialzato
        Column(
            Modifier.padding(bottom = 6.dp)
                .combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                    onClick = { vm.tab = Tab.CHAT; vm.sub = Sub.NONE },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        vm.voiceOpen = true
                    },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(70.dp).shadow(14.dp, CircleShape, ambientColor = Color(0x55FF6A2B), spotColor = Color(0x55FF6A2B))
                    .clip(CircleShape).background(J.Card),
                contentAlignment = Alignment.Center,
            ) {
                com.jarvis.files.ui.components.ArcReactor(66.dp, active = current == Tab.CHAT || vm.busy)
            }
            Text("Jarvis", color = if (current == Tab.CHAT) J.Info else J.Text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NavItem(vm: MainViewModel, tab: Tab, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, current: Tab, modifier: Modifier) {
    val selected = current == tab
    val col = if (selected) J.Orange else J.Muted
    Column(
        modifier.fillMaxHeight().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
        ) { vm.tab = tab; vm.sub = Sub.NONE },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) J.Orange.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) { Icon(icon, label, tint = col, modifier = Modifier.size(24.dp)) }
        Text(label, color = col, style = MaterialTheme.typography.labelMedium)
    }
}

/** File condivisi da un'altra app: Jarvis chiede cosa farne. */
@Composable
private fun SharedFilesDialog(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uris = vm.shared
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.shared = emptyList() },
        containerColor = J.Card,
        title = { Text("${uris.size} file condivisi con Jarvis", color = J.Text) },
        text = { Text("Dove li metto?", color = J.Muted) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                vm.shared = emptyList()
                scope.launch {
                    val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uris.count { com.jarvis.files.core.Vault.importUri(ctx, it, deleteOriginal = false) } }
                    vm.toast = "$n file nella cartella segreta"
                }
            }) { Text("Cartella segreta") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                vm.shared = emptyList()
                scope.launch {
                    val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uris.count { com.jarvis.files.core.Vault.copyToDownload(ctx, it) } }
                    vm.toast = "$n file salvati in Download/Jarvis"
                    com.jarvis.files.core.FileIndex.invalidate()
                }
            }) { Text("Salva in Download") }
        },
    )
}
