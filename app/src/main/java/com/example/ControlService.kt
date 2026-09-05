package com.example

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

class ControlService : AccessibilityService() {
    companion object {
        var instance: ControlService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        private val _lastLog = MutableStateFlow("Service initialized")
        val lastLog: StateFlow<String> = _lastLog
    }

    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var confirmation: Button? = null
    private var pending: (() -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        _lastLog.value = "Accessibility Service connected"
        if (VoiceService.instance != null) {
            showPanel()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observation; continuous screen history is not stored for privacy
    }

    override fun onInterrupt() {
        VoiceService.instance?.cancel()
    }

    override fun onDestroy() {
        VoiceService.instance?.cancel()
        hidePanel()
        _isConnected.value = false
        _lastLog.value = "Accessibility Service stopped"
        if (instance === this) instance = null
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showPanel() {
        if (panel != null) return

        val density = resources.displayMetrics.density

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (14 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)

            // Modern Dark Glassmorphic container with cyan accent border
            val shape = GradientDrawable().apply {
                setColor(Color.argb(235, 15, 23, 42)) // #0F172A
                setStroke((1.5f * density).toInt(), Color.argb(180, 0, 229, 255)) // #00E5FF
                cornerRadius = 16 * density
            }
            background = shape
        }

        // Header Title with glowing status badge
        val header = TextView(this).apply {
            text = "⚡ JARVIS CONTROLLER"
            textSize = 12f
            setTextColor(Color.rgb(0, 229, 255)) // #00E5FF
            setPadding(0, 0, 0, (6 * density).toInt())
            paint.isFakeBoldText = true
        }
        box.addView(header)

        // Status Text View
        status = TextView(this).apply {
            text = "Ready. Tap Speak to give command."
            textSize = 13f
            setTextColor(Color.WHITE)
            maxLines = 6
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        box.addView(status)

        // Action Buttons row 1 (Speak + Confirm)
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (6 * density).toInt())
        }

        val speakBtn = Button(this).apply {
            text = "🎤 Speak"
            textSize = 12f
            setTextColor(Color.rgb(10, 17, 40))
            val btnShape = GradientDrawable().apply {
                setColor(Color.rgb(0, 229, 255))
                cornerRadius = 8 * density
            }
            background = btnShape
            setOnClickListener { VoiceService.instance?.listen() }
        }
        row1.addView(speakBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (4 * density).toInt()
        })

        confirmation = Button(this).apply {
            text = "✔ Confirm"
            textSize = 12f
            setTextColor(Color.WHITE)
            val btnShape = GradientDrawable().apply {
                setColor(Color.rgb(16, 185, 129)) // Green
                cornerRadius = 8 * density
            }
            background = btnShape
            isEnabled = false
            alpha = 0.5f
            setOnClickListener {
                val action = pending
                clearConfirmation()
                action?.invoke()
            }
        }
        row1.addView(confirmation, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (4 * density).toInt()
        })
        box.addView(row1)

        // Action Buttons row 2 (Cancel + Stop)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            textSize = 11f
            setTextColor(Color.rgb(203, 213, 225))
            val btnShape = GradientDrawable().apply {
                setColor(Color.rgb(51, 65, 85))
                cornerRadius = 8 * density
            }
            background = btnShape
            setOnClickListener { VoiceService.instance?.cancel() }
        }
        row2.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (4 * density).toInt()
        })

        val stopBtn = Button(this).apply {
            text = "Stop"
            textSize = 11f
            setTextColor(Color.rgb(248, 113, 113))
            val btnShape = GradientDrawable().apply {
                setColor(Color.rgb(51, 65, 85))
                cornerRadius = 8 * density
            }
            background = btnShape
            setOnClickListener {
                stopService(Intent(this@ControlService, VoiceService::class.java))
            }
        }
        row2.addView(stopBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (4 * density).toInt()
        })
        box.addView(row2)

        val wm = getSystemService(WindowManager::class.java)

        val params = WindowManager.LayoutParams(
            (290 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (80 * density).toInt()
        }

        // Draggable floating panel touch handler
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        wm.updateViewLayout(box, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(box, params)
            panel = box
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to display floating controller panel.", Toast.LENGTH_SHORT).show()
        }
    }

    fun hidePanel() {
        clearConfirmation()
        panel?.let {
            runCatching {
                getSystemService(WindowManager::class.java).removeView(it)
            }
        }
        panel = null
        status = null
        confirmation = null
    }

    fun showStatus(message: String) {
        status?.text = message
        _lastLog.value = message
    }

    fun requestConfirmation(message: String, action: () -> Unit) {
        showStatus(message)
        pending = action
        confirmation?.isEnabled = true
        confirmation?.alpha = 1.0f
    }

    fun clearConfirmation() {
        pending = null
        confirmation?.isEnabled = false
        confirmation?.alpha = 0.5f
    }

    private fun screenRoot(): AccessibilityNodeInfo? {
        return windows.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.isActive &&
                it.root?.packageName?.toString() != packageName
        }?.root
    }

    fun screenPackage(): String? =
        screenRoot()?.packageName?.toString()

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && found.size < 600) {
            val node = queue.removeFirst()
            found.add(node)
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return found
    }

    private fun clickable(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = start
        repeat(6) {
            val current = node ?: return null
            if (current.isClickable && current.isEnabled) return current
            node = current.parent
        }
        return null
    }

    private fun protectedScreen(pkg: String): Boolean {
        return pkg.contains("permissioncontroller", true) ||
            pkg.contains("packageinstaller", true) ||
            pkg == "com.android.systemui" ||
            pkg == "com.android.settings"
    }

    @Suppress("DEPRECATION")
    private fun openApp(name: String): Outcome {
        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(query, 0)
            .distinctBy { it.activityInfo.packageName }

        val exact = apps.filter {
            it.loadLabel(packageManager).toString().equals(name, true)
        }

        val matches = if (exact.isNotEmpty()) exact else apps.filter {
            it.loadLabel(packageManager).toString().contains(name, true)
        }

        if (matches.size != 1) {
            return Outcome(
                false,
                if (matches.isEmpty()) "No matching launchable app found for '$name'."
                else "Multiple apps match '$name'. Please speak the full app name."
            )
        }

        val launch = packageManager.getLaunchIntentForPackage(
            matches.single().activityInfo.packageName
        ) ?: return Outcome(false, "No launch intent available for ${matches.single().loadLabel(packageManager)}.")

        return try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            Outcome(true, "Launched ${matches.single().loadLabel(packageManager)}.")
        } catch (e: Exception) {
            Outcome(false, "App launch failed or was blocked by the system.")
        }
    }

    fun execute(step: Step): Outcome {
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            return Outcome(false, "Phone is locked. Please unlock manually first.")
        }

        if (step.action == "open") return openApp(step.argument)

        if (step.action == "back" || step.action == "home") {
            val ok = performGlobalAction(
                if (step.action == "home") GLOBAL_ACTION_HOME
                else GLOBAL_ACTION_BACK
            )
            return Outcome(ok, if (ok) "Navigated ${step.action}." else "Navigation action failed.")
        }

        val root = screenRoot()
            ?: return Outcome(false, "No accessible application window found.")

        val pkgName = root.packageName?.toString().orEmpty()
        if (protectedScreen(pkgName)) {
            return Outcome(false, "Action blocked: protected system screen ($pkgName).")
        }

        val visible = nodes(root).filter { it.isVisibleToUser }

        return when (step.action) {
            "tap" -> {
                val matches = visible.filter {
                    !it.isPassword && (
                        it.text?.toString()?.equals(step.argument, ignoreCase = true) == true ||
                        it.contentDescription?.toString()?.equals(step.argument, ignoreCase = true) == true
                    )
                }.mapNotNull { clickable(it) }.distinct()

                if (matches.size != 1) {
                    Outcome(false, if (matches.isEmpty()) "No clickable item matching '${step.argument}'." else "Multiple items match '${step.argument}'.")
                } else {
                    val ok = matches.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Outcome(ok, if (ok) "Tapped '${step.argument}'." else "Tap action was rejected.")
                }
            }

            "type" -> {
                val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (field == null || !field.isEditable || !field.isEnabled || field.isPassword) {
                    Outcome(false, "Focus an editable, non-password input field first.")
                } else {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            step.argument
                        )
                    }
                    val ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    Outcome(ok, if (ok) "Text entered: '${step.argument}'." else "Text input rejected.")
                }
            }

            "down", "up" -> {
                val regions = visible.filter { it.isScrollable && it.isEnabled }
                if (regions.isEmpty()) {
                    Outcome(false, "No scrollable region found on screen.")
                } else {
                    val target = regions.first()
                    val ok = target.performAction(
                        if (step.action == "down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    )
                    Outcome(ok, if (ok) "Scrolled ${step.action}." else "Reached scroll boundary.")
                }
            }

            "read" -> {
                val text = visible
                    .filter { !it.isPassword && !it.isEditable }
                    .mapNotNull {
                        it.text?.toString()?.takeIf(String::isNotBlank)
                            ?: it.contentDescription?.toString()?.takeIf(String::isNotBlank)
                    }
                    .distinct()
                    .joinToString(". ")
                    .take(1800)

                Outcome(text.isNotBlank(), text.ifBlank { "No readable screen text found." })
            }

            else -> Outcome(false, "Unsupported action: ${step.action}")
        }
    }
}
