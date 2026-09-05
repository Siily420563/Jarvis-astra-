package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepBg
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisWarning
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                JarvisDashboardScreen(
                    onRequestPermissions = { permissions ->
                        requestPermissions(permissions, 101)
                    }
                )
            }
        }
    }
}

@Composable
fun JarvisDashboardScreen(
    onRequestPermissions: (Array<String>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isAccessibilityActive by remember {
        mutableStateOf(ControlService.instance != null)
    }

    val isVoiceServiceRunning by VoiceService.isServiceRunning.collectAsState()
    val isListening by VoiceService.isListening.collectAsState()
    val lastSpeech by VoiceService.lastSpeech.collectAsState()
    val serviceLog by ControlService.lastLog.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                isAccessibilityActive = (ControlService.instance != null)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasMicPermission = result[Manifest.permission.RECORD_AUDIO] == true
    }

    var testCommandText by remember { mutableStateOf("") }
    var parsedStepsPreview by remember { mutableStateOf<List<Step>?>(null) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBg),
        containerColor = JarvisDeepBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                JarvisHeader(isReady = hasMicPermission && isAccessibilityActive && isVoiceServiceRunning)
            }

            item {
                SetupStatusCard(
                    hasMicPermission = hasMicPermission,
                    isAccessibilityActive = isAccessibilityActive,
                    isVoiceServiceRunning = isVoiceServiceRunning,
                    onGrantPermission = {
                        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) {
                            needed.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(needed.toTypedArray())
                    },
                    onOpenAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onStartService = {
                        if (!hasMicPermission) {
                            Toast.makeText(context, "Grant microphone permission first.", Toast.LENGTH_SHORT).show()
                            return@SetupStatusCard
                        }
                        if (ControlService.instance == null) {
                            Toast.makeText(context, "Enable Jarvis in Accessibility Settings first.", Toast.LENGTH_SHORT).show()
                            return@SetupStatusCard
                        }
                        try {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, VoiceService::class.java)
                            )
                            Toast.makeText(context, "Jarvis controller started!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to start controller service: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onStopService = {
                        context.stopService(Intent(context, VoiceService::class.java))
                        Toast.makeText(context, "Jarvis controller stopped.", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                VoiceControlCenterCard(
                    isServiceRunning = isVoiceServiceRunning,
                    isListening = isListening,
                    lastSpeech = lastSpeech,
                    serviceLog = serviceLog,
                    onListenClicked = {
                        if (!isVoiceServiceRunning) {
                            Toast.makeText(context, "Start controller service first.", Toast.LENGTH_SHORT).show()
                        } else {
                            VoiceService.instance?.listen()
                        }
                    },
                    onCancelClicked = {
                        VoiceService.instance?.cancel()
                    }
                )
            }

            item {
                CommandTesterCard(
                    commandText = testCommandText,
                    onCommandChanged = {
                        testCommandText = it
                        parsedStepsPreview = if (it.isNotBlank()) Parser.parse(it) else null
                    },
                    parsedSteps = parsedStepsPreview,
                    onExecute = {
                        if (testCommandText.isBlank()) return@CommandTesterCard
                        if (isVoiceServiceRunning && VoiceService.instance != null) {
                            VoiceService.instance?.processSimulatedCommand(testCommandText)
                        } else {
                            val parsed = Parser.parse(testCommandText)
                            if (parsed != null) {
                                Toast.makeText(context, "Parsed ${parsed.size} step(s): ${parsed.joinToString { it.action }}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Invalid command syntax.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            item {
                CommandCheatSheetCard(
                    selectedCategoryIndex = selectedCategoryIndex,
                    onSelectCategory = { selectedCategoryIndex = it },
                    onSelectExample = { sample ->
                        testCommandText = sample
                        parsedStepsPreview = Parser.parse(sample)
                    }
                )
            }

            item {
                SafetyInfoCard()
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun JarvisHeader(isReady: Boolean) {
    val transition = rememberInfiniteTransition(label = "arc_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "JARVIS LITE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = JarvisCyan,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulseScale)
                        .background(if (isReady) JarvisSuccess else JarvisWarning, CircleShape)
                )
            }
            Text(
                text = "Autonomous Voice Phone Controller",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .border(2.dp, JarvisCyan.copy(alpha = 0.6f), CircleShape)
                .background(JarvisSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Jarvis Core",
                tint = JarvisCyan,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SetupStatusCard(
    hasMicPermission: Boolean,
    isAccessibilityActive: Boolean,
    isVoiceServiceRunning: Boolean,
    onGrantPermission: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(JarvisCardBorder, JarvisCyan.copy(alpha = 0.2f))))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "SYSTEM INITIALIZATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Step 1
            SetupStepItem(
                stepNumber = "1",
                title = "Microphone Permission",
                description = "Required to capture spoken voice commands",
                isComplete = hasMicPermission,
                actionLabel = if (hasMicPermission) "Granted" else "Grant Access",
                onAction = onGrantPermission
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = JarvisCardBorder.copy(alpha = 0.5f)
            )

            // Step 2
            SetupStepItem(
                stepNumber = "2",
                title = "Accessibility Service",
                description = "Allows Jarvis to inspect UI, tap, scroll, and type",
                isComplete = isAccessibilityActive,
                actionLabel = if (isAccessibilityActive) "Active" else "Enable in Settings",
                onAction = onOpenAccessibility
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = JarvisCardBorder.copy(alpha = 0.5f)
            )

            // Step 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Step 3: Controller Service",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (isVoiceServiceRunning) "Foreground assistant & floating overlay running" else "Service offline",
                        fontSize = 12.sp,
                        color = if (isVoiceServiceRunning) JarvisSuccess else TextMuted
                    )
                }

                if (!isVoiceServiceRunning) {
                    Button(
                        onClick = onStartService,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color(0xFF0A1128)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("start_controller_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopService,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisError),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(JarvisError, JarvisError))),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("stop_controller_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
fun SetupStepItem(
    stepNumber: String,
    title: String,
    description: String,
    isComplete: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Step $stepNumber: $title",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = JarvisSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        OutlinedButton(
            onClick = onAction,
            enabled = !isComplete,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isComplete) JarvisSuccess else JarvisCyan,
                disabledContentColor = JarvisSuccess
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("step_${stepNumber}_button")
        ) {
            Text(text = actionLabel, fontSize = 12.sp)
        }
    }
}

@Composable
fun VoiceControlCenterCard(
    isServiceRunning: Boolean,
    isListening: Boolean,
    lastSpeech: String,
    serviceLog: String,
    onListenClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_control_center"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(JarvisCardBorder, JarvisBlue.copy(alpha = 0.3f))))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOICE RECOGNITION HUB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isListening) JarvisCyan.copy(alpha = 0.2f) else JarvisSurfaceVariant
                ) {
                    Text(
                        text = if (isListening) "LISTENING..." else if (isServiceRunning) "STANDBY" else "OFFLINE",
                        color = if (isListening) JarvisCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Big push-to-talk mic button
            val transition = rememberInfiniteTransition(label = "pulse_mic")
            val micGlow by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mic_glow"
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(if (isListening) micGlow else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isListening) listOf(JarvisCyan, JarvisBlue)
                            else listOf(JarvisSurfaceVariant, JarvisCardBorder)
                        )
                    )
                    .clickable { onListenClicked() }
                    .border(3.dp, if (isListening) JarvisCyan else JarvisCardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Push to Talk",
                    tint = if (isListening) Color(0xFF090D16) else JarvisCyan,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isListening) "Listening... Speak your command" else "Tap Mic to Speak",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isListening) JarvisCyan else TextPrimary
            )

            AnimatedVisibility(visible = isListening) {
                OutlinedButton(
                    onClick = onCancelClicked,
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Last Spoken & Service log output
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(JarvisDeepBg)
                    .border(1.dp, JarvisCardBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "LAST COMMAND",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (lastSpeech.isNotBlank()) "\"$lastSpeech\"" else "No command heard yet",
                        fontSize = 13.sp,
                        color = if (lastSpeech.isNotBlank()) TextPrimary else TextMuted,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    HorizontalDivider(color = JarvisCardBorder.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "STATUS / ACTION LOG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = serviceLog,
                        fontSize = 12.sp,
                        color = JarvisCyan.copy(alpha = 0.9f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun CommandTesterCard(
    commandText: String,
    onCommandChanged: (String) -> Unit,
    parsedSteps: List<Step>?,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("command_tester_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(JarvisCardBorder, JarvisCyan.copy(alpha = 0.15f))))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "COMMAND SIMULATOR & TESTER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Test command grammar and execution without speaking",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = commandText,
                onValueChange = onCommandChanged,
                placeholder = { Text("e.g. open YouTube then tap Search", color = TextMuted, fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_command_input"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = JarvisDeepBg,
                    unfocusedContainerColor = JarvisDeepBg
                ),
                singleLine = true
            )

            if (parsedSteps != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Parsed Steps (${parsedSteps.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
                    parsedSteps.forEachIndexed { idx, step ->
                        Text(
                            text = "${idx + 1}. ${Parser.describe(step)}",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onExecute,
                enabled = commandText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color(0xFF090D16)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("execute_simulated_command_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Parse & Run Command", fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class CheatSheetCategory(val title: String, val commands: List<String>)

@Composable
fun CommandCheatSheetCard(
    selectedCategoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    onSelectExample: (String) -> Unit
) {
    val categories = listOf(
        CheatSheetCategory("Launch", listOf("open YouTube", "open Settings", "open Camera", "open Clock")),
        CheatSheetCategory("Interact", listOf("tap Search", "tap Library", "type hello world", "type lofi beats")),
        CheatSheetCategory("Navigate", listOf("scroll down", "scroll up", "go back", "go home")),
        CheatSheetCategory("Read", listOf("read screen", "read")),
        CheatSheetCategory("Pipelines", listOf("open YouTube then tap Search", "open Settings then scroll down"))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(JarvisCardBorder, JarvisCardBorder)))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "COMMAND CHEAT SHEET",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan,
                letterSpacing = 1.sp
            )
            Text(
                text = "Tap any command below to load it into the simulator",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TabRow(
                selectedTabIndex = selectedCategoryIndex,
                containerColor = JarvisDeepBg,
                contentColor = JarvisCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                        color = JarvisCyan
                    )
                }
            ) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { onSelectCategory(index) },
                        text = {
                            Text(
                                cat.title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedCategoryIndex == index) JarvisCyan else TextMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val currentCommands = categories[selectedCategoryIndex].commands
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentCommands.forEach { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(JarvisDeepBg)
                            .border(1.dp, JarvisCardBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectExample(cmd) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "\"$cmd\"",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Try",
                            tint = JarvisCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(JarvisCardBorder, JarvisCardBorder)))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security",
                tint = JarvisCyan,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Safety & Confirmation Protocol",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Every Accessibility action requires your explicit confirmation on the floating HUD.\n• Password fields and sensitive system dialogs (permissions, settings installer) are guarded.\n• Say 'Cancel' or 'Stop' at any time to halt queued actions.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
