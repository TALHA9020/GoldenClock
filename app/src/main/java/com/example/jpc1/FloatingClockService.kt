package com.example.jpc1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.text.SimpleDateFormat
import java.util.*

class FloatingClockService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        startForegroundService()
        showFloatingClock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // اگر سروس پہلے سے چل رہی ہے اور یوزر دوبارہ "Update" بٹن دباتا ہے، تو UI ریفریش ہو جائے گی
        showFloatingClock()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "floating_clock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Floating Clock Service", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Golden Clock Running")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)
    }

    private fun showFloatingClock() {
        // اگر پہلے سے کلاک موجود ہے تو اسے ہٹائیں تاکہ نئی سیٹنگز کے ساتھ دوبارہ لوڈ ہو سکے
        floatingView?.let { 
            if (it.isAttachedToWindow) windowManager.removeView(it) 
        }

        // سیٹنگز پڑھنا
        val prefs = getSharedPreferences("clock_prefs", Context.MODE_PRIVATE)
        val sizeScale = prefs.getFloat("size_scale", 1f)
        val currentOpacity = prefs.getFloat("opacity", 1f)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        floatingView = ComposeView(this).apply {
            setContent {
                var currentTime by remember { mutableStateOf(getCurrentTime()) }

                LaunchedEffect(Unit) {
                    while(true) {
                        currentTime = getCurrentTime()
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Box(
                    modifier = Modifier
                        .scale(sizeScale) // سائز اپلائی کرنا
                        .alpha(currentOpacity) // شفافیت اپلائی کرنا
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFB8860B))
                            ),
                            shape = RoundedCornerShape(50.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = currentTime,
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ڈریگنگ اور ریمو لاجک
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // ریمو لاجک: اگر اسکرین کے بالکل ٹاپ پر لے جائیں
                        if (params.y < 50) {
                            stopSelf()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        floatingView?.let { 
            if (it.isAttachedToWindow) windowManager.removeView(it) 
        }
    }
}
