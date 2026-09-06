package com.example.widget

import android.app.PendingIntent
import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
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
        private const val ACTION_WIDGET_TICK = "com.example.ACTION_WIDGET_TICK"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleTick(context)
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
            ACTION_WIDGET_TICK,
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        if (AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, javaClass)).isEmpty()) {
            cancelTick(context)
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun scheduleTick(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_WIDGET_TICK)
        val pending = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 60_000L, 60_000L, pending)
    }

    private fun cancelTick(context: Context) {
        val intent = Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_WIDGET_TICK)
        val pending = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
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
            val widgetStyle = repo.getWidgetStyle()
            val widgetOpacity = repo.getWidgetOpacity()

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
            views.setImageViewResource(R.id.widget_app_icon, R.drawable.ic_widget_header_icon)
            views.setImageViewResource(R.id.widget_btn_refresh, R.drawable.ic_refresh)

            // Dynamic Styling (Backgrounds, Card outlines, Accent tints)
            applyWidgetStyling(context, views, widgetStyle, widgetOpacity)

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
            val orderedPairs = pairs.sortedWith(compareBy<SchedulePair> {
                when (it.calculateStatus(true)) {
                    PairStatus.CURRENT -> 0
                    PairStatus.UPCOMING -> 1
                    PairStatus.PAST -> 2
                }
            }.thenBy { it.pairNumber })

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

                val isWeekend = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                views.setTextViewText(R.id.widget_empty_title, "Сьогодні занять немає 🎉")
                views.setTextViewText(
                    R.id.widget_empty_subtitle,
                    if (isWeekend) "Гарних вихідних та продуктивного відпочинку!" else "Гарного дня та продуктивного відпочинку!"
                )
            } else {
                views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                // Pair 1
                val p1 = orderedPairs.getOrNull(0)
                bindPairRow(views, R.id.widget_pair_1, R.id.widget_pair_1_num, R.id.widget_pair_1_subject, R.id.widget_pair_1_meta, p1)

                // Pair 2
                val p2 = orderedPairs.getOrNull(1)
                bindPairRow(views, R.id.widget_pair_2, R.id.widget_pair_2_num, R.id.widget_pair_2_subject, R.id.widget_pair_2_meta, p2)

                // Pair 3
                val p3 = orderedPairs.getOrNull(2)
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

    private fun applyWidgetStyling(
        context: Context,
        views: RemoteViews,
        style: String,
        opacity: Int
    ) {
        val isDarkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isMonetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val bgRes = when (style) {
            ScheduleRepository.WIDGET_STYLE_GLASS -> {
                if (opacity <= 75) R.drawable.ic_widget_bg_translucent_70 else R.drawable.ic_widget_bg_glass
            }
            ScheduleRepository.WIDGET_STYLE_DARK -> R.drawable.ic_widget_bg_dark
            ScheduleRepository.WIDGET_STYLE_LIGHT -> R.drawable.ic_widget_bg_light
            ScheduleRepository.WIDGET_STYLE_MONET -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_bg_monet_dark else R.drawable.ic_widget_bg_monet_light
                } else {
                    R.drawable.ic_widget_bg_monet
                }
            }
            ScheduleRepository.WIDGET_STYLE_SYSTEM -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_bg_monet_dark else R.drawable.ic_widget_bg_monet_light
                } else if (isDarkTheme) {
                    if (opacity <= 75) R.drawable.ic_widget_bg_translucent_70 else R.drawable.ic_widget_bg_glass
                } else {
                    R.drawable.ic_widget_bg
                }
            }
            else -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_bg_monet_dark else R.drawable.ic_widget_bg_monet_light
                } else {
                    R.drawable.ic_widget_bg_monet
                }
            }
        }

        val cardBgRes = when (style) {
            ScheduleRepository.WIDGET_STYLE_GLASS -> R.drawable.ic_widget_card_bg_glass
            ScheduleRepository.WIDGET_STYLE_DARK -> R.drawable.ic_widget_card_bg_dark
            ScheduleRepository.WIDGET_STYLE_LIGHT -> R.drawable.ic_widget_card_bg_light
            ScheduleRepository.WIDGET_STYLE_MONET -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_card_bg_monet_dark else R.drawable.ic_widget_card_bg_monet_light
                } else {
                    R.drawable.ic_widget_card_bg_monet
                }
            }
            ScheduleRepository.WIDGET_STYLE_SYSTEM -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_card_bg_monet_dark else R.drawable.ic_widget_card_bg_monet_light
                } else if (isDarkTheme) {
                    R.drawable.ic_widget_card_bg_glass
                } else {
                    R.drawable.ic_widget_card_bg
                }
            }
            else -> {
                if (isMonetSupported) {
                    if (isDarkTheme) R.drawable.ic_widget_card_bg_monet_dark else R.drawable.ic_widget_card_bg_monet_light
                } else {
                    R.drawable.ic_widget_card_bg_monet
                }
            }
        }

        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        views.setInt(R.id.widget_pair_1, "setBackgroundResource", cardBgRes)
        views.setInt(R.id.widget_pair_2, "setBackgroundResource", cardBgRes)
        views.setInt(R.id.widget_pair_3, "setBackgroundResource", cardBgRes)

        // Accent and text color calculation based on theme and Monet
        val isMonetActive = (style == ScheduleRepository.WIDGET_STYLE_MONET || 
                             style == ScheduleRepository.WIDGET_STYLE_SYSTEM || 
                             style == ScheduleRepository.WIDGET_STYLE_GLASS) && isMonetSupported

        val accentColor = if (isMonetActive) {
            try {
                if (isDarkTheme) {
                    context.getColor(android.R.color.system_accent1_200)
                } else {
                    context.getColor(android.R.color.system_accent1_600)
                }
            } catch (e: Exception) {
                Color.parseColor("#FF8A65")
            }
        } else if (style == ScheduleRepository.WIDGET_STYLE_LIGHT) {
            Color.parseColor("#6750A4")
        } else {
            // Warm amber accent for dark/glass theme
            Color.parseColor("#FF8A65")
        }

        // Explicitly restore static palette colors when Monet is disabled.
        if (!isMonetActive) {
            views.setTextColor(R.id.widget_title, if (style == ScheduleRepository.WIDGET_STYLE_LIGHT) Color.parseColor("#1D1B20") else Color.WHITE)
            views.setTextColor(R.id.widget_day_info, if (style == ScheduleRepository.WIDGET_STYLE_LIGHT) Color.parseColor("#49454F") else Color.LTGRAY)
        }

        // Apply dynamic accent color to header icon, refresh button, and more pairs text
        views.setInt(R.id.widget_btn_refresh, "setColorFilter", accentColor)
        views.setInt(R.id.widget_app_icon, "setColorFilter", accentColor)
        views.setTextColor(R.id.widget_more_pairs, accentColor)

        // Apply text colors based on style and Monet
        if (isMonetActive) {
            try {
                val titleColor = if (isDarkTheme) {
                    context.getColor(android.R.color.system_neutral1_100)
                } else {
                    context.getColor(android.R.color.system_neutral1_900)
                }
                val secondaryColor = if (isDarkTheme) {
                    context.getColor(android.R.color.system_neutral2_200)
                } else {
                    context.getColor(android.R.color.system_neutral2_700)
                }
                val footerColor = if (isDarkTheme) {
                    context.getColor(android.R.color.system_neutral2_400)
                } else {
                    context.getColor(android.R.color.system_neutral2_500)
                }

                val dividerColor = if (isDarkTheme) {
                    context.getColor(android.R.color.system_neutral2_700)
                } else {
                    context.getColor(android.R.color.system_neutral2_200)
                }

                views.setInt(R.id.widget_divider, "setBackgroundColor", dividerColor)
                views.setTextColor(R.id.widget_title, titleColor)
                views.setTextColor(R.id.widget_day_info, secondaryColor)
                views.setTextColor(R.id.widget_empty_title, titleColor)
                views.setTextColor(R.id.widget_empty_subtitle, secondaryColor)
                views.setTextColor(R.id.widget_footer, footerColor)

                views.setTextColor(R.id.widget_pair_1_subject, titleColor)
                views.setTextColor(R.id.widget_pair_1_meta, secondaryColor)
                views.setTextColor(R.id.widget_pair_2_subject, titleColor)
                views.setTextColor(R.id.widget_pair_2_meta, secondaryColor)
                views.setTextColor(R.id.widget_pair_3_subject, titleColor)
                views.setTextColor(R.id.widget_pair_3_meta, secondaryColor)

                // Pair number badge chip colors in Monet mode
                val chipBg = if (isDarkTheme) {
                    context.getColor(android.R.color.system_accent1_200)
                } else {
                    context.getColor(android.R.color.system_accent1_600)
                }
                val chipTextColor = if (isDarkTheme) {
                    context.getColor(android.R.color.system_accent1_900)
                } else {
                    context.getColor(android.R.color.system_accent1_0)
                }
                listOf(R.id.widget_pair_1_num, R.id.widget_pair_2_num, R.id.widget_pair_3_num).forEach { numId ->
                    views.setInt(numId, "setBackgroundColor", chipBg)
                    views.setTextColor(numId, chipTextColor)
                }
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "Could not apply Monet text colors", e)
            }
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
