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

// Central Constants for Javaneyar Ad Timing
const val AD_INTERVAL_MILLIS = 7 * 60 * 1000L // 420000 ms
const val AD_INTERVAL_SECONDS = 7 * 60 // 420
const val AD_DURATION_SECONDS = 15

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
    
    var showAdDialog by remember { mutableStateOf(false) }
    var isTestAd by remember { mutableStateOf(false) }
    var adCountdownSeconds by remember { mutableStateOf(AD_DURATION_SECONDS) }

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
                            showAdDialog = true
                            adCountdownSeconds = AD_DURATION_SECONDS
                        }
                    }
                }
            }
        }
    }

    // Ad countdown timer based on real system time (cannot be paused or bypassed by backgrounding)
    LaunchedEffect(showAdDialog) {
        if (showAdDialog) {
            val startTime = System.currentTimeMillis()
            if (!isTestAd) {
                // Save the last ad show time to SharedPreferences immediately when it shows
                prefs.edit().putLong("last_ad_show_time", startTime).apply()
                lastAdShowTime = startTime
                activeSeconds = 0 // Reset the active seconds timer for the next interval
            }
            
            while (true) {
                val elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                val remaining = (AD_DURATION_SECONDS - elapsedSeconds).coerceAtLeast(0)
                adCountdownSeconds = remaining
                if (remaining <= 0) {
                    break
                }
                delay(250L) // Poll frequently to ensure absolute precision
            }
            // Auto dismiss when countdown reaches 0
            showAdDialog = false
            isTestAd = false
        }
    }

    val openJavaneyarUrl = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://javaneyar.ir/"))
        try {
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
        if (showAdDialog) {
            JavaneyarAdDialog(
                onAdClicked = onAdClicked,
                countdownSeconds = adCountdownSeconds
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
    countdownSeconds: Int
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
                .background(Color(0xEE0A0E17)) // Semi-transparent dark Navy-Black background
                .safeDrawingPadding() // Ensure layout handles notches, status bars, and navigation bars safely
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp) // Ensure nice proportions on tablets and foldables
                    .verticalScroll(rememberScrollState()), // Clean scroll fallback for ultra-small screens
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top Info Bar (Countdown + Progress)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Countdown and Progress
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = countdownSeconds / AD_DURATION_SECONDS.toFloat(),
                            modifier = Modifier.size(24.dp).testTag("ad_progress_indicator"),
                            color = AccentGreen,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp
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

                // Native Styled Ad Content instead of Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onAdClicked() }
                        .testTag("ad_card_container"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Badge / Category
                        Box(
                            modifier = Modifier
                                .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🌱 پلتفرم نوآوری جوانه",
                                color = AccentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Title
                        Text(
                            text = "ایده داری؟ ایده‌ات رو رشد بده",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 30.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            text = "ایده‌ات را ثبت کن، ارزیابی بگیر و برای ساختنش با متخصصان و سازمان‌ها همکاری کن.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Feature Rows
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AdFeatureItem(emoji = "💡", title = "ایده‌پردازی و ارزیابی ایده")
                            AdFeatureItem(emoji = "🤝", title = "فرصت‌های همکاری و شبکه‌سازی")
                            AdFeatureItem(emoji = "🏢", title = "ارتباط با استارتاپ‌ها و سازمان‌ها")
                            AdFeatureItem(emoji = "🚀", title = "رشد ایده از ابتدا تا محصول")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // CTA Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(AccentGreen, Color(0xFF2E7D32))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onAdClicked() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "مشاهده وب‌سایت جوانه",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "‹",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "javaneyar.ir",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
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

