package com.focusgoal.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.data.AppCategory
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.data.InstalledApps
import com.focusgoal.app.data.UsageData
import com.focusgoal.app.data.WeekUsage
import com.focusgoal.app.data.formatDuration
import com.focusgoal.app.ui.rememberResumeTick
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.PillButton
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private data class AppRowData(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val category: AppCategory,
    val ms: Long,
)

private data class UsageSnapshot(
    val week: WeekUsage,
    val categories: Map<String, AppCategory>,
    val labels: Map<String, String>,
    val icons: Map<String, ImageBitmap?>,
)

/** Daily / weekly screen time with a stacked bar chart and per-app list, like Digital Wellbeing. */
@Composable
fun UsageScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tick = rememberResumeTick()
    val hasAccess = remember(tick) { UsageData.hasAccess(context) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Palette.Text)
            }
            Spacer(Modifier.width(6.dp))
            Text("Usage Stats", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        }
        if (hasAccess) UsageContent() else UsageAccessNeeded(context)
    }
}

@Composable
private fun UsageAccessNeeded(context: Context) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp).glass().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("See where your time goes", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow \"Usage access\" so FocusGoal can show how long you use each app. On the next screen, tap FocusGoal and turn it ON. This stays on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PillButton("Allow usage access", Modifier.fillMaxWidth()) { openUsageAccess(context) }
    }
}

internal fun openUsageAccess(context: Context) {
    for (intent in UsageData.settingsIntents(context)) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // try the next one
        } catch (e: SecurityException) {
            // try the next one
        }
    }
}

@Composable
private fun UsageContent() {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var weekly by rememberSaveable { mutableStateOf(false) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    var filter by rememberSaveable { mutableStateOf<AppCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryVersion by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<AppRowData?>(null) }

    val weekStart = selected.with(java.time.DayOfWeek.MONDAY)
    val snapshot by produceState<UsageSnapshot?>(null, weekStart, categoryVersion) {
        value = withContext(Dispatchers.IO) {
            val week = UsageData.week(context, weekStart)
            val pkgs = week.days.flatMap { it.perApp.keys }.toSet()
            UsageSnapshot(
                week = week,
                categories = pkgs.associateWith { UsageData.category(context, it) },
                labels = pkgs.associateWith { InstalledApps.label(context, it) },
                icons = pkgs.associateWith { InstalledApps.icon(context, it) },
            )
        }
    }

    val data = snapshot
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Accent) }
        return
    }

    // Days of this week that have happened (for weekly averages).
    val elapsedDays = data.week.days.count { !it.date.isAfter(today) }.coerceAtLeast(1)
    fun matches(pkg: String) = filter == null || data.categories[pkg] == filter

    // Per-app milliseconds for what's shown: one day, or the week's daily average.
    val perApp: Map<String, Long> = if (weekly) {
        val sums = HashMap<String, Long>()
        data.week.days.forEach { d -> d.perApp.forEach { (p, ms) -> sums[p] = (sums[p] ?: 0L) + ms } }
        sums.mapValues { it.value / elapsedDays }
    } else {
        data.week.days.firstOrNull { it.date == selected }?.perApp ?: emptyMap()
    }
    val byCategory = AppCategory.entries.associateWith { c -> perApp.filterKeys { data.categories[it] == c }.values.sum() }
    val total = perApp.filterKeys(::matches).values.sum()

    val rows = perApp.filterKeys(::matches)
        .map { (p, ms) -> AppRowData(p, data.labels[p] ?: p, data.icons[p], data.categories[p] ?: AppCategory.OTHERS, ms) }
        .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.ms }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "tabs") {
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(50)).padding(4.dp)) {
                listOf(false to "Daily", true to "Weekly").forEach { (isWeekly, label) ->
                    val on = weekly == isWeekly
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (on) Palette.Accent.copy(alpha = 0.6f) else Color.Transparent)
                            .clickable { weekly = isWeekly }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else Palette.TextDim)
                    }
                }
            }
        }
        item(key = "chips") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All apps", filter == null) { filter = null }
                AppCategory.entries.forEach { c -> FilterChip(c.label, filter == c, dot = c.color) { filter = c } }
            }
        }
        item(key = "total") {
            val canGoBack = true
            val canGoForward = if (weekly) weekStart.plusWeeks(1) <= today else selected < today
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArrowButton(left = true, enabled = canGoBack) {
                    selectedEpochDay = (if (weekly) selected.minusWeeks(1) else selected.minusDays(1)).toEpochDay()
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(total), style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp), color = Palette.Text)
                    Text(
                        if (weekly) {
                            "Daily average · ${weekStart.format(DAY_MONTH)} - ${weekStart.plusDays(6).format(DAY_MONTH)}"
                        } else {
                            if (selected == today) "Today" else selected.format(FULL_DAY)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                    )
                }
                ArrowButton(left = false, enabled = canGoForward) {
                    val next = if (weekly) selected.plusWeeks(1) else selected.plusDays(1)
                    selectedEpochDay = minOf(next, today).toEpochDay()
                }
            }
        }
        item(key = "chart") {
            WeekChart(
                week = data.week,
                categories = data.categories,
                filter = filter,
                highlight = if (weekly) null else selected,
                today = today,
                onSelectDay = { day ->
                    selectedEpochDay = day.toEpochDay()
                    weekly = false
                },
            )
        }
        item(key = "legend") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppCategory.entries.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c.color))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(formatDuration(byCategory[c] ?: 0L), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        }
                    }
                }
            }
        }
        item(key = "search") {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Palette.TextFaint)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.AccentLight),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No app usage here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(rows, key = { it.packageName }) { row ->
            UsageAppRow(row, averaged = weekly) { editing = row }
        }
    }

    editing?.let { row ->
        AppCategoryDialog(
            row = row,
            onDismiss = { editing = null },
            onChanged = { categoryVersion++ },
        )
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Palette.TextDim)
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ArrowButton(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (left) "Previous" else "Next",
            tint = if (enabled) Palette.Text else Palette.TextFaint.copy(alpha = 0.2f),
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Monday-to-Sunday stacked bars (distracting / productive / others), tap a bar to open that day. */
@Composable
private fun WeekChart(
    week: WeekUsage,
    categories: Map<String, AppCategory>,
    filter: AppCategory?,
    highlight: LocalDate?,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
) {
    val stacks = week.days.map { day ->
        AppCategory.entries.associateWith { c ->
            if (filter != null && filter != c) 0L else day.perApp.filterKeys { categories[it] == c }.values.sum()
        }
    }
    val maxHours = stacks.maxOf { it.values.sum() } / 3_600_000.0
    val scaleHours = (ceil(maxHours / 6.0) * 6).coerceAtLeast(6.0)
    val stackOrder = listOf(AppCategory.DISTRACTING, AppCategory.PRODUCTIVE, AppCategory.OTHERS)

    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row {
            Column(Modifier.height(170.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(scaleHours, scaleHours / 2, 0.0).forEach {
                    Text("${it.toInt()}h", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier.weight(1f).height(170.dp).pointerInput(week.weekStart) {
                    detectTapGestures { pos ->
                        val index = (pos.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                        val day = week.days[index].date
                        if (!day.isAfter(today)) onSelectDay(day)
                    }
                },
            ) {
                val grid = Color.White.copy(alpha = 0.08f)
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = size.height * f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slot = size.width / 7f
                val barWidth = slot * 0.55f
                stacks.forEachIndexed { i, stack ->
                    val totalMs = stack.values.sum()
                    if (totalMs <= 0) return@forEachIndexed
                    val barHeight = (totalMs / 3_600_000.0 / scaleHours * size.height).toFloat().coerceAtLeast(3.dp.toPx())
                    val left = slot * i + (slot - barWidth) / 2
                    val top = size.height - barHeight
                    val dim = highlight != null && week.days[i].date != highlight
                    val clip = Path().apply {
                        addRoundRect(RoundRect(Rect(left, top, left + barWidth, size.height), CornerRadius(8.dp.toPx())))
                    }
                    clipPath(clip) {
                        var y = size.height
                        stackOrder.forEach { c ->
                            val h = ((stack[c] ?: 0L).toDouble() / totalMs * barHeight).toFloat()
                            if (h > 0f) {
                                drawRect(c.color.copy(alpha = if (dim) 0.35f else 1f), Offset(left, y - h), Size(barWidth, h))
                                y -= h
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            week.days.forEach { d ->
                Text(
                    d.date.dayOfWeek.name.take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.date == highlight) Palette.Text else Palette.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun UsageAppRow(row: AppRowData, averaged: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.GlassStrong))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium, color = Palette.Text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(row.category.color))
                Spacer(Modifier.width(6.dp))
                Text(row.category.label, style = MaterialTheme.typography.bodyMedium, color = row.category.color)
            }
        }
        Text(
            formatDuration(row.ms) + if (averaged) " avg" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextDim,
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Palette.TextFaint)
    }
}

/** Tap an app: change its category, or block it during focus. */
@Composable
private fun AppCategoryDialog(row: AppRowData, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FocusRepository.get(context) }
    val blocked by repo.blockedApps.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(row.category) }

    GlassDialog(onDismiss) {
        val icon = row.icon
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
        Text(row.label, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Text(formatDuration(row.ms), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        Spacer(Modifier.height(16.dp))
        Text("This app is…", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppCategory.entries.forEach { c ->
                FilterChip(c.label, category == c, dot = c.color) {
                    category = c
                    UsageData.setCategory(context, row.packageName, c)
                    onChanged()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Block during focus", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f))
            Switch(
                checked = row.packageName in blocked,
                onCheckedChange = { repo.setAppBlocked(row.packageName, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White),
            )
        }
        Spacer(Modifier.height(16.dp))
        PillButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val FULL_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
