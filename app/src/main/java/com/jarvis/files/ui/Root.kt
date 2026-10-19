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
        ArcReactor(140.dp)
        Spacer(Modifier.height(28.dp))
        Text("JARVIS FILES", style = MaterialTheme.typography.labelSmall, color = J.Cyan)
        Spacer(Modifier.height(8.dp))
        Text("Identificazione richiesta, signore.", style = MaterialTheme.typography.titleLarge, color = J.Text, textAlign = TextAlign.Center)
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
        ArcReactor(110.dp)
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
    BackHandler(enabled = vm.sub != Sub.NONE || vm.tab != Tab.HOME) {
        if (vm.sub != Sub.NONE) vm.sub = Sub.NONE else vm.tab = Tab.HOME
    }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = J.Bg.copy(alpha = 0.96f), tonalElevation = 0.dp) {
                listOf(
                    Triple(Tab.HOME, "Home", Icons.Filled.Dashboard),
                    Triple(Tab.CHAT, "Jarvis", Icons.Filled.AutoAwesome),
                    Triple(Tab.PHOTOS, "Foto", Icons.Filled.PhotoLibrary),
                    Triple(Tab.CLEAN, "Pulizia", Icons.Filled.CleaningServices),
                    Triple(Tab.MORE, "Altro", Icons.Filled.GridView),
                ).forEach { (t, label, icon) ->
                    NavigationBarItem(
                        selected = vm.tab == t && vm.sub == Sub.NONE || (t == Tab.MORE && vm.sub != Sub.NONE && vm.tab == Tab.MORE),
                        onClick = { vm.tab = t; vm.sub = Sub.NONE },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = J.Electric, selectedTextColor = J.Electric, indicatorColor = J.CardHi,
                            unselectedIconColor = J.Muted, unselectedTextColor = J.Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(vm.tab to vm.sub, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { (tab, sub) ->
                when (sub) {
                    Sub.SECURITY -> SecurityScreen(vm)
                    Sub.BATTERY -> BatteryScreen(vm)
                    Sub.VAULT -> VaultScreen(vm)
                    Sub.AUTOMATIONS -> AutomationsScreen(vm)
                    Sub.SETTINGS -> SettingsScreen(vm)
                    Sub.TRASH -> TrashScreen(vm)
                    Sub.PEOPLE -> PeopleScreen(vm)
                    Sub.FIND -> FindScreen(vm)
                    Sub.NONE -> when (tab) {
                        Tab.HOME -> HomeScreen(vm)
                        Tab.CHAT -> ChatScreen(vm)
                        Tab.PHOTOS -> PhotosScreen(vm)
                        Tab.CLEAN -> CleanScreen(vm)
                        Tab.MORE -> MoreScreen(vm)
                    }
                }
            }
        }
    }
}
