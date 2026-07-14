package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.BoxesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.StudyScreen
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NavyDarkBg
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LeitnerViewModel
import kotlinx.coroutines.delay
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.os.SystemClock
import android.util.Log

// Central Constants for Javaneyar Ad Timing
const val AD_INTERVAL_MILLIS = 7 * 60 * 1000L // 420000 ms
const val AD_INTERVAL_SECONDS = 7 * 60 // 420
const val AD_DURATION_SECONDS = 15

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Instantiate the ViewModel
        val viewModel = ViewModelProvider(this)[LeitnerViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppContainer(viewModel)
            }
        }
    }
}

enum class AppTab {
    Home, Study, Boxes, Progress
}

@Composable
fun MainAppContainer(viewModel: LeitnerViewModel) {
    var currentTab by remember { mutableStateOf(AppTab.Study) } // "مطالعه" is active by default as requested

    val allCollocations by viewModel.allCollocations.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val todayNewCards by viewModel.assignedNewCardsToday.collectAsStateWithLifecycle()
    val dueCards by viewModel.dueCardsToday.collectAsStateWithLifecycle()
    val sessionIndex by viewModel.sessionIndex.collectAsStateWithLifecycle()
    val showAnswer by viewModel.showAnswer.collectAsStateWithLifecycle()
    val boxCounts by viewModel.boxCounts.collectAsStateWithLifecycle()
    val isRatingInProgress by viewModel.isRatingInProgress.collectAsStateWithLifecycle()

    // Javaneyar Ad Timer Logic
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("javaneh_ad_prefs", Context.MODE_PRIVATE) }
    
    var lastAdShowTime by rememberSaveable { mutableStateOf(prefs.getLong("last_ad_show_time", 0L)) }
    var activeSeconds by rememberSaveable { mutableStateOf(prefs.getInt("active_seconds", 0)) }
    
    // Sanitize lastAdShowTime if corrupted/future and handle legacy activeSeconds non-destructively
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (lastAdShowTime > now) {
            lastAdShowTime = 0L
            prefs.edit().putLong("last_ad_show_time", 0L).apply()
        }
        
        if (activeSeconds < 0) {
            activeSeconds = 0
            prefs.edit().putInt("active_seconds", 0).apply()
        } else if (activeSeconds >= AD_INTERVAL_SECONDS) {
            // Cap slightly below interval (e.g. 10 seconds remaining) to ensure a smooth transition
            activeSeconds = AD_INTERVAL_SECONDS - 10
            prefs.edit().putInt("active_seconds", activeSeconds).apply()
        }
    }
    
    // Auto-persist active seconds whenever they increment
    LaunchedEffect(activeSeconds) {
        prefs.edit().putInt("active_seconds", activeSeconds).apply()
    }
    
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isAppActive by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    
    var showAdDialog by rememberSaveable { mutableStateOf(false) }
    var isTestAd by rememberSaveable { mutableStateOf(false) }
    var adCountdownSeconds by rememberSaveable { mutableStateOf(AD_DURATION_SECONDS) }
    var isAdPending by rememberSaveable { mutableStateOf(false) }

    // Track App Foreground/Background state using active lifecycle state check
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isAppActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Active Timer (Increments every second only if app is active and ad is NOT showing)
    LaunchedEffect(isAppActive, showAdDialog, isRatingInProgress) {
        if (isAppActive && !showAdDialog) {
            while (true) {
                delay(1000L)
                activeSeconds++
                
                // Check if the required active use seconds have passed
                if (activeSeconds >= AD_INTERVAL_SECONDS) {
                    val now = System.currentTimeMillis()
                    // Confirm that at least the interval has also passed in real-world time since last ad show
                    if (now - lastAdShowTime >= AD_INTERVAL_MILLIS) {
                        // Ensure we are in a safe state (not rating/saving a card) before displaying the ad
                        if (!isRatingInProgress) {
                            val activity = context.findActivity()
                            val isValid = activity != null && !activity.isFinishing && !activity.isDestroyed
                            if (isValid) {
                                showAdDialog = true
                                adCountdownSeconds = AD_DURATION_SECONDS
                            } else {
                                isAdPending = true
                            }
                        } else {
                            isAdPending = true
                        }
                    }
                }
            }
        }
    }

    // Observe state to trigger pending ad as soon as we are in a safe foreground state
    LaunchedEffect(isAppActive, isRatingInProgress, isAdPending, showAdDialog) {
        if (isAdPending && isAppActive && !isRatingInProgress && !showAdDialog) {
            val now = System.currentTimeMillis()
            if (now - lastAdShowTime >= AD_INTERVAL_MILLIS) {
                val activity = context.findActivity()
                val isValid = activity != null && !activity.isFinishing && !activity.isDestroyed
                if (isValid) {
                    isAdPending = false
                    showAdDialog = true
                    adCountdownSeconds = AD_DURATION_SECONDS
                }
            }
        }
    }

    // Safe memory-optimized ad image loader
    val adImageBitmap = remember(showAdDialog) {
        if (!showAdDialog) null else {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeResource(context.resources, R.drawable.javaneh_ad_poster, options)
                
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    Log.e("JavaneyarAd", "Ad image asset not found or invalid.")
                    null
                } else {
                    options.inSampleSize = calculateInSampleSize(options, 512, 768)
                    options.inJustDecodeBounds = false
                    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.javaneh_ad_poster, options)
                    if (bitmap == null) {
                        Log.e("JavaneyarAd", "Decoded bitmap is null.")
                        null
                    } else {
                        bitmap.asImageBitmap()
                    }
                }
            } catch (e: Throwable) {
                Log.e("JavaneyarAd", "Error loading ad image: ${e.message}", e)
                null
            }
        }
    }

    // Automatically dismiss the ad dialog if the image cannot be loaded
    LaunchedEffect(showAdDialog, adImageBitmap) {
        if (showAdDialog && adImageBitmap == null) {
            Log.e("JavaneyarAd", "Ad dialog triggered but asset is invalid or missing. Dismissing ad.")
            showAdDialog = false
            activeSeconds = 0
            isAdPending = false
        }
    }

    // Ad countdown timer based on real system time (cannot be paused or bypassed by backgrounding)
    LaunchedEffect(showAdDialog) {
        if (showAdDialog) {
            val startTime = SystemClock.elapsedRealtime()
            val adEndTime = startTime + 15_000L
            
            if (!isTestAd) {
                val realStartTime = System.currentTimeMillis()
                // Save the last ad show time to SharedPreferences immediately when it shows
                prefs.edit().putLong("last_ad_show_time", realStartTime).apply()
                lastAdShowTime = realStartTime
                activeSeconds = 0 // Reset the active seconds timer for the next interval
            }
            
            while (showAdDialog) {
                val now = SystemClock.elapsedRealtime()
                val remainingMillis = adEndTime - now
                val remainingSeconds = (remainingMillis + 999) / 1000
                val remaining = remainingSeconds.coerceIn(0, 15).toInt()
                adCountdownSeconds = remaining
                if (remaining <= 0) {
                    break
                }
                delay(100L) // Poll frequently to ensure absolute precision
            }
            // Auto dismiss when countdown reaches 0
            showAdDialog = false
            isTestAd = false
        }
    }

    val openJavaneyarUrl = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://javaneyar.ir/")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "مرورگری برای بازکردن سایت جوانه پیدا نشد.", Toast.LENGTH_SHORT).show()
        }
    }

    val onAdClicked = {
        openJavaneyarUrl()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = NavySurface,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar")
                ) {
                    // Home (خانه)
                    NavigationBarItem(
                        selected = currentTab == AppTab.Home,
                        onClick = { currentTab = AppTab.Home },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "خانه"
                            )
                        },
                        label = { Text("خانه", fontSize = 11.sp) },
                        colors = NavigationBarItemColors(),
                        modifier = Modifier.testTag("nav_home")
                    )

                    // Study (مطالعه)
                    NavigationBarItem(
                        selected = currentTab == AppTab.Study,
                        onClick = { currentTab = AppTab.Study },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "مطالعه"
                            )
                        },
                        label = { Text("مطالعه", fontSize = 11.sp) },
                        colors = NavigationBarItemColors(),
                        modifier = Modifier.testTag("nav_study")
                    )

                    // Boxes (جعبه‌ها)
                    NavigationBarItem(
                        selected = currentTab == AppTab.Boxes,
                        onClick = { currentTab = AppTab.Boxes },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "جعبه‌ها"
                            )
                        },
                        label = { Text("جعبه‌ها", fontSize = 11.sp) },
                        colors = NavigationBarItemColors(),
                        modifier = Modifier.testTag("nav_boxes")
                    )

                    // Progress (پیشرفت)
                    NavigationBarItem(
                        selected = currentTab == AppTab.Progress,
                        onClick = { currentTab = AppTab.Progress },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "پیشرفت"
                            )
                        },
                        label = { Text("پیشرفت", fontSize = 11.sp) },
                        colors = NavigationBarItemColors(),
                        modifier = Modifier.testTag("nav_progress")
                    )
                }
            },
            containerColor = NavyDarkBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .statusBarsPadding()
            ) {
                when (currentTab) {
                    AppTab.Home -> {
                        HomeScreen(
                            collocations = allCollocations,
                            onStartStudyClick = { currentTab = AppTab.Study }
                        )
                    }
                    AppTab.Study -> {
                        StudyScreen(
                            categories = viewModel.categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.setCategory(it) },
                            activeDeck = emptyList(), // managed in ViewModel
                            todayNewCards = todayNewCards,
                            dueCards = dueCards,
                            sessionIndex = sessionIndex,
                            showAnswer = showAnswer,
                            onToggleAnswer = { viewModel.toggleShowAnswer() },
                            onRateCard = { card, rating -> viewModel.rateCard(card, rating) },
                            boxCounts = boxCounts,
                            onResetProgress = { viewModel.resetProgress() },
                            onSimulateNewDay = { viewModel.simulateNewDay() }
                        )
                    }
                    AppTab.Boxes -> {
                        BoxesScreen(
                            categories = viewModel.categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.setCategory(it) },
                            collocations = allCollocations
                        )
                    }
                    AppTab.Progress -> {
                        ProgressScreen(
                            categories = viewModel.categories,
                            collocations = allCollocations,
                            onResetProgress = { viewModel.resetProgress() },
                            isDebug = com.example.BuildConfig.DEBUG,
                            adControllerActive = isAppActive && !showAdDialog,
                            adAssetLoaded = true,
                            adVisible = showAdDialog,
                            activeUsageSeconds = activeSeconds,
                            remainingSecondsUntilNextAd = (AD_INTERVAL_SECONDS - activeSeconds).coerceAtLeast(0),
                            lastAdShownTime = lastAdShowTime,
                            currentAppLifecycleState = if (isAppActive) "foreground" else "background",
                            onTriggerAdTest = {
                                isTestAd = true
                                showAdDialog = true
                                adCountdownSeconds = AD_DURATION_SECONDS
                            }
                        )
                    }
                }
            }
        }
        
        // Show the Javaneyar Ad Dialog if triggered
        if (showAdDialog && adImageBitmap != null) {
            JavaneyarAdDialog(
                onAdClicked = onAdClicked,
                countdownSeconds = adCountdownSeconds,
                imageBitmap = adImageBitmap
            )
        }
    }
}

@Composable
fun NavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentGreen,
    selectedTextColor = AccentGreen,
    indicatorColor = NavySurfaceVariant,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

@Composable
fun JavaneyarAdDialog(
    onAdClicked: () -> Unit,
    countdownSeconds: Int,
    imageBitmap: ImageBitmap
) {
    Dialog(
        onDismissRequest = {}, // Empty lambda prevents dismissal on outside tap or back press
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Allow full screen layout
            dismissOnBackPress = false, // Disable dismiss via Android physical/gesture back button
            dismissOnClickOutside = false // Disable dismiss via tapping on background
        )
    ) {
        BackHandler(enabled = true) {
            // Completely intercept back button and gesture to lock the ad for 15 seconds
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp), // Max width to ensure nice proportion on tablets and foldables
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Info Bar (Semi-transparent small bar)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = countdownSeconds / AD_DURATION_SECONDS.toFloat(),
                            modifier = Modifier.size(18.dp).testTag("ad_progress_indicator"),
                            color = AccentGreen,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "این تبلیغ تا $countdownSeconds ثانیه دیگر بسته میشود",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics {
                                contentDescription = "این تبلیغ تا $countdownSeconds ثانیه دیگر بسته میشود"
                            }
                        )
                    }
                }

                // Javaneyar Ad Poster Image
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAdClicked() }
                        .testTag("ad_image_card"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "تبلیغ پلتفرم جوانه",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Bottom Brand Text
                Text(
                    text = "javaneyar.ir",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AdFeatureItem(emoji: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

