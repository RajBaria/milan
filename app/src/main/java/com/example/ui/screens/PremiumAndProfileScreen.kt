package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AppColorTheme
import com.example.data.model.BlockRecord
import com.example.data.model.DAILY_STREAK_TARGET_SECONDS
import com.example.data.model.STREAK_REQUIRED_DAYS
import com.example.data.model.UPI_RECEIVER_ID
import com.example.data.model.UPI_RECEIVER_NAME
import com.example.data.model.UserProfile
import com.example.data.model.isValidUpiUtr12Digits
import com.example.data.model.parseAndVerifyUpiCallbackResponse
import com.example.ui.components.FaceVerificationVideoPlayer
import com.example.ui.components.LiveFaceVerificationRecorderDialog
import com.example.ui.components.ProfileAvatarView
import com.example.ui.components.ProfilePhotoGalleryStrip
import com.example.ui.components.SixPhotosGalleryEditorGrid
import com.example.ui.components.VerifiedBlueTickBadge
import com.example.ui.components.encodeUriToBase64Thumbnail
import com.example.ui.theme.PremiumGold
import com.example.ui.theme.PremiumGoldLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTimestampDate(epochMs: Long): String {
    if (epochMs <= 0L) return "Not available"
    return try {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(epochMs))
    } catch (e: Exception) {
        "Verified"
    }
}

@Composable
fun PremiumSubscriptionScreen(
    myProfile: UserProfile,
    sessionUsageSeconds: Int = 0,
    onSelectPlan: (String, String) -> Unit,
    onSubmitUtrForVerification: (String, String) -> Unit = { _, _ -> }
) {
    var pendingUpiPlan by remember { mutableStateOf<Pair<String, String>?>(null) }
    var utr12DigitsInput by remember { mutableStateOf("") }
    var upiVerificationMessage by remember { mutableStateOf<String?>(null) }

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val planId = pendingUpiPlan?.first ?: return@rememberLauncherForActivityResult
        val responseStr = result.data?.getStringExtra("response")
        val verification = parseAndVerifyUpiCallbackResponse(result.resultCode, responseStr)

        if (verification.isBankVerified) {
            pendingUpiPlan = null
            utr12DigitsInput = ""
            upiVerificationMessage = null
            onSelectPlan(planId, verification.extractedUtr12Digits)
        } else {
            if (verification.extractedUtr12Digits.isNotBlank()) {
                utr12DigitsInput = verification.extractedUtr12Digits
            }
            upiVerificationMessage = verification.errorReason
        }
    }

    fun openUpiAppIntent(planId: String, amountInr: String) {
        val txnRef = "MILAN${System.currentTimeMillis()}"
        val upiUri = Uri.parse("upi://pay").buildUpon()
            .appendQueryParameter("pa", UPI_RECEIVER_ID)
            .appendQueryParameter("pn", UPI_RECEIVER_NAME)
            .appendQueryParameter("tn", "Milan $planId Gold Subscription")
            .appendQueryParameter("tr", txnRef)
            .appendQueryParameter("am", amountInr)
            .appendQueryParameter("cu", "INR")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, upiUri)
        try {
            upiLauncher.launch(intent)
        } catch (e: Exception) {
            upiVerificationMessage = "No UPI app opened automatically. Complete your ₹$amountInr payment in your UPI app and enter the 12-digit UTR number below."
        }
    }

    fun startPaymentFlow(planId: String, amountInr: String) {
        pendingUpiPlan = planId to amountInr
        utr12DigitsInput = ""
        upiVerificationMessage = null
        openUpiAppIntent(planId, amountInr)
    }

    if (pendingUpiPlan != null) {
        val (planId, amountStr) = pendingUpiPlan!!
        val isUtrValid = isValidUpiUtr12Digits(utr12DigitsInput)

        AlertDialog(
            onDismissRequest = {
                pendingUpiPlan = null
                upiVerificationMessage = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Verify UPI Payment • $planId Gold",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Plan: Milan $planId Gold",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Amount (Kimmat): ₹$amountStr INR",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (upiVerificationMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = upiVerificationMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { openUpiAppIntent(planId, amountStr) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reopen_upi_app_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Step 1: Pay ₹$amountStr in UPI App")
                    }

                    Text(
                        text = "Step 2: After completing payment in Google Pay / PhonePe / Paytm, enter your 12-Digit UPI UTR / Reference No. below. Premium will ONLY start after payment is verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = utr12DigitsInput,
                        onValueChange = { input ->
                            utr12DigitsInput = input.filter { it.isDigit() }.take(12)
                        },
                        label = { Text("12-Digit UPI UTR / Ref No.") },
                        placeholder = { Text("e.g. 431289451023") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upi_utr_input")
                    )

                    Text(
                        text = if (isUtrValid) {
                            "✓ Valid 12-digit UTR format (${utr12DigitsInput.length}/12). Ready to submit for check."
                        } else {
                            "Enter all 12 digits (${utr12DigitsInput.length}/12) from your UPI payment receipt."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUtrValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedPlan = planId
                        val submittedUtr = utr12DigitsInput.trim()
                        pendingUpiPlan = null
                        upiVerificationMessage = null
                        onSubmitUtrForVerification(selectedPlan, submittedUtr)
                    },
                    enabled = isUtrValid,
                    modifier = Modifier.testTag("confirm_upi_payment_button")
                ) {
                    Text("Submit UTR for Check")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUpiPlan = null
                        upiVerificationMessage = null
                    }
                ) {
                    Text("Cancel")
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
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Gold Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PremiumGoldLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFF8E1),
                                Color(0xFFFCE4EC),
                                Color(0xFFFFF3E0)
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(PremiumGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Milan Gold • Premium",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF4E3600)
                )

                Text(
                    text = if (myProfile.isPremium) {
                        val expiryInfo = if (myProfile.premiumExpiresAt > 0L) {
                            " Valid until ${formatTimestampDate(myProfile.premiumExpiresAt)}."
                        } else {
                            ""
                        }
                        "Your ${myProfile.premiumPlan} Gold plan is Active!$expiryInfo"
                    } else {
                        "Unlimited Likes, Messages, Distance & Ad-Free."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF6B4C00)
                )
            }
        }

        // Pending / Rejected UPI Payment Status Banner
        if (myProfile.paymentStatus == "PendingVerification") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pending_upi_verification_banner"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payment Verification Pending ⏳",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${myProfile.pendingPlan} Gold (₹${myProfile.lastPaymentAmountInr}) • UTR: ${myProfile.lastUpiTxnId}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else if (myProfile.paymentStatus == "Rejected") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "UPI Payment Not Verified ❌",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "UTR (${myProfile.lastUpiTxnId}) was rejected. Retry or contact Admin Support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // How Payment Verification Works Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "How UPI Payment is Verified",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "1. Bank callback verifies Status=SUCCESS + 12-digit UTR automatically.\n2. Manual 12-digit UTR activates Premium after Admin approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6-Day × 15-Minute Daily Usage Streak Card is ONLY shown to non-paid users
        if (!myProfile.isPaidPremium) {
            val effectiveTodaySeconds = (myProfile.todayUsageSeconds + sessionUsageSeconds).coerceAtLeast(0)
            val todayMinutes = effectiveTodaySeconds / 60
            val dailyProgress = (effectiveTodaySeconds.toFloat() / DAILY_STREAK_TARGET_SECONDS.toFloat()).coerceIn(0f, 1f)
            val streakProgress = (myProfile.streakDays.toFloat() / STREAK_REQUIRED_DAYS.toFloat()).coerceIn(0f, 1f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("streak_reward_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎁 6-Day Streak Reward (24 Hours Free Premium)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use the app for 15+ minutes daily for 6 consecutive days to automatically activate 24 Hours of Free Premium!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Streak: ${myProfile.streakDays} / $STREAK_REQUIRED_DAYS days",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Today's usage: $todayMinutes / 15 mins",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    LinearProgressIndicator(
                        progress = { streakProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PremiumGold,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    LinearProgressIndicator(
                        progress = { dailyProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Benefits comparison
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Premium Membership Benefits:",
                    style = MaterialTheme.typography.titleLarge
                )
                PremiumBenefitRow(
                    title = "Unlimited Likes & Direct Chat",
                    subtitle = "Send unlimited likes and message or audio call anyone directly without liking first."
                )
                PremiumBenefitRow(
                    title = "Unlimited Daily Messages & Photo Sharing",
                    subtitle = "Chat, share photos, and make voice audio calls with no daily caps."
                )
                PremiumBenefitRow(
                    title = "Unlimited Profile Views & Distance Range",
                    subtitle = "Free plan is limited to 50 km; Premium unlocks unlimited range and unlimited profile views."
                )
                PremiumBenefitRow(
                    title = "100% Ad-Free Experience",
                    subtitle = "Enjoy smooth browsing with all banner, interstitial, and native ads removed."
                )
            }
        }

        // Plan Cards
        PlanOptionCard(
            planId = "Monthly",
            title = "Monthly Gold Plan",
            priceText = "₹199 / month",
            upiSubtitle = "Verified UPI Payment",
            badgeText = "Popular",
            isCurrent = myProfile.isPremium && myProfile.premiumPlan == "Monthly",
            isPending = myProfile.paymentStatus == "PendingVerification" && myProfile.pendingPlan == "Monthly",
            onSelect = { startPaymentFlow("Monthly", "199.00") }
        )

        PlanOptionCard(
            planId = "Yearly",
            title = "Yearly Gold VIP Plan",
            priceText = "₹999 / year (Save 58%)",
            upiSubtitle = "Verified UPI Payment",
            badgeText = "Best Value",
            isCurrent = myProfile.isPremium && myProfile.premiumPlan == "Yearly",
            isPending = myProfile.paymentStatus == "PendingVerification" && myProfile.pendingPlan == "Yearly",
            onSelect = { startPaymentFlow("Yearly", "999.00") }
        )

        if (myProfile.isPremium) {
            OutlinedButton(
                onClick = { onSelectPlan("Free", "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("downgrade_free_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Switch Back to Free Plan")
            }
        }
    }
    }
}

@Composable
private fun PremiumBenefitRow(title: String, subtitle: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = PremiumGold,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanOptionCard(
    planId: String,
    title: String,
    priceText: String,
    upiSubtitle: String,
    badgeText: String,
    isCurrent: Boolean,
    isPending: Boolean = false,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrent) 2.5.dp else 1.dp,
                color = if (isCurrent) PremiumGold else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    color = PremiumGoldLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6B4C00),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = priceText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = upiSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSelect,
                enabled = !isCurrent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("subscribe_plan_$planId"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(imageVector = Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when {
                        isCurrent -> "Active Plan ✓"
                        isPending -> "Verification Pending ⏳ (Update UTR)"
                        else -> "Pay via UPI & Verify"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileAndBlockedScreen(
    myProfile: UserProfile,
    blockedUsers: List<BlockRecord>,
    isDarkMode: Boolean = false,
    colorTheme: AppColorTheme = AppColorTheme.PINK,
    onToggleDarkMode: () -> Unit = {},
    onSelectColorTheme: (AppColorTheme) -> Unit = {},
    onEditProfileClick: () -> Unit,
    onUpdateMainPhoto: (String) -> Unit = {},
    onUpdateGalleryPhotos: (List<String>) -> Unit = {},
    onSubmitFaceVerificationVideo: (List<String>) -> Unit = {},
    onUnblockUser: (BlockRecord) -> Unit,
    onChatWithAdminClick: () -> Unit = {},
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    var showFaceRecorderDialog by remember { mutableStateOf(false) }

    val mainPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = encodeUriToBase64Thumbnail(context.contentResolver, uri)
            if (!encoded.isNullOrBlank()) {
                onUpdateMainPhoto(encoded)
            }
        }
    }

    if (showFaceRecorderDialog) {
        LiveFaceVerificationRecorderDialog(
            displayName = myProfile.fullName,
            onDismiss = { showFaceRecorderDialog = false },
            onSubmitVideoFrames = { frames ->
                showFaceRecorderDialog = false
                onSubmitFaceVerificationVideo(frames)
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main Profile Identity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatarView(
                    photoBase64 = myProfile.photoBase64,
                    displayName = myProfile.fullName,
                    size = 104.dp,
                    shape = CircleShape,
                    isPremium = myProfile.isPremium,
                    isVerified = myProfile.isVerifiedWithBlueTick
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${myProfile.fullName}, ${myProfile.age} yrs",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (myProfile.isAdminUser) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Verified User Badge & Blue Tick on Profile
                if (myProfile.isVerifiedWithBlueTick) {
                    VerifiedBlueTickBadge(
                        isVerified = true,
                        showLabel = true
                    )
                }

                // Locked Google Account Identity Notice
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Name, ${myProfile.gender} & ${myProfile.age} yrs synced from Google Account (Locked)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${myProfile.locationName} • ${myProfile.gender}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val profileSummaryText = if (myProfile.isPaidPremium) {
                    "Age: ${myProfile.age} years • Plan: ${myProfile.premiumPlan} Gold"
                } else {
                    "Age: ${myProfile.age} years • Plan: ${myProfile.premiumPlan} • Streak: ${myProfile.streakDays}/6 days"
                }

                Text(
                    text = profileSummaryText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                if (myProfile.bio.isNotBlank()) {
                    Text(
                        text = myProfile.bio,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Button(
                    onClick = onEditProfileClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("edit_profile_button")
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Bio, Location & Profile Setup")
                }
            }
        }

        // Appearance & Theme Card (Day/Dark Mode + 6 Color Themes)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("appearance_theme_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isDarkMode) "Dark" else "Day",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { onToggleDarkMode() },
                            modifier = Modifier.testTag("profile_dark_mode_toggle")
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                .testTag("theme_option_${option.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Dedicated Profile Verification (1 Main Photo + 6 Gallery Photos + Live Face Video) Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_verification_section_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Profile Verification & Photos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1 Main Photo + 6 Gallery + Live Video",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Current Verification Status Banner
                when {
                    myProfile.isVerifiedWithBlueTick -> {
                        Surface(
                            color = Color(0xFF1D9BF0).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF1D9BF0),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Verified User ✓ (Blue Tick Active)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    myProfile.verificationStatus == "Pending" ||
                        (myProfile.verificationVideoFrames.isNotEmpty() && myProfile.verificationStatus != "Rejected") -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_verification_submitted_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "Live Face Video Submitted ✅",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    myProfile.verificationStatus == "Rejected" -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_unverified_retake_banner")
                        ) {
                            Text(
                                text = "Marked Unverified • Please record & submit video again ⚠️",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Step 1: 1 Main Profile Picture Button
                OutlinedButton(
                    onClick = {
                        mainPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_upload_main_photo_button")
                ) {
                    Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (myProfile.hasUploadedRealMainPhoto) {
                            "✓ 1 Main Profile Picture Uploaded (Tap to Change)"
                        } else {
                            "Step 1: Upload 1 Main Profile Picture (Required)"
                        }
                    )
                }

                // Step 2: 6 Additional Profile Photos Grid
                SixPhotosGalleryEditorGrid(
                    galleryPhotos = myProfile.galleryPhotos,
                    onPhotosChanged = onUpdateGalleryPhotos
                )

                // Step 3: Only show the Record button if the user has NOT yet submitted a video,
                // OR if Admin marked them Unverified ("Rejected") and asked them to record & submit again.
                // Never display/loop the face verification video on the user's own profile screen once taken!
                val needsFaceVideoRecording = !myProfile.isVerifiedWithBlueTick &&
                    (myProfile.verificationStatus == "Rejected" ||
                        (myProfile.verificationStatus != "Pending" && myProfile.verificationVideoFrames.isEmpty()))

                if (needsFaceVideoRecording) {
                    Button(
                        onClick = { showFaceRecorderDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D9BF0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("open_live_face_verification_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (myProfile.verificationStatus == "Rejected") {
                                "Record & Submit Verification Video Again"
                            } else {
                                "Record & Submit Live Face Verification Video"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Payment Details & Billing History Card in Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payment_details_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Payment Details & Plan History",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Current Plan:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (myProfile.isPremium) "${myProfile.premiumPlan} (Active)" else "Free Plan",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Payment Status:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (myProfile.paymentStatus) {
                                    "PendingVerification" -> "Pending Admin Check ⏳ (${myProfile.pendingPlan})"
                                    "Verified" -> "Verified ✓"
                                    "Rejected" -> "Rejected ❌"
                                    else -> if (myProfile.isPaidPremium) "Verified ✓" else "No active payment"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Last Payment Amount (Kimmat):",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (myProfile.lastPaymentAmountInr > 0) {
                                    "₹${myProfile.lastPaymentAmountInr} INR"
                                } else {
                                    "₹0 (No paid transaction)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (myProfile.lastUpiTxnId.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "12-Digit UPI UTR / Ref:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = myProfile.lastUpiTxnId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Payment Date:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (myProfile.lastPaymentDateMs > 0L) {
                                    formatTimestampDate(myProfile.lastPaymentDateMs)
                                } else {
                                    "No payment recorded yet"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (myProfile.isPremium && myProfile.premiumExpiresAt > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Auto-Expires On:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTimestampDate(myProfile.premiumExpiresAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Blocked Users Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Blocked Users (${blockedUsers.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (blockedUsers.isEmpty()) {
                    Text(
                        text = "You have not blocked any users.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    blockedUsers.forEach { block ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = block.blockedUserName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = { onUnblockUser(block) },
                                modifier = Modifier.testTag("unblock_button_${block.blockedUserId}")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }

        // Have a Question? Chat with Admin Support Card at bottom of Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_support_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Talk to Admin",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Payments, Verification & Help",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onChatWithAdminClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("chat_with_admin_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chat with Admin Support")
                }
            }
        }

        OutlinedButton(
            onClick = onSignOutClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
    }
}

/**
 * Dedicated Admin Verification Menu (`AdminVerificationMenuScreen`) for Admin (`rk3171998@gmail.com`).
 * Shows:
 * 1. User's submitted Live Face Verification Video (Left-to-Right face scan player).
 * 2. Complete User Profile (Name, Age, Gender, Location, Bio, Interests).
 * 3. All User Photos (1 Main Profile Picture + up to 6 Additional Gallery Photos).
 * 4. Bottom "Admin Verify" button that sets `isProfileVerified = true` & `verificationStatus = "Verified"`
 *    so the user's profile displays "Verified User" and a Blue Tick across the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminVerificationMenuScreen(
    allRegisteredProfiles: List<UserProfile>,
    onAdminVerifyFaceProfile: (UserProfile, Boolean) -> Unit
) {
    var filterTab by remember { mutableIntStateOf(0) }

    val pendingUsers = remember(allRegisteredProfiles) {
        allRegisteredProfiles.filter {
            it.verificationStatus == "Pending" ||
                (it.verificationVideoFrames.isNotEmpty() && !it.isVerifiedWithBlueTick)
        }
    }
    val verifiedUsers = remember(allRegisteredProfiles) {
        allRegisteredProfiles.filter { it.isVerifiedWithBlueTick }
    }

    val displayedUsers = when (filterTab) {
        0 -> if (pendingUsers.isNotEmpty()) pendingUsers else allRegisteredProfiles
        1 -> verifiedUsers
        else -> allRegisteredProfiles
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("admin_verification_menu_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1D9BF0).copy(alpha = 0.14f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.size(34.dp)
                    )
                    Column {
                        Text(
                            text = "Admin Profile Verification",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Face Video, Photos & Blue Tick",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Filter Chips for Pending / Verified / All Profiles
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = filterTab == 0,
                onClick = { filterTab = 0 },
                label = { Text("Pending Video (${pendingUsers.size})") },
                modifier = Modifier.testTag("admin_verify_tab_pending")
            )
            FilterChip(
                selected = filterTab == 1,
                onClick = { filterTab = 1 },
                label = { Text("Verified (${verifiedUsers.size})") },
                modifier = Modifier.testTag("admin_verify_tab_verified")
            )
            FilterChip(
                selected = filterTab == 2,
                onClick = { filterTab = 2 },
                label = { Text("All Profiles (${allRegisteredProfiles.size})") },
                modifier = Modifier.testTag("admin_verify_tab_all")
            )
        }

        if (filterTab == 0 && pendingUsers.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No pending videos. Showing all users.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        if (displayedUsers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "No profiles found in this category.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            displayedUsers.forEach { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_verification_card_${user.userId}"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Profile Summary Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarView(
                                photoBase64 = user.photoBase64,
                                displayName = user.fullName,
                                size = 68.dp,
                                shape = CircleShape,
                                isPremium = user.isPremium,
                                isVerified = user.isVerifiedWithBlueTick
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
                                        text = "${user.fullName}, ${user.age} yrs",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                VerifiedBlueTickBadge(
                                    isVerified = user.isVerifiedWithBlueTick,
                                    showLabel = true
                                )
                                Text(
                                    text = "Gender: ${user.gender} • Location: ${user.locationName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Verification Status: ${user.verificationStatus} • Photos: ${1 + user.galleryPhotos.size}/7",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Full Bio & Interests
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Bio: ${user.bio.ifBlank { "No bio added" }}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (user.interests.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        user.interests.forEach { interest ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "#$interest",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // All User Photos (1 Main Profile Picture + up to 6 Gallery Photos)
                        ProfilePhotoGalleryStrip(
                            mainPhotoBase64 = user.photoBase64,
                            galleryPhotos = user.galleryPhotos,
                            displayName = user.fullName
                        )

                        // Submitted Live Face Verification Video Player
                        Text(
                            text = if (user.verificationVideoFrames.isNotEmpty()) {
                                "📹 Submitted Live Face Verification Video (Left to Right Face Scan • ${user.verificationVideoFrames.size} frames):"
                            } else {
                                "📹 Live Face Verification Video: Not submitted by user yet"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FaceVerificationVideoPlayer(
                            frames = user.verificationVideoFrames
                        )

                        if (user.verificationSubmittedAt > 0L) {
                            Text(
                                text = "Video Submitted On: ${formatTimestampDate(user.verificationSubmittedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Bottom Admin Verify & Unverified (Request Re-record Video) Buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onAdminVerifyFaceProfile(user, true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1D9BF0)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_verify_face_button_${user.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (user.isVerifiedWithBlueTick) {
                                        "Verified User ✓ (Blue Tick Active)"
                                    } else {
                                        "Admin Verify (Add Verified User & Blue Tick)"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { onAdminVerifyFaceProfile(user, false) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("admin_reject_face_button_${user.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unverified (Ask User to Re-record & Submit Video)",
                                    fontWeight = FontWeight.SemiBold
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    allRegisteredProfiles: List<UserProfile>,
    onAdminVerifyUpiPayment: (UserProfile, Boolean) -> Unit = { _, _ -> },
    onAdminSetUserPlan: (String, String, String) -> Unit,
    onAdminDeleteUser: (String, String) -> Unit,
    onOpenVerificationMenu: (() -> Unit)? = null
) {
    val pendingPaymentUsers = remember(allRegisteredProfiles) {
        allRegisteredProfiles.filter { it.paymentStatus == "PendingVerification" }
    }
    val pendingFaceVerificationCount = remember(allRegisteredProfiles) {
        allRegisteredProfiles.count { it.verificationStatus == "Pending" }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Milan Admin Control Panel",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Administrator Dashboard • UPI Verification & User Control",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Verify UPI UTRs, plans & users.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (onOpenVerificationMenu != null) {
                    Button(
                        onClick = onOpenVerificationMenu,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9BF0)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_open_verification_menu_button")
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Admin Face Verification Menu ($pendingFaceVerificationCount Pending)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Pending UPI Payment Verification Section for Admin
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_pending_upi_section"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pending UPI Payment Verifications (${pendingPaymentUsers.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pendingPaymentUsers.isEmpty()) {
                    Text(
                        text = "No pending UPI UTR verification requests right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    pendingPaymentUsers.forEach { pendingUser ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${pendingUser.fullName} (${pendingUser.age} yrs, ${pendingUser.gender})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Requested Plan: ${pendingUser.pendingPlan} Gold • Amount: ₹${pendingUser.lastPaymentAmountInr}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "12-Digit UPI UTR / Ref No: ${pendingUser.lastUpiTxnId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Submitted On: ${formatTimestampDate(pendingUser.lastPaymentDateMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { onAdminVerifyUpiPayment(pendingUser, true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_verify_utr_${pendingUser.userId}")
                                    ) {
                                        Text("Approve & Start Premium")
                                    }
                                    OutlinedButton(
                                        onClick = { onAdminVerifyUpiPayment(pendingUser, false) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("admin_reject_utr_${pendingUser.userId}")
                                    ) {
                                        Text("Reject UTR")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Registered Users (${allRegisteredProfiles.size}):",
            style = MaterialTheme.typography.titleLarge
        )

        if (allRegisteredProfiles.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "No registered users found.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            allRegisteredProfiles.forEach { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_user_card_${user.userId}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileAvatarView(
                                photoBase64 = user.photoBase64,
                                displayName = user.fullName,
                                size = 56.dp,
                                isPremium = user.isPremium,
                                isVerified = user.isVerifiedWithBlueTick
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${user.fullName} (Age: ${user.age} yrs, ${user.gender})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                VerifiedBlueTickBadge(
                                    isVerified = user.isVerifiedWithBlueTick,
                                    showLabel = true
                                )
                                Text(
                                    text = "${user.locationName} • Plan: ${user.premiumPlan} • Payment: ${user.paymentStatus}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (user.lastUpiTxnId.isNotBlank()) {
                                    Text(
                                        text = "Last UTR: ${user.lastUpiTxnId} (₹${user.lastPaymentAmountInr})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!user.isPaidPremium) {
                                    Text(
                                        text = "Streak: ${user.streakDays}/6 days • Today: ${user.todayUsageSeconds / 60} min",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onAdminSetUserPlan(user.userId, user.fullName, "Monthly") },
                                modifier = Modifier.testTag("admin_grant_monthly_${user.userId}")
                            ) {
                                Text("Grant Monthly")
                            }
                            FilledTonalButton(
                                onClick = { onAdminSetUserPlan(user.userId, user.fullName, "Yearly") },
                                modifier = Modifier.testTag("admin_grant_yearly_${user.userId}")
                            ) {
                                Text("Grant Yearly")
                            }
                            FilledTonalButton(
                                onClick = { onAdminSetUserPlan(user.userId, user.fullName, "FreeReward1Day") }
                            ) {
                                Text("Grant 1-Day Free")
                            }
                            if (user.isPremium) {
                                OutlinedButton(
                                    onClick = { onAdminSetUserPlan(user.userId, user.fullName, "Free") },
                                    modifier = Modifier.testTag("admin_revoke_premium_${user.userId}")
                                ) {
                                    Text("Revoke Premium")
                                }
                            }
                            OutlinedButton(
                                onClick = { onAdminDeleteUser(user.userId, user.fullName) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("admin_delete_user_${user.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
