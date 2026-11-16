package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CallMode
import com.example.data.model.MAX_PREMIUM_DISTANCE_KM
import com.example.data.model.RandomChatMessage
import com.example.data.model.RandomChatPreferences
import com.example.data.model.RandomChatSession
import com.example.data.model.RandomMatchStatus
import com.example.data.model.RandomRevealStep
import com.example.data.model.UNLIMITED_DISTANCE_KM
import com.example.data.model.UserProfile
import com.example.data.model.formatMessageDateTime
import com.example.ui.components.OnlineStatusDot
import com.example.ui.components.ProfileAvatarView
import com.example.ui.components.ProfilePhotoGalleryStrip
import com.example.ui.components.VerifiedBlueTickBadge
import com.example.ui.viewmodel.ActiveRandomChatSummary
import com.example.ui.viewmodel.MaskedRandomPartnerView

@Composable
fun RandomChatScreen(
    currentUserId: String,
    myProfile: UserProfile,
    preferences: RandomChatPreferences,
    matchStatus: RandomMatchStatus,
    activeSession: RandomChatSession?,
    activeRandomChats: List<ActiveRandomChatSummary>,
    maskedPartner: MaskedRandomPartnerView?,
    messages: List<RandomChatMessage>,
    remainingFreeFinds: Int = Int.MAX_VALUE,
    onUpdatePreferences: (RandomChatPreferences) -> Unit,
    onToggleRandomMeet: (Boolean) -> Unit,
    onOpenExistingSession: (String) -> Unit,
    onStartMatchmaking: () -> Unit,
    onCancelMatchmaking: () -> Unit,
    onConsentRevealStep: (RandomRevealStep) -> Unit,
    onSendMessage: (String) -> Unit,
    onMinimizeChat: () -> Unit,
    onNextPartner: () -> Unit,
    onEndChat: () -> Unit,
    onDeleteSession: (String) -> Unit = {},
    onUpgradeToPremium: () -> Unit = {},
    onBlockPartner: (partnerId: String, revealedName: String?) -> Unit,
    onStartAudioCall: (UserProfile, CallMode) -> Unit,
    onOpenDirectChat: (UserProfile) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (matchStatus) {
            RandomMatchStatus.IDLE -> {
                RandomChatLobbyView(
                    myProfile = myProfile,
                    preferences = preferences,
                    activeRandomChats = activeRandomChats,
                    remainingFreeFinds = remainingFreeFinds,
                    onUpdatePreferences = onUpdatePreferences,
                    onToggleRandomMeet = onToggleRandomMeet,
                    onOpenExistingSession = onOpenExistingSession,
                    onDeleteSession = onDeleteSession,
                    onUpgradeToPremium = onUpgradeToPremium,
                    onStartMatchmaking = onStartMatchmaking
                )
            }

            RandomMatchStatus.SEARCHING -> {
                RandomChatSearchingView(
                    preferences = preferences,
                    onCancelSearch = onCancelMatchmaking
                )
            }

            RandomMatchStatus.CONNECTED,
            RandomMatchStatus.PARTNER_LEFT -> {
                if (activeSession != null && maskedPartner != null) {
                    RandomChatSessionView(
                        currentUserId = currentUserId,
                        session = activeSession,
                        maskedPartner = maskedPartner,
                        matchStatus = matchStatus,
                        messages = messages,
                        onConsentRevealStep = onConsentRevealStep,
                        onSendMessage = onSendMessage,
                        onMinimizeChat = onMinimizeChat,
                        onNextPartner = onNextPartner,
                        onEndChat = onEndChat,
                        onDeleteSession = { onDeleteSession(activeSession.id) },
                        onBlockPartner = { onBlockPartner(maskedPartner.partnerId, maskedPartner.revealedName) },
                        onStartAudioCall = onStartAudioCall,
                        onOpenDirectChat = onOpenDirectChat
                    )
                } else {
                    RandomChatSearchingView(
                        preferences = preferences,
                        onCancelSearch = onCancelMatchmaking
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomChatLobbyView(
    myProfile: UserProfile,
    preferences: RandomChatPreferences,
    activeRandomChats: List<ActiveRandomChatSummary>,
    remainingFreeFinds: Int,
    onUpdatePreferences: (RandomChatPreferences) -> Unit,
    onToggleRandomMeet: (Boolean) -> Unit,
    onOpenExistingSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onUpgradeToPremium: () -> Unit,
    onStartMatchmaking: () -> Unit
) {
    var pendingDeleteSummary by remember { mutableStateOf<ActiveRandomChatSummary?>(null) }

    if (pendingDeleteSummary != null) {
        val summary = pendingDeleteSummary!!
        AlertDialog(
            onDismissRequest = { pendingDeleteSummary = null },
            title = { Text("Delete Random Chat?") },
            text = { Text("This chat and all its messages will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteSummary = null
                        onDeleteSession(summary.session.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSummary = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isUnlimitedUser = myProfile.isPremium || myProfile.isAdminUser
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Privacy Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Random Chat",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Private • Mutual Consent Reveal",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Unlimited Messages") }
                        )
                        AssistChip(
                            onClick = {},
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Gender-Only Start") }
                        )
                    }
                }
            }
        }

        // Random Meet Option Card (Allows matching & messaging anytime when enabled)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (myProfile.allowRandomMeet) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Random Meet Option",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OnlineStatusDot(isOnline = myProfile.allowRandomMeet, showLabel = false)
                    }
                    Text(
                        text = if (myProfile.allowRandomMeet) "ON" else "OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = myProfile.allowRandomMeet,
                    onCheckedChange = onToggleRandomMeet,
                    modifier = Modifier.testTag("random_meet_toggle")
                )
            }
        }

        // Saved Random Chats Card List (separate from Normal Chats)
        if (activeRandomChats.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Saved Random Chats (${activeRandomChats.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    activeRandomChats.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExistingSession(item.session.id) }
                                .testTag("open_random_session_${item.session.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VisibilityOff,
                                        contentDescription = "Anonymous Random Partner",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val displayLabel = if (item.isNameRevealed && !item.revealedName.isNullOrBlank()) {
                                        item.revealedName
                                    } else {
                                        "Anonymous Partner"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatMessageDateTime(item.session.updatedAt ?: item.session.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OnlineStatusDot(
                                            isOnline = item.isPartnerOnline,
                                            showLabel = true
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Gender: ${item.partnerGender}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { pendingDeleteSummary = item },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_random_session_${item.session.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Random Chat",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onOpenExistingSession(item.session.id) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Matchmaking Preference Controls Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Match Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 1. Preferred Gender
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Preferred Gender",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("All" to "Any", "Female" to "Female", "Male" to "Male").forEach { (value, label) ->
                            val selected = preferences.preferredGender.equals(value, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdatePreferences(preferences.copy(preferredGender = value))
                                },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("random_pref_gender_${value.lowercase()}")
                            )
                        }
                    }
                }

                // 2. Age Range Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Partner Age Range",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${preferences.minAge} – ${preferences.maxAge} yrs",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    RangeSlider(
                        value = preferences.minAge.toFloat()..preferences.maxAge.toFloat(),
                        onValueChange = { range ->
                            onUpdatePreferences(
                                preferences.copy(
                                    minAge = range.start.toInt().coerceIn(18, 65),
                                    maxAge = range.endInclusive.toInt().coerceIn(range.start.toInt(), 65)
                                )
                            )
                        },
                        valueRange = 18f..65f,
                        modifier = Modifier.testTag("random_pref_age_slider")
                    )
                }

                // 3. Distance Slider (Free: up to 500 km, Premium: Unlimited)
                val maxSliderKm = if (isUnlimitedUser) MAX_PREMIUM_DISTANCE_KM else 500f
                val sliderValue = preferences.maxDistanceKm.toFloat().coerceIn(10f, maxSliderKm)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Maximum Distance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isUnlimitedUser && preferences.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM.toInt()) {
                                "Unlimited"
                            } else {
                                "Within ${sliderValue.toInt()} km"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            val mappedDist = if (isUnlimitedUser && value >= MAX_PREMIUM_DISTANCE_KM - 40f) {
                                UNLIMITED_DISTANCE_KM.toInt()
                            } else {
                                value.toInt().coerceIn(10, maxSliderKm.toInt())
                            }
                            onUpdatePreferences(preferences.copy(maxDistanceKm = mappedDist))
                        },
                        valueRange = 10f..maxSliderKm,
                        modifier = Modifier.testTag("random_pref_distance_slider")
                    )
                }
            }
        }

        if (!isUnlimitedUser) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily Finds Left: $remainingFreeFinds / 2",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("free_random_finds_badge")
                    )
                    TextButton(
                        onClick = onUpgradeToPremium,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlimited")
                    }
                }
            }
        }

        Button(
            onClick = onStartMatchmaking,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("start_random_chat_button")
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Find Random Person",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RandomChatSearchingView(
    preferences: RandomChatPreferences,
    onCancelSearch: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "random_search_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 6.dp,
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Searching for random partner",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Finding a Random Partner...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        val searchDistText = if (preferences.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM.toInt()) {
            "Unlimited"
        } else {
            "Up to ${preferences.maxDistanceKm} km"
        }
        Text(
            text = "${preferences.preferredGender} • Age ${preferences.minAge}–${preferences.maxAge} • $searchDistText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(
            onClick = onCancelSearch,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("cancel_random_search_button")
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Search")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomChatSessionView(
    currentUserId: String,
    session: RandomChatSession,
    maskedPartner: MaskedRandomPartnerView,
    matchStatus: RandomMatchStatus,
    messages: List<RandomChatMessage>,
    onConsentRevealStep: (RandomRevealStep) -> Unit,
    onSendMessage: (String) -> Unit,
    onMinimizeChat: () -> Unit,
    onNextPartner: () -> Unit,
    onEndChat: () -> Unit,
    onDeleteSession: () -> Unit,
    onBlockPartner: () -> Unit,
    onStartAudioCall: (UserProfile, CallMode) -> Unit,
    onOpenDirectChat: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var inputMessage by remember { mutableStateOf("") }
    var showFullProfileDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingCallTarget by remember { mutableStateOf<UserProfile?>(null) }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val target = pendingCallTarget
        pendingCallTarget = null
        if (target != null) {
            onStartAudioCall(target, CallMode.AUDIO)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isEnded = session.status == "ended" || matchStatus == RandomMatchStatus.PARTNER_LEFT

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Random Chat?") },
            text = { Text("This chat and all its messages will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Block Random Partner?") },
            text = {
                Text("This will immediately end this Random Chat and prevent matching with this user again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        onBlockPartner()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block & Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Profile Dialog (ONLY accessible after Step 4 mutual consent)
    val unlockedProfile = maskedPartner.revealedFullProfile
    if (showFullProfileDialog && session.isProfileRevealed && unlockedProfile != null) {
        AlertDialog(
            onDismissRequest = { showFullProfileDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${unlockedProfile.fullName}, ${unlockedProfile.age}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    VerifiedBlueTickBadge(
                        isVerified = unlockedProfile.isVerifiedWithBlueTick,
                        showLabel = false
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileAvatarView(
                            photoBase64 = unlockedProfile.photoBase64,
                            displayName = unlockedProfile.fullName,
                            size = 68.dp,
                            shape = CircleShape,
                            isPremium = unlockedProfile.isPremium,
                            isVerified = unlockedProfile.isVerifiedWithBlueTick
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${unlockedProfile.gender} • ${unlockedProfile.age} yrs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                val distLabel = maskedPartner.distanceKm?.let { " • $it km away" } ?: ""
                                Text(
                                    text = "${unlockedProfile.locationName}$distLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            VerifiedBlueTickBadge(
                                isVerified = unlockedProfile.isVerifiedWithBlueTick,
                                showLabel = true
                            )
                        }
                    }

                    if (unlockedProfile.bio.isNotBlank()) {
                        Text(
                            text = unlockedProfile.bio,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (unlockedProfile.interests.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unlockedProfile.interests.forEach { interest ->
                                AssistChip(onClick = {}, label = { Text(interest) })
                            }
                        }
                    }

                    ProfilePhotoGalleryStrip(
                        mainPhotoBase64 = unlockedProfile.photoBase64,
                        galleryPhotos = unlockedProfile.galleryPhotos,
                        displayName = unlockedProfile.fullName
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showFullProfileDialog = false
                                val hasAudio = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasAudio) {
                                    onStartAudioCall(unlockedProfile, CallMode.AUDIO)
                                } else {
                                    pendingCallTarget = unlockedProfile
                                    callPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio Call")
                        }

                        Button(
                            onClick = {
                                showFullProfileDialog = false
                                onOpenDirectChat(unlockedProfile)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Direct Chat")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullProfileDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Top Masked / Progressive Identity Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onMinimizeChat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("random_back_to_lobby_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Random Chat Lobby"
                        )
                    }

                    // Avatar: strictly masked until Step 3 (Face Reveal)
                    if (session.isPhotoRevealed && !maskedPartner.revealedPhotoBase64.isNullOrBlank()) {
                        ProfileAvatarView(
                            photoBase64 = maskedPartner.revealedPhotoBase64,
                            displayName = maskedPartner.revealedName ?: "Member",
                            size = 54.dp,
                            shape = CircleShape,
                            isVerified = maskedPartner.isVerifiedWithBlueTick
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                )
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Anonymous Photo Hidden",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val titleText = if (session.isNameRevealed && !maskedPartner.revealedName.isNullOrBlank()) {
                                maskedPartner.revealedName
                            } else {
                                "Anonymous Partner"
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (maskedPartner.isVerifiedWithBlueTick) {
                                VerifiedBlueTickBadge(isVerified = true, showLabel = false)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OnlineStatusDot(
                                isOnline = maskedPartner.isOnline,
                                showLabel = true,
                                modifier = Modifier.testTag("random_partner_online_status")
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Gender: ${maskedPartner.gender}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (session.isAgeRevealed && maskedPartner.revealedAge != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${maskedPartner.revealedAge} yrs",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = onNextPartner,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("random_next_person_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Person",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("random_delete_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Random Chat",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(
                            onClick = { showBlockConfirmDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("random_block_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Block Partner",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(
                            onClick = onEndChat,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("random_end_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "End Random Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Step-by-Step Mutual Consent Progress Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RandomRevealStep.entries.forEach { step ->
                        val unlocked = session.isStepRevealed(step)
                        val myAgreed = session.myConsentFor(currentUserId, step)
                        val partnerAgreed = session.partnerConsentFor(currentUserId, step)
                        val shortLabel = when (step) {
                            RandomRevealStep.NAME -> "1.Name"
                            RandomRevealStep.AGE -> "2.Age"
                            RandomRevealStep.PHOTO -> "3.Face"
                            RandomRevealStep.PROFILE -> "4.Profile"
                        }
                        val bgColor = when {
                            unlocked -> MaterialTheme.colorScheme.tertiaryContainer
                            myAgreed || partnerAgreed -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val fgColor = when {
                            unlocked -> MaterialTheme.colorScheme.onTertiaryContainer
                            myAgreed || partnerAgreed -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        unlocked -> Icons.Default.CheckCircle
                                        myAgreed || partnerAgreed -> Icons.Default.HourglassTop
                                        else -> Icons.Default.Lock
                                    },
                                    contentDescription = null,
                                    tint = fgColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fgColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 3. Active Reveal Step Prompt / View Full Profile Button
                val nextStep = session.nextPendingStep()
                if (!isEnded && nextStep != null) {
                    val myAgreed = session.myConsentFor(currentUserId, nextStep)
                    val partnerAgreed = session.partnerConsentFor(currentUserId, nextStep)

                    Surface(
                        color = if (partnerAgreed && !myAgreed) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        partnerAgreed && !myAgreed -> "Partner wants to unlock ${nextStep.title} — Say Yes?"
                                        myAgreed && !partnerAgreed -> "Waiting for partner to say Yes for ${nextStep.title}"
                                        else -> "Step ${nextStep.stepNumber}/4: ${nextStep.title}"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (myAgreed) {
                                FilledTonalButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Asked ✓")
                                }
                            } else {
                                Button(
                                    onClick = { onConsentRevealStep(nextStep) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("random_agree_reveal_button")
                                ) {
                                    Text(
                                        if (partnerAgreed) {
                                            "Yes, Reveal"
                                        } else if (nextStep == RandomRevealStep.NAME) {
                                            "Ask for Name"
                                        } else {
                                            "Request / Yes"
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (session.isProfileRevealed && unlockedProfile != null) {
                    FilledTonalButton(
                        onClick = { showFullProfileDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("random_view_full_profile_button")
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All 4 Steps Unlocked • View Full Profile & Photos")
                    }
                }
            }
        }

        // 4. Partner Left Banner
        AnimatedVisibility(visible = isEnded) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Partner left the Random Chat.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onNextPartner,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("random_find_new_partner_button")
                    ) {
                        Text("Find New Partner")
                    }
                }
            }
        }

        // 5. Unlimited Real-Time Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Connected with Anonymous (${maskedPartner.gender})!\nSay hello and chat as long as you like.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id.ifEmpty { "${it.senderId}_${it.text.hashCode()}" } }) { msg ->
                if (msg.messageType == "system") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    val isMine = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            ),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!isMine) {
                                    Text(
                                        text = if (session.isNameRevealed && !maskedPartner.revealedName.isNullOrBlank()) {
                                            maskedPartner.revealedName
                                        } else {
                                            "Anonymous (${maskedPartner.gender})"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = formatMessageDateTime(msg.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Unlimited Message Input Bar
        if (!isEnded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Unlimited Random Chat message...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("random_chat_message_input")
                )
                FilledIconButton(
                    onClick = {
                        val trimmed = inputMessage.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
                            inputMessage = ""
                        }
                    },
                    enabled = inputMessage.isNotBlank(),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("random_chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Random Chat Message"
                    )
                }
            }
        }
    }
}
