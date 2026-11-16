package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlin.math.sqrt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ADMIN_SUPPORT_USER_ID
import com.example.data.model.ActiveCallSession
import com.example.data.model.CallMode
import com.example.data.model.CallStreamPacket
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationSummary
import com.example.data.model.LikeRecord
import com.example.data.model.UserProfile
import com.example.data.model.formatMessageDateTime
import com.example.data.repository.MilanRepository
import com.example.ui.components.ProfileAvatarView
import com.example.ui.components.VerifiedBlueTickBadge
import com.example.ui.components.encodeUriToBase64Thumbnail
import com.example.ui.theme.PremiumGold
import com.example.ui.viewmodel.MilanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun LikesScreen(
    sentLikes: List<LikeRecord>,
    receivedLikes: List<LikeRecord>,
    discoverProfiles: List<Pair<UserProfile, Double>>,
    onOpenChatWithUser: (UserProfile) -> Unit,
    onStartCall: (UserProfile, CallMode) -> Unit = { _, _ -> },
    onRemoveLike: (String) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val profileMap = remember(discoverProfiles) {
        discoverProfiles.associate { it.first.userId to it }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Likes & Matches",
            style = MaterialTheme.typography.headlineMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                label = { Text("Likes Sent (${sentLikes.size})") },
                modifier = Modifier.testTag("subtab_sent_likes")
            )
            FilterChip(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                label = { Text("Likes Received (${receivedLikes.size})") },
                modifier = Modifier.testTag("subtab_received_likes")
            )
        }

        val activeList = if (selectedSubTab == 0) sentLikes else receivedLikes

        if (activeList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (selectedSubTab == 0) {
                            "You haven't liked any profiles yet."
                        } else {
                            "No likes received yet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(activeList, key = { it.id.ifEmpty { "${it.fromUserId}_${it.toUserId}" } }) { like ->
                    val otherId = if (selectedSubTab == 0) like.toUserId else like.fromUserId
                    val otherName = if (selectedSubTab == 0) like.toUserName else like.fromUserName
                    val pair = profileMap[otherId]
                    val profile = pair?.first ?: UserProfile(
                        userId = otherId,
                        firstName = otherName.substringBefore(" "),
                        lastName = otherName.substringAfter(" ", ""),
                        age = 25,
                        locationName = "Nearby"
                    )
                    val distText = pair?.second?.let { "$it km away" } ?: profile.locationName

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarView(
                                photoBase64 = profile.photoBase64,
                                displayName = profile.fullName,
                                size = 56.dp,
                                shape = CircleShape,
                                isPremium = profile.isPremium,
                                isVerified = profile.isVerifiedWithBlueTick
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${profile.fullName}, ${profile.age} yrs",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    VerifiedBlueTickBadge(
                                        isVerified = profile.isVerifiedWithBlueTick,
                                        showLabel = false
                                    )
                                }
                                Text(
                                    text = if (selectedSubTab == 0) {
                                        "$distText • Chat & Audio Call unlocked!"
                                    } else {
                                        "$distText • Liked your profile"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onStartCall(profile, CallMode.AUDIO) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                                    .testTag("like_row_audio_call_${profile.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Audio Call",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Button(
                                onClick = { onOpenChatWithUser(profile) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("like_row_chat_${profile.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat")
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun ChatsAndConversationScreen(
    currentUserId: String,
    myProfile: UserProfile,
    conversations: List<ConversationSummary>,
    allMessages: List<ChatMessage>,
    activeChatPartner: UserProfile?,
    activeCallSession: ActiveCallSession?,
    sentLikes: List<LikeRecord>,
    onSelectConversation: (ConversationSummary) -> Unit,
    onCloseActiveChat: () -> Unit,
    onSendMessage: (UserProfile, String) -> Unit,
    onSendPhoto: (UserProfile, String) -> Unit,
    onStartCall: (UserProfile, CallMode) -> Unit,
    onToggleCallMute: () -> Unit,
    onToggleCallSpeaker: () -> Unit,
    onToggleCallCamera: () -> Unit = {},
    onSwitchCallCamera: () -> Unit = {},
    onEndCall: (Int) -> Unit,
    onBlockPartner: (UserProfile) -> Unit,
    onDeleteConversation: (String) -> Unit = {},
    onNavigateToPremium: () -> Unit
) {
    if (activeCallSession != null) {
        ActiveCallOverlay(
            myProfile = myProfile,
            session = activeCallSession,
            onToggleMute = onToggleCallMute,
            onToggleSpeaker = onToggleCallSpeaker,
            onToggleCamera = onToggleCallCamera,
            onSwitchCamera = onSwitchCallCamera,
            onEndCall = onEndCall
        )
        return
    }

    var pendingDeleteConversation by remember { mutableStateOf<ConversationSummary?>(null) }

    if (pendingDeleteConversation != null) {
        val convToDelete = pendingDeleteConversation!!
        AlertDialog(
            onDismissRequest = { pendingDeleteConversation = null },
            title = { Text("Delete Chat?") },
            text = { Text("Delete this conversation with ${convToDelete.otherUserName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteConversation = null
                        onDeleteConversation(convToDelete.otherUserId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteConversation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (activeChatPartner != null) {
        BackHandler { onCloseActiveChat() }
        val chatId = remember(currentUserId, activeChatPartner.userId) {
            MilanRepository.buildChatId(currentUserId, activeChatPartner.userId)
        }
        val threadMessages = remember(allMessages, chatId) {
            allMessages.filter { it.chatId == chatId }
        }
        val hasLikedPartner = remember(sentLikes, activeChatPartner.userId) {
            sentLikes.any { it.toUserId == activeChatPartner.userId }
        }
        val isAdminSupportPartner = activeChatPartner.userId == ADMIN_SUPPORT_USER_ID || activeChatPartner.isAdminUser
        val distanceKm = remember(myProfile, activeChatPartner) {
            MilanRepository.calculateDistanceKm(
                myProfile.latitude,
                myProfile.longitude,
                activeChatPartner.latitude,
                activeChatPartner.longitude
            )
        }

        ActiveChatThreadView(
            currentUserId = currentUserId,
            myProfile = myProfile,
            partner = activeChatPartner,
            distanceKm = distanceKm,
            messages = threadMessages,
            canSend = myProfile.isPremium || hasLikedPartner || isAdminSupportPartner || threadMessages.isNotEmpty(),
            onBack = onCloseActiveChat,
            onSendText = { text -> onSendMessage(activeChatPartner, text) },
            onSendPhoto = { base64 -> onSendPhoto(activeChatPartner, base64) },
            onStartAudioCall = { onStartCall(activeChatPartner, CallMode.AUDIO) },
            onDeleteChat = { onDeleteConversation(activeChatPartner.userId) },
            onBlock = { onBlockPartner(activeChatPartner) },
            onUpgradePremium = onNavigateToPremium
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages & Audio Calls",
                    style = MaterialTheme.typography.headlineMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (myProfile.isPremium) {
                            "Unlimited Messages"
                        } else {
                            "Today: ${myProfile.dailyMessageCount}/${MilanViewModel.FREE_DAILY_MESSAGE_LIMIT}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No conversations yet.",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(conversations, key = { it.chatId }) { conv ->
                        val partnerProfile = conv.otherUserProfile ?: UserProfile(
                            userId = conv.otherUserId,
                            firstName = conv.otherUserName.substringBefore(" "),
                            lastName = conv.otherUserName.substringAfter(" ", ""),
                            age = 25,
                            locationName = "Nearby"
                        )
                        val partnerDistance = conv.otherUserProfile?.let {
                            MilanRepository.calculateDistanceKm(
                                myProfile.latitude,
                                myProfile.longitude,
                                it.latitude,
                                it.longitude
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectConversation(conv) }
                                .testTag("conversation_item_${conv.otherUserId}"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileAvatarView(
                                    photoBase64 = partnerProfile.photoBase64.ifBlank { "avatar_rose" },
                                    displayName = conv.otherUserName,
                                    size = 54.dp,
                                    shape = CircleShape,
                                    isPremium = partnerProfile.isPremium,
                                    isVerified = partnerProfile.isVerifiedWithBlueTick
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (conv.otherUserId == ADMIN_SUPPORT_USER_ID) {
                                                "Admin Support"
                                            } else if (conv.otherUserProfile != null) {
                                                "${conv.otherUserName}, ${partnerProfile.age} yrs"
                                            } else {
                                                conv.otherUserName
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        VerifiedBlueTickBadge(
                                            isVerified = partnerProfile.isVerifiedWithBlueTick,
                                            showLabel = false
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = formatMessageDateTime(conv.lastMessageTimestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (partnerDistance != null && conv.otherUserId != ADMIN_SUPPORT_USER_ID) {
                                        Text(
                                            text = "${partnerProfile.locationName} • $partnerDistance km away",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = conv.lastMessageText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { pendingDeleteConversation = conv },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("delete_conversation_${conv.otherUserId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Chat",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Direct Audio Call Button on Conversation Row (Video Call removed)
                                IconButton(
                                    onClick = { onStartCall(partnerProfile, CallMode.AUDIO) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                                        .testTag("conv_audio_call_${conv.otherUserId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Audio Call",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
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

@Composable
private fun ActiveChatThreadView(
    currentUserId: String,
    myProfile: UserProfile,
    partner: UserProfile,
    distanceKm: Double,
    messages: List<ChatMessage>,
    canSend: Boolean,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendPhoto: (String) -> Unit,
    onStartAudioCall: () -> Unit,
    onDeleteChat: () -> Unit = {},
    onBlock: () -> Unit,
    onUpgradePremium: () -> Unit
) {
    val context = LocalContext.current
    var messageInput by remember { mutableStateOf("") }
    var fullScreenPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var showDeleteThreadDialog by remember { mutableStateOf(false) }
    val isAdminSupportPartner = partner.userId == ADMIN_SUPPORT_USER_ID || partner.isAdminUser

    if (showDeleteThreadDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteThreadDialog = false },
            title = { Text("Delete Chat?") },
            text = { Text("Delete all messages with ${partner.fullName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteThreadDialog = false
                        onDeleteChat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteThreadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = encodeUriToBase64Thumbnail(context.contentResolver, uri)
            if (!encoded.isNullOrBlank()) {
                onSendPhoto(encoded)
            }
        }
    }

    val audioCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onStartAudioCall()
    }

    if (fullScreenPhotoBase64 != null) {
        val bitmap = remember(fullScreenPhotoBase64) {
            try {
                val bytes = Base64.decode(fullScreenPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { fullScreenPhotoBase64 = null },
            title = { Text("Shared Photo") },
            text = {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Shared photo preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { fullScreenPhotoBase64 = null }) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
    ) {
        // Chat Top Bar with Back, Partner Age & Distance, Verified Blue Tick, Audio Call, & Block
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                ProfileAvatarView(
                    photoBase64 = partner.photoBase64,
                    displayName = partner.fullName,
                    size = 44.dp,
                    shape = CircleShape,
                    isPremium = partner.isPremium,
                    isVerified = partner.isVerifiedWithBlueTick
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isAdminSupportPartner) {
                                "Admin Support • Official Help"
                            } else {
                                "${partner.fullName}, ${partner.age} yrs"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VerifiedBlueTickBadge(
                            isVerified = partner.isVerifiedWithBlueTick,
                            showLabel = false
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isAdminSupportPartner) Icons.Default.SupportAgent else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isAdminSupportPartner) {
                                "Official Support"
                            } else {
                                "${partner.locationName.substringBefore(",")} • $distanceKm km away"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Audio Call Button (Video Call removed)
                IconButton(
                    onClick = {
                        val hasAudio = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudio) {
                            onStartAudioCall()
                        } else {
                            audioCallPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.testTag("chat_audio_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Audio Call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showDeleteThreadDialog = true },
                    modifier = Modifier.testTag("chat_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                if (!isAdminSupportPartner) {
                    // Block Button
                    IconButton(
                        onClick = onBlock,
                        modifier = Modifier.testTag("chat_block_user_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Block user",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Daily limit notice for free users
        if (!myProfile.isPremium && !isAdminSupportPartner) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Free Plan: ${myProfile.dailyMessageCount}/${MilanViewModel.FREE_DAILY_MESSAGE_LIMIT} messages used",
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = onUpgradePremium) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = PremiumGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upgrade for Unlimited")
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = if (isAdminSupportPartner) {
                                "Message Admin Support below."
                            } else {
                                "Say hi to ${partner.firstName}!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            items(messages, key = { it.id.ifEmpty { it.text + it.senderId + it.photoBase64.take(16) } }) { msg ->
                val isMine = msg.senderId == currentUserId
                val decodedPhoto = remember(msg.photoBase64) {
                    if (msg.photoBase64.length > 50) {
                        try {
                            val bytes = Base64.decode(msg.photoBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 18.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 18.dp
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (decodedPhoto != null) {
                                Image(
                                    bitmap = decodedPhoto.asImageBitmap(),
                                    contentDescription = "Shared photo in chat",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { fullScreenPhotoBase64 = msg.photoBase64 },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge
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

        // Message & Photo Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Share Photo Button
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("chat_attach_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Share Photo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        Text(
                            if (canSend) "Type a message or share photo..." else "Send a Like first to message..."
                        )
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        val trimmed = messageInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendText(trimmed)
                            messageInput = ""
                        }
                    },
                    enabled = canSend && messageInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend && messageInput.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = if (canSend && messageInput.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ActiveCallOverlay(
    myProfile: UserProfile,
    session: ActiveCallSession,
    partnerCallStream: CallStreamPacket? = null,
    onPublishMediaPacket: (String, String, Int) -> Unit = { _, _, _ -> },
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onEndCall: (Int) -> Unit
) {
    val context = LocalContext.current
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var localMicLevel by remember { mutableIntStateOf(0) }

    val runtimeAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPerm = granted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            runtimeAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler {
        onEndCall(elapsedSeconds)
    }

    // Initialize AudioManager voice communication mode once for the call lifecycle
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = session.isSpeakerOn
            audioManager?.isMicrophoneMute = session.isMuted
        } catch (_: Throwable) {
            // Ignore in test/restricted environments
        }
        onDispose {
            try {
                audioManager?.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
                audioManager?.isMicrophoneMute = false
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    // Update speakerphone & microphone mute dynamically
    LaunchedEffect(session.isSpeakerOn, session.isMuted) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = session.isSpeakerOn
            audioManager?.isMicrophoneMute = session.isMuted
        } catch (_: Throwable) {
            // Ignore
        }
    }

    // Real-Time Microphone Audio Capture (8kHz 16-bit PCM) + Throttled Voice Packet Broadcaster
    LaunchedEffect(hasAudioPerm, session.isMuted) {
        withContext(Dispatchers.IO) {
            val sampleRate = 8000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = try {
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            } catch (_: Throwable) {
                -1
            }
            val chunkSamples = 2400 // 300ms at 8000Hz
            val bufferSizeInBytes = maxOf(minBuf, chunkSamples * 2, 4800)
            var audioRecord: AudioRecord? = null

            if (hasAudioPerm && !session.isMuted && minBuf > 0) {
                try {
                    val rec = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSizeInBytes
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        rec.startRecording()
                        audioRecord = rec
                    } else {
                        rec.release()
                    }
                } catch (_: Throwable) {
                    audioRecord = null
                }
            }

            val shortBuffer = ShortArray(chunkSamples)
            val byteBuffer = ByteArray(chunkSamples * 2)

            try {
                while (isActive) {
                    var encodedAudio = ""
                    var computedLevel = 0

                    val recorder = audioRecord
                    if (recorder != null && !session.isMuted) {
                        val readShorts = try {
                            recorder.read(shortBuffer, 0, chunkSamples)
                        } catch (_: Throwable) {
                            0
                        }
                        if (readShorts > 0) {
                            var sumSquares = 0.0
                            for (i in 0 until readShorts) {
                                val s = shortBuffer[i].toInt()
                                sumSquares += (s * s).toDouble()
                                byteBuffer[i * 2] = (s and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            val rms = sqrt(sumSquares / readShorts)
                            computedLevel = ((rms / 4000.0) * 100.0).toInt().coerceIn(0, 100)
                            encodedAudio = Base64.encodeToString(byteBuffer, 0, readShorts * 2, Base64.NO_WRAP)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        localMicLevel = computedLevel
                    }

                    onPublishMediaPacket("", encodedAudio, computedLevel)

                    // Respect Firestore 1 write/sec per document limit
                    delay(800L)
                }
            } finally {
                try {
                    audioRecord?.stop()
                } catch (_: Throwable) {}
                try {
                    audioRecord?.release()
                } catch (_: Throwable) {}
            }
        }
    }

    // Real-Time Partner Audio Playback via AudioTrack (8kHz 16-bit PCM)
    val audioTrackHolder = remember {
        object {
            var track: AudioTrack? = null
        }
    }

    DisposableEffect(Unit) {
        try {
            val sampleRate = 8000
            val minOutBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minOutBuf > 0) {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minOutBuf, 6400))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                audioTrackHolder.track = track
            }
        } catch (_: Throwable) {
            // Ignore in unit test environment
        }
        onDispose {
            try {
                audioTrackHolder.track?.stop()
            } catch (_: Throwable) {}
            try {
                audioTrackHolder.track?.release()
            } catch (_: Throwable) {}
            audioTrackHolder.track = null
        }
    }

    // Play incoming PCM audio packet whenever partnerCallStream emits a new sequence number
    LaunchedEffect(partnerCallStream?.seq) {
        val b64 = partnerCallStream?.audioChunkBase64.orEmpty()
        val track = audioTrackHolder.track
        if (b64.isNotBlank() && track != null) {
            withContext(Dispatchers.IO) {
                try {
                    val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                    if (pcmBytes.isNotEmpty()) {
                        track.write(pcmBytes, 0, pcmBytes.size)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    // Pure Kotlin 1-second call timer
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000L)
            elapsedSeconds += 1
        }
    }

    val isCallConnected = session.isConnected || (partnerCallStream?.isActive == true)
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val formattedTimer = "%02d:%02d".format(mins, secs)
    val statusText = if (!isCallConnected) {
        "Ringing • Waiting for ${session.partner.firstName} to answer ($formattedTimer)"
    } else {
        "Connected Live Audio Call • $formattedTimer"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2B192E),
                        Color(0xFF1A1423),
                        Color(0xFF100D16)
                    )
                )
            )
            .testTag("active_call_overlay")
    ) {
        // Center Partner Info & Live Audio Stream Status
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileAvatarView(
                photoBase64 = session.partner.photoBase64,
                displayName = session.partner.fullName,
                size = 136.dp,
                shape = CircleShape,
                isPremium = session.partner.isPremium,
                isVerified = session.partner.isVerifiedWithBlueTick
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${session.partner.fullName}, ${session.partner.age} yrs",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                VerifiedBlueTickBadge(
                    isVerified = session.partner.isVerifiedWithBlueTick,
                    showLabel = true
                )
            }

            Text(
                text = session.partner.locationName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Surface(
                color = if (isCallConnected) Color(0xFF2E7D32).copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Live Mic & Partner Audio Status Indicator
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            ) {
                val partnerMic = partnerCallStream?.micLevel ?: 0
                val micStatusLabel = when {
                    session.isMuted -> "🔇 Your Mic is Muted"
                    !hasAudioPerm -> "⚠️ Grant Microphone permission to transmit voice"
                    isCallConnected -> "🎙️ Your Mic: ${localMicLevel}% • Partner Audio: ${partnerMic}% • 🔊 Live Voice Active"
                    else -> "🎙️ Mic Ready (${localMicLevel}%) • Ringing ${session.partner.firstName}..."
                }
                Text(
                    text = micStatusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Audio Call Controls (Mute, Speaker, End Call)
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute Microphone
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (session.isMuted) Color(0xFFE57373) else Color.White.copy(alpha = 0.2f))
                        .testTag("call_toggle_mute_button")
                ) {
                    Icon(
                        imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute Microphone",
                        tint = Color.White
                    )
                }

                // Speakerphone Toggle
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (session.isSpeakerOn) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.2f))
                        .testTag("call_toggle_speaker_button")
                ) {
                    Icon(
                        imageVector = if (session.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Toggle Speaker",
                        tint = Color.White
                    )
                }

                // End Call Button
                IconButton(
                    onClick = { onEndCall(elapsedSeconds) },
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD32F2F))
                        .testTag("call_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
