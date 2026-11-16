package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.ADMIN_SUPPORT_USER_ID
import com.example.data.model.ActiveCallSession
import com.example.data.model.BlockRecord
import com.example.data.model.CallMode
import com.example.data.model.CallStreamPacket
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationSummary
import com.example.data.model.DAILY_STREAK_TARGET_SECONDS
import com.example.data.model.DiscoverViewMode
import com.example.data.model.FilterState
import com.example.data.model.GoogleAccountIdentity
import com.example.data.model.LikeRecord
import com.example.data.model.MAX_FREE_DISTANCE_KM
import com.example.data.model.MAX_PREMIUM_DISTANCE_KM
import com.example.data.model.RandomChatMessage
import com.example.data.model.RandomChatPreferences
import com.example.data.model.RandomChatSession
import com.example.data.model.RandomMatchStatus
import com.example.data.model.RandomRevealStep
import com.example.data.model.STREAK_REQUIRED_DAYS
import com.example.data.model.SavedNotificationItem
import com.example.data.model.UNLIMITED_DISTANCE_KM
import com.example.data.model.UserProfile
import com.example.data.model.extractGoogleAccountIdentity
import com.example.data.repository.MilanRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class NotificationEvent(
    val title: String,
    val body: String,
    val isReward: Boolean = false
)

data class MaskedRandomPartnerView(
    val partnerId: String,
    val gender: String,
    val isOnline: Boolean = false,
    val lastActiveAtMs: Long = 0L,
    val revealedName: String? = null,
    val revealedAge: Int? = null,
    val revealedPhotoBase64: String? = null,
    val isVerifiedWithBlueTick: Boolean = false,
    val revealedFullProfile: UserProfile? = null,
    val distanceKm: Double? = null
)

data class ActiveRandomChatSummary(
    val session: RandomChatSession,
    val partnerId: String,
    val partnerGender: String,
    val isPartnerOnline: Boolean,
    val isNameRevealed: Boolean,
    val revealedName: String?
)

class MilanViewModel(
    private val repository: MilanRepository,
    val currentUserId: String,
    val currentUserEmail: String? = null,
    val currentUserDisplayName: String? = null
) : ViewModel() {

    val isCurrentUserAdmin: Boolean =
        currentUserEmail?.trim()?.equals(ADMIN_EMAIL, ignoreCase = true) == true

    // Automatically extracted and locked from the user's Google Account
    val googleIdentity: GoogleAccountIdentity = extractGoogleAccountIdentity(
        displayName = currentUserDisplayName,
        email = currentUserEmail,
        uid = currentUserId
    )

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _discoverViewMode = MutableStateFlow(DiscoverViewMode.LIST)
    val discoverViewMode: StateFlow<DiscoverViewMode> = _discoverViewMode.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _likeGatePromptTarget = MutableStateFlow<UserProfile?>(null)
    val likeGatePromptTarget: StateFlow<UserProfile?> = _likeGatePromptTarget.asStateFlow()

    private val _activeChatPartner = MutableStateFlow<UserProfile?>(null)
    val activeChatPartner: StateFlow<UserProfile?> = _activeChatPartner.asStateFlow()

    private val _activeCallSession = MutableStateFlow<ActiveCallSession?>(null)
    val activeCallSession: StateFlow<ActiveCallSession?> = _activeCallSession.asStateFlow()

    private val _incomingCallAlert = MutableStateFlow<ActiveCallSession?>(null)
    val incomingCallAlert: StateFlow<ActiveCallSession?> = _incomingCallAlert.asStateFlow()

    private var callPacketSeq = 0L
    private val isPublishingCallPacket = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val partnerCallStreamState: StateFlow<CallStreamPacket?> = _activeCallSession
        .map { it?.partner?.userId }
        .distinctUntilChanged()
        .flatMapLatest { partnerUid ->
            if (partnerUid.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observePartnerCallStream(partnerUid)
            }
        }
        .onEach { packet ->
            if (packet != null && packet.isActive && packet.partnerId == currentUserId) {
                val active = _activeCallSession.value
                if (active != null && active.partner.userId == packet.userId && !active.isConnected) {
                    _activeCallSession.value = active.copy(isConnected = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val _lastLocationSyncStatus = MutableStateFlow("GPS Active • Updates every hour")
    val lastLocationSyncStatus: StateFlow<String> = _lastLocationSyncStatus.asStateFlow()

    private val _notificationEvents = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 16)
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()

    private val _savedNotifications = MutableStateFlow<List<SavedNotificationItem>>(emptyList())
    val savedNotifications: StateFlow<List<SavedNotificationItem>> = _savedNotifications.asStateFlow()

    private val _sessionUsageSeconds = MutableStateFlow(0)
    val sessionUsageSeconds: StateFlow<Int> = _sessionUsageSeconds.asStateFlow()

    private val viewModelStartTimeMs = System.currentTimeMillis()
    private val seenNotificationKeys = mutableSetOf<String>()
    private val liveRingingCallMsgIds = mutableSetOf<String>()
    private var activeIncomingCallMsgId: String? = null

    private var knownReceivedLikeIds = setOf<String>()
    private var initialLikesLoaded = false
    private var knownReceivedMessageIds = setOf<String>()
    private var initialMessagesLoaded = false
    private var knownRandomSessionIds = setOf<String>()
    private var initialRandomSessionsLoaded = false
    private var hasSeededInitialNotifications = false

    private val _presenceClockTick = MutableStateFlow(System.currentTimeMillis())
    private var isAppForegrounded = false
    private var presenceHeartbeatJob: Job? = null

    val myProfileState: StateFlow<UiState<UserProfile?>> = repository.observeMyProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing myProfile", error)
            emit(UiState.Error(error.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val publicProfilesFlow = repository.observePublicProfiles()
        .catch { error ->
            Log.w(TAG, "Error observing publicProfiles", error)
            emit(emptyList())
        }

    val allRegisteredProfilesState: StateFlow<List<UserProfile>> = publicProfilesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val sentLikesState: StateFlow<List<LikeRecord>> = repository.observeSentLikes(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing sentLikes", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val receivedLikesState: StateFlow<List<LikeRecord>> = repository.observeReceivedLikes(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing receivedLikes", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val blockedUsersState: StateFlow<List<BlockRecord>> = repository.observeBlockedUsers(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing blockedUsers", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val allMessagesState: StateFlow<List<ChatMessage>> = repository.observeAllUserMessages(
        userId = currentUserId,
        isAdmin = isCurrentUserAdmin
    )
        .catch { error ->
            Log.w(TAG, "Error observing messages", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    // ==================== RANDOM CHAT STATE (UNLIMITED & MUTUAL REVEAL) ====================

    private val _randomChatPreferences = MutableStateFlow(RandomChatPreferences())
    val randomChatPreferences: StateFlow<RandomChatPreferences> = _randomChatPreferences.asStateFlow()

    private val _randomMatchStatus = MutableStateFlow(RandomMatchStatus.IDLE)
    val randomMatchStatus: StateFlow<RandomMatchStatus> = _randomMatchStatus.asStateFlow()

    private val _activeRandomSessionId = MutableStateFlow<String?>(null)
    private var lastEndedRandomSessionId: String? = null
    private var randomMatchJob: Job? = null

    private val _fetchedRandomPartnerProfile = MutableStateFlow<UserProfile?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeRandomSessionState: StateFlow<RandomChatSession?> = _activeRandomSessionId
        .flatMapLatest { sid ->
            if (sid.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeRandomSessionById(sid)
                    .catch { error ->
                        Log.w(TAG, "Error observing random session", error)
                        emit(null)
                    }
            }
        }
        .onEach { session ->
            if (session != null && session.status == "ended" && session.id == _activeRandomSessionId.value) {
                if (session.endedByUserId != currentUserId && _randomMatchStatus.value == RandomMatchStatus.CONNECTED) {
                    _randomMatchStatus.value = RandomMatchStatus.PARTNER_LEFT
                }
            } else if (session != null && session.status == "active" && session.id == _activeRandomSessionId.value) {
                if (_randomMatchStatus.value != RandomMatchStatus.CONNECTED) {
                    _randomMatchStatus.value = RandomMatchStatus.CONNECTED
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val randomChatMessagesState: StateFlow<List<RandomChatMessage>> = _activeRandomSessionId
        .flatMapLatest { sid ->
            if (sid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeRandomChatMessages(sid, currentUserId)
                    .catch { error ->
                        Log.w(TAG, "Error observing random chat messages", error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val myActiveRandomSessionsState: StateFlow<List<RandomChatSession>> =
        repository.observeMyActiveRandomSessions(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing active random sessions", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val activeRandomChatSummariesState: StateFlow<List<ActiveRandomChatSummary>> = combine(
        myActiveRandomSessionsState,
        publicProfilesFlow,
        blockedUsersState,
        _presenceClockTick
    ) { sessions, publicProfiles, blockedList, nowMs ->
        val blockedIds = blockedList.map { it.blockedUserId }.toSet()
        val profileMap = publicProfiles.associateBy { it.userId }
        sessions
            .filter { it.status == "active" && it.partnerIdFor(currentUserId) !in blockedIds }
            .map { session ->
                val partnerId = session.partnerIdFor(currentUserId)
                val partnerProfile = profileMap[partnerId]
                val isPartnerOnline = partnerProfile != null &&
                    partnerProfile.isOnline &&
                    partnerProfile.lastActiveAtMs > 0L &&
                    (nowMs - partnerProfile.lastActiveAtMs) <= 120_000L

                // Strictly keep partner Name hidden (null) until partner agrees (isNameRevealed == true)
                val revealedName = if (session.isNameRevealed) {
                    partnerProfile?.fullName ?: "Milan Member"
                } else {
                    null
                }
                ActiveRandomChatSummary(
                    session = session,
                    partnerId = partnerId,
                    partnerGender = session.partnerGenderFor(currentUserId),
                    isPartnerOnline = isPartnerOnline,
                    isNameRevealed = session.isNameRevealed,
                    revealedName = revealedName
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val maskedRandomPartnerState: StateFlow<MaskedRandomPartnerView?> = combine(
        myProfileState,
        activeRandomSessionState,
        publicProfilesFlow,
        _fetchedRandomPartnerProfile,
        _presenceClockTick
    ) { myState, session, publicProfiles, fetchedPartner, nowMs ->
        if (session == null) return@combine null
        val partnerId = session.partnerIdFor(currentUserId)
        val partnerGender = session.partnerGenderFor(currentUserId)
        val partnerProfile = publicProfiles.firstOrNull { it.userId == partnerId }
            ?: fetchedPartner?.takeIf { it.userId == partnerId }

        val myProfile = (myState as? UiState.Success)?.data
        val distKm = if (myProfile != null && partnerProfile != null) {
            MilanRepository.calculateDistanceKm(
                myProfile.latitude,
                myProfile.longitude,
                partnerProfile.latitude,
                partnerProfile.longitude
            )
        } else {
            null
        }

        val isPartnerOnline = partnerProfile != null &&
            partnerProfile.isOnline &&
            partnerProfile.lastActiveAtMs > 0L &&
            (nowMs - partnerProfile.lastActiveAtMs) <= 120_000L

        // Strict Step-by-Step Privacy Masking: Name, Age, Photo & Full Profile stay null until mutual consent!
        MaskedRandomPartnerView(
            partnerId = partnerId,
            gender = partnerGender,
            isOnline = isPartnerOnline,
            lastActiveAtMs = partnerProfile?.lastActiveAtMs ?: 0L,
            revealedName = if (session.isNameRevealed) partnerProfile?.fullName ?: "Milan Member" else null,
            revealedAge = if (session.isAgeRevealed) partnerProfile?.age else null,
            revealedPhotoBase64 = if (session.isPhotoRevealed) partnerProfile?.photoBase64 else null,
            isVerifiedWithBlueTick = session.isPhotoRevealed && (partnerProfile?.isVerifiedWithBlueTick == true),
            revealedFullProfile = if (session.isProfileRevealed) partnerProfile else null,
            distanceKm = if (session.isProfileRevealed) distKm else null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    /**
     * Release Mode Discovery:
     * - Male users are strictly shown Female profiles.
     * - Female users are strictly shown Male profiles.
     * - Sorted nearest-to-farthest using real GPS Haversine distance.
     * - Free users capped at 50 km max range; Premium users get unlimited range & views.
     */
    val discoverProfilesState: StateFlow<List<Pair<UserProfile, Double>>> = combine(
        myProfileState,
        publicProfilesFlow,
        blockedUsersState,
        _filterState
    ) { myState, firestoreProfiles, blockedList, filters ->
        val myProfile = (myState as? UiState.Success)?.data
        val myLat = myProfile?.latitude ?: 23.0225
        val myLon = myProfile?.longitude ?: 72.5714
        val myGender = myProfile?.gender?.ifBlank { googleIdentity.gender } ?: googleIdentity.gender
        val isPremium = myProfile?.isPremium == true
        val blockedIds = blockedList.map { it.blockedUserId }.toSet()

        // Male -> Female profiles only; Female -> Male profiles only
        val requiredOppositeGender = when {
            myGender.equals("Male", ignoreCase = true) -> "Female"
            myGender.equals("Female", ignoreCase = true) -> "Male"
            else -> null
        }

        val effectiveMaxDistanceKm = if (isPremium) {
            if (filters.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM) Float.MAX_VALUE
            else filters.maxDistanceKm.coerceAtLeast(5f)
        } else {
            filters.maxDistanceKm.coerceIn(5f, MAX_FREE_DISTANCE_KM)
        }

        val filteredAndSorted = firestoreProfiles
            .distinctBy { it.userId }
            .filter { it.userId != currentUserId && it.userId !in blockedIds }
            .map { profile ->
                val distanceKm = MilanRepository.calculateDistanceKm(
                    myLat,
                    myLon,
                    profile.latitude,
                    profile.longitude
                )
                profile to distanceKm
            }
            .filter { (profile, distanceKm) ->
                val matchesGender = if (requiredOppositeGender != null) {
                    profile.gender.equals(requiredOppositeGender, ignoreCase = true)
                } else {
                    filters.selectedGender == "All" ||
                        profile.gender.equals(filters.selectedGender, ignoreCase = true)
                }
                val matchesDistance = if (effectiveMaxDistanceKm == Float.MAX_VALUE) {
                    true
                } else {
                    distanceKm <= effectiveMaxDistanceKm
                }
                val matchesAge = profile.age in filters.minAge..filters.maxAge
                val matchesInterest = filters.selectedInterest == "All" ||
                    profile.interests.any { it.equals(filters.selectedInterest, ignoreCase = true) }
                val matchesSearch = filters.searchQuery.isBlank() ||
                    profile.fullName.contains(filters.searchQuery, ignoreCase = true) ||
                    profile.locationName.contains(filters.searchQuery, ignoreCase = true) ||
                    profile.bio.contains(filters.searchQuery, ignoreCase = true)

                matchesGender && matchesDistance && matchesAge && matchesInterest && matchesSearch
            }
            .sortedBy { it.second } // Strictly nearest to farthest

        if (isPremium) {
            filteredAndSorted
        } else {
            filteredAndSorted.take(FREE_MAX_PROFILE_VIEWS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val conversationsState: StateFlow<List<ConversationSummary>> = combine(
        allMessagesState,
        publicProfilesFlow,
        blockedUsersState
    ) { messages, publicProfiles, blockedList ->
        val blockedIds = blockedList.map { it.blockedUserId }.toSet()
        val profileMap = publicProfiles.associateBy { it.userId }

        messages
            .groupBy { it.chatId }
            .mapNotNull { (chatId, chatMessages) ->
                val lastMsg = chatMessages.lastOrNull() ?: return@mapNotNull null
                val isSelfSender = lastMsg.senderId == currentUserId ||
                    (isCurrentUserAdmin && lastMsg.senderId == ADMIN_SUPPORT_USER_ID)
                val otherId = if (isSelfSender) lastMsg.receiverId else lastMsg.senderId
                if (otherId in blockedIds) return@mapNotNull null
                val otherName = if (isSelfSender) lastMsg.receiverName else lastMsg.senderName
                val resolvedProfile = profileMap[otherId] ?: if (otherId == ADMIN_SUPPORT_USER_ID) {
                    buildAdminSupportProfile(publicProfiles)
                } else {
                    null
                }
                ConversationSummary(
                    chatId = chatId,
                    otherUserId = otherId,
                    otherUserName = otherName,
                    otherUserProfile = resolvedProfile,
                    lastMessageText = lastMsg.text,
                    lastMessageTimestamp = lastMsg.createdAt,
                    messageCount = chatMessages.size
                )
            }
            .sortedByDescending { it.lastMessageTimestamp?.seconds ?: 0L }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    init {
        observeProfileLifecycleAndExpiry()
        observeIncomingNotifications()
        startDailyUsageAndStreakTracker()
    }

    private fun recordSavedNotification(
        title: String,
        body: String,
        isReward: Boolean = false,
        timestampMs: Long = System.currentTimeMillis(),
        emitPush: Boolean = true,
        uniqueKey: String? = null
    ) {
        val dedupeKey = uniqueKey?.takeIf { it.isNotBlank() }
            ?: "notif_${title.trim().lowercase(Locale.US)}_${body.trim().lowercase(Locale.US)}"

        if (!seenNotificationKeys.add(dedupeKey)) {
            return
        }
        if (_savedNotifications.value.any { it.id == dedupeKey || (it.title == title && it.body == body) }) {
            return
        }

        val newItem = SavedNotificationItem(
            id = dedupeKey,
            title = title,
            body = body,
            timestampMs = timestampMs,
            isReward = isReward,
            isRead = !emitPush
        )
        _savedNotifications.value = (listOf(newItem) + _savedNotifications.value)
            .distinctBy { it.id }
            .sortedByDescending { it.timestampMs }
            .take(100)

        if (emitPush) {
            _notificationEvents.tryEmit(
                NotificationEvent(
                    title = title,
                    body = body,
                    isReward = isReward
                )
            )
        }
    }

    fun markAllNotificationsRead() {
        _savedNotifications.value = _savedNotifications.value.map { it.copy(isRead = true) }
    }

    fun clearSavedNotifications() {
        _savedNotifications.value = emptyList()
    }

    /**
     * Monitors the user's profile for:
     * 1. Initial notification history seeding
     * 2. Verification status updates (Verified / Unverified)
     * 3. Automatic Premium expiration -> downgrade to Free user
     * 4. Automatic 6-day streak completion -> 24-hour Free Premium activation
     */
    private fun observeProfileLifecycleAndExpiry() {
        viewModelScope.launch {
            myProfileState.collect { state ->
                val profile = (state as? UiState.Success)?.data ?: return@collect

                if (!hasSeededInitialNotifications) {
                    hasSeededInitialNotifications = true
                    recordSavedNotification(
                        title = "Google Account Verified ✓",
                        body = "Profile synced from Google Account: ${profile.fullName}, ${profile.age} yrs (${profile.gender}).",
                        isReward = false,
                        emitPush = false,
                        uniqueKey = "seed_google_${profile.userId}"
                    )
                    if (profile.isPremium) {
                        val expiryText = if (profile.premiumExpiresAt > 0L) {
                            val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                            "Valid until ${fmt.format(Date(profile.premiumExpiresAt))}"
                        } else {
                            "Active"
                        }
                        recordSavedNotification(
                            title = "Milan ${profile.premiumPlan} Premium Active 👑",
                            body = "$expiryText • Unlimited Likes, Messages & Views enabled.",
                            isReward = true,
                            emitPush = false,
                            uniqueKey = "seed_premium_${profile.premiumPlan}_${profile.premiumExpiresAt}"
                        )
                    }
                }

                // Save deduplicated notification if profile verification status is Verified or Unverified ("Rejected")
                if (profile.isVerifiedWithBlueTick) {
                    recordSavedNotification(
                        title = "Profile Verified ✓ (Blue Tick)",
                        body = "Admin verified your Live Face Video! Your profile now displays 'Verified User' and the Blue Tick.",
                        isReward = true,
                        timestampMs = if (profile.verificationSubmittedAt > 0L) profile.verificationSubmittedAt else System.currentTimeMillis(),
                        emitPush = false,
                        uniqueKey = "verify_status_verified_${profile.verificationSubmittedAt}"
                    )
                } else if (profile.verificationStatus == "Rejected") {
                    recordSavedNotification(
                        title = "Profile Unverified • Re-record Video ⚠️",
                        body = "Admin marked your profile as Unverified. Please record a new Live Face Verification Video and submit it again.",
                        isReward = false,
                        timestampMs = if (profile.verificationSubmittedAt > 0L) profile.verificationSubmittedAt else System.currentTimeMillis(),
                        emitPush = false,
                        uniqueKey = "verify_status_rejected_${profile.verificationSubmittedAt}"
                    )
                }

                // Automatically expire ANY Premium plan (FreeReward1Day, Monthly, Yearly) once premiumExpiresAt passes
                val nowMs = System.currentTimeMillis()
                if (profile.isPremium && profile.premiumExpiresAt > 0L && nowMs >= profile.premiumExpiresAt) {
                    downgradeExpiredPremiumToFree(profile.premiumPlan)
                    return@collect
                }

                // If user has reached 6 streak days on a Free plan, automatically grant 24 hours of Free Premium
                if (!profile.isPremium && profile.streakDays >= STREAK_REQUIRED_DAYS) {
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val res = repository.updateUsageStreakProgress(
                        currentProfile = profile,
                        newStreakDays = 0,
                        newTodaySeconds = profile.todayUsageSeconds,
                        todayDate = profile.lastUsageDate.ifBlank { todayStr },
                        completedDate = todayStr,
                        grantFreeRewardDay = true
                    )
                    if (res.isSuccess) {
                        val rewardMsg = "6-Day Streak Completed! 24-Hour Free Milan Gold Premium has been activated automatically."
                        _statusBanner.value = rewardMsg
                        recordSavedNotification(
                            title = "🎁 24-Hour Free Premium Activated!",
                            body = rewardMsg,
                            isReward = true,
                            emitPush = true,
                            uniqueKey = "streak_reward_$todayStr"
                        )
                    }
                }
            }
        }
    }

    private suspend fun downgradeExpiredPremiumToFree(expiredPlanName: String) {
        val res = repository.upgradeToPremium("Free", expiresAtMillis = 0L)
        if (res.isSuccess) {
            _filterState.value = _filterState.value.copy(
                maxDistanceKm = _filterState.value.maxDistanceKm.coerceAtMost(MAX_FREE_DISTANCE_KM)
            )
            val msg = "Your $expiredPlanName Premium period has ended. Your account is now on the Free Plan."
            _statusBanner.value = msg
            recordSavedNotification(
                title = "Premium Plan Ended",
                body = msg,
                isReward = false,
                emitPush = true,
                uniqueKey = "expired_plan_${expiredPlanName}_${System.currentTimeMillis() / 60000L}"
            )
        }
    }

    private fun observeIncomingNotifications() {
        // Observe incoming Random Chat sessions anonymously (NEVER reveal Name/Age/Photo before mutual consent)
        viewModelScope.launch {
            myActiveRandomSessionsState.collect { sessions ->
                val currentIds = sessions.map { it.id }.toSet()
                if (initialRandomSessionsLoaded) {
                    sessions.forEach { sess ->
                        if (sess.id !in knownRandomSessionIds && sess.id != _activeRandomSessionId.value) {
                            val gender = sess.partnerGenderFor(currentUserId)
                            val createdMs = sess.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            recordSavedNotification(
                                title = "New Random Chat Match 🎲",
                                body = "Anonymous ($gender) started a Random Chat with you! Name stays hidden until you both say Yes.",
                                isReward = false,
                                timestampMs = createdMs,
                                emitPush = true,
                                uniqueKey = "rnd_sess_${sess.id}"
                            )
                        }
                    }
                }
                initialRandomSessionsLoaded = true
                knownRandomSessionIds = currentIds
            }
        }

        viewModelScope.launch {
            receivedLikesState.collect { likes ->
                val currentIds = likes.map { it.id.ifEmpty { "${it.fromUserId}_${it.toUserId}" } }.toSet()
                likes.forEach { like ->
                    val likeId = like.id.ifEmpty { "${like.fromUserId}_${like.toUserId}" }
                    val likeTimeMs = like.createdAt?.toDate()?.time ?: 0L
                    val effectiveTimeMs = if (likeTimeMs > 0L) likeTimeMs else System.currentTimeMillis()
                    val isNewLiveLike = initialLikesLoaded &&
                        likeId !in knownReceivedLikeIds &&
                        (likeTimeMs == 0L || likeTimeMs >= viewModelStartTimeMs - 30_000L)

                    recordSavedNotification(
                        title = if (isNewLiveLike) "New Like Received! ❤️" else "Liked by ${like.fromUserName} ❤️",
                        body = "${like.fromUserName} liked your profile!",
                        isReward = false,
                        timestampMs = effectiveTimeMs,
                        emitPush = isNewLiveLike,
                        uniqueKey = "like_$likeId"
                    )
                }
                if (likes.isNotEmpty()) {
                    initialLikesLoaded = true
                }
                knownReceivedLikeIds = currentIds
            }
        }

        viewModelScope.launch {
            allMessagesState.collect { messages ->
                val incoming = messages.filter {
                    it.receiverId == currentUserId ||
                        (isCurrentUserAdmin && it.receiverId == ADMIN_SUPPORT_USER_ID && it.senderId != currentUserId)
                }
                val currentIds = incoming.map { it.id }.toSet()

                incoming.forEach { msg ->
                    val msgTimeMs = msg.createdAt?.toDate()?.time ?: 0L
                    val effectiveTimeMs = if (msgTimeMs > 0L) msgTimeMs else viewModelStartTimeMs
                    val ageMs = (System.currentTimeMillis() - effectiveTimeMs).coerceAtLeast(0L)
                    val isLiveRecentMessage = (msgTimeMs == 0L && initialMessagesLoaded && msg.id !in knownReceivedMessageIds) ||
                        (msgTimeMs >= viewModelStartTimeMs - 30_000L && ageMs <= 45_000L)

                    val isCall = msg.messageType == "audio_call" || msg.messageType == "video_call"
                    if (isCall) {
                        val chatMessages = messages.filter { it.chatId == msg.chatId }
                        val msgIdx = chatMessages.indexOfFirst { it.id == msg.id }
                        val subsequentInChat = if (msgIdx >= 0) chatMessages.drop(msgIdx + 1) else emptyList()

                        when {
                            msg.text.contains("Answered", ignoreCase = true) -> {
                                if (isLiveRecentMessage) {
                                    val active = _activeCallSession.value
                                    if (active != null && active.partner.userId == msg.senderId) {
                                        _activeCallSession.value = active.copy(isConnected = true)
                                    }
                                }
                            }

                            msg.text.contains("Declined", ignoreCase = true) -> {
                                if (isLiveRecentMessage) {
                                    val active = _activeCallSession.value
                                    if (active != null && active.partner.userId == msg.senderId) {
                                        _activeCallSession.value = null
                                        _statusBanner.value = "${msg.senderName} is busy or declined the call."
                                    }
                                    if (_incomingCallAlert.value?.partner?.userId == msg.senderId) {
                                        _incomingCallAlert.value = null
                                        activeIncomingCallMsgId = null
                                    }
                                }
                            }

                            msg.text.contains("ended", ignoreCase = true) -> {
                                if (isLiveRecentMessage) {
                                    val active = _activeCallSession.value
                                    if (active != null && active.partner.userId == msg.senderId) {
                                        _activeCallSession.value = null
                                        _statusBanner.value = "Call with ${msg.senderName} ended."
                                    }
                                    if (_incomingCallAlert.value?.partner?.userId == msg.senderId) {
                                        // Caller hung up before receiver answered -> Missed Call!
                                        val startedId = activeIncomingCallMsgId ?: msg.id
                                        _incomingCallAlert.value = null
                                        activeIncomingCallMsgId = null
                                        recordSavedNotification(
                                            title = "Missed Audio Call 📵",
                                            body = "Missed audio call from ${msg.senderName}.",
                                            isReward = false,
                                            timestampMs = effectiveTimeMs,
                                            emitPush = true,
                                            uniqueKey = "missed_call_$startedId"
                                        )
                                    }
                                }
                            }

                            else -> {
                                // Call initiation ("Started an Audio Call")
                                val wasAnswered = subsequentInChat.any { it.text.contains("Answered", ignoreCase = true) }
                                val wasDeclined = subsequentInChat.any { it.text.contains("Declined", ignoreCase = true) }
                                val wasEnded = subsequentInChat.any { it.text.contains("ended", ignoreCase = true) }

                                if (!wasAnswered && !wasDeclined) {
                                    if (wasEnded || !isLiveRecentMessage) {
                                        // Older call from earlier or already ended without answer -> save as Missed Call!
                                        if (_incomingCallAlert.value?.partner?.userId == msg.senderId && activeIncomingCallMsgId == msg.id) {
                                            _incomingCallAlert.value = null
                                            activeIncomingCallMsgId = null
                                        }
                                        recordSavedNotification(
                                            title = "Missed Audio Call 📵",
                                            body = "Missed audio call from ${msg.senderName}.",
                                            isReward = false,
                                            timestampMs = effectiveTimeMs,
                                            emitPush = false,
                                            uniqueKey = "missed_call_${msg.id}"
                                        )
                                    } else {
                                        // Genuinely live incoming audio call right now!
                                        if (liveRingingCallMsgIds.add(msg.id)) {
                                            recordSavedNotification(
                                                title = "Incoming Audio Call 📞",
                                                body = "${msg.senderName} is calling you.",
                                                isReward = false,
                                                timestampMs = effectiveTimeMs,
                                                emitPush = true,
                                                uniqueKey = "incoming_call_${msg.id}"
                                            )
                                            if (_activeCallSession.value == null) {
                                                val callerProfile = allRegisteredProfilesState.value.firstOrNull { it.userId == msg.senderId }
                                                    ?: UserProfile(
                                                        userId = msg.senderId,
                                                        firstName = msg.senderName.substringBefore(" "),
                                                        lastName = msg.senderName.substringAfter(" ", ""),
                                                        age = 24,
                                                        gender = if (googleIdentity.gender == "Male") "Female" else "Male",
                                                        locationName = "Nearby"
                                                    )
                                                activeIncomingCallMsgId = msg.id
                                                _incomingCallAlert.value = ActiveCallSession(
                                                    partner = callerProfile,
                                                    mode = CallMode.AUDIO,
                                                    isMuted = false,
                                                    isSpeakerOn = true,
                                                    isCameraEnabled = false,
                                                    isFrontCamera = true,
                                                    isIncoming = true,
                                                    isConnected = false
                                                )
                                                // Auto-convert unanswered ringing call to Missed Call after 45 seconds
                                                viewModelScope.launch {
                                                    delay(45_000L)
                                                    if (_incomingCallAlert.value?.partner?.userId == msg.senderId &&
                                                        activeIncomingCallMsgId == msg.id &&
                                                        _activeCallSession.value == null
                                                    ) {
                                                        _incomingCallAlert.value = null
                                                        activeIncomingCallMsgId = null
                                                        recordSavedNotification(
                                                            title = "Missed Audio Call 📵",
                                                            body = "Missed audio call from ${msg.senderName}.",
                                                            isReward = false,
                                                            timestampMs = System.currentTimeMillis(),
                                                            emitPush = true,
                                                            uniqueKey = "missed_call_${msg.id}"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular text or photo chat message
                        val isNewLiveMsg = initialMessagesLoaded &&
                            msg.id !in knownReceivedMessageIds &&
                            isLiveRecentMessage

                        recordSavedNotification(
                            title = "Message from ${msg.senderName} 💬",
                            body = msg.text,
                            isReward = msg.text.contains("Verified", ignoreCase = true) && msg.text.contains("Premium", ignoreCase = true),
                            timestampMs = effectiveTimeMs,
                            emitPush = isNewLiveMsg,
                            uniqueKey = "msg_${msg.id}"
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    initialMessagesLoaded = true
                }
                knownReceivedMessageIds = currentIds
            }
        }
    }

    /**
     * Tracks daily active usage in real time & checks Premium expiration:
     * - Checks every 5 seconds if any Premium plan (FreeReward1Day, Monthly, Yearly) has expired and automatically reverts to Free.
     * - Paid Premium users (Monthly / Yearly) have the 6-day streak hidden/skipped while active.
     * - When daily usage reaches 15 minutes (900 seconds) for 6 days, automatically activates 24 hours of Free Premium.
     */
    private fun startDailyUsageAndStreakTracker() {
        viewModelScope.launch {
            var unsyncedSeconds = 0
            var clockLoops = 0
            while (isActive) {
                delay(5_000L) // Tick every 5 seconds for responsive streak & expiration checks
                clockLoops += 1
                if (clockLoops % 6 == 0) {
                    _presenceClockTick.value = System.currentTimeMillis()
                }
                val myProfile = (myProfileState.value as? UiState.Success)?.data ?: continue

                // 1. Check if ANY Premium subscription or 24-hour reward has expired
                if (myProfile.isPremium && myProfile.premiumExpiresAt > 0L &&
                    System.currentTimeMillis() >= myProfile.premiumExpiresAt
                ) {
                    downgradeExpiredPremiumToFree(myProfile.premiumPlan)
                    continue
                }

                // 2. Paid Premium users (Monthly / Yearly) do not use the 6-day streak tracker
                if (myProfile.isPaidPremium) {
                    unsyncedSeconds = 0
                    continue
                }

                _sessionUsageSeconds.value += 5
                unsyncedSeconds += 5

                val projectedTodaySeconds = myProfile.todayUsageSeconds + unsyncedSeconds
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val justReachedDailyTarget = projectedTodaySeconds >= DAILY_STREAK_TARGET_SECONDS &&
                    myProfile.lastStreakCompletedDate != todayStr

                // Sync immediately when daily 15-min target is reached, or every 25 seconds
                if (justReachedDailyTarget || unsyncedSeconds >= 25) {
                    val delta = unsyncedSeconds
                    unsyncedSeconds = 0
                    syncUsageTick(myProfile, delta)
                }
            }
        }
    }

    private suspend fun syncUsageTick(myProfile: UserProfile, addedSeconds: Int) {
        if (myProfile.isPaidPremium) return
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())
        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = sdf.format(calYesterday.time)

        val isSameDay = myProfile.lastUsageDate == todayStr
        val baseSeconds = if (isSameDay) myProfile.todayUsageSeconds else 0
        val newTodaySeconds = (baseSeconds + addedSeconds).coerceAtMost(86400)

        var currentStreak = when (myProfile.lastStreakCompletedDate) {
            todayStr, yesterdayStr -> myProfile.streakDays
            else -> 0
        }

        var completedDate = myProfile.lastStreakCompletedDate
        var grantFreeDay = false

        if (newTodaySeconds >= DAILY_STREAK_TARGET_SECONDS && completedDate != todayStr) {
            currentStreak += 1
            completedDate = todayStr

            if (currentStreak >= STREAK_REQUIRED_DAYS) {
                grantFreeDay = true
                currentStreak = 0
            }
        }

        val res = repository.updateUsageStreakProgress(
            currentProfile = myProfile,
            newStreakDays = currentStreak,
            newTodaySeconds = newTodaySeconds,
            todayDate = todayStr,
            completedDate = completedDate,
            grantFreeRewardDay = grantFreeDay
        )

        if (res.isSuccess && grantFreeDay) {
            val msg = "Congratulations! Your 6-day × 15-minute streak is complete. 24-Hour Free Milan Gold Premium is now automatically active!"
            _statusBanner.value = msg
            recordSavedNotification(
                title = "🎁 24-Hour Free Premium Unlocked!",
                body = msg,
                isReward = true,
                emitPush = true
            )
        }
    }

    fun setDiscoverViewMode(mode: DiscoverViewMode) {
        _discoverViewMode.value = mode
    }

    fun updateFilters(newFilters: FilterState) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        val isPremium = myProfile?.isPremium == true
        val clampedDistance = if (isPremium) {
            newFilters.maxDistanceKm.coerceIn(5f, MAX_PREMIUM_DISTANCE_KM)
        } else {
            newFilters.maxDistanceKm.coerceIn(5f, MAX_FREE_DISTANCE_KM)
        }
        _filterState.value = newFilters.copy(maxDistanceKm = clampedDistance)
    }

    fun resetFilters() {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        val defaultDist = if (myProfile?.isPremium == true) MAX_PREMIUM_DISTANCE_KM else MAX_FREE_DISTANCE_KM
        _filterState.value = FilterState(maxDistanceKm = defaultDist)
    }

    fun updateMyLocation(latitude: Double, longitude: Double, locationName: String, showBanner: Boolean = false) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.updateUserLocation(latitude, longitude, locationName)
            if (res.isSuccess) {
                val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                _lastLocationSyncStatus.value = "Updated at $timeStr • Hourly GPS Active"
                if (showBanner) {
                    _statusBanner.value = "Location updated to $locationName"
                }
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun dismissLikeGatePrompt() {
        _likeGatePromptTarget.value = null
    }

    fun closeChatScreen() {
        _activeChatPartner.value = null
    }

    /**
     * Enforces that Name, Gender, and Age are strictly populated from the user's Google Account
     * and cannot be manually altered.
     */
    fun saveUserProfile(profile: UserProfile, onComplete: () -> Unit = {}) {
        val existingProfile = (myProfileState.value as? UiState.Success)?.data
        val alreadyExists = existingProfile != null
        val lockedFirstName = existingProfile?.firstName?.takeIf { it.isNotBlank() } ?: googleIdentity.firstName
        val lockedLastName = existingProfile?.lastName?.takeIf { it.isNotBlank() } ?: googleIdentity.lastName
        val lockedAge = existingProfile?.age?.takeIf { it in 18..100 } ?: googleIdentity.age
        val lockedBirthDate = existingProfile?.birthDate?.takeIf { it.isNotBlank() } ?: googleIdentity.birthDate
        val lockedGender = existingProfile?.gender?.takeIf { it in listOf("Male", "Female") } ?: googleIdentity.gender

        val enforcedProfile = profile.copy(
            firstName = if (isCurrentUserAdmin) "admin" else lockedFirstName,
            lastName = lockedLastName,
            age = lockedAge,
            birthDate = lockedBirthDate,
            gender = lockedGender,
            role = if (isCurrentUserAdmin) "admin" else "user"
        )
        viewModelScope.launch {
            val result = repository.createOrSaveProfile(enforcedProfile, isExistingProfile = alreadyExists)
            if (result.isSuccess) {
                _statusBanner.value = "Profile saved with verified Google Account details!"
                onComplete()
            } else {
                _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    fun toggleLike(targetProfile: UserProfile) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        val currentLikes = sentLikesState.value
        val alreadyLiked = currentLikes.any { it.toUserId == targetProfile.userId }

        if (!alreadyLiked && !myProfile.isPremium && currentLikes.size >= FREE_MAX_LIKES_LIMIT) {
            _statusBanner.value = "Free plan limit ($FREE_MAX_LIKES_LIMIT Likes) reached! Upgrade to Premium for unlimited likes."
            return
        }

        viewModelScope.launch {
            if (alreadyLiked) {
                repository.removeLike(targetProfile.userId)
                _statusBanner.value = "Removed like from ${targetProfile.firstName}"
            } else {
                val res = repository.sendLike(
                    fromUserName = myProfile.fullName,
                    toUserId = targetProfile.userId,
                    toUserName = targetProfile.fullName
                )
                if (res.isSuccess) {
                    _statusBanner.value = "You liked ${targetProfile.firstName}! You can now chat & call."
                } else {
                    _statusBanner.value = "Failed to send like: ${res.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    private fun isAdminSupportPartner(profile: UserProfile): Boolean {
        return profile.userId == ADMIN_SUPPORT_USER_ID || profile.isAdminUser ||
            profile.firstName.equals("admin", ignoreCase = true)
    }

    private fun buildAdminSupportProfile(publicProfiles: List<UserProfile>): UserProfile {
        val existingAdmin = publicProfiles.firstOrNull {
            it.isAdminUser || it.firstName.equals("admin", ignoreCase = true)
        }
        if (existingAdmin != null && existingAdmin.userId != currentUserId) {
            return existingAdmin
        }
        return UserProfile(
            id = ADMIN_SUPPORT_USER_ID,
            userId = ADMIN_SUPPORT_USER_ID,
            firstName = "admin",
            lastName = "Support",
            age = 28,
            gender = "Male",
            bio = "Official Milan Admin Support • Ask any question about your account, payments, or features.",
            locationName = "Milan Help Desk",
            isPremium = true,
            role = "admin"
        )
    }

    fun openAdminSupportChat(onNavigateToChats: () -> Unit) {
        val adminProfile = buildAdminSupportProfile(allRegisteredProfilesState.value)
        _activeChatPartner.value = adminProfile
        onNavigateToChats()
    }

    fun initiateChatWithUser(targetProfile: UserProfile, onNavigateToChats: () -> Unit) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        val hasLiked = sentLikesState.value.any { it.toUserId == targetProfile.userId } ||
            receivedLikesState.value.any { it.fromUserId == targetProfile.userId }

        if (myProfile.isPremium || hasLiked || isAdminSupportPartner(targetProfile) || isCurrentUserAdmin) {
            _activeChatPartner.value = targetProfile
            onNavigateToChats()
        } else {
            _likeGatePromptTarget.value = targetProfile
        }
    }

    fun likeAndOpenChat(targetProfile: UserProfile, onNavigateToChats: () -> Unit) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.sendLike(
                fromUserName = myProfile.fullName,
                toUserId = targetProfile.userId,
                toUserName = targetProfile.fullName
            )
            _likeGatePromptTarget.value = null
            if (res.isSuccess) {
                _activeChatPartner.value = targetProfile
                onNavigateToChats()
            } else {
                _statusBanner.value = "Failed to send like: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun openExistingConversation(summary: ConversationSummary) {
        val partner = summary.otherUserProfile ?: UserProfile(
            userId = summary.otherUserId,
            firstName = summary.otherUserName.substringBefore(" "),
            lastName = summary.otherUserName.substringAfter(" ", ""),
            age = 25,
            gender = if (googleIdentity.gender == "Male") "Female" else "Male",
            locationName = "Nearby"
        )
        _activeChatPartner.value = partner
    }

    fun deleteNormalConversation(otherUserId: String) {
        if (otherUserId.isBlank()) return
        if (_activeChatPartner.value?.userId == otherUserId) {
            _activeChatPartner.value = null
        }
        viewModelScope.launch {
            val res = repository.deleteNormalChatThread(otherUserId)
            if (res.isSuccess) {
                _statusBanner.value = "Chat deleted."
            } else {
                _statusBanner.value = "Could not delete chat: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun sendChatMessage(receiverProfile: UserProfile, messageText: String) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        val isSupportChat = isAdminSupportPartner(receiverProfile) || isCurrentUserAdmin
        val hasLiked = sentLikesState.value.any { it.toUserId == receiverProfile.userId } ||
            receivedLikesState.value.any { it.fromUserId == receiverProfile.userId }

        if (!isSupportChat && !myProfile.isPremium && myProfile.dailyMessageCount >= FREE_DAILY_MESSAGE_LIMIT) {
            _statusBanner.value = "Daily free message limit ($FREE_DAILY_MESSAGE_LIMIT) reached! Upgrade to Premium for unlimited messaging."
            return
        }

        viewModelScope.launch {
            if (!isSupportChat && !myProfile.isPremium && !hasLiked) {
                repository.sendLike(
                    fromUserName = myProfile.fullName,
                    toUserId = receiverProfile.userId,
                    toUserName = receiverProfile.fullName
                )
            }
            val effectiveDailyCount = if (isSupportChat) {
                (myProfile.dailyMessageCount - 1).coerceAtLeast(0)
            } else {
                myProfile.dailyMessageCount
            }
            val result = repository.sendMessage(
                senderName = myProfile.fullName,
                receiverId = receiverProfile.userId,
                receiverName = receiverProfile.fullName,
                text = messageText,
                currentDailyCount = effectiveDailyCount,
                messageType = "text"
            )
            if (result.isFailure) {
                _statusBanner.value = "Failed to send message: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun sendPhotoMessage(receiverProfile: UserProfile, photoBase64: String, caption: String = "") {
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        val isSupportChat = isAdminSupportPartner(receiverProfile) || isCurrentUserAdmin
        val hasLiked = sentLikesState.value.any { it.toUserId == receiverProfile.userId } ||
            receivedLikesState.value.any { it.fromUserId == receiverProfile.userId }

        if (!isSupportChat && !myProfile.isPremium && myProfile.dailyMessageCount >= FREE_DAILY_MESSAGE_LIMIT) {
            _statusBanner.value = "Daily free message limit ($FREE_DAILY_MESSAGE_LIMIT) reached! Upgrade to Premium for unlimited photo & text messaging."
            return
        }

        viewModelScope.launch {
            if (!isSupportChat && !myProfile.isPremium && !hasLiked) {
                repository.sendLike(
                    fromUserName = myProfile.fullName,
                    toUserId = receiverProfile.userId,
                    toUserName = receiverProfile.fullName
                )
            }
            val textCaption = caption.trim().ifEmpty { "📷 Shared a photo" }
            val result = repository.sendMessage(
                senderName = myProfile.fullName,
                receiverId = receiverProfile.userId,
                receiverName = receiverProfile.fullName,
                text = textCaption,
                currentDailyCount = myProfile.dailyMessageCount,
                photoBase64 = photoBase64,
                messageType = "photo"
            )
            if (result.isSuccess) {
                _statusBanner.value = "Photo shared with ${receiverProfile.firstName}!"
            } else {
                _statusBanner.value = "Failed to send photo: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    /**
     * Starts an Audio Call immediately with real-time Firestore signaling.
     * Video calling is disabled per user requirement; Audio calling remains active.
     */
    fun startCall(partner: UserProfile, mode: CallMode = CallMode.AUDIO) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        val hasLiked = sentLikesState.value.any { it.toUserId == partner.userId }

        _incomingCallAlert.value = null
        _activeChatPartner.value = partner
        _activeCallSession.value = ActiveCallSession(
            partner = partner,
            mode = CallMode.AUDIO,
            isMuted = false,
            isSpeakerOn = true,
            isCameraEnabled = false,
            isFrontCamera = true,
            isIncoming = false,
            isConnected = false
        )

        if (myProfile != null && partner.userId != currentUserId) {
            viewModelScope.launch {
                if (!myProfile.isPremium && !hasLiked && !isAdminSupportPartner(partner)) {
                    repository.sendLike(
                        fromUserName = myProfile.fullName,
                        toUserId = partner.userId,
                        toUserName = partner.fullName
                    )
                }
                repository.sendMessage(
                    senderName = myProfile.fullName,
                    receiverId = partner.userId,
                    receiverName = partner.fullName,
                    text = "📞 Started an Audio Call",
                    currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                    messageType = "audio_call"
                )
            }
        }
    }

    fun acceptIncomingCall(onNavigateToChats: () -> Unit) {
        val incoming = _incomingCallAlert.value ?: return
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        _incomingCallAlert.value = null
        _activeChatPartner.value = incoming.partner
        _activeCallSession.value = incoming.copy(mode = CallMode.AUDIO, isCameraEnabled = false, isIncoming = false, isConnected = true)
        onNavigateToChats()

        if (myProfile != null && incoming.partner.userId != currentUserId) {
            viewModelScope.launch {
                repository.sendMessage(
                    senderName = myProfile.fullName,
                    receiverId = incoming.partner.userId,
                    receiverName = incoming.partner.fullName,
                    text = "✅ Answered Audio Call",
                    currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                    messageType = "audio_call"
                )
            }
        }
    }

    fun declineIncomingCall() {
        val incoming = _incomingCallAlert.value ?: return
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        _incomingCallAlert.value = null

        if (myProfile != null && incoming.partner.userId != currentUserId) {
            viewModelScope.launch {
                repository.sendMessage(
                    senderName = myProfile.fullName,
                    receiverId = incoming.partner.userId,
                    receiverName = incoming.partner.fullName,
                    text = "❌ Declined Audio Call",
                    currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                    messageType = "audio_call"
                )
            }
        }
    }

    fun toggleCallMute() {
        _activeCallSession.value = _activeCallSession.value?.let { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleCallSpeaker() {
        _activeCallSession.value = _activeCallSession.value?.let { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun toggleCallCamera() {
        _activeCallSession.value = _activeCallSession.value?.let { it.copy(isCameraEnabled = !it.isCameraEnabled) }
    }

    fun switchCallCamera() {
        _activeCallSession.value = _activeCallSession.value?.let { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun publishCallMediaPacket(
        videoFrameBase64: String = "",
        audioChunkBase64: String = "",
        micLevel: Int = 0
    ) {
        val session = _activeCallSession.value ?: return
        if (session.partner.userId == currentUserId) return
        if (!isPublishingCallPacket.compareAndSet(false, true)) return
        callPacketSeq += 1L
        val seqNum = callPacketSeq
        viewModelScope.launch {
            try {
                repository.publishCallStreamPacket(
                    partnerId = session.partner.userId,
                    callMode = if (session.mode == CallMode.VIDEO) "VIDEO" else "AUDIO",
                    isActive = true,
                    seq = seqNum,
                    videoFrameBase64 = if (session.mode == CallMode.VIDEO && session.isCameraEnabled) videoFrameBase64 else "",
                    audioChunkBase64 = if (!session.isMuted) audioChunkBase64 else "",
                    micLevel = if (!session.isMuted) micLevel else 0
                )
            } finally {
                isPublishingCallPacket.set(false)
            }
        }
    }

    fun endCall(durationSeconds: Int) {
        val session = _activeCallSession.value ?: return
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        _activeCallSession.value = null
        callPacketSeq = 0L
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        val formattedDuration = "%02d:%02d".format(mins, secs)
        val modeLabel = if (session.mode == CallMode.VIDEO) "Video Call" else "Audio Call"
        _statusBanner.value = "$modeLabel with ${session.partner.firstName} ended ($formattedDuration)"

        viewModelScope.launch {
            repository.clearCallStream()
            if (myProfile != null && session.partner.userId != currentUserId) {
                val callType = if (session.mode == CallMode.VIDEO) "video_call" else "audio_call"
                repository.sendMessage(
                    senderName = myProfile.fullName,
                    receiverId = session.partner.userId,
                    receiverName = session.partner.fullName,
                    text = "📴 $modeLabel ended ($formattedDuration)",
                    currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                    messageType = callType
                )
            }
        }
    }

    fun blockUser(targetUserId: String, targetUserName: String) {
        viewModelScope.launch {
            val result = repository.blockUser(targetUserId, targetUserName)
            if (result.isSuccess) {
                if (_activeChatPartner.value?.userId == targetUserId) {
                    _activeChatPartner.value = null
                }
                if (_activeCallSession.value?.partner?.userId == targetUserId) {
                    _activeCallSession.value = null
                }
                _statusBanner.value = "$targetUserName has been blocked."
            } else {
                _statusBanner.value = "Failed to block user: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun unblockUser(blockedUserId: String, blockedUserName: String) {
        viewModelScope.launch {
            val result = repository.unblockUser(blockedUserId)
            if (result.isSuccess) {
                _statusBanner.value = "$blockedUserName has been unblocked."
            }
        }
    }

    /**
     * Submits a 12-digit UPI UTR / Bank Reference number for verification.
     * Premium is NOT activated until the payment is verified!
     */
    fun submitUpiUtrForVerification(planName: String, rawUtr12Digits: String) {
        val cleanUtr = rawUtr12Digits.trim()
        if (!com.example.data.model.isValidUpiUtr12Digits(cleanUtr)) {
            _statusBanner.value = "Invalid UTR! Please enter a valid 12-digit numeric UPI UTR / Ref No. from your payment receipt."
            return
        }
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        val amountInr = if (planName == "Yearly") 999 else 199
        val nowMs = System.currentTimeMillis()

        viewModelScope.launch {
            val res = repository.submitUpiPaymentForVerification(
                planName = planName,
                amountInr = amountInr,
                upiTxnId = cleanUtr,
                paymentDateMs = nowMs
            )
            if (res.isSuccess) {
                val adminProfile = buildAdminSupportProfile(allRegisteredProfilesState.value)
                if (adminProfile.userId != currentUserId) {
                    repository.sendMessage(
                        senderName = myProfile.fullName,
                        receiverId = adminProfile.userId,
                        receiverName = adminProfile.fullName,
                        text = "💳 UPI Payment Verification Request • Plan: $planName (₹$amountInr) • 12-Digit UTR: $cleanUtr • Waiting for Admin verification.",
                        currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                        messageType = "text"
                    )
                }
                val msg = "UPI UTR ($cleanUtr) submitted for verification! Admin will check the ₹$amountInr transaction and activate your $planName Gold Premium."
                _statusBanner.value = msg
                recordSavedNotification(
                    title = "UPI Payment Submitted for Verification ⏳",
                    body = "Plan: $planName (₹$amountInr) • UTR: $cleanUtr • Premium will activate once verified.",
                    isReward = false,
                    timestampMs = nowMs,
                    emitPush = true
                )
            } else {
                _statusBanner.value = "Failed to submit UTR: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    /**
     * Called ONLY when a real UPI app returns a bank-verified callback (RESULT_OK + Status=SUCCESS + responseCode=00 + 12-digit UTR)
     * OR when switching back to Free plan.
     */
    fun activatePremiumAfterUpiPayment(planName: String, verifiedBankUtr: String = "") {
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            if (planName == "Free") {
                val res = repository.upgradeToPremium(
                    planName = "Free",
                    expiresAtMillis = 0L,
                    paymentStatus = "None",
                    pendingPlan = ""
                )
                if (res.isSuccess) {
                    _filterState.value = _filterState.value.copy(
                        maxDistanceKm = _filterState.value.maxDistanceKm.coerceAtMost(MAX_FREE_DISTANCE_KM)
                    )
                    _statusBanner.value = "Your plan has been switched to Free (50 km range)."
                }
                return@launch
            }

            // Guard: Never activate Monthly/Yearly unless a valid 12-digit bank UTR is present
            if ((planName == "Monthly" || planName == "Yearly") &&
                !com.example.data.model.isValidUpiUtr12Digits(verifiedBankUtr)
            ) {
                _statusBanner.value = "Payment verification required! Please complete the UPI payment and verify your 12-digit UTR number first."
                return@launch
            }

            val durationMs = when (planName) {
                "Monthly" -> 30L * 24L * 60L * 60L * 1000L
                "Yearly" -> 365L * 24L * 60L * 60L * 1000L
                "FreeReward1Day" -> 24L * 60L * 60L * 1000L
                else -> 0L
            }
            val amountInr = when (planName) {
                "Monthly" -> 199
                "Yearly" -> 999
                else -> null
            }
            val paymentDateMs = if (amountInr != null) nowMs else null
            val expiresAt = if (durationMs > 0L) nowMs + durationMs else 0L

            val result = repository.upgradeToPremium(
                planName = planName,
                expiresAtMillis = expiresAt,
                paymentAmountInr = amountInr,
                paymentDateMs = paymentDateMs,
                upiTxnId = verifiedBankUtr.ifBlank { null },
                paymentStatus = "Verified",
                pendingPlan = ""
            )
            if (result.isSuccess) {
                _filterState.value = _filterState.value.copy(maxDistanceKm = MAX_PREMIUM_DISTANCE_KM)
                _randomChatPreferences.value = _randomChatPreferences.value.copy(maxDistanceKm = UNLIMITED_DISTANCE_KM.toInt())
                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(nowMs))
                val priceInfo = if (amountInr != null) "₹$amountInr verified (UTR: $verifiedBankUtr) on $dateStr • " else ""
                val msg = "${priceInfo}Milan $planName Gold Premium is now active with Unlimited Likes, Messages & Views!"
                _statusBanner.value = msg
                recordSavedNotification(
                    title = "UPI Payment Verified • $planName Gold Active! 👑",
                    body = msg,
                    isReward = true,
                    timestampMs = nowMs,
                    emitPush = true
                )
            } else {
                _statusBanner.value = "Failed to update plan: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // Admin Panel Operations
    fun adminVerifyUserUpiPayment(targetUser: UserProfile, approve: Boolean) {
        if (!isCurrentUserAdmin) return
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val planToGrant = targetUser.pendingPlan.takeIf { it in listOf("Monthly", "Yearly") } ?: "Monthly"
            if (approve) {
                val expiresAt = when (planToGrant) {
                    "Yearly" -> nowMs + 365L * 24L * 60L * 60L * 1000L
                    else -> nowMs + 30L * 24L * 60L * 60L * 1000L
                }
                val res = repository.adminUpdateUserPremium(
                    targetUserId = targetUser.userId,
                    planName = planToGrant,
                    expiresAtMillis = expiresAt,
                    paymentStatus = "Verified",
                    pendingPlan = ""
                )
                if (res.isSuccess) {
                    _statusBanner.value = "Admin: Verified UTR ${targetUser.lastUpiTxnId} & activated $planToGrant Premium for ${targetUser.fullName}!"
                    if (myProfile != null && targetUser.userId != currentUserId) {
                        repository.sendMessage(
                            senderName = "Admin Support",
                            receiverId = targetUser.userId,
                            receiverName = targetUser.fullName,
                            text = "✅ Payment Verified! Your UPI UTR (${targetUser.lastUpiTxnId}) for ₹${targetUser.lastPaymentAmountInr} has been verified by Admin. Your Milan $planToGrant Gold Premium is now active!",
                            currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                            messageType = "text"
                        )
                    }
                } else {
                    _statusBanner.value = "Admin Error: ${res.exceptionOrNull()?.localizedMessage}"
                }
            } else {
                val res = repository.adminUpdateUserPremium(
                    targetUserId = targetUser.userId,
                    planName = "Free",
                    expiresAtMillis = 0L,
                    paymentStatus = "Rejected",
                    pendingPlan = ""
                )
                if (res.isSuccess) {
                    _statusBanner.value = "Admin: Rejected unverified UTR ${targetUser.lastUpiTxnId} for ${targetUser.fullName}."
                    if (myProfile != null && targetUser.userId != currentUserId) {
                        repository.sendMessage(
                            senderName = "Admin Support",
                            receiverId = targetUser.userId,
                            receiverName = targetUser.fullName,
                            text = "❌ Payment Verification Failed: Your submitted UPI UTR (${targetUser.lastUpiTxnId}) was not found in bank records. Please check your 12-digit UTR number.",
                            currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                            messageType = "text"
                        )
                    }
                } else {
                    _statusBanner.value = "Admin Error: ${res.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    fun adminSetUserPlan(targetUserId: String, targetName: String, planName: String) {
        if (!isCurrentUserAdmin) return
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val expiresAt = when (planName) {
                "FreeReward1Day" -> nowMs + 24L * 60L * 60L * 1000L
                "Monthly" -> nowMs + 30L * 24L * 60L * 60L * 1000L
                "Yearly" -> nowMs + 365L * 24L * 60L * 60L * 1000L
                else -> 0L
            }
            val newStatus = if (planName == "Free") "None" else "Verified"
            val res = repository.adminUpdateUserPremium(
                targetUserId = targetUserId,
                planName = planName,
                expiresAtMillis = expiresAt,
                paymentStatus = newStatus,
                pendingPlan = ""
            )
            if (res.isSuccess) {
                _statusBanner.value = "Admin: Updated $targetName's plan to $planName."
            } else {
                _statusBanner.value = "Admin Error: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updateMyGalleryPhotos(galleryPhotos: List<String>) {
        viewModelScope.launch {
            val res = repository.updateGalleryPhotos(galleryPhotos)
            if (res.isSuccess) {
                _statusBanner.value = "Updated profile gallery (${galleryPhotos.size}/6 photos saved)!"
            } else {
                _statusBanner.value = "Failed to update gallery photos: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun submitLiveFaceVerificationVideo(
        videoFrames: List<String>,
        mainPhotoBase64: String? = null,
        galleryPhotos: List<String>? = null
    ) {
        if (videoFrames.isEmpty()) {
            _statusBanner.value = "Please record your live left-to-right face verification video first!"
            return
        }
        val myProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.submitFaceVerificationVideo(
                videoFrames = videoFrames,
                mainPhotoBase64 = mainPhotoBase64,
                galleryPhotos = galleryPhotos
            )
            if (res.isSuccess) {
                val adminProfile = buildAdminSupportProfile(allRegisteredProfilesState.value)
                if (adminProfile.userId != currentUserId) {
                    repository.sendMessage(
                        senderName = myProfile.fullName,
                        receiverId = adminProfile.userId,
                        receiverName = adminProfile.fullName,
                        text = "🛡️ Live Face Verification Video submitted (${videoFrames.size} frames, 1 main photo + ${galleryPhotos?.size ?: myProfile.galleryPhotos.size} gallery photos). Waiting for Admin Blue Tick verification.",
                        currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                        messageType = "text"
                    )
                }
                _statusBanner.value = "Live Face Video submitted! Admin will review your video & photos and grant your Verified User Blue Tick."
                recordSavedNotification(
                    title = "Face Verification Video Submitted 🛡️",
                    body = "Your left-to-right live face video was sent to Admin. Once approved, your profile will receive the Verified User Blue Tick.",
                    isReward = false,
                    emitPush = true
                )
            } else {
                _statusBanner.value = "Failed to submit verification video: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun adminVerifyUserFaceProfile(targetUser: UserProfile, approve: Boolean) {
        if (!isCurrentUserAdmin) return
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val res = repository.adminVerifyUserProfile(
                targetUserId = targetUser.userId,
                approve = approve
            )
            if (res.isSuccess) {
                if (approve) {
                    _statusBanner.value = "Admin: Verified ${targetUser.fullName}! 'Verified User' & Blue Tick added to their profile."
                    if (myProfile != null && targetUser.userId != currentUserId) {
                        repository.sendMessage(
                            senderName = "Admin Support",
                            receiverId = targetUser.userId,
                            receiverName = targetUser.fullName,
                            text = "☑️ Congratulations ${targetUser.firstName}! Admin verified your live face video and photos. Your profile now has the 'Verified User' badge and Blue Tick!",
                            currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                            messageType = "text"
                        )
                    }
                } else {
                    _statusBanner.value = "Admin: Marked ${targetUser.fullName} as Unverified and asked them to record & submit a new verification video."
                    if (myProfile != null && targetUser.userId != currentUserId) {
                        repository.sendMessage(
                            senderName = "Admin Support",
                            receiverId = targetUser.userId,
                            receiverName = targetUser.fullName,
                            text = "⚠️ Profile Unverified: Admin reviewed your profile and marked it Unverified. Please open your Profile tab, record a new Live Face Verification Video (turning your face from Left to Right), and submit it again.",
                            currentDailyCount = (myProfile.dailyMessageCount - 1).coerceAtLeast(0),
                            messageType = "text"
                        )
                    }
                }
            } else {
                _statusBanner.value = "Admin Verification Error: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun adminDeleteUser(targetUserId: String, targetName: String) {
        if (!isCurrentUserAdmin) return
        viewModelScope.launch {
            val res = repository.adminDeleteUserProfile(targetUserId)
            if (res.isSuccess) {
                _statusBanner.value = "Admin: Deleted $targetName's profile."
            } else {
                _statusBanner.value = "Admin Delete Error: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // ==================== PRESENCE & RANDOM CHAT ACTIONS ====================

    fun setForegroundPresence(isOnline: Boolean) {
        isAppForegrounded = isOnline
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        if (isOnline) {
            presenceHeartbeatJob = viewModelScope.launch {
                while (isActive && isAppForegrounded) {
                    _presenceClockTick.value = System.currentTimeMillis()
                    if ((myProfileState.value as? UiState.Success)?.data != null) {
                        repository.updateUserPresence(isOnline = true)
                    }
                    delay(30_000L)
                }
            }
        } else {
            viewModelScope.launch {
                _presenceClockTick.value = System.currentTimeMillis()
                if ((myProfileState.value as? UiState.Success)?.data != null) {
                    repository.updateUserPresence(isOnline = false)
                }
            }
        }
    }

    fun toggleRandomMeetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val res = repository.setRandomMeetEnabled(enabled)
            if (res.isSuccess) {
                _statusBanner.value = if (enabled) {
                    "Random Meet is ON — Anyone matching your profile can connect & message you in Random Chat anytime."
                } else {
                    "Random Meet is OFF — You are hidden from new Random Chat requests."
                }
            } else {
                _statusBanner.value = "Could not update Random Meet option: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun openExistingRandomSession(sessionId: String) {
        val existing = myActiveRandomSessionsState.value.firstOrNull { it.id == sessionId } ?: return
        randomMatchJob?.cancel()
        randomMatchJob = null
        onRandomSessionConnected(existing)
    }

    fun updateRandomChatPreferences(newPrefs: RandomChatPreferences) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        val isPremium = myProfile?.isPremium == true || myProfile?.isAdminUser == true || isCurrentUserAdmin
        val cleanMin = newPrefs.minAge.coerceIn(18, 65)
        val cleanMax = newPrefs.maxAge.coerceIn(cleanMin, 65)
        val cleanDist = if (isPremium) {
            if (newPrefs.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM.toInt()) {
                UNLIMITED_DISTANCE_KM.toInt()
            } else {
                newPrefs.maxDistanceKm.coerceIn(10, UNLIMITED_DISTANCE_KM.toInt())
            }
        } else {
            newPrefs.maxDistanceKm.coerceIn(10, 500)
        }
        val cleanGender = if (newPrefs.preferredGender in listOf("All", "Male", "Female")) {
            newPrefs.preferredGender
        } else {
            "All"
        }
        _randomChatPreferences.value = RandomChatPreferences(
            preferredGender = cleanGender,
            minAge = cleanMin,
            maxAge = cleanMax,
            maxDistanceKm = cleanDist
        )
    }

    private fun todayDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun getRemainingFreeRandomFinds(profile: UserProfile): Int {
        if (profile.isPremium || profile.isAdminUser || isCurrentUserAdmin) return Int.MAX_VALUE
        val usedToday = if (profile.lastRandomFindDate == todayDateString()) profile.dailyRandomFindCount else 0
        return (FREE_DAILY_RANDOM_FIND_LIMIT - usedToday).coerceAtLeast(0)
    }

    fun startRandomMatchmaking(excludePartnerId: String? = null) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        if (myProfile == null) {
            _statusBanner.value = "Complete your profile first."
            return
        }

        val isUnlimitedFinder = myProfile.isPremium || myProfile.isAdminUser || isCurrentUserAdmin
        val todayStr = todayDateString()
        val usedToday = if (myProfile.lastRandomFindDate == todayStr) myProfile.dailyRandomFindCount else 0
        if (!isUnlimitedFinder && usedToday >= FREE_DAILY_RANDOM_FIND_LIMIT) {
            _statusBanner.value = "Free limit ($FREE_DAILY_RANDOM_FIND_LIMIT/day) reached. Saved chats stay unlimited — upgrade for unlimited finds."
            return
        }

        val previousSid = _activeRandomSessionId.value
        randomMatchJob?.cancel()
        _activeRandomSessionId.value = null
        _fetchedRandomPartnerProfile.value = null
        _randomMatchStatus.value = RandomMatchStatus.SEARCHING
        val recordedFind = AtomicBoolean(false)

        fun consumeOneFindIfNew() {
            if (!isUnlimitedFinder && recordedFind.compareAndSet(false, true)) {
                viewModelScope.launch {
                    repository.recordDailyRandomFind(todayStr, usedToday + 1)
                }
            }
        }

        randomMatchJob = viewModelScope.launch {
            val prefs = _randomChatPreferences.value
            val iUseUnlimitedDist = isUnlimitedFinder && prefs.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM.toInt()
            val effectivePrefs = if (iUseUnlimitedDist) {
                prefs.copy(maxDistanceKm = UNLIMITED_DISTANCE_KM.toInt())
            } else if (!isUnlimitedFinder) {
                prefs.copy(maxDistanceKm = prefs.maxDistanceKm.coerceAtMost(500))
            } else {
                prefs
            }

            val enqueueRes = repository.enqueueForRandomChat(myProfile, effectivePrefs)
            if (enqueueRes.isFailure) {
                _randomMatchStatus.value = RandomMatchStatus.IDLE
                _statusBanner.value = "Unable to start Random Chat: ${enqueueRes.exceptionOrNull()?.localizedMessage}"
                return@launch
            }

            try {
                coroutineScope {
                    // Watcher A: Detect if a compatible peer pairs with us via queue
                    val incomingSessionJob = launch {
                        repository.observeMyLatestActiveRandomSession(currentUserId)
                            .catch { emit(null) }
                            .collect { session ->
                                if (session != null &&
                                    session.status == "active" &&
                                    session.id.isNotBlank() &&
                                    session.id != previousSid &&
                                    session.id != lastEndedRandomSessionId &&
                                    _randomMatchStatus.value == RandomMatchStatus.SEARCHING
                                ) {
                                    consumeOneFindIfNew()
                                    onRandomSessionConnected(session)
                                }
                            }
                    }

                    // Watcher B: Scan waiting queue entries immediately (online first)
                    val queueScannerJob = launch {
                        repository.observeWaitingRandomQueueEntries()
                            .catch { emit(emptyList()) }
                            .collect { waitingList ->
                                if (_randomMatchStatus.value != RandomMatchStatus.SEARCHING) return@collect
                                val blockedIds = blockedUsersState.value.map { it.blockedUserId }.toSet()

                                val candidates = waitingList
                                    .filter { entry ->
                                        entry.userId != currentUserId &&
                                            entry.status == "waiting" &&
                                            entry.userId !in blockedIds &&
                                            entry.userId != excludePartnerId
                                    }
                                    .filter { entry ->
                                        val iWantPartnerGender = effectivePrefs.preferredGender == "All" ||
                                            entry.gender.equals(effectivePrefs.preferredGender, ignoreCase = true)
                                        val partnerWantsMyGender = entry.preferredGender == "All" ||
                                            myProfile.gender.equals(entry.preferredGender, ignoreCase = true)
                                        val partnerInMyAgeRange = entry.age in effectivePrefs.minAge..effectivePrefs.maxAge
                                        val iAmInPartnerAgeRange = myProfile.age in entry.preferredMinAge..entry.preferredMaxAge
                                        val distKm = MilanRepository.calculateDistanceKm(
                                            myProfile.latitude,
                                            myProfile.longitude,
                                            entry.latitude,
                                            entry.longitude
                                        )
                                        val myDistOk = iUseUnlimitedDist || distKm <= effectivePrefs.maxDistanceKm
                                        val partnerDistOk = entry.preferredMaxDistanceKm >= MAX_PREMIUM_DISTANCE_KM.toInt() ||
                                            distKm <= entry.preferredMaxDistanceKm

                                        iWantPartnerGender && partnerWantsMyGender &&
                                            partnerInMyAgeRange && iAmInPartnerAgeRange && myDistOk && partnerDistOk
                                    }

                                for (candidate in candidates) {
                                    if (_randomMatchStatus.value != RandomMatchStatus.SEARCHING) break
                                    if (currentUserId > candidate.userId) {
                                        delay(180L)
                                        if (_randomMatchStatus.value != RandomMatchStatus.SEARCHING) break
                                    }
                                    val pairRes = repository.tryPairWithWaitingUser(myProfile, candidate)
                                    val pairedSession = pairRes.getOrNull()
                                    if (pairedSession != null) {
                                        consumeOneFindIfNew()
                                        onRandomSessionConnected(pairedSession)
                                        repository.sendRandomChatMessage(
                                            session = pairedSession,
                                            text = "🎲 Random Chat Connected. Only Gender is visible until both agree.",
                                            messageType = "system"
                                        )
                                        break
                                    }
                                }
                            }
                    }

                    // Watcher C: Instant Matchmaker (Online Random Meet users first, else Offline Random Meet users)
                    val directRandomMeetJob = launch {
                        delay(150L)
                        if (_randomMatchStatus.value != RandomMatchStatus.SEARCHING) return@launch

                        val blockedIds = blockedUsersState.value.map { it.blockedUserId }.toSet()
                        val activeSessions = myActiveRandomSessionsState.value
                        val activePartnerIds = activeSessions.map { it.partnerIdFor(currentUserId) }.toSet()

                        val allPublic = allRegisteredProfilesState.value.ifEmpty {
                            runCatching { repository.observePublicProfiles().first() }.getOrDefault(emptyList())
                        }

                        val allMatches = allPublic
                            .filter { profile ->
                                profile.userId != currentUserId &&
                                    profile.allowRandomMeet &&
                                    profile.userId !in blockedIds
                            }
                            .filter { profile ->
                                val matchesGender = effectivePrefs.preferredGender == "All" ||
                                    profile.gender.equals(effectivePrefs.preferredGender, ignoreCase = true)
                                val matchesAge = profile.age in effectivePrefs.minAge..effectivePrefs.maxAge
                                val distKm = MilanRepository.calculateDistanceKm(
                                    myProfile.latitude,
                                    myProfile.longitude,
                                    profile.latitude,
                                    profile.longitude
                                )
                                val distOk = iUseUnlimitedDist || distKm <= effectivePrefs.maxDistanceKm
                                matchesGender && matchesAge && distOk
                            }

                        val freshPool = allMatches.filter { it.userId !in activePartnerIds }
                        val poolExcludingLast = freshPool.filter { it.userId != excludePartnerId }
                            .ifEmpty { freshPool }

                        // Priority: Live Online users first, otherwise Offline users immediately
                        val onlinePreferred = poolExcludingLast.filter { it.isCurrentlyOnline }.shuffled()
                        val offlineFallback = poolExcludingLast.filter { !it.isCurrentlyOnline }.shuffled()
                        val chosenPartner = (onlinePreferred + offlineFallback).firstOrNull()

                        if (chosenPartner != null && _randomMatchStatus.value == RandomMatchStatus.SEARCHING) {
                            val directRes = repository.createDirectRandomSession(myProfile, chosenPartner)
                            val createdSession = directRes.getOrNull()
                            if (createdSession != null) {
                                consumeOneFindIfNew()
                                onRandomSessionConnected(createdSession)
                                val onlineLabel = if (chosenPartner.isCurrentlyOnline) "Online" else "Offline"
                                repository.sendRandomChatMessage(
                                    session = createdSession,
                                    text = "🎲 Connected ($onlineLabel). Only Gender is visible until both say Yes.",
                                    messageType = "system"
                                )
                            }
                        } else if (_randomMatchStatus.value == RandomMatchStatus.SEARCHING) {
                            // If all matching Random Meet users already have saved sessions, open an existing saved session (online first)
                            val activeByPartner = activeSessions.associateBy { it.partnerIdFor(currentUserId) }
                            val fallbackMatches = allMatches.filter { it.userId != excludePartnerId }.ifEmpty { allMatches }
                            val reusablePartner = (fallbackMatches.filter { it.isCurrentlyOnline }.shuffled() +
                                fallbackMatches.filter { !it.isCurrentlyOnline }.shuffled())
                                .firstOrNull { activeByPartner.containsKey(it.userId) }
                            val reusableSession = reusablePartner?.let { activeByPartner[it.userId] }
                            if (reusableSession != null) {
                                onRandomSessionConnected(reusableSession)
                            } else if (_randomMatchStatus.value == RandomMatchStatus.SEARCHING) {
                                _randomMatchStatus.value = RandomMatchStatus.IDLE
                                _statusBanner.value = "No matching Random Meet users found right now."
                            }
                        }
                    }

                    incomingSessionJob.join()
                    queueScannerJob.join()
                    directRandomMeetJob.join()
                }
            } finally {
                if (_randomMatchStatus.value == RandomMatchStatus.SEARCHING) {
                    repository.leaveRandomChatQueue()
                }
            }
        }
    }

    private fun onRandomSessionConnected(session: RandomChatSession) {
        if (_activeRandomSessionId.value == session.id && _randomMatchStatus.value == RandomMatchStatus.CONNECTED) {
            return
        }
        _activeRandomSessionId.value = session.id
        _randomMatchStatus.value = RandomMatchStatus.CONNECTED
        val partnerId = session.partnerIdFor(currentUserId)
        viewModelScope.launch {
            val fetched = repository.getProfileById(partnerId).getOrNull()
            if (fetched != null) {
                _fetchedRandomPartnerProfile.value = fetched
            }
        }
        randomMatchJob?.cancel()
    }

    fun cancelRandomMatchmaking() {
        randomMatchJob?.cancel()
        randomMatchJob = null
        _randomMatchStatus.value = RandomMatchStatus.IDLE
        viewModelScope.launch {
            repository.leaveRandomChatQueue()
        }
    }

    fun consentToRandomRevealStep(step: RandomRevealStep) {
        val session = activeRandomSessionState.value ?: return
        if (session.status != "active") return
        if (session.myConsentFor(currentUserId, step)) return

        val prereqSatisfied = when (step) {
            RandomRevealStep.NAME -> true
            RandomRevealStep.AGE -> session.isNameRevealed
            RandomRevealStep.PHOTO -> session.isAgeRevealed
            RandomRevealStep.PROFILE -> session.isPhotoRevealed
        }
        if (!prereqSatisfied) {
            _statusBanner.value = "Complete the previous reveal step first."
            return
        }

        val partnerAlreadyConsented = session.partnerConsentFor(currentUserId, step)
        viewModelScope.launch {
            val res = repository.submitRandomRevealConsent(session, step, consent = true)
            if (res.isSuccess) {
                val systemNotice = if (partnerAlreadyConsented) {
                    "🎉 Partner also said Yes! ${step.title} unlocked."
                } else {
                    "✨ Requested '${step.title}'. Waiting for partner."
                }
                repository.sendRandomChatMessage(session, systemNotice, messageType = "system")
            } else {
                _statusBanner.value = "Failed to update reveal consent: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun sendRandomChatMessage(text: String) {
        val session = activeRandomSessionState.value ?: return
        if (session.status != "active") {
            _statusBanner.value = "This Random Chat session has ended. Find a new partner to keep chatting."
            return
        }
        viewModelScope.launch {
            val res = repository.sendRandomChatMessage(session, text, messageType = "text")
            if (res.isFailure) {
                _statusBanner.value = "Failed to send message: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun minimizeRandomChatView() {
        randomMatchJob?.cancel()
        randomMatchJob = null
        _activeRandomSessionId.value = null
        _fetchedRandomPartnerProfile.value = null
        _randomMatchStatus.value = RandomMatchStatus.IDLE
    }

    fun endRandomChat() {
        val sid = _activeRandomSessionId.value
        randomMatchJob?.cancel()
        randomMatchJob = null
        if (!sid.isNullOrBlank()) {
            lastEndedRandomSessionId = sid
        }
        _activeRandomSessionId.value = null
        _fetchedRandomPartnerProfile.value = null
        _randomMatchStatus.value = RandomMatchStatus.IDLE
        viewModelScope.launch {
            if (!sid.isNullOrBlank()) {
                repository.endRandomChatSession(sid)
            } else {
                repository.leaveRandomChatQueue()
            }
        }
    }

    fun deleteRandomChatSession(sessionId: String) {
        if (sessionId.isBlank()) return
        if (_activeRandomSessionId.value == sessionId) {
            randomMatchJob?.cancel()
            randomMatchJob = null
            _activeRandomSessionId.value = null
            _fetchedRandomPartnerProfile.value = null
            _randomMatchStatus.value = RandomMatchStatus.IDLE
        }
        viewModelScope.launch {
            val res = repository.deleteRandomChatSession(sessionId)
            if (res.isSuccess) {
                _statusBanner.value = "Random chat deleted."
            } else {
                _statusBanner.value = "Could not delete random chat: ${res.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun findNextRandomPartner() {
        val previousPartnerId = activeRandomSessionState.value?.partnerIdFor(currentUserId)
        startRandomMatchmaking(excludePartnerId = previousPartnerId)
    }

    fun blockRandomPartner(partnerId: String, revealedName: String?) {
        val safeName = revealedName?.takeIf { it.isNotBlank() } ?: "Random Chat User"
        endRandomChat()
        blockUser(partnerId, safeName)
    }

    override fun onCleared() {
        super.onCleared()
        randomMatchJob?.cancel()
        presenceHeartbeatJob?.cancel()
    }

    companion object {
        private const val TAG = "MilanViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        const val FREE_DAILY_MESSAGE_LIMIT = 5
        const val FREE_DAILY_RANDOM_FIND_LIMIT = 2
        const val FREE_MAX_LIKES_LIMIT = 15
        const val FREE_MAX_PROFILE_VIEWS = 25
    }
}
