package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.UserProfile
import com.example.ui.components.FaceVerificationVideoPlayer
import com.example.ui.components.LiveFaceVerificationRecorderDialog
import com.example.ui.components.PastelHeroIllustration
import com.example.ui.components.ProfileAvatarView
import com.example.ui.components.SixPhotosGalleryEditorGrid
import com.example.ui.components.VerifiedBlueTickBadge
import com.example.ui.components.encodeUriToBase64Thumbnail
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activityContext = context as? Activity ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun performGoogleSignOut(
    context: Context,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    val credentialManager = CredentialManager.create(context)
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PastelHeroIllustration(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )

                Text(
                    text = "Milan",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureBullet("📍 Hourly GPS location sync & exact distance in km")
                        FeatureBullet("☑️ Verified User profiles with Live Face Verification & Blue Tick")
                        FeatureBullet("📷 Share photos & make crystal-clear Audio Calls in chat")
                        FeatureBullet("👑 Premium: Unlimited Likes, Messages & Views")
                    }
                }

                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupWizardScreen(
    currentUserId: String,
    currentUserEmail: String? = null,
    currentUserDisplayName: String? = null,
    initialProfile: UserProfile?,
    onSaveProfile: (UserProfile) -> Unit,
    onCancelEdit: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val firebaseUser = Firebase.auth.currentUser
    val effectiveEmail = currentUserEmail ?: firebaseUser?.email
    val effectiveDisplayName = currentUserDisplayName ?: firebaseUser?.displayName
    val isAdminEmail = effectiveEmail?.trim()?.equals(ADMIN_EMAIL, ignoreCase = true) == true
    val googleIdentity = remember(currentUserId, effectiveDisplayName, effectiveEmail) {
        com.example.data.model.extractGoogleAccountIdentity(
            displayName = effectiveDisplayName,
            email = effectiveEmail,
            uid = currentUserId
        )
    }

    // Name, Gender, and Age are strictly locked to the user's Google Account (read-only)
    val firstName = initialProfile?.firstName?.takeIf { it.isNotBlank() } ?: googleIdentity.firstName
    val lastName = initialProfile?.lastName?.takeIf { it.isNotBlank() } ?: googleIdentity.lastName
    val age = initialProfile?.age?.takeIf { it in 18..100 } ?: googleIdentity.age
    val gender = initialProfile?.gender?.takeIf { it in listOf("Male", "Female") } ?: googleIdentity.gender

    var currentStep by remember { mutableIntStateOf(1) }
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "Looking for genuine friendships and meaningful connections ✨") }
    val selectedInterests = remember {
        mutableStateListOf<String>().apply {
            addAll(initialProfile?.interests?.ifEmpty { listOf("Music", "Travel", "Coffee") } ?: listOf("Music", "Travel", "Coffee"))
        }
    }
    var photoBase64 by remember { mutableStateOf(initialProfile?.photoBase64 ?: "avatar_rose") }
    var galleryPhotos by remember { mutableStateOf(initialProfile?.galleryPhotos ?: emptyList()) }
    var verificationVideoFrames by remember { mutableStateOf(initialProfile?.verificationVideoFrames ?: emptyList()) }
    var verificationStatus by remember {
        mutableStateOf(
            initialProfile?.verificationStatus ?: "None"
        )
    }
    var isProfileVerified by remember { mutableStateOf(initialProfile?.isProfileVerified ?: false) }
    var verificationSubmittedAt by remember { mutableStateOf(initialProfile?.verificationSubmittedAt ?: 0L) }
    var showFaceRecorderDialog by remember { mutableStateOf(false) }

    var latitude by remember { mutableStateOf(initialProfile?.latitude ?: 23.0225) }
    var longitude by remember { mutableStateOf(initialProfile?.longitude ?: 72.5714) }
    var locationName by remember { mutableStateOf(initialProfile?.locationName ?: "Ahmedabad, Gujarat") }
    var locationGranted by remember { mutableStateOf(initialProfile != null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentStep > 1 || onCancelEdit != null) {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onCancelEdit?.invoke()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = encodeUriToBase64Thumbnail(context.contentResolver, uri)
            if (!encoded.isNullOrBlank()) {
                photoBase64 = encoded
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = granted
        if (granted) {
            isFetchingLocation = true
            fetchDeviceLocation(context) { lat, lon, cityLabel ->
                latitude = lat
                longitude = lon
                locationName = cityLabel
                isFetchingLocation = false
            }
        }
    }

    if (showFaceRecorderDialog) {
        LiveFaceVerificationRecorderDialog(
            displayName = "$firstName $lastName",
            onDismiss = { showFaceRecorderDialog = false },
            onSubmitVideoFrames = { frames ->
                verificationVideoFrames = frames
                verificationStatus = "Pending"
                isProfileVerified = false
                verificationSubmittedAt = System.currentTimeMillis()
                showFaceRecorderDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (onCancelEdit != null) "Edit Photos, Verification & Bio" else "Complete Your Profile (Step $currentStep/3)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (onCancelEdit != null) {
                    TextButton(onClick = onCancelEdit) {
                        Text("Close")
                    }
                }
            }

            LinearProgressIndicator(
                progress = { currentStep / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            if (validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AnimatedContent(targetState = currentStep, label = "onboarding_step") { step ->
                when (step) {
                    1 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "1. Google Account Verified Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Read-Only Locked Card for Name, Gender, and Age from Google Account
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("google_locked_identity_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Synced from Google Account",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Locked 🔒",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Full Name:",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "$firstName $lastName",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.testTag("locked_profile_name")
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Age (Auto-detected):",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "$age years",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.testTag("locked_profile_age")
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Gender (Auto-detected):",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = gender,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.testTag("locked_profile_gender")
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = bio,
                                    onValueChange = { bio = it },
                                    label = { Text("About You (Bio)") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_bio")
                                )

                                Text(
                                    text = "Your Interests:",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                val allInterests = listOf("Music", "Travel", "Coffee", "Fitness", "Art", "Foodie", "Books", "Movies", "Photography", "Sports")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    allInterests.forEach { interest ->
                                        val isSelected = interest in selectedInterests
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected && selectedInterests.size > 1) {
                                                    selectedInterests.remove(interest)
                                                } else if (!isSelected && selectedInterests.size < 6) {
                                                    selectedInterests.add(interest)
                                                }
                                            },
                                            label = { Text(interest) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "2. Photos & Face Verification",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )

                                ProfileAvatarView(
                                    photoBase64 = photoBase64,
                                    displayName = "$firstName $lastName",
                                    size = 120.dp,
                                    shape = CircleShape,
                                    isVerified = isProfileVerified && verificationStatus == "Verified"
                                )

                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("upload_dp_button")
                                ) {
                                    Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload 1 Main Profile Picture (Required)")
                                }

                                Text(
                                    text = "Or pick a theme avatar color:",
                                    style = MaterialTheme.typography.labelLarge
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val presets = listOf(
                                        "avatar_rose" to Color(0xFFE86F88),
                                        "avatar_lavender" to Color(0xFF8E7AB5),
                                        "avatar_mint" to Color(0xFF5FA896),
                                        "avatar_peach" to Color(0xFFE67E5A),
                                        "avatar_sky" to Color(0xFF5C9CE6)
                                    )
                                    presets.forEach { (presetKey, color) ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(
                                                    width = if (photoBase64 == presetKey) 3.dp else 0.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = CircleShape
                                                )
                                                .clickable { photoBase64 = presetKey }
                                        )
                                    }
                                }

                                // 6 Additional Photos Grid in Profile Setup
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SixPhotosGalleryEditorGrid(
                                            galleryPhotos = galleryPhotos,
                                            onPhotosChanged = { updatedList ->
                                                galleryPhotos = updatedList
                                            }
                                        )
                                    }
                                }

                                // Live Face Video Verification Section in Profile Setup
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("wizard_face_verification_card")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = Color(0xFF1D9BF0),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "Live Face Verification (Blue Tick)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (isProfileVerified && verificationStatus == "Verified") {
                                            VerifiedBlueTickBadge(isVerified = true, showLabel = true)
                                        } else if (verificationVideoFrames.isNotEmpty() && verificationStatus != "Rejected") {
                                            Text(
                                                text = "✅ Live Face Verification Video Recorded & Submitted for Admin Review.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            if (verificationStatus == "Rejected") {
                                                Text(
                                                    text = "Marked Unverified — Record video again.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { showFaceRecorderDialog = true },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("wizard_open_face_recorder_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Videocam,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    if (verificationStatus == "Rejected") {
                                                        "Record & Submit Face Verification Video Again"
                                                    } else {
                                                        "Record Live Face Verification Video"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(54.dp)
                                )

                                Text(
                                    text = "3. GPS Location",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (locationGranted) Icons.Default.CheckCircle else Icons.Default.MyLocation,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = locationName,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = if (locationGranted) "GPS active • Updates automatically every hour" else "Select or detect your location",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val hasFine = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val hasCoarse = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasFine || hasCoarse) {
                                            locationGranted = true
                                            isFetchingLocation = true
                                            fetchDeviceLocation(context) { lat, lon, cityLabel ->
                                                latitude = lat
                                                longitude = lon
                                                locationName = cityLabel
                                                isFetchingLocation = false
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
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("grant_location_button")
                                ) {
                                    if (isFetchingLocation) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Detect GPS Location")
                                }

                                Text(
                                    text = "Or choose your city:",
                                    style = MaterialTheme.typography.labelLarge
                                )

                                val cities = listOf(
                                    Triple("Ahmedabad, Gujarat", 23.0225, 72.5714),
                                    Triple("Surat, Gujarat", 21.1702, 72.8311),
                                    Triple("Vadodara, Gujarat", 22.3072, 73.1812),
                                    Triple("Rajkot, Gujarat", 22.3039, 70.8022),
                                    Triple("Gandhinagar, Gujarat", 23.2156, 72.6369),
                                    Triple("Mumbai, Maharashtra", 19.0760, 72.8777)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cities.forEach { (cityName, lat, lon) ->
                                        FilterChip(
                                            selected = locationName.startsWith(cityName.substringBefore(",")),
                                            onClick = {
                                                locationName = cityName
                                                latitude = lat
                                                longitude = lon
                                                locationGranted = true
                                            },
                                            label = { Text(cityName.substringBefore(",")) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep -= 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("wizard_back_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        validationError = null
                        if (currentStep == 1) {
                            if (firstName.isBlank() || lastName.isBlank()) {
                                validationError = "Please enter your First Name and Surname."
                                return@Button
                            }
                            currentStep = 2
                        } else if (currentStep == 2) {
                            currentStep = 3
                        } else {
                            val birthYear = Calendar.getInstance().get(Calendar.YEAR) - age.coerceIn(18, 99)
                            val computedBirthDate = "$birthYear-01-01"
                            val newProfile = UserProfile(
                                id = currentUserId,
                                userId = currentUserId,
                                firstName = if (isAdminEmail && firstName.isBlank()) "admin" else firstName.trim(),
                                lastName = lastName.trim(),
                                birthDate = computedBirthDate,
                                age = age.coerceIn(18, 100),
                                gender = gender,
                                bio = bio.trim().ifEmpty { "Hello from Milan!" },
                                interests = selectedInterests.toList(),
                                photoBase64 = photoBase64,
                                galleryPhotos = galleryPhotos.take(6),
                                isProfileVerified = isProfileVerified,
                                verificationStatus = verificationStatus,
                                verificationVideoFrames = verificationVideoFrames.take(8),
                                verificationSubmittedAt = verificationSubmittedAt,
                                latitude = latitude,
                                longitude = longitude,
                                locationName = locationName,
                                isPremium = initialProfile?.isPremium ?: false,
                                premiumPlan = initialProfile?.premiumPlan ?: "Free",
                                premiumExpiresAt = initialProfile?.premiumExpiresAt ?: 0L,
                                dailyMessageCount = initialProfile?.dailyMessageCount ?: 0,
                                streakDays = initialProfile?.streakDays ?: 0,
                                todayUsageSeconds = initialProfile?.todayUsageSeconds ?: 0,
                                lastUsageDate = initialProfile?.lastUsageDate ?: "",
                                lastStreakCompletedDate = initialProfile?.lastStreakCompletedDate ?: "",
                                role = if (isAdminEmail) "admin" else (initialProfile?.role ?: "user"),
                                visibility = "public"
                            )
                            onSaveProfile(newProfile)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("wizard_next_save_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (currentStep < 3) "Next" else "Complete & Save Profile"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (currentStep < 3) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.CheckCircle,
                        contentDescription = null
                    )
                }
            }
        }
        }
    }
}

@SuppressLint("MissingPermission")
internal fun fetchDeviceLocation(
    context: Context,
    onResult: (Double, Double, String) -> Unit
) {
    try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    val cityLabel = try {
                        @Suppress("DEPRECATION")
                        val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                        val addr = addresses?.firstOrNull()
                        val locality = addr?.locality ?: addr?.subAdminArea ?: "Nearby Area"
                        val state = addr?.adminArea ?: ""
                        if (state.isNotBlank()) "$locality, $state" else locality
                    } catch (e: Exception) {
                        "Nearby Location"
                    }
                    onResult(lat, lon, cityLabel)
                } else {
                    client.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val lat = lastLoc.latitude
                            val lon = lastLoc.longitude
                            val cityLabel = try {
                                @Suppress("DEPRECATION")
                                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                                val addr = addresses?.firstOrNull()
                                val locality = addr?.locality ?: addr?.subAdminArea ?: "Nearby Area"
                                val state = addr?.adminArea ?: ""
                                if (state.isNotBlank()) "$locality, $state" else locality
                            } catch (e: Exception) {
                                "Nearby Location"
                            }
                            onResult(lat, lon, cityLabel)
                        } else {
                            onResult(23.0225, 72.5714, "Ahmedabad, Gujarat")
                        }
                    }.addOnFailureListener {
                        onResult(23.0225, 72.5714, "Ahmedabad, Gujarat")
                    }
                }
            }
            .addOnFailureListener {
                onResult(23.0225, 72.5714, "Ahmedabad, Gujarat")
            }
    } catch (e: Exception) {
        onResult(23.0225, 72.5714, "Ahmedabad, Gujarat")
    }
}
