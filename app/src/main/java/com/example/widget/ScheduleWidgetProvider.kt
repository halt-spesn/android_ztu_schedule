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
            Intent.ACTION_CONFIGURATION_CHANGED,
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
            applyWidgetStyling(context, views, widgetStyle, widgetOpacity, repo.isDynamicColorEnabled(), repo.isOledModeEnabled())

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
        opacity: Int,
        dynamicColorEnabled: Boolean,
        oledModeEnabled: Boolean
    ) {
        val isDarkTheme = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isMonetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val isMonetActive = dynamicColorEnabled && isMonetSupported
        val isOledActive = isDarkTheme && oledModeEnabled
        val isGlass = style == ScheduleRepository.WIDGET_STYLE_GLASS

        val bgRes = when {
            isGlass -> {
                if (opacity <= 75) R.drawable.ic_widget_bg_translucent_70 else R.drawable.ic_widget_bg_glass
            }
            isOledActive -> R.drawable.ic_widget_bg_dark
            isMonetActive -> R.drawable.ic_widget_bg_monet
            isDarkTheme -> R.drawable.ic_widget_bg_dark
            else -> R.drawable.ic_widget_bg_light
        }

        val cardBgRes = when {
            isGlass -> R.drawable.ic_widget_card_bg_glass
            isOledActive -> R.drawable.ic_widget_card_bg_dark
            isMonetActive -> R.drawable.ic_widget_card_bg_monet
            isDarkTheme -> R.drawable.ic_widget_card_bg_dark
            else -> R.drawable.ic_widget_card_bg_light
        }

        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        views.setInt(R.id.widget_pair_1, "setBackgroundResource", cardBgRes)
        views.setInt(R.id.widget_pair_2, "setBackgroundResource", cardBgRes)
        views.setInt(R.id.widget_pair_3, "setBackgroundResource", cardBgRes)

        when {
            !isMonetActive -> {
                // When Monet dynamic color is disabled, enforce static palette (matches in-app preview)
                if (isDarkTheme) {
                    applyFixedWidgetColors(
                        views = views,
                        titleColor = Color.parseColor("#FFF3EF"),
                        secondaryColor = Color.parseColor("#D6C2BC"),
                        footerColor = Color.parseColor("#9C8B85"),
                        dividerColor = Color.parseColor("#1AFFFFFF"),
                        accentColor = if (isOledActive) Color.parseColor("#FF8A65") else Color.parseColor("#D0BCFF"),
                        chipBg = if (isOledActive) Color.parseColor("#FF8A65") else Color.parseColor("#6750A4"),
                        chipTextColor = if (isOledActive) Color.parseColor("#1C1210") else Color.WHITE
                    )
                } else {
                    applyFixedWidgetColors(
                        views = views,
                        titleColor = Color.parseColor("#1D1B20"),
                        secondaryColor = Color.parseColor("#49454F"),
                        footerColor = Color.parseColor("#938F99"),
                        dividerColor = Color.parseColor("#1F79747E"),
                        accentColor = Color.parseColor("#6750A4"),
                        chipBg = Color.parseColor("#6750A4"),
                        chipTextColor = Color.WHITE
                    )
                }
            }
            else -> {
                // Adaptive Monet mode with DayNight theme enabled.
                // XML layout references (@color/widget_text_title, @color/widget_bg, etc.)
                // dynamically adapt to system Light/Dark mode changes via DayNight theme.
                views.setInt(R.id.widget_btn_refresh, "setColorFilter", 0)
                views.setInt(R.id.widget_app_icon, "setColorFilter", 0)
            }
        }
    }

    private fun applyFixedWidgetColors(
        views: RemoteViews,
        titleColor: Int,
        secondaryColor: Int,
        footerColor: Int,
        dividerColor: Int,
        accentColor: Int,
        chipBg: Int,
        chipTextColor: Int
    ) {
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextColor(R.id.widget_day_info, secondaryColor)
        views.setTextColor(R.id.widget_empty_title, titleColor)
        views.setTextColor(R.id.widget_empty_subtitle, secondaryColor)
        views.setTextColor(R.id.widget_footer, footerColor)
        views.setInt(R.id.widget_divider, "setBackgroundColor", dividerColor)

        views.setTextColor(R.id.widget_pair_1_subject, titleColor)
        views.setTextColor(R.id.widget_pair_1_meta, secondaryColor)
        views.setTextColor(R.id.widget_pair_2_subject, titleColor)
        views.setTextColor(R.id.widget_pair_2_meta, secondaryColor)
        views.setTextColor(R.id.widget_pair_3_subject, titleColor)
        views.setTextColor(R.id.widget_pair_3_meta, secondaryColor)

        views.setInt(R.id.widget_btn_refresh, "setColorFilter", accentColor)
        views.setInt(R.id.widget_app_icon, "setColorFilter", accentColor)
        views.setTextColor(R.id.widget_more_pairs, accentColor)

        listOf(R.id.widget_pair_1_num, R.id.widget_pair_2_num, R.id.widget_pair_3_num).forEach { numId ->
            views.setInt(numId, "setBackgroundColor", chipBg)
            views.setTextColor(numId, chipTextColor)
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
