package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AppColorTheme
import com.example.data.model.CallMode
import com.example.data.model.DiscoverViewMode
import com.example.data.model.FilterState
import com.example.data.model.LikeRecord
import com.example.data.model.MAX_FREE_DISTANCE_KM
import com.example.data.model.MAX_PREMIUM_DISTANCE_KM
import com.example.data.model.SavedNotificationItem
import com.example.data.model.UserProfile
import com.example.ui.components.MilanAdMobBanner
import com.example.ui.components.MilanAdMobManager
import com.example.ui.components.MilanNativeAdvancedAdCard
import com.example.ui.components.ProfileAvatarView
import com.example.ui.components.ProfilePhotoGalleryStrip
import com.example.ui.components.VerifiedBlueTickBadge
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.PremiumGoldLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverListScreen(
    myProfile: UserProfile,
    profilesWithDistance: List<Pair<UserProfile, Double>>,
    sentLikes: List<LikeRecord>,
    filterState: FilterState,
    discoverViewMode: DiscoverViewMode,
    lastLocationSyncStatus: String,
    likeGatePromptTarget: UserProfile?,
    savedNotifications: List<SavedNotificationItem> = emptyList(),
    isDarkMode: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.PINK,
    onToggleDarkMode: () -> Unit = {},
    onSelectColorTheme: (AppColorTheme) -> Unit = {},
    onMarkNotificationsRead: () -> Unit = {},
    onClearNotifications: () -> Unit = {},
    onChangeViewMode: (DiscoverViewMode) -> Unit,
    onRefreshLocation: () -> Unit,
    onUpdateFilters: (FilterState) -> Unit,
    onResetFilters: () -> Unit,
    onToggleLike: (UserProfile) -> Unit,
    onMessageClick: (UserProfile) -> Unit,
    onStartCall: (UserProfile, CallMode) -> Unit = { _, _ -> },
    onLikeAndOpenChat: (UserProfile) -> Unit,
    onDismissLikeGate: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onBlockUser: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var showFilterSheet by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedDetailProfile by remember { mutableStateOf<Pair<UserProfile, Double>?>(null) }
    var pendingCallTarget by remember { mutableStateOf<UserProfile?>(null) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val target = pendingCallTarget
        pendingCallTarget = null
        if (target != null) {
            onStartCall(target, CallMode.AUDIO)
        }
    }

    fun triggerAudioCallWithPermission(target: UserProfile) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            onStartCall(target, CallMode.AUDIO)
        } else {
            pendingCallTarget = target
            callPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val likedUserIds = remember(sentLikes) { sentLikes.map { it.toUserId }.toSet() }
    val unreadCount = remember(savedNotifications) { savedNotifications.count { !it.isRead } }
    val effectiveMaxDistance = if (myProfile.isPremium) {
        filterState.maxDistanceKm.toInt()
    } else {
        filterState.maxDistanceKm.coerceAtMost(MAX_FREE_DISTANCE_KM).toInt()
    }

    if (showThemeDialog) {
        ThemeSelectorDialog(
            isDarkMode = isDarkMode,
            colorTheme = colorTheme,
            onToggleDarkMode = onToggleDarkMode,
            onSelectColorTheme = onSelectColorTheme,
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showNotificationsDialog) {
        NotificationCenterDialog(
            notifications = savedNotifications,
            onDismiss = { showNotificationsDialog = false },
            onClearAll = onClearNotifications
        )
    }

    if (selectedDetailProfile != null) {
        BackHandler { selectedDetailProfile = null }
        ProfileDetailDialog(
            profile = selectedDetailProfile!!.first,
            distanceKm = selectedDetailProfile!!.second,
            isLiked = selectedDetailProfile!!.first.userId in likedUserIds,
            canDirectMessage = myProfile.isPremium || selectedDetailProfile!!.first.userId in likedUserIds,
            onDismiss = { selectedDetailProfile = null },
            onToggleLike = { onToggleLike(selectedDetailProfile!!.first) },
            onMessageClick = {
                val target = selectedDetailProfile!!.first
                selectedDetailProfile = null
                onMessageClick(target)
            },
            onAudioCallClick = {
                val target = selectedDetailProfile!!.first
                selectedDetailProfile = null
                triggerAudioCallWithPermission(target)
            },
            onBlockClick = {
                val target = selectedDetailProfile!!.first
                selectedDetailProfile = null
                onBlockUser(target)
            }
        )
    }

    if (likeGatePromptTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissLikeGate,
            icon = {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Send a Like Before Messaging!",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "On the Free plan, you must send a Like (❤️) before messaging someone.\n\nUpgrade to Milan Gold (Premium) for Unlimited Likes, Unlimited Messages, Unlimited Views & Distance Range, and Direct Chat!",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onLikeAndOpenChat(likeGatePromptTarget) },
                    modifier = Modifier.testTag("dialog_like_and_chat_button")
                ) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Like & Message Now")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onDismissLikeGate()
                        onNavigateToPremium()
                    },
                    modifier = Modifier.testTag("dialog_upgrade_premium_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = PremiumGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get Premium (Unlimited)")
                }
            }
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FilterBottomSheetContent(
                isPremium = myProfile.isPremium,
                filterState = filterState,
                onUpdateFilters = onUpdateFilters,
                onResetFilters = {
                    onResetFilters()
                    showFilterSheet = false
                },
                onUpgradeToPremium = {
                    showFilterSheet = false
                    onNavigateToPremium()
                },
                onClose = { showFilterSheet = false }
            )
        }
    }

    // Entire Discover page scrolls as one unified surface so dragging up moves the search bar
    // and status bar off-screen, maximizing vertical space for viewing people's profiles.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    if (discoverViewMode == DiscoverViewMode.LIST) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp)
                .testTag("discover_profiles_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "discover_scrollable_header") {
                DiscoverScrollableHeaderSection(
                    myProfile = myProfile,
                    profilesCount = profilesWithDistance.size,
                    effectiveMaxDistance = effectiveMaxDistance,
                    lastLocationSyncStatus = lastLocationSyncStatus,
                    filterState = filterState,
                    discoverViewMode = discoverViewMode,
                    unreadNotificationCount = unreadCount,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenThemePicker = { showThemeDialog = true },
                    onOpenNotifications = {
                        onMarkNotificationsRead()
                        showNotificationsDialog = true
                    },
                    onRefreshLocation = onRefreshLocation,
                    onOpenFilters = { showFilterSheet = true },
                    onChangeViewMode = onChangeViewMode,
                    onUpdateFilters = onUpdateFilters,
                    onNavigateToPremium = onNavigateToPremium
                )
            }

            if (profilesWithDistance.isEmpty()) {
                item(key = "discover_empty_state") {
                    DiscoverEmptyStateCard(
                        isPremium = myProfile.isPremium,
                        effectiveMaxDistance = effectiveMaxDistance,
                        myGender = myProfile.gender,
                        onResetFilters = onResetFilters,
                        onNavigateToPremium = onNavigateToPremium
                    )
                }
            } else {
                items(
                    items = profilesWithDistance,
                    key = { it.first.userId }
                ) { (profile, distanceKm) ->
                    val isLiked = profile.userId in likedUserIds
                    val canMessageDirectly = myProfile.isPremium || isLiked

                    ProfileListCard(
                        profile = profile,
                        distanceKm = distanceKm,
                        isLiked = isLiked,
                        canMessageDirectly = canMessageDirectly,
                        onCardClick = {
                            MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                            selectedDetailProfile = profile to distanceKm
                        },
                        onLikeClick = { onToggleLike(profile) },
                        onMessageClick = { onMessageClick(profile) },
                        onAudioCallClick = { triggerAudioCallWithPermission(profile) },
                        onBlockClick = { onBlockUser(profile) }
                    )
                }

                if (!myProfile.isPremium) {
                    item(key = "native_advanced_ad_footer") {
                        MilanNativeAdvancedAdCard(
                            onUpgradeClick = onNavigateToPremium
                        )
                    }
                }
            }
        }
    } else {
        // TILES VIEW (Adaptive Grid: 2 columns on mobile phones, 3-4 columns on tablets)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 165.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 16.dp)
                .testTag("discover_profiles_tiles"),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(
                key = "discover_scrollable_header_grid",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                DiscoverScrollableHeaderSection(
                    myProfile = myProfile,
                    profilesCount = profilesWithDistance.size,
                    effectiveMaxDistance = effectiveMaxDistance,
                    lastLocationSyncStatus = lastLocationSyncStatus,
                    filterState = filterState,
                    discoverViewMode = discoverViewMode,
                    unreadNotificationCount = unreadCount,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenThemePicker = { showThemeDialog = true },
                    onOpenNotifications = {
                        onMarkNotificationsRead()
                        showNotificationsDialog = true
                    },
                    onRefreshLocation = onRefreshLocation,
                    onOpenFilters = { showFilterSheet = true },
                    onChangeViewMode = onChangeViewMode,
                    onUpdateFilters = onUpdateFilters,
                    onNavigateToPremium = onNavigateToPremium
                )
            }

            if (profilesWithDistance.isEmpty()) {
                item(
                    key = "discover_empty_state_grid",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    DiscoverEmptyStateCard(
                        isPremium = myProfile.isPremium,
                        effectiveMaxDistance = effectiveMaxDistance,
                        myGender = myProfile.gender,
                        onResetFilters = onResetFilters,
                        onNavigateToPremium = onNavigateToPremium
                    )
                }
            } else {
                items(
                    items = profilesWithDistance,
                    key = { it.first.userId }
                ) { (profile, distanceKm) ->
                    val isLiked = profile.userId in likedUserIds
                    val canMessageDirectly = myProfile.isPremium || isLiked

                    ProfileTileCard(
                        profile = profile,
                        distanceKm = distanceKm,
                        isLiked = isLiked,
                        canMessageDirectly = canMessageDirectly,
                        onCardClick = {
                            MilanAdMobManager.maybeShowInterstitial(context, isPremium = myProfile.isPremium)
                            selectedDetailProfile = profile to distanceKm
                        },
                        onLikeClick = { onToggleLike(profile) },
                        onMessageClick = { onMessageClick(profile) }
                    )
                }

                if (!myProfile.isPremium) {
                    item(
                        key = "native_advanced_ad_tile_footer",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        MilanNativeAdvancedAdCard(
                            onUpgradeClick = onNavigateToPremium
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DiscoverScrollableHeaderSection(
    myProfile: UserProfile,
    profilesCount: Int,
    effectiveMaxDistance: Int,
    lastLocationSyncStatus: String,
    filterState: FilterState,
    discoverViewMode: DiscoverViewMode,
    unreadNotificationCount: Int,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenFilters: () -> Unit,
    onChangeViewMode: (DiscoverViewMode) -> Unit,
    onUpdateFilters: (FilterState) -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val showingOppositeGenderLabel = if (myProfile.gender.equals("Male", ignoreCase = true)) {
        "Female"
    } else {
        "Male"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Title + GOLD badge + Day/Dark Button + Color Theme Picker + Bell
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Nearby People",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (myProfile.isPremium) {
                    Surface(
                        color = PremiumGoldLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Gold",
                                tint = PremiumGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "GOLD",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7A5900),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.testTag("theme_mode_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkMode) "Switch to Day Mode" else "Switch to Dark Mode"
                    )
                }

                FilledTonalIconButton(
                    onClick = onOpenThemePicker,
                    modifier = Modifier.testTag("theme_palette_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Choose Color Theme"
                    )
                }

                FilledTonalIconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.testTag("top_notification_bell_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge {
                                    Text(text = unreadNotificationCount.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Saved Notifications"
                        )
                    }
                }
            }
        }

        // Row 2: Location Summary + GPS & Filter Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${myProfile.locationName} • $profilesCount $showingOppositeGenderLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = lastLocationSyncStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onRefreshLocation,
                    modifier = Modifier.testTag("refresh_gps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Refresh GPS Location"
                    )
                }

                FilledTonalIconButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag("open_filters_button")
                ) {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter profiles")
                }
            }
        }

        // Compact Search Bar (scrolls up with list)
        OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = { onUpdateFilters(filterState.copy(searchQuery = it)) },
            placeholder = { Text("Search name, city, or interests...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filterState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onUpdateFilters(filterState.copy(searchQuery = "")) }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discover_search_input")
        )

        // List View vs Tiles View Switcher + Distance Status Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = discoverViewMode == DiscoverViewMode.LIST,
                onClick = { onChangeViewMode(DiscoverViewMode.LIST) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("List View") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("view_mode_list_chip")
            )

            FilterChip(
                selected = discoverViewMode == DiscoverViewMode.TILES,
                onClick = { onChangeViewMode(DiscoverViewMode.TILES) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Tiles View") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("view_mode_tiles_chip")
            )

            Spacer(modifier = Modifier.weight(1f))

            AssistChip(
                onClick = onOpenFilters,
                label = {
                    Text(
                        if (myProfile.isPremium && effectiveMaxDistance >= MAX_PREMIUM_DISTANCE_KM.toInt() - 10) {
                            "Unlimited km"
                        } else {
                            "≤ $effectiveMaxDistance km"
                        }
                    )
                }
            )
        }

        // Google AdMob Banner for Non-Premium Users (scrolls up with header)
        AnimatedVisibility(visible = !myProfile.isPremium) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("free_tier_ad_banner"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MilanAdMobBanner()

                Surface(
                    color = PremiumGoldLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPremium() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = PremiumGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Free Plan: 50 km max • Upgrade for Unlimited Range, Likes & Views",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5D4200),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Upgrade →",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7A5900)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverEmptyStateCard(
    isPremium: Boolean,
    effectiveMaxDistance: Int,
    myGender: String,
    onResetFilters: () -> Unit,
    onNavigateToPremium: () -> Unit
) {
    val oppositeGenderLabel = if (myGender.equals("Male", ignoreCase = true)) "Female" else "Male"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = if (!isPremium) {
                "No $oppositeGenderLabel profiles found within $effectiveMaxDistance km"
            } else {
                "No $oppositeGenderLabel profiles match your filters"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Try adjusting filters or distance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onResetFilters) {
                Text("Reset Filters")
            }
            if (!isPremium) {
                Button(onClick = onNavigateToPremium) {
                    Text("Unlock >50 km Range")
                }
            }
        }
        if (!isPremium) {
            MilanNativeAdvancedAdCard(
                modifier = Modifier.padding(top = 8.dp),
                onUpgradeClick = onNavigateToPremium
            )
        }
    }
}

@Composable
private fun NotificationCenterDialog(
    notifications: List<SavedNotificationItem>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Notifications (${notifications.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                if (notifications.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear notifications",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved notifications yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isReward) {
                                    PremiumGoldLight
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isReward) Color(0xFF5D4200) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dateFormat.format(Date(item.timestampMs)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isReward) Color(0xFF6B4C00) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_notifications_dialog_button")
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileListCard(
    profile: UserProfile,
    distanceKm: Double,
    isLiked: Boolean,
    canMessageDirectly: Boolean,
    onCardClick: () -> Unit,
    onLikeClick: () -> Unit,
    onMessageClick: () -> Unit,
    onAudioCallClick: () -> Unit,
    onBlockClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("profile_card_${profile.userId}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProfileAvatarView(
                    photoBase64 = profile.photoBase64,
                    displayName = profile.fullName,
                    size = 76.dp,
                    isPremium = profile.isPremium,
                    isVerified = profile.isVerifiedWithBlueTick
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${profile.fullName}, ${profile.age} yrs",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        VerifiedBlueTickBadge(
                            isVerified = profile.isVerifiedWithBlueTick,
                            showLabel = true
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${profile.locationName} • $distanceKm km away",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = profile.gender,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Age: ${profile.age}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (profile.galleryPhotos.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "📷 ${1 + profile.galleryPhotos.size} photos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("more_menu_${profile.userId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View All Photos & Details") },
                            onClick = {
                                menuExpanded = false
                                onCardClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio Call") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onAudioCallClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block User", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onBlockClick()
                            }
                        )
                    }
                }
            }

            if (profile.bio.isNotBlank()) {
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (profile.interests.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.interests.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Action Buttons: Like, Message, Audio Call (Video Call removed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("like_button_${profile.userId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isLiked) "Liked ❤️" else "Like")
                }

                FilledTonalButton(
                    onClick = onMessageClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("message_button_${profile.userId}"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (canMessageDirectly) Icons.AutoMirrored.Filled.Chat else Icons.Default.Lock,
                        contentDescription = "Message",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chat")
                }

                FilledTonalIconButton(
                    onClick = onAudioCallClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("card_audio_call_${profile.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Audio Call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTileCard(
    profile: UserProfile,
    distanceKm: Double,
    isLiked: Boolean,
    canMessageDirectly: Boolean,
    onCardClick: () -> Unit,
    onLikeClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("profile_tile_${profile.userId}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                ProfileAvatarView(
                    photoBase64 = profile.photoBase64,
                    displayName = profile.fullName,
                    size = 96.dp,
                    shape = RoundedCornerShape(20.dp),
                    isPremium = profile.isPremium,
                    isVerified = profile.isVerifiedWithBlueTick
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$distanceKm km",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${profile.firstName}, ${profile.age}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                VerifiedBlueTickBadge(
                    isVerified = profile.isVerifiedWithBlueTick,
                    showLabel = false
                )
            }

            Text(
                text = "${profile.locationName.substringBefore(",")} • ${profile.gender}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLikeClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tile_like_${profile.userId}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onMessageClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tile_chat_${profile.userId}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (canMessageDirectly) Icons.AutoMirrored.Filled.Chat else Icons.Default.Lock,
                        contentDescription = "Message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheetContent(
    isPremium: Boolean,
    filterState: FilterState,
    onUpdateFilters: (FilterState) -> Unit,
    onResetFilters: () -> Unit,
    onUpgradeToPremium: () -> Unit,
    onClose: () -> Unit
) {
    val maxAllowedKm = if (isPremium) MAX_PREMIUM_DISTANCE_KM else MAX_FREE_DISTANCE_KM
    val currentSliderDistance = filterState.maxDistanceKm.coerceIn(5f, maxAllowedKm)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile Filters",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onResetFilters) {
                Text("Reset")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distLabel = if (isPremium && currentSliderDistance >= MAX_PREMIUM_DISTANCE_KM - 10f) {
                    "Maximum Distance: Unlimited"
                } else {
                    "Maximum Distance: ${currentSliderDistance.toInt()} km"
                }
                Text(
                    text = distLabel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isPremium) "Unlimited" else "Free: 50 km max",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Slider(
                value = currentSliderDistance,
                onValueChange = { onUpdateFilters(filterState.copy(maxDistanceKm = it)) },
                valueRange = 5f..maxAllowedKm,
                modifier = Modifier.testTag("filter_distance_slider")
            )

            if (!isPremium) {
                Surface(
                    color = PremiumGoldLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpgradeToPremium() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Want to discover people beyond 50 km?",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF5D4200)
                        )
                        Text(
                            text = "Get Premium →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7A5900)
                        )
                    }
                }
            }
        }

        Text(
            text = "Age Range: ${filterState.minAge} to ${filterState.maxAge} years",
            style = MaterialTheme.typography.titleMedium
        )
        RangeSlider(
            value = filterState.minAge.toFloat()..filterState.maxAge.toFloat(),
            onValueChange = { range ->
                onUpdateFilters(
                    filterState.copy(
                        minAge = range.start.toInt().coerceAtLeast(18),
                        maxAge = range.endInclusive.toInt().coerceAtMost(80)
                    )
                )
            },
            valueRange = 18f..65f
        )

        Text(
            text = "Filter by Interest:",
            style = MaterialTheme.typography.titleMedium
        )
        val interests = listOf("All", "Music", "Travel", "Coffee", "Fitness", "Art", "Foodie", "Books", "Movies")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            interests.forEach { interest ->
                FilterChip(
                    selected = filterState.selectedInterest == interest,
                    onClick = { onUpdateFilters(filterState.copy(selectedInterest = interest)) },
                    label = { Text(interest) }
                )
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("apply_filters_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Apply Filters")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDetailDialog(
    profile: UserProfile,
    distanceKm: Double,
    isLiked: Boolean,
    canDirectMessage: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onMessageClick: () -> Unit,
    onAudioCallClick: () -> Unit,
    onBlockClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatarView(
                    photoBase64 = profile.photoBase64,
                    displayName = profile.fullName,
                    size = 64.dp,
                    shape = CircleShape,
                    isPremium = profile.isPremium,
                    isVerified = profile.isVerifiedWithBlueTick
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${profile.fullName}, ${profile.age} yrs",
                        style = MaterialTheme.typography.titleLarge
                    )
                    VerifiedBlueTickBadge(
                        isVerified = profile.isVerifiedWithBlueTick,
                        showLabel = true
                    )
                    Text(
                        text = "${profile.locationName} • $distanceKm km away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Age: ${profile.age} years • Gender: ${profile.gender} • Distance: $distanceKm km",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // Show all profile photos (1 Main Profile Picture + up to 6 Additional Gallery Photos)
                ProfilePhotoGalleryStrip(
                    mainPhotoBase64 = profile.photoBase64,
                    galleryPhotos = profile.galleryPhotos,
                    displayName = profile.fullName
                )

                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (profile.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.interests.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Direct Audio Call button inside Profile Detail Dialog (Video Call removed)
                FilledTonalButton(
                    onClick = onAudioCallClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_audio_call_button")
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Audio Call")
                }

                TextButton(
                    onClick = onBlockClick,
                    modifier = Modifier.testTag("detail_block_user_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Block ${profile.firstName}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onMessageClick) {
                Text(if (canDirectMessage) "Open Chat" else "Like & Chat")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onToggleLike) {
                Text(if (isLiked) "Liked ❤️" else "Send Like")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSelectorDialog(
    isDarkMode: Boolean,
    colorTheme: AppColorTheme,
    onToggleDarkMode: () -> Unit,
    onSelectColorTheme: (AppColorTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("App Theme & Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isDarkMode) "Dark Mode" else "Day Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        modifier = Modifier.testTag("dialog_dark_mode_switch")
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppColorTheme.entries.forEach { option ->
                        val selected = option == colorTheme
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectColorTheme(option) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(option.swatchColorHex))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            },
                            label = { Text(option.displayName) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("dialog_theme_option_${option.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
