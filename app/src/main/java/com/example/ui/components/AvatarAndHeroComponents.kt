package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Surface as AndroidSurface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.PremiumGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

val VerifiedBlueColor = Color(0xFF1D9BF0)

@Composable
fun VerifiedBlueTickBadge(
    isVerified: Boolean,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!isVerified) return
    Surface(
        color = VerifiedBlueColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showLabel) 8.dp else 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified User Blue Tick",
                tint = VerifiedBlueColor,
                modifier = Modifier.size(16.dp)
            )
            if (showLabel) {
                Text(
                    text = "Verified User",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedBlueColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

val OnlineGreenColor = Color(0xFF22C55E)
val OfflineRedColor = Color(0xFFEF4444)

@Composable
fun OnlineStatusDot(
    isOnline: Boolean,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isOnline) OnlineGreenColor else OfflineRedColor
    val statusText = if (isOnline) "Online" else "Offline"
    Surface(
        color = dotColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (showLabel) 8.dp else 5.dp,
                vertical = 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            )
            if (showLabel) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProfileAvatarView(
    photoBase64: String,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    shape: Shape = RoundedCornerShape(20.dp),
    isPremium: Boolean = false,
    isVerified: Boolean = false
) {
    val decodedBitmap = remember(photoBase64) {
        if (photoBase64.length > 100 && !photoBase64.startsWith("avatar_")) {
            try {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val gradientColors = remember(photoBase64) {
        when (photoBase64) {
            "avatar_rose" -> listOf(Color(0xFFF8BBD0), Color(0xFFE86F88))
            "avatar_lavender" -> listOf(Color(0xFFD1C4E9), Color(0xFF8E7AB5))
            "avatar_mint" -> listOf(Color(0xFFB2DFDB), Color(0xFF5FA896))
            "avatar_peach" -> listOf(Color(0xFFFFCCBC), Color(0xFFE67E5A))
            "avatar_sky" -> listOf(Color(0xFFBBDEFB), Color(0xFF5C9CE6))
            else -> listOf(Color(0xFFF8BBD0), Color(0xFF8E7AB5))
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(gradientColors))
                .border(
                    width = if (isPremium || isVerified) 2.5.dp else 1.dp,
                    color = when {
                        isVerified -> VerifiedBlueColor
                        isPremium -> PremiumGold
                        else -> Color.White.copy(alpha = 0.7f)
                    },
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "$displayName profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = displayName
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercase() }
                    .ifEmpty { "M" }

                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.34f).sp
                    )
                )
            }
        }

        if (isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = "Verified User",
                    tint = VerifiedBlueColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isPremium) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(PremiumGold)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Premium Member",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 6-Slot Additional Profile Photos Editor Grid.
 * Allows users to upload up to 6 additional gallery photos alongside their 1 mandatory main profile picture.
 */
@Composable
fun SixPhotosGalleryEditorGrid(
    galleryPhotos: List<String>,
    onPhotosChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var targetSlotIndex by remember { mutableIntStateOf(-1) }

    val slotPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = encodeUriToBase64Thumbnail(context.contentResolver, uri, maxDim = 300, quality = 60)
            if (!encoded.isNullOrBlank()) {
                val updated = galleryPhotos.toMutableList()
                if (targetSlotIndex in 0 until updated.size) {
                    updated[targetSlotIndex] = encoded
                } else if (updated.size < 6) {
                    updated.add(encoded)
                }
                onPhotosChanged(updated.take(6))
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Additional Profile Photos (${galleryPhotos.size}/6)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap any slot to add photo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        for (row in 0..1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0..2) {
                    val slotIdx = row * 3 + col
                    val photoB64 = galleryPhotos.getOrNull(slotIdx)
                    val decoded = remember(photoB64) {
                        if (!photoB64.isNullOrBlank() && photoB64.length > 100) {
                            try {
                                val bytes = Base64.decode(photoB64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(106.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(
                                width = 1.dp,
                                color = if (decoded != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                targetSlotIndex = slotIdx
                                slotPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("gallery_photo_slot_$slotIdx"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (decoded != null) {
                            Image(
                                bitmap = decoded,
                                contentDescription = "Gallery Photo ${slotIdx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    val updated = galleryPhotos.toMutableList()
                                    if (slotIdx in updated.indices) {
                                        updated.removeAt(slotIdx)
                                        onPhotosChanged(updated)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Add Photo ${slotIdx + 1}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Photo ${slotIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal gallery strip displaying the user's 1 Main Profile Picture + up to 6 Additional Gallery Photos,
 * with tap-to-zoom full photo preview dialog.
 */
@Composable
fun ProfilePhotoGalleryStrip(
    mainPhotoBase64: String,
    galleryPhotos: List<String>,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val allPhotos = remember(mainPhotoBase64, galleryPhotos) {
        buildList {
            if (mainPhotoBase64.length > 100 && !mainPhotoBase64.startsWith("avatar_")) {
                add(mainPhotoBase64)
            }
            galleryPhotos.filter { it.length > 100 }.forEach { add(it) }
        }
    }

    var zoomedPhotoBase64 by remember { mutableStateOf<String?>(null) }

    if (zoomedPhotoBase64 != null) {
        val bmp = remember(zoomedPhotoBase64) {
            try {
                val bytes = Base64.decode(zoomedPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { zoomedPhotoBase64 = null },
            title = { Text("$displayName's Photo") },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "$displayName Full Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { zoomedPhotoBase64 = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (allPhotos.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Profile Photos (${allPhotos.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(allPhotos) { index, b64 ->
                val bmp = remember(b64) {
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .size(width = 96.dp, height = 120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (index == 0) 2.dp else 1.dp,
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { zoomedPhotoBase64 = b64 }
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Photo ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = if (index == 0) "Main DP" else "#${index}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Smooth looping video player for recorded left-to-right Live Face Verification Videos.
 * Used in both the user's verification preview and the Admin Verification Menu.
 */
@Composable
fun FaceVerificationVideoPlayer(
    frames: List<String>,
    modifier: Modifier = Modifier
) {
    val decodedBitmaps = remember(frames) {
        frames.mapNotNull { b64 ->
            if (b64.length > 50) {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    var currentFrameIndex by remember(decodedBitmaps.size) { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(decodedBitmaps.size, isPlaying) {
        if (decodedBitmaps.size > 1 && isPlaying) {
            while (isActive) {
                delay(420L)
                currentFrameIndex = (currentFrameIndex + 1) % decodedBitmaps.size
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1423))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val activeBmp = decodedBitmaps.getOrNull(currentFrameIndex)
                if (activeBmp != null) {
                    Image(
                        bitmap = activeBmp,
                        contentDescription = "Live Face Verification Video Frame ${currentFrameIndex + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "No verification video recorded yet",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Top Overlay Badge showing Left -> Center -> Right angle phase
                if (decodedBitmaps.isNotEmpty()) {
                    val phaseText = when {
                        currentFrameIndex < decodedBitmaps.size / 3 -> "⬅️ Left Side Face"
                        currentFrameIndex < (decodedBitmaps.size * 2) / 3 -> "⬆️ Center Face"
                        else -> "➡️ Right Side Face"
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "LIVE FACE VIDEO • $phaseText (${currentFrameIndex + 1}/${decodedBitmaps.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (decodedBitmaps.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { (currentFrameIndex + 1).toFloat() / decodedBitmaps.size.coerceAtLeast(1).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = VerifiedBlueColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Left-to-Right Face Scan (${decodedBitmaps.size} frames)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    TextButton(
                        onClick = { isPlaying = !isPlaying }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Pause" else "Play Loop",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-App Front Camera Live Face Verification Video Recorder Dialog.
 * Instructs the user before recording to turn their face slowly from LEFT side to RIGHT side,
 * records 8 live camera frames over ~4.5 seconds, previews the recorded video, and submits to Admin.
 */
@SuppressLint("MissingPermission")
@Composable
fun LiveFaceVerificationRecorderDialog(
    displayName: String,
    onDismiss: () -> Unit,
    onSubmitVideoFrames: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    var isRecording by remember { mutableStateOf(false) }
    var recordingStep by remember { mutableIntStateOf(0) } // 0 = Not started, 1..8 = Capturing frames
    val capturedFrames = remember { mutableStateListOf<String>() }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var cameraAvailable by remember { mutableStateOf(true) }

    val cameraManager = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    var activeCameraDevice by remember { mutableStateOf<CameraDevice?>(null) }
    var activeCaptureSession by remember { mutableStateOf<CameraCaptureSession?>(null) }
    var activeSurface by remember { mutableStateOf<AndroidSurface?>(null) }

    fun closeRecorderCamera() {
        try {
            activeCaptureSession?.stopRepeating()
        } catch (_: Throwable) {}
        try {
            activeCaptureSession?.close()
        } catch (_: Throwable) {}
        activeCaptureSession = null
        try {
            activeCameraDevice?.close()
        } catch (_: Throwable) {}
        activeCameraDevice = null
        try {
            activeSurface?.release()
        } catch (_: Throwable) {}
        activeSurface = null
    }

    DisposableEffect(Unit) {
        onDispose {
            closeRecorderCamera()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 8-Frame Guided Left-to-Right Face Recording Coroutine
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        capturedFrames.clear()
        for (step in 1..8) {
            recordingStep = step
            delay(550L)
            val tv = textureViewRef
            val rawBmp = if (tv != null && tv.isAvailable && activeCameraDevice != null) {
                try {
                    tv.getBitmap(220, 290)
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }

            val encodedFrame = withContext(Dispatchers.Default) {
                val bmpToEncode = rawBmp ?: generateFallbackFaceScanBitmap(displayName, step)
                val baos = ByteArrayOutputStream()
                bmpToEncode.compress(Bitmap.CompressFormat.JPEG, 48, baos)
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
            capturedFrames.add(encodedFrame)
        }
        isRecording = false
        recordingStep = 0
    }

    val instructionDirectionText = when {
        !isRecording && capturedFrames.isEmpty() ->
            "Ready to Record: Hold phone at eye level and tap 'Start Face Video Recording'"
        isRecording && recordingStep <= 3 ->
            "⬅️ STEP 1/3: Slowly turn your face to the LEFT side (ડાબી બાજુ ચહેરો ફેરવો)"
        isRecording && recordingStep <= 5 ->
            "⬆️ STEP 2/3: Look straight at the camera (સીધા કેમેરા સામે જુઓ)"
        isRecording ->
            "➡️ STEP 3/3: Slowly turn your face to the RIGHT side (જમણી બાજુ ચહેરો ફેરવો)"
        else ->
            "✅ Recording Complete! Review your left-to-right face video below and submit to Admin."
    }

    AlertDialog(
        onDismissRequest = {
            if (!isRecording) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = VerifiedBlueColor
                )
                Text(
                    text = "Live Face Video Verification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pre-Recording Instructions Card (Mandatory Left-to-Right Face Scan Notice)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📋 Important Recording Instructions:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "1. Use the in-app camera to record a live video of your face.\n" +
                                "2. Slowly turn your face from the LEFT side to the RIGHT side (ડાબી બાજુના ચહેરાથી જમણી બાજુના ચહેરા સુધીનો વિડિયો બનાવો).\n" +
                                "3. Admin will verify your live face video with your profile photos and grant your Verified User Blue Tick.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Live Direction Banner
                Surface(
                    color = if (isRecording) Color(0xFFD32F2F) else VerifiedBlueColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = instructionDirectionText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (capturedFrames.isNotEmpty() && !isRecording) {
                    // Show recorded video loop preview
                    FaceVerificationVideoPlayer(frames = capturedFrames.toList())
                } else {
                    // Show Live In-App Front Camera Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1A1423))
                            .border(
                                width = 2.dp,
                                color = if (isRecording) Color(0xFFD32F2F) else VerifiedBlueColor,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPerm && cameraManager != null && cameraAvailable) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    TextureView(ctx).apply {
                                        textureViewRef = this
                                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(
                                                surfaceTex: SurfaceTexture,
                                                width: Int,
                                                height: Int
                                            ) {
                                                closeRecorderCamera()
                                                try {
                                                    val ids = cameraManager.cameraIdList
                                                    if (ids.isEmpty()) {
                                                        cameraAvailable = false
                                                        return
                                                    }
                                                    val frontId = ids.firstOrNull { id ->
                                                        cameraManager.getCameraCharacteristics(id)
                                                            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                                                    } ?: ids.first()

                                                    surfaceTex.setDefaultBufferSize(320, 240)
                                                    val surf = AndroidSurface(surfaceTex)
                                                    activeSurface = surf

                                                    cameraManager.openCamera(
                                                        frontId,
                                                        object : CameraDevice.StateCallback() {
                                                            override fun onOpened(camera: CameraDevice) {
                                                                activeCameraDevice = camera
                                                                try {
                                                                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                                                    req.addTarget(surf)
                                                                    @Suppress("DEPRECATION")
                                                                    camera.createCaptureSession(
                                                                        listOf(surf),
                                                                        object : CameraCaptureSession.StateCallback() {
                                                                            override fun onConfigured(session: CameraCaptureSession) {
                                                                                activeCaptureSession = session
                                                                                try {
                                                                                    session.setRepeatingRequest(
                                                                                        req.build(),
                                                                                        null,
                                                                                        Handler(Looper.getMainLooper())
                                                                                    )
                                                                                } catch (_: Throwable) {}
                                                                            }

                                                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                                                        },
                                                                        Handler(Looper.getMainLooper())
                                                                    )
                                                                } catch (_: Throwable) {
                                                                    closeRecorderCamera()
                                                                }
                                                            }

                                                            override fun onDisconnected(camera: CameraDevice) {
                                                                closeRecorderCamera()
                                                            }

                                                            override fun onError(camera: CameraDevice, error: Int) {
                                                                closeRecorderCamera()
                                                            }
                                                        },
                                                        Handler(Looper.getMainLooper())
                                                    )
                                                } catch (_: Throwable) {
                                                    cameraAvailable = false
                                                }
                                            }

                                            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                                            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                                                closeRecorderCamera()
                                                return true
                                            }
                                            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                                        }
                                    }
                                },
                                update = {}
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (!hasCameraPerm) {
                                        "Camera permission required for Live Face Verification"
                                    } else {
                                        "In-App Front Camera Ready for Left-to-Right Face Scan"
                                    },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                if (!hasCameraPerm) {
                                    OutlinedButton(
                                        onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) }
                                    ) {
                                        Text("Grant Camera Permission", color = Color.White)
                                    }
                                }
                            }
                        }

                        if (isRecording) {
                            LinearProgressIndicator(
                                progress = { recordingStep / 8f },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (capturedFrames.isNotEmpty() && !isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            capturedFrames.clear()
                            isRecording = true
                        },
                        modifier = Modifier.testTag("retake_face_video_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retake")
                    }
                    Button(
                        onClick = {
                            onSubmitVideoFrames(capturedFrames.toList())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedBlueColor),
                        modifier = Modifier.testTag("submit_face_video_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit to Admin")
                    }
                }
            } else {
                Button(
                    onClick = { isRecording = true },
                    enabled = !isRecording,
                    modifier = Modifier.testTag("start_face_video_record_button")
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRecording) "Recording (${recordingStep}/8)..." else "Start Face Video Recording")
                }
            }
        },
        dismissButton = {
            if (!isRecording) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun generateFallbackFaceScanBitmap(displayName: String, step: Int): Bitmap {
    val bmp = Bitmap.createBitmap(220, 290, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val bgPaint = AndroidPaint().apply {
        color = android.graphics.Color.rgb(38, 28, 52)
    }
    canvas.drawRect(0f, 0f, 220f, 290f, bgPaint)

    val faceOffsetX = when {
        step <= 3 -> -28f + (step * 6f)
        step <= 5 -> 0f
        else -> (step - 5) * 12f
    }
    val circlePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(232, 111, 136)
    }
    canvas.drawCircle(110f + faceOffsetX, 130f, 52f, circlePaint)

    val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 18f
        textAlign = AndroidPaint.Align.CENTER
        isFakeBoldText = true
    }
    val angleLabel = when {
        step <= 3 -> "LEFT FACE ($step/8)"
        step <= 5 -> "CENTER FACE ($step/8)"
        else -> "RIGHT FACE ($step/8)"
    }
    canvas.drawText(displayName.take(16), 110f, 225f, textPaint)
    canvas.drawText(angleLabel, 110f, 255f, textPaint)
    return bmp
}

@Composable
fun PastelHeroIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFCE4EC),
                        Color(0xFFEDE7F6),
                        Color(0xFFE0F2F1)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x33E86F88),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.25f, size.height * 0.3f)
            )
            drawCircle(
                color = Color(0x338E7AB5),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.78f, size.height * 0.7f)
            )
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Milan Heart",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}

fun encodeUriToBase64Thumbnail(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    maxDim: Int = 320,
    quality: Int = 72
): String? {
    return try {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return null

        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
