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
import com.example.notification.NotificationHelper
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
    intent?.let {
      if (it.getBooleanExtra("create_event", false)) {
        val title = it.getStringExtra("create_title")
        val category = it.getStringExtra("create_category")
        val minutes = it.getIntExtra("create_minutes", 60)
        calendarViewModel.openCreateEventDialog(
          title = title,
          category = category,
          durationMinutes = minutes
        )
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
      } else if (it.hasExtra("view_mode")) {
        when (it.getStringExtra("view_mode")) {
          "agenda" -> calendarViewModel.setViewMode(com.example.ui.viewmodel.CalendarViewMode.SCHEDULE)
          "month" -> calendarViewModel.setViewMode(com.example.ui.viewmodel.CalendarViewMode.MONTH)
          else -> {}
        }
      }
    }
  }
}
