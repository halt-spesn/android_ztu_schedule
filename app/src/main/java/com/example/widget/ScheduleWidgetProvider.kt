package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.PairStatus
import com.example.data.model.SchedulePair
import com.example.data.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.example.ACTION_WIDGET_REFRESH"
        const val ACTION_UPDATE_FROM_APP = "com.example.ACTION_UPDATE_FROM_APP"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "onUpdate failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        when (intent.action) {
            ACTION_WIDGET_REFRESH -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val repo = ScheduleRepository(context)
                        val groupId = repo.getSelectedGroupId()
                        repo.refreshSchedule(groupId)
                        for (id in appWidgetIds) {
                            updateAppWidget(context, appWidgetManager, id)
                        }
                    } catch (e: Exception) {
                        Log.e("ScheduleWidget", "Refresh failed: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_UPDATE_FROM_APP,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        for (id in appWidgetIds) {
                            updateAppWidget(context, appWidgetManager, id)
                        }
                    } catch (e: Exception) {
                        Log.e("ScheduleWidget", "Update from app failed: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            val repo = ScheduleRepository(context)
            val groupId = repo.getSelectedGroupId()
            val groupName = repo.getSelectedGroupName()

            // Header title
            views.setTextViewText(R.id.widget_title, "ЖТУ • $groupName")

            // Day info
            val cal = Calendar.getInstance()
            val dayNames = listOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
            val dayOfWeek = dayNames.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 1) { "" }
            val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
            val dateStr = dateFormat.format(cal.time)
            views.setTextViewText(R.id.widget_day_info, "Сьогодні: $dayOfWeek, $dateStr")

            // Icons
            views.setImageViewResource(R.id.widget_app_icon, R.mipmap.ic_launcher)
            views.setImageViewResource(R.id.widget_btn_refresh, R.drawable.ic_refresh)

            // Click on widget opens MainActivity
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_header, appPendingIntent)

            // Click on refresh button
            val refreshIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // Load today's pairs safely
            val pairs = try {
                repo.getTodayPairs(groupId)
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "Could not load today pairs", e)
                emptyList()
            }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val updateTime = timeFormat.format(System.currentTimeMillis())
            views.setTextViewText(R.id.widget_footer, "Оновлено о $updateTime • Натисніть для деталей")

            if (pairs.isEmpty()) {
                // Show empty view
                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                views.setViewVisibility(R.id.widget_pair_1, View.GONE)
                views.setViewVisibility(R.id.widget_pair_2, View.GONE)
                views.setViewVisibility(R.id.widget_pair_3, View.GONE)
                views.setViewVisibility(R.id.widget_more_pairs, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                // Pair 1
                val p1 = pairs.getOrNull(0)
                bindPairRow(views, R.id.widget_pair_1, R.id.widget_pair_1_num, R.id.widget_pair_1_subject, R.id.widget_pair_1_meta, p1)

                // Pair 2
                val p2 = pairs.getOrNull(1)
                bindPairRow(views, R.id.widget_pair_2, R.id.widget_pair_2_num, R.id.widget_pair_2_subject, R.id.widget_pair_2_meta, p2)

                // Pair 3
                val p3 = pairs.getOrNull(2)
                bindPairRow(views, R.id.widget_pair_3, R.id.widget_pair_3_num, R.id.widget_pair_3_subject, R.id.widget_pair_3_meta, p3)

                // More pairs indicator
                if (pairs.size > 3) {
                    views.setViewVisibility(R.id.widget_more_pairs, View.VISIBLE)
                    views.setTextViewText(R.id.widget_more_pairs, "+ ще ${pairs.size - 3} пар (натисніть щоб переглянути)")
                } else {
                    views.setViewVisibility(R.id.widget_more_pairs, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e("ScheduleWidget", "Error updating widget $appWidgetId", e)
        }
    }

    private fun bindPairRow(
        views: RemoteViews,
        rowId: Int,
        numId: Int,
        subjectId: Int,
        metaId: Int,
        pair: SchedulePair?
    ) {
        if (pair == null) {
            views.setViewVisibility(rowId, View.GONE)
            return
        }
        views.setViewVisibility(rowId, View.VISIBLE)
        views.setTextViewText(numId, pair.pairNumber.toString())

        var subjectText = pair.subject
        if (pair.subgroup.isNotEmpty()) {
            subjectText += " (${pair.subgroup})"
        }
        views.setTextViewText(subjectId, subjectText)

        val metaParts = mutableListOf<String>()
        metaParts.add(pair.timeRange)
        if (pair.kind.isNotEmpty()) {
            metaParts.add(pair.kind)
        }
        if (pair.room.isNotEmpty()) {
            metaParts.add("ауд. ${pair.room}")
        }
        views.setTextViewText(metaId, metaParts.joinToString(" • "))
    }
}
