package kr.dwas.dwas_EQ.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.dwas.dwas_EQ.BuildConfig
import kr.dwas.dwas_EQ.EqUiState
import kr.dwas.dwas_EQ.EqViewModel
import kr.dwas.dwas_EQ.R
import kr.dwas.dwas_EQ.safety.ExperimentalAppWarningPolicy
import kr.dwas.dwas_EQ.safety.HearingSafetyPolicy
import kr.dwas.dwas_EQ.ui.components.HearingSafetySheet
import kr.dwas.dwas_EQ.ui.motion.DwasMotionPolicy
import kr.dwas.dwas_EQ.ui.screens.DiagnosticsScreen
import kr.dwas.dwas_EQ.ui.screens.EffectsScreen
import kr.dwas.dwas_EQ.ui.screens.EqualizerScreen
import kr.dwas.dwas_EQ.ui.screens.ExperimentalScreen
import kr.dwas.dwas_EQ.ui.screens.HomeScreen
import kr.dwas.dwas_EQ.ui.screens.SettingsScreen
import kr.dwas.dwas_EQ.update.AvailableUpdate
import kr.dwas.dwas_EQ.update.GitHubUpdateChecker
import kr.dwas.dwas_EQ.update.UpdateNotification
import kotlinx.coroutines.launch

private enum class Screen(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    EQ(R.string.nav_equalizer, Icons.Rounded.Equalizer),
    EFFECTS(R.string.nav_effects, Icons.Rounded.SurroundSound),
    EXPERIMENTAL(R.string.nav_experimental, Icons.Rounded.Science),
    DIAGNOSTICS(R.string.nav_diagnostics, Icons.Rounded.Troubleshoot),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DwasEqApp(
    vm: EqViewModel = viewModel(),
    notificationPermissionGranted: Boolean = true,
    onDeclineHearingSafety: () -> Unit = {},
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var showHearingSafetyReview by rememberSaveable { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val visibleScreens = remember(state.settings.diagnosticsEnabled) {
        Screen.entries.filter { it != Screen.DIAGNOSTICS || state.settings.diagnosticsEnabled }
    }
    val initialSafetyRequired = HearingSafetyPolicy.shouldRequireConsent(
        notificationPermissionGranted = notificationPermissionGranted,
        preferencesReady = state.preferencesReady,
        acceptedVersion = state.settings.hearingSafetyAcceptedVersion,
    )

    val experimentalWarningRequired = ExperimentalAppWarningPolicy.shouldShow(
        preferencesReady = state.preferencesReady,
        acknowledged = state.settings.experimentalWarningAcknowledged,
    )

    LaunchedEffect(state.settings.diagnosticsEnabled, screen) {
        if (!state.settings.diagnosticsEnabled && screen == Screen.DIAGNOSTICS) screen = Screen.HOME
    }

    LaunchedEffect(Unit) {
        GitHubUpdateChecker.check(BuildConfig.VERSION_NAME)?.let { update ->
            availableUpdate = update
            UpdateNotification.show(context, update)
        }
    }

    val noUpdateMessage = stringResource(R.string.update_check_no_update, BuildConfig.VERSION_NAME)
    val checkForUpdates: () -> Unit = {
        scope.launch {
            val update = GitHubUpdateChecker.check(BuildConfig.VERSION_NAME)
            if (update != null) {
                availableUpdate = update
                updateCheckMessage = null
                UpdateNotification.show(context, update)
            } else {
                updateCheckMessage = noUpdateMessage
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${stringResource(R.string.app_brand_title)} · ${stringResource(screen.titleRes)}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 840.dp) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        visibleScreens.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.titleRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 840.dp) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.width(110.dp), containerColor = MaterialTheme.colorScheme.surface) {
                        visibleScreens.forEach { item ->
                            NavigationRailItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(28.dp)) },
                                label = {
                                    Text(
                                        stringResource(item.titleRes),
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.labelLarge,
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    AnimatedScreenContent(screen, state, vm, Modifier.weight(1f), { showHearingSafetyReview = true }, checkForUpdates)
                }
            } else {
                AnimatedScreenContent(screen, state, vm, Modifier.fillMaxSize(), { showHearingSafetyReview = true }, checkForUpdates)
            }
        }
    }

    if (initialSafetyRequired) {
        HearingSafetySheet(
            requireAgreement = true,
            onDecline = onDeclineHearingSafety,
            onAgree = { vm.acceptHearingSafety() },
            onClose = {},
        )
    } else if (experimentalWarningRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.experimental_warning_title)) },
            text = { Text(stringResource(R.string.experimental_warning_message)) },
            confirmButton = {
                TextButton(onClick = { vm.acceptExperimentalAppWarning() }) {
                    Text(stringResource(R.string.experimental_warning_confirm))
                }
            },
        )
    } else if (showHearingSafetyReview) {
        HearingSafetySheet(
            requireAgreement = false,
            onDecline = {},
            onAgree = {},
            onClose = { showHearingSafetyReview = false },
        )
    } else {
        availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = { availableUpdate = null },
                title = { Text(stringResource(R.string.update_dialog_title)) },
                text = { Text(stringResource(R.string.update_dialog_message, update.version.toString())) },
                confirmButton = {
                    TextButton(onClick = {
                        uriHandler.openUri(update.releaseUrl)
                        availableUpdate = null
                    }) { Text(stringResource(R.string.update_dialog_open_release)) }
                },
                dismissButton = {
                    TextButton(onClick = { availableUpdate = null }) { Text(stringResource(R.string.update_dialog_later)) }
                },
            )
        } ?: updateCheckMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { updateCheckMessage = null },
                title = { Text(stringResource(R.string.card_update_check)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { updateCheckMessage = null }) { Text(stringResource(R.string.update_check_dismiss)) }
                },
            )
        }
    }
}

@Composable
private fun AnimatedScreenContent(
    screen: Screen,
    state: EqUiState,
    vm: EqViewModel,
    modifier: Modifier,
    onReviewHearingSafety: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    AnimatedContent(
        targetState = screen,
        modifier = modifier,
        transitionSpec = {
            val enterFade = fadeIn(animationSpec = tween(DwasMotionPolicy.SCREEN_FADE_IN_MS))
            val exitFade = fadeOut(animationSpec = tween(DwasMotionPolicy.SCREEN_FADE_OUT_MS))
            if (targetState.ordinal >= initialState.ordinal) {
                (slideInHorizontally(
                    animationSpec = tween(DwasMotionPolicy.SCREEN_SLIDE_MS, easing = FastOutSlowInEasing),
                    initialOffsetX = { it / DwasMotionPolicy.SCREEN_SLIDE_DIVISOR },
                ) + enterFade) togetherWith (slideOutHorizontally(
                    animationSpec = tween(DwasMotionPolicy.SCREEN_SLIDE_MS, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it / DwasMotionPolicy.SCREEN_SLIDE_DIVISOR },
                ) + exitFade)
            } else {
                (slideInHorizontally(
                    animationSpec = tween(DwasMotionPolicy.SCREEN_SLIDE_MS, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it / DwasMotionPolicy.SCREEN_SLIDE_DIVISOR },
                ) + enterFade) togetherWith (slideOutHorizontally(
                    animationSpec = tween(DwasMotionPolicy.SCREEN_SLIDE_MS, easing = FastOutSlowInEasing),
                    targetOffsetX = { it / DwasMotionPolicy.SCREEN_SLIDE_DIVISOR },
                ) + exitFade)
            }
        },
        label = "dwas_screen_transition",
    ) { target ->
        ScreenContent(target, state, vm, Modifier.fillMaxSize(), onReviewHearingSafety, onCheckForUpdates)
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    state: EqUiState,
    vm: EqViewModel,
    modifier: Modifier,
    onReviewHearingSafety: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    when (screen) {
        Screen.HOME -> HomeScreen(state, vm, onReviewHearingSafety, modifier)
        Screen.EQ -> EqualizerScreen(state, vm, modifier)
        Screen.EFFECTS -> EffectsScreen(state, vm, modifier)
        Screen.EXPERIMENTAL -> ExperimentalScreen(state, vm, modifier)
        Screen.DIAGNOSTICS -> DiagnosticsScreen(state, vm, modifier)
        Screen.SETTINGS -> SettingsScreen(state, vm, modifier, onCheckForUpdates)
    }
}
