package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.example.notification.NotificationHelper
import com.example.notification.ReminderScheduler
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CalendarViewModel
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

  private val calendarViewModel: CalendarViewModel by viewModels()

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.createChannel(this)
    requestNotificationPermissionIfNeeded()
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainScreen(viewModel = calendarViewModel)
      }
    }
    handleWidgetIntent()
    lifecycleScope.launch { WidgetUpdater.update(this@MainActivity) }
    // Self-healing reminders: after a device reboot, backup restore, or WorkManager
    // data loss, re-arm every upcoming event reminder from the local database.
    lifecycleScope.launch {
      ReminderScheduler.rescheduleAll(applicationContext)
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleWidgetIntent()
  }

  private fun handleWidgetIntent() {
    Log.d("MCAL", "intent=${intent} extras=${intent?.extras}")
    intent?.let {
      if (it.getBooleanExtra("create_event", false)) {
        calendarViewModel.openCreateEventDialog()
      } else if (it.hasExtra("event_id")) {
        val id = it.getLongExtra("event_id", -1L)
        if (id > 0L) calendarViewModel.openEventById(id)
      } else if (it.hasExtra("jump_date")) {
        val millis = it.getLongExtra("jump_date", 0L)
        if (millis > 0L) {
          Calendar.getInstance().apply { timeInMillis = millis }.let { cal ->
            calendarViewModel.setSelectedDate(cal)
            calendarViewModel.setViewMode(com.example.ui.viewmodel.CalendarViewMode.DAY)
          }
        }
      }
    }
  }
}
