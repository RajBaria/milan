package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.PremiumGoldLight
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

const val MILAN_PACKAGE_NAME = "com.rajkumar.milan"
const val MILAN_APP_ADS_TXT = "google.com, pub-5899208639069583, DIRECT, f08c47fec0942fa0"

const val MILAN_ADMOB_APP_ID = "ca-app-pub-5899208639069583~7829989097"
const val MILAN_ADMOB_BANNER_UNIT_ID = "ca-app-pub-5899208639069583/6355632037"
const val MILAN_ADMOB_APP_OPEN_UNIT_ID = "ca-app-pub-5899208639069583/1714513596"
const val MILAN_ADMOB_INTERSTITIAL_UNIT_ID = "ca-app-pub-5899208639069583/1236660584"
const val MILAN_ADMOB_NATIVE_UNIT_ID = "ca-app-pub-5899208639069583/2620613186"

const val ADMOB_APP_ID = MILAN_ADMOB_APP_ID
const val ADMOB_BANNER_UNIT_ID = MILAN_ADMOB_BANNER_UNIT_ID
const val ADMOB_APP_OPEN_UNIT_ID = MILAN_ADMOB_APP_OPEN_UNIT_ID
const val ADMOB_INTERSTITIAL_UNIT_ID = MILAN_ADMOB_INTERSTITIAL_UNIT_ID
const val ADMOB_NATIVE_UNIT_ID = MILAN_ADMOB_NATIVE_UNIT_ID

/**
 * Manages App Open Ads and Interstitial Ads for Free-tier users.
 * Premium users never see ads. No Ad IDs are shown in the UI.
 */
object MilanAdMobManager {
    private const val TAG = "MilanAdMobManager"
    private const val APP_OPEN_COOLDOWN_MS = 2 * 60 * 1000L // 1 impression / 2 minutes per AdMob setting

    private var isSdkInitialized: Boolean = false
    private var lastAppOpenShownAtMs: Long = 0L
    private var isAppOpenLoading: Boolean = false
    private var cachedInterstitialAd: InterstitialAd? = null
    private var isInterstitialLoading: Boolean = false
    private var interactionCounter: Int = 0

    /**
     * Returns true only on physical Android devices where `android.adservices.MEASUREMENT_SERVICE`
     * is properly bound, preventing `E/adservices: onNullBinding android.adservices.MEASUREMENT_SERVICE`
     * on virtual/emulator environments.
     */
    fun isAdServicesRuntimeSupported(context: Context): Boolean {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.FINGERPRINT.contains("sdk_gphone", ignoreCase = true) ||
            Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.PRODUCT.contains("emulator", ignoreCase = true)
        if (isEmulator) return false

        return try {
            val measurementIntent = Intent("android.adservices.MEASUREMENT_SERVICE")
            val resolved = context.packageManager.queryIntentServices(measurementIntent, 0)
            resolved.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun initializeIfSupported(context: Context) {
        if (isSdkInitialized || !isAdServicesRuntimeSupported(context)) return
        try {
            MobileAds.initialize(context.applicationContext) {
                isSdkInitialized = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skipped MobileAds initialization", e)
        }
    }

    fun loadAndMaybeShowAppOpenAd(context: Context, isPremium: Boolean) {
        if (isPremium || !isAdServicesRuntimeSupported(context)) return
        initializeIfSupported(context)
        val now = System.currentTimeMillis()
        if (now - lastAppOpenShownAtMs < APP_OPEN_COOLDOWN_MS || isAppOpenLoading) return

        val activity = context as? Activity ?: return
        isAppOpenLoading = true
        try {
            AppOpenAd.load(
                context.applicationContext,
                MILAN_ADMOB_APP_OPEN_UNIT_ID,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isAppOpenLoading = false
                        if (!activity.isFinishing) {
                            lastAppOpenShownAtMs = System.currentTimeMillis()
                            ad.show(activity)
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isAppOpenLoading = false
                        Log.d(TAG, "AppOpenAd load status: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isAppOpenLoading = false
            Log.w(TAG, "Skipped AppOpenAd load in test environment", e)
        }
    }

    fun preloadInterstitial(context: Context, isPremium: Boolean) {
        if (isPremium || cachedInterstitialAd != null || isInterstitialLoading || !isAdServicesRuntimeSupported(context)) return
        initializeIfSupported(context)
        isInterstitialLoading = true
        try {
            InterstitialAd.load(
                context.applicationContext,
                MILAN_ADMOB_INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        isInterstitialLoading = false
                        cachedInterstitialAd = interstitialAd
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isInterstitialLoading = false
                        cachedInterstitialAd = null
                        Log.d(TAG, "InterstitialAd load status: ${loadAdError.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.w(TAG, "Skipped InterstitialAd load in test environment", e)
        }
    }

    fun maybeShowInterstitial(context: Context, isPremium: Boolean) {
        if (isPremium || !isAdServicesRuntimeSupported(context)) {
            cachedInterstitialAd = null
            return
        }
        interactionCounter += 1
        if (interactionCounter % 3 == 0) {
            val activity = context as? Activity
            val ad = cachedInterstitialAd
            if (activity != null && !activity.isFinishing && ad != null) {
                cachedInterstitialAd = null
                ad.show(activity)
                preloadInterstitial(context, isPremium = false)
            } else {
                preloadInterstitial(context, isPremium = false)
            }
        } else if (cachedInterstitialAd == null) {
            preloadInterstitial(context, isPremium = false)
        }
    }
}

/**
 * AdMob Banner Ad Unit (No Ad ID shown in UI)
 */
@Composable
fun MilanAdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = MILAN_ADMOB_BANNER_UNIT_ID
) {
    val context = LocalContext.current
    val useLiveAdView = remember(context) { MilanAdMobManager.isAdServicesRuntimeSupported(context) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("admob_banner_container"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sponsored",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (useLiveAdView) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        MilanAdMobManager.initializeIfSupported(ctx)
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            } else {
                Text(
                    text = "Milan Gold • Unlimited Likes, Messages & Full Distance Discovery",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * AdMob Native Advanced Ad Unit (No Ad ID shown in UI)
 */
@Composable
fun MilanNativeAdvancedAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = MILAN_ADMOB_NATIVE_UNIT_ID,
    onUpgradeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val useLiveNativeAd = remember(context) { MilanAdMobManager.isAdServicesRuntimeSupported(context) }
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId, useLiveNativeAd) {
        var currentAd: NativeAd? = null
        if (useLiveNativeAd) {
            try {
                MilanAdMobManager.initializeIfSupported(context)
                val adLoader = AdLoader.Builder(context, adUnitId)
                    .forNativeAd { ad ->
                        currentAd?.destroy()
                        currentAd = ad
                        loadedNativeAd = ad
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d("MilanNativeAd", "Native ad load status: ${adError.message}")
                        }
                    })
                    .withNativeAdOptions(NativeAdOptions.Builder().build())
                    .build()

                adLoader.loadAd(AdRequest.Builder().build())
            } catch (e: Exception) {
                Log.w("MilanNativeAd", "Skipped NativeAd load in test environment", e)
            }
        }

        onDispose {
            currentAd?.destroy()
            loadedNativeAd?.destroy()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_native_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = PremiumGoldLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Sponsored",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6B4C00),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            val nativeAd = loadedNativeAd
            if (nativeAd != null) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        NativeAdView(ctx).apply {
                            val headlineView = TextView(ctx).apply {
                                textSize = 16f
                                text = nativeAd.headline ?: "Sponsored"
                            }
                            addView(headlineView)
                            this.headlineView = headlineView
                            setNativeAd(nativeAd)
                        }
                    },
                    update = { view ->
                        (view.headlineView as? TextView)?.text = nativeAd.headline ?: "Sponsored"
                        view.setNativeAd(nativeAd)
                    }
                )
                if (!nativeAd.body.isNullOrBlank()) {
                    Text(
                        text = nativeAd.body!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Upgrade to Milan Gold for a 100% Ad-Free experience and direct messaging without liking first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onUpgradeClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = loadedNativeAd?.callToAction ?: "Remove Ads • Upgrade to Gold"
                )
            }
        }
    }
}
