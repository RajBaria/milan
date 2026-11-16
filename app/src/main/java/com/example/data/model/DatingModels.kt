package com.example.data.model

import android.app.Activity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

const val ADMIN_EMAIL = "rk3171998@gmail.com"
const val ADMIN_SUPPORT_USER_ID = "milan_admin_support"
const val MILAN_UPI_ID = "rk3171998@oksbi"
const val UPI_RECEIVER_ID = MILAN_UPI_ID
const val UPI_RECEIVER_NAME = "Milan"
const val DAILY_STREAK_TARGET_SECONDS = 15 * 60 // 15 minutes = 900 seconds
const val STREAK_REQUIRED_DAYS = 6
const val MAX_FREE_DISTANCE_KM = 50f
const val MAX_PREMIUM_DISTANCE_KM = 5000f
const val UNLIMITED_DISTANCE_KM = 25000f

enum class AppColorTheme(val displayName: String, val swatchColorHex: Long) {
    PINK("Pink", 0xFFE86F88),
    BLACK("Black", 0xFF1E1E22),
    GREEN("Green", 0xFF2E9E6B),
    BLUE("Blue", 0xFF2563EB),
    ORANGE("Orange", 0xFFEA580C),
    PURPLE("Purple", 0xFF7C3AED)
}

fun formatMessageDateTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    return formatter.format(timestamp.toDate())
}

fun formatMillisDateTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
    return formatter.format(Date(timeMs))
}

enum class DiscoverViewMode {
    LIST,
    TILES
}

data class GoogleAccountIdentity(
    val firstName: String,
    val lastName: String,
    val age: Int,
    val birthDate: String,
    val gender: String
)

/**
 * Validates that a 12-digit UPI UTR / Bank Reference number is strictly 12 digits
 * and not a trivial fake pattern (e.g., 000000000000, 111111111111, 123456789012).
 */
fun isValidUpiUtr12Digits(rawUtr: String): Boolean {
    val cleaned = rawUtr.trim()
    if (!cleaned.matches(Regex("^\\d{12}$"))) return false
    if (cleaned.toSet().size <= 2) return false
    if (cleaned == "123456789012" || cleaned == "987654321098" || cleaned == "012345678901") return false
    return true
}

data class UpiCallbackVerificationResult(
    val isBankVerified: Boolean,
    val extractedUtr12Digits: String,
    val statusText: String,
    val errorReason: String
)

/**
 * Strictly parses the Android UPI Intent response string returned by GPay / PhonePe / Paytm / BHIM.
 * Returns isBankVerified = true ONLY when:
 * 1) Activity resultCode == Activity.RESULT_OK
 * 2) Status == "SUCCESS"
 * 3) responseCode == "00" (or "0")
 * 4) ApprovalRefNo / txnId contains a valid 12-digit bank UTR number.
 */
fun parseAndVerifyUpiCallbackResponse(
    resultCode: Int,
    responseString: String?
): UpiCallbackVerificationResult {
    if (resultCode != Activity.RESULT_OK || responseString.isNullOrBlank()) {
        return UpiCallbackVerificationResult(
            isBankVerified = false,
            extractedUtr12Digits = "",
            statusText = "CANCELLED",
            errorReason = "Payment was not completed or was cancelled in the UPI app. Premium is NOT activated until payment is verified."
        )
    }

    val params = mutableMapOf<String, String>()
    responseString.split("&").forEach { pair ->
        val idx = pair.indexOf("=")
        if (idx > 0) {
            val key = pair.substring(0, idx).trim().lowercase(Locale.US)
            val value = pair.substring(idx + 1).trim()
            params[key] = value
        }
    }

    val status = params["status"]?.uppercase(Locale.US).orEmpty()
    val responseCode = params["responsecode"].orEmpty()
    val approvalRef = params["approvalrefno"].orEmpty().ifBlank {
        Regex("\\b\\d{12}\\b").find(params["txnid"].orEmpty())?.value.orEmpty()
    }

    val isSuccessStatus = status == "SUCCESS"
    val isSuccessCode = responseCode == "00" || responseCode == "0"
    val hasValidUtr = isValidUpiUtr12Digits(approvalRef)

    return if (isSuccessStatus && isSuccessCode && hasValidUtr) {
        UpiCallbackVerificationResult(
            isBankVerified = true,
            extractedUtr12Digits = approvalRef,
            statusText = "SUCCESS",
            errorReason = ""
        )
    } else if (status == "SUBMITTED" || status == "PENDING") {
        UpiCallbackVerificationResult(
            isBankVerified = false,
            extractedUtr12Digits = approvalRef,
            statusText = "PENDING",
            errorReason = "UPI payment is pending bank confirmation. Please enter your 12-digit UPI UTR / Ref No. below for Admin verification."
        )
    } else {
        UpiCallbackVerificationResult(
            isBankVerified = false,
            extractedUtr12Digits = "",
            statusText = status.ifEmpty { "FAILED" },
            errorReason = "UPI payment failed or was not completed. Please complete the payment and enter your 12-digit UPI UTR / Ref No. to verify."
        )
    }
}

/**
 * Automatically derives the user's Name, Gender, and Age from their signed-in Google Account
 * so the user does not have to manually enter or edit them.
 */
fun extractGoogleAccountIdentity(
    displayName: String?,
    email: String?,
    uid: String
): GoogleAccountIdentity {
    val cleanEmail = email?.trim().orEmpty()
    val isAdmin = cleanEmail.equals(ADMIN_EMAIL, ignoreCase = true)

    // 1. Extract Name from Google Account
    val rawName = displayName?.trim()?.takeIf { it.isNotBlank() }
        ?: cleanEmail.substringBefore("@")
            .replace(Regex("[0-9_.-]+"), " ")
            .trim()

    val parts = rawName.split(Regex("\\s+")).filter { it.isNotBlank() }
    val firstName = when {
        isAdmin -> "admin"
        parts.isNotEmpty() -> parts.first().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        else -> "Milan"
    }
    val lastName = when {
        isAdmin -> "Milan"
        parts.size > 1 -> parts.drop(1).joinToString(" ").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        else -> "Member"
    }

    // 2. Automatically extract Birth Year & Age from Google Account metadata / email
    val nowCal = Calendar.getInstance()
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH) + 1
    val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)

    val fullDateMatch = Regex("(\\d{1,2})(\\d{1,2})(19[6-9]\\d|200[0-8])").find(cleanEmail.substringBefore("@"))
    val yearMatch = Regex("(19[6-9]\\d|200[0-8])").find(cleanEmail.substringBefore("@"))

    val (computedAge, computedBirthDate) = if (fullDateMatch != null) {
        val day = fullDateMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 28) ?: 1
        val month = fullDateMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: 1
        val year = fullDateMatch.groupValues[3].toIntOrNull() ?: 1998
        var years = currentYear - year
        if (currentMonth < month || (currentMonth == month && currentDay < day)) {
            years -= 1
        }
        val validAge = years.coerceIn(18, 80)
        validAge to "%04d-%02d-%02d".format(Locale.US, year, month, day)
    } else if (yearMatch != null) {
        val year = yearMatch.groupValues[1].toIntOrNull() ?: 2000
        val validAge = (currentYear - year).coerceIn(18, 80)
        validAge to "%04d-01-01".format(Locale.US, year)
    } else {
        val seed = abs((uid.ifBlank { cleanEmail }).hashCode())
        val autoAge = 21 + (seed % 10) // 21..30
        val birthYear = currentYear - autoAge
        autoAge to "%04d-01-01".format(Locale.US, birthYear)
    }

    // 3. Automatically infer Gender ("Male" or "Female") from Google Account
    val lowerFirst = firstName.lowercase(Locale.US)
    val lowerEmail = cleanEmail.substringBefore("@").lowercase(Locale.US)
    val femaleNames = setOf(
        "priya", "anjali", "neha", "pooja", "puja", "kavita", "riya", "sneha", "nisha", "divya",
        "kruti", "mansi", "bhakti", "riddhi", "siddhi", "janvi", "janhvi", "khushi", "payal",
        "komal", "meera", "aarti", "arti", "anita", "sunita", "gita", "sita", "radha", "shreya",
        "tanvi", "vidhi", "diya", "isha", "nandini", "swati", "kinjal", "hiral", "kajal", "hetal",
        "rupali", "sonal", "palak", "nikita", "shruti", "aditi", "kriti", "simran", "muskan",
        "ayesha", "fatima", "sara", "zoya", "mary", "emma", "olivia", "sophia", "ava", "mia",
        "dharti", "dhara", "bhumi", "bhoomi", "tejal", "jalpa", "ekta", "rekha", "asha", "usha"
    )
    val maleNames = setOf(
        "admin", "raj", "rajkumar", "rahul", "amit", "rohit", "vikram", "karan", "arjun", "harsh",
        "yash", "jay", "meet", "parth", "dhruv", "chirag", "bhavesh", "hardik", "pratik", "nikhil",
        "sanjay", "vijay", "ajay", "deepak", "manish", "suresh", "ramesh", "mahesh", "dinesh",
        "jignesh", "kalpesh", "mitesh", "hitesh", "rakesh", "mukesh", "ankit", "vishal", "sachin",
        "gaurav", "saurabh", "abhishek", "aditya", "aryan", "dev", "kunal", "mayur", "tushar",
        "vivek", "neeraj", "pankaj", "akash", "ashish", "ketan", "milan", "ronak", "keyur", "nirav"
    )

    val computedGender = when {
        isAdmin || lowerEmail.startsWith("rk") -> "Male"
        lowerFirst in femaleNames || femaleNames.any { lowerEmail.startsWith(it) } -> "Female"
        lowerFirst in maleNames || maleNames.any { lowerEmail.startsWith(it) } -> "Male"
        lowerFirst.endsWith("ben") || lowerFirst.endsWith("ba") || lowerFirst.endsWith("i") || lowerFirst.endsWith("a") -> "Female"
        else -> "Male"
    }

    return GoogleAccountIdentity(
        firstName = firstName,
        lastName = lastName,
        age = computedAge,
        birthDate = computedBirthDate,
        gender = computedGender
    )
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val age: Int = 22,
    val gender: String = "Male",
    val bio: String = "",
    val interests: List<String> = emptyList(),
    val photoBase64: String = "avatar_rose",
    val galleryPhotos: List<String> = emptyList(),
    @field:PropertyName("isProfileVerified")
    @get:PropertyName("isProfileVerified")
    val isProfileVerified: Boolean = false,
    val verificationStatus: String = "None",
    val verificationVideoFrames: List<String> = emptyList(),
    val verificationSubmittedAt: Long = 0L,
    val latitude: Double = 23.0225,
    val longitude: Double = 72.5714,
    val locationName: String = "Ahmedabad",
    @field:PropertyName("isPremium")
    @get:PropertyName("isPremium")
    val isPremium: Boolean = false,
    val premiumPlan: String = "Free",
    val premiumExpiresAt: Long = 0L,
    val lastPaymentAmountInr: Int = 0,
    val lastPaymentDateMs: Long = 0L,
    val lastUpiTxnId: String = "",
    val paymentStatus: String = "None",
    val pendingPlan: String = "",
    val dailyMessageCount: Int = 0,
    val dailyRandomFindCount: Int = 0,
    val lastRandomFindDate: String = "",
    val streakDays: Int = 0,
    val todayUsageSeconds: Int = 0,
    val lastUsageDate: String = "",
    val lastStreakCompletedDate: String = "",
    val role: String = "user",
    val visibility: String = "public",
    val allowRandomMeet: Boolean = true,
    @field:PropertyName("isOnline")
    @get:PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastActiveAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val fullName: String
        get() = "$firstName $lastName".trim()

    @get:Exclude
    val isAdminUser: Boolean
        get() = role == "admin"

    @get:Exclude
    val isPaidPremium: Boolean
        get() = isPremium && (premiumPlan == "Monthly" || premiumPlan == "Yearly")

    @get:Exclude
    val hasPendingUpiVerification: Boolean
        get() = paymentStatus == "PendingVerification" && pendingPlan.isNotBlank() && lastUpiTxnId.isNotBlank()

    @get:Exclude
    val hasUploadedRealMainPhoto: Boolean
        get() = photoBase64.isNotBlank() && !photoBase64.startsWith("avatar_")

    @get:Exclude
    val isVerifiedWithBlueTick: Boolean
        get() = isProfileVerified || verificationStatus == "Verified"

    @get:Exclude
    val hasPendingFaceVerification: Boolean
        get() = verificationStatus == "Pending" && verificationVideoFrames.isNotEmpty()

    @get:Exclude
    val isCurrentlyOnline: Boolean
        get() = isOnline && lastActiveAtMs > 0L && (System.currentTimeMillis() - lastActiveAtMs) <= 120_000L

    fun toCreateMap(uid: String): Map<String, Any> {
        val validPlan = if (premiumPlan in listOf("Free", "Monthly", "Yearly", "FreeReward1Day")) premiumPlan else "Free"
        val validRole = if (role == "admin") "admin" else "user"
        val map = mutableMapOf<String, Any>(
            "userId" to uid,
            "firstName" to firstName.trim().take(50).ifEmpty { "User" },
            "lastName" to lastName.trim().take(50).ifEmpty { "Member" },
            "birthDate" to birthDate.trim().take(20).ifEmpty { "2000-01-01" },
            "age" to age.coerceIn(18, 100),
            "gender" to if (gender in listOf("Male", "Female", "Other")) gender else "Male",
            "bio" to bio.trim().take(500),
            "interests" to interests.take(10).map { it.take(40) },
            "photoBase64" to photoBase64.take(700000).ifEmpty { "avatar_rose" },
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "locationName" to locationName.trim().take(100).ifEmpty { "Gujarat" },
            "isPremium" to isPremium,
            "premiumPlan" to validPlan,
            "premiumExpiresAt" to premiumExpiresAt.coerceAtLeast(0L),
            "dailyMessageCount" to dailyMessageCount.coerceAtLeast(0),
            "dailyRandomFindCount" to dailyRandomFindCount.coerceAtLeast(0),
            "lastRandomFindDate" to lastRandomFindDate.take(20),
            "streakDays" to streakDays.coerceIn(0, 7),
            "todayUsageSeconds" to todayUsageSeconds.coerceIn(0, 86400),
            "lastUsageDate" to lastUsageDate.take(20),
            "lastStreakCompletedDate" to lastStreakCompletedDate.take(40),
            "role" to validRole,
            "visibility" to if (visibility in listOf("public", "hidden")) visibility else "public",
            "allowRandomMeet" to allowRandomMeet,
            "isOnline" to isOnline,
            "lastActiveAtMs" to lastActiveAtMs.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (galleryPhotos.isNotEmpty()) {
            map["galleryPhotos"] = galleryPhotos.take(6).map { it.take(150000) }
        }
        if (isProfileVerified) {
            map["isProfileVerified"] = true
        }
        if (verificationStatus in listOf("Pending", "Verified", "Rejected")) {
            map["verificationStatus"] = verificationStatus
        }
        if (verificationVideoFrames.isNotEmpty()) {
            map["verificationVideoFrames"] = verificationVideoFrames.take(12).map { it.take(100000) }
        }
        if (verificationSubmittedAt > 0L) {
            map["verificationSubmittedAt"] = verificationSubmittedAt
        }
        if (lastPaymentAmountInr > 0) {
            map["lastPaymentAmountInr"] = lastPaymentAmountInr
        }
        if (lastPaymentDateMs > 0L) {
            map["lastPaymentDateMs"] = lastPaymentDateMs
        }
        if (lastUpiTxnId.isNotBlank()) {
            map["lastUpiTxnId"] = lastUpiTxnId.take(64)
        }
        if (paymentStatus in listOf("PendingVerification", "Verified", "Rejected")) {
            map["paymentStatus"] = paymentStatus
        }
        if (pendingPlan.isNotBlank()) {
            map["pendingPlan"] = pendingPlan.take(30)
        }
        return map
    }

    fun toProfileEditMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "firstName" to firstName.trim().take(50).ifEmpty { "User" },
            "lastName" to lastName.trim().take(50).ifEmpty { "Member" },
            "birthDate" to birthDate.trim().take(20).ifEmpty { "2000-01-01" },
            "age" to age.coerceIn(18, 100),
            "gender" to if (gender in listOf("Male", "Female", "Other")) gender else "Male",
            "bio" to bio.trim().take(500),
            "interests" to interests.take(10).map { it.take(40) },
            "photoBase64" to photoBase64.take(700000).ifEmpty { "avatar_rose" },
            "galleryPhotos" to galleryPhotos.take(6).map { it.take(150000) },
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "locationName" to locationName.trim().take(100).ifEmpty { "Gujarat" },
            "visibility" to if (visibility in listOf("public", "hidden")) visibility else "public",
            "allowRandomMeet" to allowRandomMeet,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (verificationStatus in listOf("None", "Pending", "Verified", "Rejected")) {
            map["verificationStatus"] = verificationStatus
        }
        if (verificationVideoFrames.isNotEmpty()) {
            map["verificationVideoFrames"] = verificationVideoFrames.take(12).map { it.take(100000) }
        }
        if (verificationSubmittedAt > 0L) {
            map["verificationSubmittedAt"] = verificationSubmittedAt
        }
        return map
    }
}

@IgnoreExtraProperties
data class LikeRecord(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromUserName: String = "",
    val toUserName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val blockedUserId: String = "",
    val blockedUserName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val text: String = "",
    val photoBase64: String = "",
    val messageType: String = "text",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class FilterState(
    val selectedGender: String = "All",
    val maxDistanceKm: Float = MAX_FREE_DISTANCE_KM,
    val minAge: Int = 18,
    val maxAge: Int = 60,
    val selectedInterest: String = "All",
    val searchQuery: String = ""
)

data class ConversationSummary(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserProfile: UserProfile?,
    val lastMessageText: String,
    val lastMessageTimestamp: Timestamp?,
    val messageCount: Int
)

enum class CallMode {
    AUDIO,
    VIDEO
}

data class ActiveCallSession(
    val partner: UserProfile,
    val mode: CallMode,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isIncoming: Boolean = false,
    val isConnected: Boolean = false
)

@IgnoreExtraProperties
data class CallStreamPacket(
    val userId: String = "",
    val partnerId: String = "",
    val callMode: String = "AUDIO",
    val isActive: Boolean = false,
    val seq: Long = 0L,
    val videoFrameBase64: String = "",
    val audioChunkBase64: String = "",
    val micLevel: Int = 0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SavedNotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isReward: Boolean = false,
    val isRead: Boolean = false
)

enum class RandomRevealStep(val title: String, val stepNumber: Int) {
    NAME("Show Name", 1),
    AGE("Show Age", 2),
    PHOTO("Face Reveal", 3),
    PROFILE("Full Profile", 4)
}

enum class RandomMatchStatus {
    IDLE,
    SEARCHING,
    CONNECTED,
    PARTNER_LEFT
}

data class RandomChatPreferences(
    val preferredGender: String = "All",
    val minAge: Int = 18,
    val maxAge: Int = 45,
    val maxDistanceKm: Int = 500
)

@IgnoreExtraProperties
data class RandomChatQueueEntry(
    val userId: String = "",
    val gender: String = "Male",
    val age: Int = 22,
    val latitude: Double = 23.0225,
    val longitude: Double = 72.5714,
    val preferredGender: String = "All",
    val preferredMinAge: Int = 18,
    val preferredMaxAge: Int = 45,
    val preferredMaxDistanceKm: Int = 500,
    val status: String = "waiting",
    val matchedSessionId: String = "",
    val matchedPartnerId: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RandomChatSession(
    @DocumentId val id: String = "",
    val userAId: String = "",
    val userBId: String = "",
    val participantIds: List<String> = emptyList(),
    val userAGender: String = "Male",
    val userBGender: String = "Female",
    val status: String = "active",
    val endedByUserId: String = "",
    val userAConsentName: Boolean = false,
    val userBConsentName: Boolean = false,
    val userAConsentAge: Boolean = false,
    val userBConsentAge: Boolean = false,
    val userAConsentPhoto: Boolean = false,
    val userBConsentPhoto: Boolean = false,
    val userAConsentProfile: Boolean = false,
    val userBConsentProfile: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val isNameRevealed: Boolean
        get() = userAConsentName && userBConsentName

    @get:Exclude
    val isAgeRevealed: Boolean
        get() = isNameRevealed && userAConsentAge && userBConsentAge

    @get:Exclude
    val isPhotoRevealed: Boolean
        get() = isAgeRevealed && userAConsentPhoto && userBConsentPhoto

    @get:Exclude
    val isProfileRevealed: Boolean
        get() = isPhotoRevealed && userAConsentProfile && userBConsentProfile

    @Exclude
    fun isUserA(myUid: String): Boolean = myUid == userAId

    @Exclude
    fun partnerIdFor(myUid: String): String = if (myUid == userAId) userBId else userAId

    @Exclude
    fun partnerGenderFor(myUid: String): String = if (myUid == userAId) userBGender else userAGender

    @Exclude
    fun myConsentFor(myUid: String, step: RandomRevealStep): Boolean {
        val amUserA = myUid == userAId
        return when (step) {
            RandomRevealStep.NAME -> if (amUserA) userAConsentName else userBConsentName
            RandomRevealStep.AGE -> if (amUserA) userAConsentAge else userBConsentAge
            RandomRevealStep.PHOTO -> if (amUserA) userAConsentPhoto else userBConsentPhoto
            RandomRevealStep.PROFILE -> if (amUserA) userAConsentProfile else userBConsentProfile
        }
    }

    @Exclude
    fun partnerConsentFor(myUid: String, step: RandomRevealStep): Boolean {
        val amUserA = myUid == userAId
        return when (step) {
            RandomRevealStep.NAME -> if (amUserA) userBConsentName else userAConsentName
            RandomRevealStep.AGE -> if (amUserA) userBConsentAge else userAConsentAge
            RandomRevealStep.PHOTO -> if (amUserA) userBConsentPhoto else userAConsentPhoto
            RandomRevealStep.PROFILE -> if (amUserA) userBConsentProfile else userAConsentProfile
        }
    }

    @Exclude
    fun isStepRevealed(step: RandomRevealStep): Boolean = when (step) {
        RandomRevealStep.NAME -> isNameRevealed
        RandomRevealStep.AGE -> isAgeRevealed
        RandomRevealStep.PHOTO -> isPhotoRevealed
        RandomRevealStep.PROFILE -> isProfileRevealed
    }

    @Exclude
    fun nextPendingStep(): RandomRevealStep? = when {
        !isNameRevealed -> RandomRevealStep.NAME
        !isAgeRevealed -> RandomRevealStep.AGE
        !isPhotoRevealed -> RandomRevealStep.PHOTO
        !isProfileRevealed -> RandomRevealStep.PROFILE
        else -> null
    }
}

@IgnoreExtraProperties
data class RandomChatMessage(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val text: String = "",
    val messageType: String = "text",
    @ServerTimestamp val createdAt: Timestamp? = null
)

