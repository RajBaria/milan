package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AppColorTheme
import com.example.data.model.CallMode
import com.example.data.model.RandomMatchStatus
import com.example.data.repository.MilanRepository
import com.example.data.util.NotificationHelper
import com.example.ui.components.MilanAdMobManager
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminVerificationMenuScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatsAndConversationScreen
import com.example.ui.screens.DiscoverListScreen
import com.example.ui.screens.LikesScreen
import com.example.ui.screens.MyProfileAndBlockedScreen
import com.example.ui.screens.PreLoginLocationSnapshot
import com.example.ui.screens.PremiumSubscriptionScreen
import com.example.ui.screens.ProfileSetupWizardScreen
import com.example.ui.screens.RandomChatScreen
import com.example.ui.screens.fetchDeviceLocation
import com.example.ui.screens.performGoogleSignOut
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemePreferences
import com.example.ui.viewmodel.MilanViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannels(this)
        MilanAdMobManager.initializeIfSupported(this)
        enableEdgeToEdge()
        setContent {
            val appContext = LocalContext.current
            var isDarkMode by remember { mutableStateOf(ThemePreferences.loadDarkMode(appContext)) }
            var colorTheme by remember { mutableStateOf(ThemePreferences.loadColorTheme(appContext)) }

            MyApplicationTheme(
                darkTheme = isDarkMode,
                colorTheme = colorTheme
            ) {
                AppNavigation(
                    isDarkMode = isDarkMode,
                    colorTheme = colorTheme,
                    onToggleDarkMode = {
                        isDarkMode = !isDarkMode
                        ThemePreferences.saveDarkMode(appContext, isDarkMode)
                    },
                    onSelectColorTheme = { selected ->
                        colorTheme = selected
                        ThemePreferences.saveColorTheme(appContext, selected)
                    }
                )
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    isDarkMode: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.PINK,
    onToggleDarkMode: () -> Unit = {},
    onSelectColorTheme: (AppColorTheme) -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var preLoginLocation by remember { mutableStateOf<PreLoginLocationSnapshot?>(null) }

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {},
            onLocationCaptured = { snapshot -> preLoginLocation = snapshot }
        )
    } else {
        AuthenticatedMilanApp(
            currentUserId = user.uid,
            currentUserEmail = user.email,
            currentUserDisplayName = user.displayName,
            preLoginLocation = preLoginLocation,
            isDarkMode = isDarkMode,
            colorTheme = colorTheme,
            onToggleDarkMode = onToggleDarkMode,
            onSelectColorTheme = onSelectColorTheme
        )
    }
}

@Composable
fun AuthenticatedMilanApp(
    currentUserId: String,
    currentUserEmail: String? = null,
    currentUserDisplayName: String? = null,
    preLoginLocation: PreLoginLocationSnapshot? = null,
    isDarkMode: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.PINK,
    onToggleDarkMode: () -> Unit = {},
    onSelectColorTheme: (AppColorTheme) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: MilanViewModel = viewModel(
        key = "$currentUserId-${currentUserEmail.orEmpty()}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MilanViewModel(
                    repository = MilanRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation(context) { lat, lon, cityLabel ->
                viewModel.updateMyLocation(lat, lon, cityLabel, showBanner = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createNotificationChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.notificationEvents.collect { event ->
            if (event.isReward) {
                NotificationHelper.sendRewardNotification(context, event.title, event.body)
            } else {
                NotificationHelper.sendSocialNotification(context, event.title, event.body)
            }
        }
    }

    val myProfileUiState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val discoverProfiles by viewModel.discoverProfilesState.collectAsStateWithLifecycle()
    val allRegisteredProfiles by viewModel.allRegisteredProfilesState.collectAsStateWithLifecycle()
    val sentLikes by viewModel.sentLikesState.collectAsStateWithLifecycle()
    val receivedLikes by viewModel.receivedLikesState.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsersState.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessagesState.collectAsStateWithLifecycle()
    val conversations by viewModel.conversationsState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val discoverViewMode by viewModel.discoverViewMode.collectAsStateWithLifecycle()
    val lastLocationSyncStatus by viewModel.lastLocationSyncStatus.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val likeGatePromptTarget by viewModel.likeGatePromptTarget.collectAsStateWithLifecycle()
    val activeChatPartner by viewModel.activeChatPartner.collectAsStateWithLifecycle()
    val activeCallSession by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val partnerCallStream by viewModel.partnerCallStreamState.collectAsStateWithLifecycle()
    val incomingCallAlert by viewModel.incomingCallAlert.collectAsStateWithLifecycle()
    val savedNotifications by viewModel.savedNotifications.collectAsStateWithLifecycle()
    val sessionUsageSeconds by viewModel.sessionUsageSeconds.collectAsStateWithLifecycle()
    val randomChatPreferences by viewModel.randomChatPreferences.collectAsStateWithLifecycle()
    val randomMatchStatus by viewModel.randomMatchStatus.collectAsStateWithLifecycle()
    val activeRandomSession by viewModel.activeRandomSessionState.collectAsStateWithLifecycle()
    val activeRandomChats by viewModel.activeRandomChatSummariesState.collectAsStateWithLifecycle()
    val maskedRandomPartner by viewModel.maskedRandomPartnerState.collectAsStateWithLifecycle()
    val randomChatMessages by viewModel.randomChatMessagesState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setForegroundPresence(true)
                Lifecycle.Event.ON_STOP -> viewModel.setForegroundPresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setForegroundPresence(false)
        }
    }

    var currentTab by remember { mutableIntStateOf(0) }
    var isEditingProfile by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditingProfile || currentTab != 0) {
        if (isEditingProfile) {
            isEditingProfile = false
        } else if (activeChatPartner != null) {
            viewModel.closeChatScreen()
        } else if (currentTab == 7 && (randomMatchStatus == RandomMatchStatus.CONNECTED || randomMatchStatus == RandomMatchStatus.PARTNER_LEFT)) {
            viewModel.minimizeRandomChatView()
        } else {
            currentTab = 0
        }
    }

    // Real-time Incoming Audio Call Alert Dialog across all screens
    if (incomingCallAlert != null && activeCallSession == null) {
        val incoming = incomingCallAlert!!
        AlertDialog(
            onDismissRequest = { viewModel.declineIncomingCall() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Incoming Audio Call",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "${incoming.partner.fullName} (${incoming.partner.age} yrs) is calling you right now.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acceptIncomingCall {
                            currentTab = 2
                        }
                    },
                    modifier = Modifier.testTag("accept_incoming_call_button")
                ) {
                    Text("Answer Audio Call")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.declineIncomingCall() },
                    modifier = Modifier.testTag("decline_incoming_call_button")
                ) {
                    Text("Decline")
                }
            }
        )
    }

    when (val state = myProfileUiState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is UiState.Error -> {
            ProfileSetupWizardScreen(
                currentUserId = currentUserId,
                currentUserEmail = currentUserEmail,
                currentUserDisplayName = currentUserDisplayName,
                initialProfile = null,
                preLoginLocation = preLoginLocation,
                onSaveProfile = { profile ->
                    viewModel.saveUserProfile(profile)
                }
            )
        }

        is UiState.Success -> {
            val myProfile = state.data
            if (myProfile == null || isEditingProfile) {
                ProfileSetupWizardScreen(
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName,
                    initialProfile = myProfile,
                    preLoginLocation = preLoginLocation,
                    onSaveProfile = { updatedProfile ->
                        viewModel.saveUserProfile(updatedProfile) {
                            isEditingProfile = false
                        }
                    },
                    onCancelEdit = if (myProfile != null) {
                        { isEditingProfile = false }
                    } else {
                        null
                    }
                )
            } else {
                // Sync pre-login captured location right after login
                LaunchedEffect(myProfile.userId, preLoginLocation) {
                    if (preLoginLocation != null) {
                        viewModel.updateMyLocation(
                            preLoginLocation.latitude,
                            preLoginLocation.longitude,
                            preLoginLocation.locationName,
                            showBanner = false
                        )
                    }
                }

                // Automatic Hourly GPS Location Sync Loop
                LaunchedEffect(myProfile.userId) {
                    while (isActive) {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFine || hasCoarse) {
                            fetchDeviceLocation(context) { lat, lon, cityLabel ->
                                viewModel.updateMyLocation(lat, lon, cityLabel, showBanner = false)
                            }
                        }
                        delay(60L * 60L * 1000L) // Refresh location every 1 hour
                    }
                }

                LaunchedEffect(myProfile.userId, myProfile.isPremium) {
                    if (!myProfile.isPremium) {
                        MilanAdMobManager.loadAndMaybeShowAppOpenAd(context, isPremium = false)
                        MilanAdMobManager.preloadInterstitial(context, isPremium = false)
                    }
                }

                val showAdminTab = viewModel.isCurrentUserAdmin || myProfile.isAdminUser
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideTabletOrFoldable = maxWidth >= 600.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isWideTabletOrFoldable) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                windowInsets = NavigationRailDefaults.windowInsets,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                NavigationRailItem(
                                    selected = currentTab == 0,
                                    onClick = {
                                        viewModel.closeChatScreen()
                                        currentTab = 0
                                    },
                                    icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                                    label = { Text("Discover", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_discover")
                                )
                                NavigationRailItem(
                                    selected = currentTab == 1,
                                    onClick = {
                                        viewModel.closeChatScreen()
                                        MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                                        currentTab = 1
                                    },
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Likes") },
                                    label = { Text("Likes", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_likes")
                                )
                                NavigationRailItem(
                                    selected = currentTab == 7,
                                    onClick = {
                                        viewModel.closeChatScreen()
                                        currentTab = 7
                                    },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (activeRandomChats.isNotEmpty()) {
                                                    Badge { Text("${activeRandomChats.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = "Random Chat")
                                        }
                                    },
                                    label = { Text("Random", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_random")
                                )
                                NavigationRailItem(
                                    selected = currentTab == 2,
                                    onClick = {
                                        MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                                        currentTab = 2
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                    label = { Text("Chats", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_chats")
                                )
                                NavigationRailItem(
                                    selected = currentTab == 3,
                                    onClick = {
                                        viewModel.closeChatScreen()
                                        currentTab = 3
                                    },
                                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = "Premium") },
                                    label = { Text("Premium", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_premium")
                                )
                                NavigationRailItem(
                                    selected = currentTab == 4,
                                    onClick = {
                                        viewModel.closeChatScreen()
                                        currentTab = 4
                                    },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                    label = { Text("Profile", maxLines = 1) },
                                    modifier = Modifier.testTag("nav_profile")
                                )
                                if (showAdminTab) {
                                    NavigationRailItem(
                                        selected = currentTab == 6,
                                        onClick = {
                                            viewModel.closeChatScreen()
                                            currentTab = 6
                                        },
                                        icon = { Icon(Icons.Default.Verified, contentDescription = "Admin Verification Menu") },
                                        label = { Text("Verify", maxLines = 1) },
                                        modifier = Modifier.testTag("nav_admin_verify")
                                    )
                                    NavigationRailItem(
                                        selected = currentTab == 5,
                                        onClick = {
                                            viewModel.closeChatScreen()
                                            currentTab = 5
                                        },
                                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                                        label = { Text("Admin", maxLines = 1) },
                                        modifier = Modifier.testTag("nav_admin")
                                    )
                                }
                            }
                        }

                        Scaffold(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentWindowInsets = WindowInsets.safeDrawing,
                            bottomBar = {
                                if (!isWideTabletOrFoldable) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 4.dp,
                                        windowInsets = NavigationBarDefaults.windowInsets
                                    ) {
                                        val navLabelSize = if (showAdminTab) 9.sp else 10.sp
                                        NavigationBarItem(
                                            selected = currentTab == 0,
                                            onClick = {
                                                viewModel.closeChatScreen()
                                                currentTab = 0
                                            },
                                            icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                                            label = { Text("Discover", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_discover")
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 1,
                                            onClick = {
                                                viewModel.closeChatScreen()
                                                MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                                                currentTab = 1
                                            },
                                            icon = { Icon(Icons.Default.Favorite, contentDescription = "Likes") },
                                            label = { Text("Likes", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_likes")
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 7,
                                            onClick = {
                                                viewModel.closeChatScreen()
                                                currentTab = 7
                                            },
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        if (activeRandomChats.isNotEmpty()) {
                                                            Badge { Text("${activeRandomChats.size}") }
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Shuffle, contentDescription = "Random Chat")
                                                }
                                            },
                                            label = { Text("Random", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_random")
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 2,
                                            onClick = {
                                                MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                                                currentTab = 2
                                            },
                                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                            label = { Text("Chats", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_chats")
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 3,
                                            onClick = {
                                                viewModel.closeChatScreen()
                                                currentTab = 3
                                            },
                                            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = "Premium") },
                                            label = { Text("Premium", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_premium")
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 4,
                                            onClick = {
                                                viewModel.closeChatScreen()
                                                currentTab = 4
                                            },
                                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                            label = { Text("Profile", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                            alwaysShowLabel = true,
                                            modifier = Modifier.testTag("nav_profile")
                                        )
                                        if (showAdminTab) {
                                            NavigationBarItem(
                                                selected = currentTab == 6,
                                                onClick = {
                                                    viewModel.closeChatScreen()
                                                    currentTab = 6
                                                },
                                                icon = { Icon(Icons.Default.Verified, contentDescription = "Admin Verification Menu") },
                                                label = { Text("Verify", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                                alwaysShowLabel = true,
                                                modifier = Modifier.testTag("nav_admin_verify")
                                            )
                                            NavigationBarItem(
                                                selected = currentTab == 5,
                                                onClick = {
                                                    viewModel.closeChatScreen()
                                                    currentTab = 5
                                                },
                                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                                                label = { Text("Admin", fontSize = navLabelSize, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                                                alwaysShowLabel = true,
                                                modifier = Modifier.testTag("nav_admin")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                        AnimatedVisibility(visible = statusBanner != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = statusBanner ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    )
                                    TextButton(
                                        onClick = { viewModel.clearBanner() },
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    ) {
                                        Text("OK")
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (activeCallSession != null) {
                                ActiveCallOverlay(
                                    myProfile = myProfile,
                                    session = activeCallSession!!,
                                    partnerCallStream = partnerCallStream,
                                    onPublishMediaPacket = viewModel::publishCallMediaPacket,
                                    onToggleMute = viewModel::toggleCallMute,
                                    onToggleSpeaker = viewModel::toggleCallSpeaker,
                                    onToggleCamera = viewModel::toggleCallCamera,
                                    onSwitchCamera = viewModel::switchCallCamera,
                                    onEndCall = viewModel::endCall
                                )
                            } else {
                                when (currentTab) {
                                    0 -> DiscoverListScreen(
                                        myProfile = myProfile,
                                        profilesWithDistance = discoverProfiles,
                                        sentLikes = sentLikes,
                                        filterState = filterState,
                                        discoverViewMode = discoverViewMode,
                                        lastLocationSyncStatus = lastLocationSyncStatus,
                                        likeGatePromptTarget = likeGatePromptTarget,
                                        savedNotifications = savedNotifications,
                                        isDarkMode = isDarkMode,
                                        colorTheme = colorTheme,
                                        onToggleDarkMode = onToggleDarkMode,
                                        onSelectColorTheme = onSelectColorTheme,
                                        onMarkNotificationsRead = viewModel::markAllNotificationsRead,
                                        onClearNotifications = viewModel::clearSavedNotifications,
                                        onChangeViewMode = viewModel::setDiscoverViewMode,
                                        onRefreshLocation = {
                                            val hasFine = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            val hasCoarse = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasFine || hasCoarse) {
                                                fetchDeviceLocation(context) { lat, lon, cityLabel ->
                                                    viewModel.updateMyLocation(lat, lon, cityLabel, showBanner = true)
                                                }
                                            } else {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        },
                                        onUpdateFilters = viewModel::updateFilters,
                                        onResetFilters = viewModel::resetFilters,
                                        onToggleLike = viewModel::toggleLike,
                                        onMessageClick = { target ->
                                            viewModel.initiateChatWithUser(target) {
                                                currentTab = 2
                                            }
                                        },
                                        onStartCall = { target, _ ->
                                            viewModel.startCall(target, CallMode.AUDIO)
                                            currentTab = 2
                                        },
                                        onLikeAndOpenChat = { target ->
                                            viewModel.likeAndOpenChat(target) {
                                                currentTab = 2
                                            }
                                        },
                                        onDismissLikeGate = viewModel::dismissLikeGatePrompt,
                                        onNavigateToPremium = { currentTab = 3 },
                                        onBlockUser = { target ->
                                            viewModel.blockUser(target.userId, target.fullName)
                                        }
                                    )

                                    1 -> LikesScreen(
                                        sentLikes = sentLikes,
                                        receivedLikes = receivedLikes,
                                        discoverProfiles = discoverProfiles,
                                        onOpenChatWithUser = { target ->
                                            viewModel.initiateChatWithUser(target) {
                                                currentTab = 2
                                            }
                                        },
                                        onStartCall = { target, _ ->
                                            viewModel.startCall(target, CallMode.AUDIO)
                                            currentTab = 2
                                        },
                                        onRemoveLike = { toUid ->
                                            discoverProfiles.firstOrNull { it.first.userId == toUid }?.first?.let {
                                                viewModel.toggleLike(it)
                                            }
                                        }
                                    )

                                    2 -> ChatsAndConversationScreen(
                                        currentUserId = currentUserId,
                                        myProfile = myProfile,
                                        conversations = conversations,
                                        allMessages = allMessages,
                                        activeChatPartner = activeChatPartner,
                                        activeCallSession = activeCallSession,
                                        sentLikes = sentLikes,
                                        onSelectConversation = viewModel::openExistingConversation,
                                        onCloseActiveChat = viewModel::closeChatScreen,
                                        onSendMessage = viewModel::sendChatMessage,
                                        onSendPhoto = { partner, base64 ->
                                            viewModel.sendPhotoMessage(partner, base64)
                                        },
                                        onStartCall = { target, _ ->
                                            viewModel.startCall(target, CallMode.AUDIO)
                                        },
                                        onToggleCallMute = viewModel::toggleCallMute,
                                        onToggleCallSpeaker = viewModel::toggleCallSpeaker,
                                        onToggleCallCamera = viewModel::toggleCallCamera,
                                        onSwitchCallCamera = viewModel::switchCallCamera,
                                        onEndCall = viewModel::endCall,
                                        onBlockPartner = { partner ->
                                            viewModel.blockUser(partner.userId, partner.fullName)
                                        },
                                        onDeleteConversation = viewModel::deleteNormalConversation,
                                        onNavigateToPremium = { currentTab = 3 }
                                    )

                                    3 -> PremiumSubscriptionScreen(
                                        myProfile = myProfile,
                                        sessionUsageSeconds = sessionUsageSeconds,
                                        onSelectPlan = viewModel::activatePremiumAfterUpiPayment,
                                        onSubmitUtrForVerification = viewModel::submitUpiUtrForVerification
                                    )

                                    4 -> MyProfileAndBlockedScreen(
                                        myProfile = myProfile,
                                        blockedUsers = blockedUsers,
                                        isDarkMode = isDarkMode,
                                        colorTheme = colorTheme,
                                        onToggleDarkMode = onToggleDarkMode,
                                        onSelectColorTheme = onSelectColorTheme,
                                        onEditProfileClick = { isEditingProfile = true },
                                        onUpdateMainPhoto = { newMainPhoto ->
                                            viewModel.saveUserProfile(
                                                myProfile.copy(photoBase64 = newMainPhoto)
                                            )
                                        },
                                        onUpdateGalleryPhotos = viewModel::updateMyGalleryPhotos,
                                        onSubmitFaceVerificationVideo = viewModel::submitLiveFaceVerificationVideo,
                                        onUnblockUser = { block ->
                                            viewModel.unblockUser(block.blockedUserId, block.blockedUserName)
                                        },
                                        onChatWithAdminClick = {
                                            viewModel.openAdminSupportChat {
                                                currentTab = 2
                                            }
                                        },
                                        onSignOutClick = {
                                            performGoogleSignOut(
                                                context = context,
                                                onSignOutComplete = {},
                                                scope = scope
                                            )
                                        }
                                    )

                                    5 -> AdminDashboardScreen(
                                        allRegisteredProfiles = allRegisteredProfiles,
                                        onAdminVerifyUpiPayment = viewModel::adminVerifyUserUpiPayment,
                                        onAdminSetUserPlan = viewModel::adminSetUserPlan,
                                        onAdminDeleteUser = viewModel::adminDeleteUser,
                                        onOpenVerificationMenu = { currentTab = 6 }
                                    )

                                    6 -> AdminVerificationMenuScreen(
                                        allRegisteredProfiles = allRegisteredProfiles,
                                        onAdminVerifyFaceProfile = viewModel::adminVerifyUserFaceProfile
                                    )

                                    7 -> RandomChatScreen(
                                        currentUserId = currentUserId,
                                        myProfile = myProfile,
                                        preferences = randomChatPreferences,
                                        matchStatus = randomMatchStatus,
                                        activeSession = activeRandomSession,
                                        activeRandomChats = activeRandomChats,
                                        maskedPartner = maskedRandomPartner,
                                        messages = randomChatMessages,
                                        remainingFreeFinds = viewModel.getRemainingFreeRandomFinds(myProfile),
                                        onUpdatePreferences = viewModel::updateRandomChatPreferences,
                                        onToggleRandomMeet = viewModel::toggleRandomMeetEnabled,
                                        onOpenExistingSession = viewModel::openExistingRandomSession,
                                        onStartMatchmaking = viewModel::startRandomMatchmaking,
                                        onCancelMatchmaking = viewModel::cancelRandomMatchmaking,
                                        onConsentRevealStep = viewModel::consentToRandomRevealStep,
                                        onSendMessage = viewModel::sendRandomChatMessage,
                                        onMinimizeChat = viewModel::minimizeRandomChatView,
                                        onNextPartner = viewModel::findNextRandomPartner,
                                        onEndChat = viewModel::endRandomChat,
                                        onDeleteSession = viewModel::deleteRandomChatSession,
                                        onUpgradeToPremium = { currentTab = 3 },
                                        onBlockPartner = viewModel::blockRandomPartner,
                                        onStartAudioCall = { target, _ ->
                                            viewModel.startCall(target, CallMode.AUDIO)
                                            currentTab = 2
                                        },
                                        onOpenDirectChat = { partner ->
                                            viewModel.likeAndOpenChat(partner) {
                                                currentTab = 2
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                        }
                    }
                }
            }
        }
    }
}
