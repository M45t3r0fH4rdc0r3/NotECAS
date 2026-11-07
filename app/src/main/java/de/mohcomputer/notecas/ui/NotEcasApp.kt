package de.mohcomputer.notecas.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.mohcomputer.notecas.data.LegacyMeterRecord
import de.mohcomputer.notecas.data.LegacyReadingRecord
import de.mohcomputer.notecas.BuildConfig
import de.mohcomputer.notecas.R
import de.mohcomputer.notecas.domain.MeterCalculations
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val ElectricBlue = Color(0xFF536DFE)
private val VividPurple = Color(0xFF7C4DFF)
private val EnergyTeal = Color(0xFF00A884)
private val SolarOrange = Color(0xFFFF8A34)
private val SensorPink = Color(0xFFE5488E)
private val ReadingColors = listOf(
    "Schwarz" to Color(0xFF202124),
    "Rot" to Color(0xFFE53935),
    "Grün" to Color(0xFF2EAD65),
    "Gelb" to Color(0xFFFFC928),
    "Blau" to Color(0xFF2979FF),
)

private fun meterAccent(meter: LegacyMeterRecord): Color = when {
    meter.type == 1 -> SensorPink
    meter.production -> SolarOrange
    !meter.vmsetup.isNullOrBlank() -> VividPurple
    meter.invert -> EnergyTeal
    else -> ElectricBlue
}

private fun meterIcon(meter: LegacyMeterRecord): ImageVector = when {
    meter.type == 1 -> Icons.Rounded.Thermostat
    else -> Icons.Rounded.Bolt
}

@Composable
fun NotEcasApp(
    initialState: NotEcasUiState,
    onStateConsumer: (((NotEcasUiState) -> Unit) -> Unit),
    onAction: (UiAction) -> Unit,
    onImportDatabase: () -> Unit,
    onExportDatabase: () -> Unit,
    onImportCsv: (Long) -> Unit,
    onExportCsv: (Long) -> Unit,
    torchAvailable: Boolean,
    onTorch: () -> Unit,
    onCopyDonationAddress: () -> Unit,
    onOpenPayPalApp: () -> Unit,
    payPalAppAvailable: Boolean,
) {
    var state by remember { mutableStateOf(initialState) }
    DisposableEffect(Unit) { onStateConsumer { state = it }; onDispose { onStateConsumer {} } }
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9ECBFF), secondary = Color(0xFFB7C8E5))
        else -> lightColorScheme(primary = Color(0xFF0B57A3), secondary = Color(0xFF425F82))
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) {
        val snackbarHost = remember { SnackbarHostState() }
        LaunchedEffect(state.snackbar) {
            state.snackbar?.let { snackbarHost.showSnackbar(it); onAction(UiAction.ClearSnackbar) }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 720.dp
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                snackbarHost = { SnackbarHost(snackbarHost) },
                topBar = { AppTopBar(state, onAction) },
                bottomBar = { if (!expanded) BottomNavigation(state.destination, onAction) },
                floatingActionButton = {
                    when (state.destination) {
                        RootDestination.METERS -> ExtendedFloatingActionButton(
                            onClick = { onAction(UiAction.OpenNewMeter) },
                            text = { Text("Zähler anlegen", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Rounded.Add, null) },
                            containerColor = VividPurple,
                            contentColor = Color.White,
                        )
                        RootDestination.READINGS -> state.selectedMeterId?.let { id -> ExtendedFloatingActionButton(
                            onClick = { onAction(UiAction.OpenNewReading(id)) },
                            text = { Text("Messwert", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Rounded.Add, null) },
                            containerColor = EnergyTeal,
                            contentColor = Color.White,
                        ) }
                        else -> Unit
                    }
                },
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    if (expanded) NavigationRailPanel(state.destination, onAction)
                    Box(Modifier.fillMaxSize().widthIn(max = 1200.dp).padding(horizontal = if (expanded) 28.dp else 16.dp)) {
                        when (state.destination) {
                            RootDestination.OVERVIEW -> OverviewScreen(state, onAction)
                            RootDestination.METERS -> MetersScreen(state, onAction)
                            RootDestination.READINGS -> ReadingsScreen(state, onAction)
                            RootDestination.ANALYSIS -> AnalysisScreen(state, onAction)
                        }
                    }
                }
            }
        }
        state.meterEditor?.let { MeterEditorDialog(it, state.meters, onAction) }
        state.readingEditor?.let { ReadingEditorDialog(it, state.meters.firstOrNull { m -> m.id == it.meterId }, torchAvailable, onTorch, onAction) }
        when (state.overlay) {
            Overlay.TRANSFER -> TransferDialog(state, onImportDatabase, onExportDatabase, onImportCsv, onExportCsv) { onAction(UiAction.CloseOverlay) }
            Overlay.ABOUT -> AboutDialog { onAction(UiAction.CloseOverlay) }
            Overlay.DONATION -> DonationDialog(
                payPalAppAvailable = payPalAppAvailable,
                onCopyAddress = onCopyDonationAddress,
                onOpenPayPalApp = onOpenPayPalApp,
                close = { onAction(UiAction.CloseOverlay) },
            )
            null -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppTopBar(state: NotEcasUiState, onAction: (UiAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).background(
                        Brush.linearGradient(listOf(VividPurple, ElectricBlue)),
                        RoundedCornerShape(14.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Bolt, null, tint = Color.White) }
                Column {
                    Text("NotECAS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(state.destination.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "Mehr Optionen" }) {
                    Icon(Icons.Rounded.MoreVert, null)
                }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(text = { Text("Transfer") }, leadingIcon = { Icon(Icons.Rounded.UploadFile, null) }, onClick = { expanded = false; onAction(UiAction.ShowOverlay(Overlay.TRANSFER)) })
                    DropdownMenuItem(text = { Text("Kleine Spende via PayPal") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, tint = SensorPink) }, onClick = { expanded = false; onAction(UiAction.ShowOverlay(Overlay.DONATION)) })
                    DropdownMenuItem(text = { Text("Info") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { expanded = false; onAction(UiAction.ShowOverlay(Overlay.ABOUT)) })
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    )
}

@Composable private fun BottomNavigation(selected: RootDestination, onAction: (UiAction) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        RootDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onAction(UiAction.Navigate(item)) },
                icon = { Icon(navIcon(item), contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = VividPurple.copy(alpha = .18f), selectedIconColor = VividPurple, selectedTextColor = VividPurple),
            )
        }
    }
}

@Composable private fun NavigationRailPanel(selected: RootDestination, onAction: (UiAction) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Spacer(Modifier.height(16.dp))
        RootDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onAction(UiAction.Navigate(item)) },
                icon = { Icon(navIcon(item), contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationRailItemDefaults.colors(indicatorColor = VividPurple.copy(alpha = .18f), selectedIconColor = VividPurple),
            )
        }
    }
}
private fun navIcon(item: RootDestination): ImageVector = when (item) {
    RootDestination.OVERVIEW -> Icons.Rounded.Home
    RootDestination.METERS -> Icons.Rounded.Speed
    RootDestination.READINGS -> Icons.Rounded.QueryStats
    RootDestination.ANALYSIS -> Icons.Rounded.Analytics
}

@Composable private fun OverviewScreen(state: NotEcasUiState, onAction: (UiAction) -> Unit) {
    if (state.meters.isEmpty()) EmptyState("Noch keine Zähler", "Lege deinen ersten Zähler an, um Verbrauch und Kosten zu erfassen.", "Zähler anlegen") { onAction(UiAction.OpenNewMeter) }
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp, start = 2.dp, end = 2.dp)) {
        item { DashboardHero(state) }
        item { Text("Deine Zähler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, start = 4.dp)) }
        items(state.meters, key = { it.id }) { meter -> OverviewCard(meter, state.readings[meter.id].orEmpty(), onAction) }
    }
}

@Composable private fun DashboardHero(state: NotEcasUiState) {
    val readingCount = state.readings.values.sumOf { it.size }
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF5B36F2), Color(0xFF246BFD), Color(0xFF00A7B7))), RoundedCornerShape(32.dp))
            .semantics { contentDescription = "Verbrauchsübersicht" }
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(50.dp).background(Color.White.copy(alpha = .16f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dein Verbrauch. Klar im Blick.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Alle Verbräuche, Kosten und Trends an einem Ort.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .82f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric("${state.meters.size}", "Zähler", Modifier.weight(1f))
                HeroMetric("$readingCount", "Messwerte", Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun HeroMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .78f))
    }
}

@Composable private fun OverviewCard(meter: LegacyMeterRecord, rows: List<LegacyReadingRecord>, onAction: (UiAction) -> Unit) {
    val ordered = rows.sortedBy { it.date }
    val latest = ordered.lastOrNull()
    val previous = ordered.dropLast(1).lastOrNull()
    val delta = if (latest != null && previous != null) runCatching { MeterCalculations.interval(meter.toDomain(), previous.toDomain(), latest.toDomain()) }.getOrNull() else null
    val accent = meterAccent(meter)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(accent.copy(alpha = .15f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                    Icon(meterIcon(meter), null, tint = accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(meter.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(meter.comment ?: typeLabel(meter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = { onAction(UiAction.OpenNewReading(meter.id)) }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent.copy(alpha = .15f), contentColor = accent)) {
                    Icon(Icons.Rounded.Add, contentDescription = "Messwert für ${meter.name}")
                }
            }
            Text(if (latest == null) "Noch kein Messwert" else "${formatNumber(latest.value * meter.scaling)} ${meter.units}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            if (latest != null) Text(formatTimestamp(latest.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (delta != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColoredStat("Trend", "${if (delta.delta >= 0) "▲" else "▼"} ${formatNumber(abs(delta.delta))} ${meter.units}", accent, Modifier.weight(1f))
                ColoredStat("Kosten", "${formatNumber(delta.totalCost)} ${meter.currency ?: "€"}", EnergyTeal, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun ColoredStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(color.copy(alpha = .11f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun MetersScreen(state: NotEcasUiState, onAction: (UiAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = state.meterFilter.ordinal, edgePadding = 0.dp, divider = {}) {
            MeterFilter.entries.forEach { filter -> FilterChip(filter == state.meterFilter, { onAction(UiAction.SetMeterFilter(filter)) }, { Text(filter.label) }, modifier = Modifier.padding(end = 8.dp)) }
        }
        val meters = filterMeters(state.meters, state.meterFilter)
        if (meters.isEmpty()) EmptyState("Keine passenden Zähler", "Passe den Filter an oder lege einen Zähler an.", "Zähler anlegen") { onAction(UiAction.OpenNewMeter) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 2.dp)) { items(meters, key = { it.id }) { MeterRow(it, onAction) } }
    }
}

@Composable private fun MeterRow(meter: LegacyMeterRecord, onAction: (UiAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val accent = meterAccent(meter)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accent.copy(alpha = .18f)),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(accent.copy(alpha = .14f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(meterIcon(meter), null, tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(meter.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${typeLabel(meter)} · ${meter.units}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }, Modifier.semantics { contentDescription = "Aktionen für ${meter.name}" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Messwert hinzufügen") }, { menu = false; onAction(UiAction.OpenNewReading(meter.id)) }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
                    DropdownMenuItem({ Text("Bearbeiten") }, { menu = false; onAction(UiAction.EditMeter(meter.id)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text("Löschen") }, { menu = false; onAction(UiAction.DeleteMeter(meter.id)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ReadingsScreen(state: NotEcasUiState, onAction: (UiAction) -> Unit) {
    val meter = state.meters.firstOrNull { it.id == state.selectedMeterId } ?: state.meters.firstOrNull()
    if (meter == null) { EmptyState("Noch keine Zähler", "Für Messwerte wird zuerst ein Zähler benötigt.", "Zähler anlegen") { onAction(UiAction.OpenNewMeter) }; return }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var select by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(select, { select = it }) {
            OutlinedTextField(meter.name, {}, readOnly = true, label = { Text("Zähler") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(select) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
            ExposedDropdownMenu(select, { select = false }) { state.meters.forEach { item -> DropdownMenuItem({ Text(item.name) }, { select = false; onAction(UiAction.SelectMeter(item.id)) }) } }
        }
        OutlinedTextField(state.readingQuery, { onAction(UiAction.SetReadingQuery(it)) }, label = { Text("Messwerte filtern") }, placeholder = { Text("Datum, Kommentar oder Wert") }, modifier = Modifier.fillMaxWidth())
        val rows = state.readings[meter.id].orEmpty().filter { row -> state.readingQuery.isBlank() || listOf(row.date, row.comment.orEmpty(), row.value.toString()).any { it.contains(state.readingQuery, true) } }.sortedByDescending { it.date }
        if (rows.isEmpty()) EmptyState("Noch keine Messwerte", "Erfasse den ersten Stand für ${meter.name}.", "Messwert hinzufügen") { onAction(UiAction.OpenNewReading(meter.id)) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 96.dp)) { items(rows, key = { it.id }) { ReadingRow(it, meter, onAction) } }
    }
}

@Composable private fun ReadingRow(row: LegacyReadingRecord, meter: LegacyMeterRecord, onAction: (UiAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }; val color = listOf(Color.Black, Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFF1565C0))[row.color.coerceIn(0, 4)]
    OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { drawCircle(color) } }; Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("${formatNumber(row.value * meter.scaling)} ${meter.units}", fontWeight = FontWeight.Bold); Text(formatTimestamp(row.date)); row.comment?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }; if (row.first == 1) Text("Erste Messung / Reset", color = MaterialTheme.colorScheme.primary) }
        Box { IconButton({ menu = true }) { Text("⋮") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Bearbeiten") }, { menu = false; onAction(UiAction.EditReading(row.id)) }); DropdownMenuItem({ Text("Löschen") }, { menu = false; onAction(UiAction.DeleteReading(row.id)) }) } }
    } }
}

@Composable private fun AnalysisScreen(state: NotEcasUiState, onAction: (UiAction) -> Unit) {
    val meter = state.meters.firstOrNull { it.id == state.selectedMeterId } ?: state.meters.firstOrNull()
    if (meter == null) { EmptyState("Keine Analysedaten", "Lege zuerst einen Zähler und Messwerte an.", "Zähler anlegen") { onAction(UiAction.OpenNewMeter) }; return }
    val meterReadings = state.readings[meter.id].orEmpty()
    val model = buildAnalysis(meter, meterReadings, state.analysisPeriod, year = state.analysisYear)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AnalysisMeterSelector(state.meters, meter, onAction)
        AnalysisYearSelector(state.analysisYear, meterReadings, onAction)
        Text(meter.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { AnalysisMode.entries.forEachIndexed { index, mode -> SegmentedButton(state.analysisMode == mode, { onAction(UiAction.SetAnalysisMode(mode)) }, SegmentedButtonDefaults.itemShape(index, AnalysisMode.entries.size)) { Text(mode.label) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AnalysisPeriod.entries.forEach { p -> FilterChip(state.analysisPeriod == p, { onAction(UiAction.SetAnalysisPeriod(p)) }, { Text(p.label) }) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("Verbrauch", "${formatNumber(model.consumption)} ${meter.units}", VividPurple, Icons.Rounded.Analytics, Modifier.weight(1f))
            SummaryCard("Kosten", "${formatNumber(model.cost)} ${meter.currency ?: "€"}", EnergyTeal, Icons.Rounded.QueryStats, Modifier.weight(1f))
        }
        model.previousYearConsumption?.let { Text("Vorjahresvergleich: ${formatNumber(it)} ${meter.units} · ${formatNumber(model.consumption - it)} ${meter.units}", style = MaterialTheme.typography.titleMedium) }
        when (state.analysisMode) {
            AnalysisMode.TABLE -> if (model.points.isEmpty()) Text("Keine Tabellenwerte im gewählten Zeitraum.") else AnalysisTable(model, meter, state.analysisPeriod, state.analysisYear)
            AnalysisMode.TREND, AnalysisMode.BARS -> if (model.points.size < 2) {
                Text("Für die Visualisierung werden mindestens zwei Messwerte im Zeitraum benötigt.")
            } else if (state.analysisMode == AnalysisMode.TREND) {
                DataChart(smoothAnalysisPoints(model.points, meter.vsf, meter.tsf), false, state.analysisPeriod)
            } else {
                DataChart(model.points, true, state.analysisPeriod)
            }
        }
    }
}

@Composable private fun AnalysisYearSelector(selectedYear: Int, rows: List<LegacyReadingRecord>, onAction: (UiAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentYear = LocalDate.now().year
    val years = remember(rows, selectedYear, currentYear) {
        (rows.mapNotNull { row -> runCatching { LocalDateTime.parse(row.date, de.mohcomputer.notecas.data.LEGACY_TIMESTAMP_FORMAT).year }.getOrNull() } + selectedYear + currentYear)
            .distinct().sortedDescending()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(
            onClick = { onAction(UiAction.ChangeAnalysisYear(-1)) },
            modifier = Modifier.semantics { contentDescription = "Vorheriges Jahr" },
        ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.widthIn(min = 150.dp).heightIn(min = 52.dp)) {
                Text("Jahr $selectedYear", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = { expanded = false; onAction(UiAction.SetAnalysisYear(year)) },
                    )
                }
            }
        }
        IconButton(
            onClick = { onAction(UiAction.ChangeAnalysisYear(1)) },
            enabled = selectedYear < currentYear,
            modifier = Modifier.semantics { contentDescription = "Nächstes Jahr" },
        ) { Text("›", style = MaterialTheme.typography.headlineMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AnalysisMeterSelector(meters: List<LegacyMeterRecord>, selected: LegacyMeterRecord, onAction: (UiAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zähler auswählen") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            meters.forEach { meter ->
                DropdownMenuItem(
                    text = { Text(meter.name) },
                    onClick = { expanded = false; onAction(UiAction.SelectMeter(meter.id)) },
                )
            }
        }
    }
}

@Composable private fun SummaryCard(title: String, value: String, accent: Color, icon: ImageVector, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = accent.copy(alpha = .12f), border = BorderStroke(1.dp, accent.copy(alpha = .18f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable private fun AnalysisTable(model: AnalysisModel, meter: LegacyMeterRecord, period: AnalysisPeriod, year: Int) {
    val sensor = meter.type == 1
    val rows = remember(model.points, model.previousPoints, period, sensor) {
        buildAnalysisTableRows(model.points, model.previousPoints, period, sensor)
    }
    val title = if (period == AnalysisPeriod.MONTH) "Tagesvergleich" else "Monatsvergleich"
    val currentTotal = analysisTableTotal(model.points, sensor) ?: 0.0
    val previousTotal = analysisTableTotal(model.previousPoints, sensor)
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, VividPurple.copy(alpha = .18f)),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Vergleichstabelle mit Vorjahreswerten" },
    ) {
        Column {
            Column(
                Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(VividPurple.copy(alpha = .16f), ElectricBlue.copy(alpha = .08f)))).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (sensor) "Durchschnittswerte im Vergleich zum Vorjahr" else "Verbrauch und Veränderung gegenüber dem Vorjahr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Zeitraum", Modifier.weight(.7f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text("Aktuell $year", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Text("Vorjahr ${year - 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            rows.forEachIndexed { index, row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                AnalysisTableLine(row, meter, index % 2 == 1)
            }
            HorizontalDivider(thickness = 2.dp, color = VividPurple.copy(alpha = .30f))
            Column(Modifier.fillMaxWidth().background(VividPurple.copy(alpha = .08f)).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (sensor) "Mittelwert" else "Gesamt", Modifier.weight(.7f), fontWeight = FontWeight.Bold)
                    Text("${formatNumber(currentTotal)} ${meter.units}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(previousTotal?.let { "${formatNumber(it)} ${meter.units}" } ?: "–", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
                if (previousTotal != null) {
                    val change = currentTotal - previousTotal
                    val percent = if (abs(previousTotal) > 1e-9) change / abs(previousTotal) * 100.0 else null
                    Text("Gesamtänderung: ${signedNumber(change)} ${meter.units}${percent?.let { " · ${signedNumber(it)} %" }.orEmpty()}", style = MaterialTheme.typography.labelLarge, color = comparisonColor(change, meter))
                }
            }
        }
    }
}

@Composable private fun AnalysisTableLine(row: AnalysisTableRow, meter: LegacyMeterRecord, shaded: Boolean) {
    val rowDescription = buildString {
        append("${row.label}: aktuell ${formatNumber(row.current)} ${meter.units}")
        row.previous?.let { append(", Vorjahr ${formatNumber(it)} ${meter.units}") }
        row.change?.let { append(", Änderung ${signedNumber(it)} ${meter.units}") }
    }
    Column(
        Modifier.fillMaxWidth().background(if (shaded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .26f) else Color.Transparent).semantics { contentDescription = rowDescription }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.label, Modifier.weight(.7f), fontWeight = FontWeight.Bold)
            Text("${formatNumber(row.current)} ${meter.units}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.previous?.let { "${formatNumber(it)} ${meter.units}" } ?: "–", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        row.change?.let { change ->
            val percent = row.changePercent?.let { " · ${signedNumber(it)} %" }.orEmpty()
            Text("Änderung: ${signedNumber(change)} ${meter.units}$percent", style = MaterialTheme.typography.labelMedium, color = comparisonColor(change, meter), modifier = Modifier.align(Alignment.End))
        }
    }
}

private fun signedNumber(value: Double): String = (if (value > 0.0) "+" else "") + formatNumber(value)

@Composable private fun comparisonColor(change: Double, meter: LegacyMeterRecord): Color = when {
    meter.type == 1 || abs(change) < 1e-9 -> MaterialTheme.colorScheme.onSurfaceVariant
    (change > 0.0) == meter.production -> EnergyTeal
    else -> MaterialTheme.colorScheme.error
}

@Composable private fun DataChart(points: List<AnalysisPoint>, bars: Boolean, period: AnalysisPeriod) {
    val primary = VividPurple
    val secondary = ElectricBlue
    val axisTicks = remember(points, period) { chartAxisTicks(points, period) }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, primary.copy(alpha = .16f)), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(primary.copy(alpha = .10f), Color.Transparent)), RoundedCornerShape(28.dp))
                .semantics { contentDescription = if (bars) "Balkendiagramm mit Zeitachsenbeschriftung" else "Trenddiagramm mit Zeitachsenbeschriftung" },
        ) {
            Canvas(Modifier.fillMaxWidth().height(244.dp).padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp)) {
                val min = points.minOf { it.value }; val max = points.maxOf { it.value }; val range = (max - min).takeIf { it != 0.0 } ?: 1.0
                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(primary.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                }
                val coords = points.mapIndexed { index, point -> Offset(size.width * index / (points.size - 1).coerceAtLeast(1), size.height - ((point.value - min) / range).toFloat() * size.height) }
                if (bars) coords.forEachIndexed { index, p ->
                    val color = if (index % 2 == 0) primary else secondary
                    drawLine(color, Offset(p.x, size.height), p, strokeWidth = (size.width / points.size * .58f).coerceAtLeast(10f), cap = StrokeCap.Round)
                } else coords.zipWithNext().forEach { (a, b) -> drawLine(Brush.linearGradient(listOf(primary, secondary), a, b), a, b, strokeWidth = 7f, cap = StrokeCap.Round) }
                coords.forEach { drawCircle(Color.White, 9f, it); drawCircle(primary, 6f, it) }
            }
            ChartAxis(axisTicks, points.size, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 14.dp))
        }
    }
}

internal data class ChartAxisTick(val pointIndex: Int, val text: String)

internal fun chartAxisTicks(points: List<AnalysisPoint>, period: AnalysisPeriod): List<ChartAxisTick> {
    if (points.isEmpty()) return emptyList()
    val dates = points.mapNotNull { point -> runCatching { LocalDate.parse(point.label) }.getOrNull() }
    if (dates.size != points.size) {
        return points.withIndex().toList().sampleEvenly(6).map { ChartAxisTick(it.index, it.value.label) }
    }
    val indexedDates = dates.withIndex()
    val grouped = if (period == AnalysisPeriod.MONTH) {
        indexedDates.groupBy { it.value }
    } else {
        indexedDates.groupBy { it.value.year to it.value.monthValue }
    }
    val months = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    return grouped.values.map { entries ->
        val representative = entries[entries.size / 2]
        val text = if (period == AnalysisPeriod.MONTH) representative.value.dayOfMonth.toString() else months[representative.value.monthValue - 1]
        ChartAxisTick(representative.index, text)
    }.sampleEvenly(6)
}

private fun <T> List<T>.sampleEvenly(maxItems: Int): List<T> {
    if (size <= maxItems) return this
    return (0 until maxItems).map { position -> this[(position * lastIndex) / (maxItems - 1)] }.distinct()
}

@Composable private fun ChartAxis(ticks: List<ChartAxisTick>, pointCount: Int, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier.height(24.dp),
        content = {
            ticks.forEach { tick ->
                Text(
                    tick.text,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val fraction = if (pointCount <= 1) .5f else ticks[index].pointIndex.toFloat() / (pointCount - 1)
                val x = (fraction * width).roundToInt() - placeable.width / 2
                placeable.placeRelative(x.coerceIn(0, (width - placeable.width).coerceAtLeast(0)), 0)
            }
        }
    }
}

@Composable private fun EmptyState(title: String, detail: String, action: String, onClick: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(Modifier.widthIn(max = 440.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("◇", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodyLarge); Button(onClick, modifier = Modifier.heightIn(min = 56.dp)) { Text(action) } } } }

@Composable private fun MeterEditorDialog(editor: MeterEditor, meters: List<LegacyMeterRecord>, onAction: (UiAction) -> Unit) {
    var smoothingExpanded by remember(editor.id) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { onAction(UiAction.CloseEditor) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (editor.id == null) "Zähler anlegen" else "Zähler bearbeiten",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({ onAction(UiAction.CloseEditor) }) { Text("Abbrechen") }
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { EditField("Name", editor.name) { onAction(UiAction.SetMeterEditor(editor.copy(name = it))) } }
                    item { EditField("Einheit", editor.unit) { onAction(UiAction.SetMeterEditor(editor.copy(unit = it))) } }
                    item { EditField("Kommentar", editor.comment) { onAction(UiAction.SetMeterEditor(editor.copy(comment = it))) } }
                    item { Text("Zählertyp", fontWeight = FontWeight.Bold) }
                    item { Check("Sensor (nicht kumulativ)", editor.sensor) { onAction(UiAction.SetMeterEditor(editor.copy(sensor = it, virtual = false))) } }
                    item { Check("Produktion", editor.production) { onAction(UiAction.SetMeterEditor(editor.copy(production = it))) } }
                    item { Check("Invertiert / Prepaid", editor.inverted) { onAction(UiAction.SetMeterEditor(editor.copy(inverted = it))) } }
                    item { Check("Bidirektional / Überlauf", editor.bidirectional) { onAction(UiAction.SetMeterEditor(editor.copy(bidirectional = it))) } }
                    item { Check("Virtuell", editor.virtual) { onAction(UiAction.SetMeterEditor(editor.copy(virtual = it, sensor = false))) } }
                    if (editor.virtual) {
                        item { EditField("Quellen/Faktoren (z. B. 1|1.0|2|-0.5)", editor.virtualSetup) { onAction(UiAction.SetMeterEditor(editor.copy(virtualSetup = it))) } }
                        item { Text("Verfügbare Quellen: ${meters.filterNot { it.id == editor.id }.joinToString { "${it.id} ${it.name}" }}", style = MaterialTheme.typography.bodySmall) }
                    }
                    item { EditField("Skalierung", editor.scaling) { onAction(UiAction.SetMeterEditor(editor.copy(scaling = it))) } }
                    item { EditField("Physische Einheit", editor.physicalUnit) { onAction(UiAction.SetMeterEditor(editor.copy(physicalUnit = it))) } }
                    item { EditField("Währung", editor.currency) { onAction(UiAction.SetMeterEditor(editor.copy(currency = it))) } }
                    item { EditField("Preis je Einheit", editor.price) { onAction(UiAction.SetMeterEditor(editor.copy(price = it))) } }
                    item { EditField("Fixkosten pro Jahr", editor.fixedCost) { onAction(UiAction.SetMeterEditor(editor.copy(fixedCost = it))) } }
                    item {
                        OutlinedButton(
                            onClick = { smoothingExpanded = !smoothingExpanded },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Erweiterte Diagrammeinstellungen") }
                    }
                    if (smoothingExpanded) {
                        item {
                            Text(
                                "Beeinflusst nur die Trenddarstellung – niemals Messwerte, Verbrauch oder Kosten.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item { Column { Text("Wertglättung ${editor.valueSmoothing.toInt()}"); Slider(editor.valueSmoothing, { onAction(UiAction.SetMeterEditor(editor.copy(valueSmoothing = it))) }, valueRange = 0f..100f) } }
                        item { Column { Text("Trendglättung ${editor.trendSmoothing.toInt()}"); Slider(editor.trendSmoothing, { onAction(UiAction.SetMeterEditor(editor.copy(trendSmoothing = it))) }, valueRange = 0f..100f) } }
                        item {
                            Text(
                                "50 entspricht dem ECAS-Standard. Höhere Werte glätten stärker und reagieren langsamer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Button(
                    onClick = { onAction(UiAction.SaveMeter) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                ) { Text("Speichern") }
            }
        }
    }
}

@Composable private fun ReadingEditorDialog(editor: ReadingEditor, meter: LegacyMeterRecord?, torchAvailable: Boolean, onTorch: () -> Unit, onAction: (UiAction) -> Unit) {
    Dialog(
        onDismissRequest = { onAction(UiAction.CloseEditor) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (editor.id == null) "Messwert erfassen" else "Messwert bearbeiten", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(meter?.name.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (torchAvailable) IconButton(onTorch, Modifier.semantics { contentDescription = "Taschenlampe" }) { Icon(Icons.Rounded.Bolt, null) }
                    TextButton({ onAction(UiAction.CloseEditor) }) { Text("Abbrechen") }
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    editor.lastValue?.let { value -> item { Text("Letzter Wert: ${formatNumber(value)} ${meter?.physicalUnits ?: meter?.units.orEmpty()}", fontWeight = FontWeight.SemiBold) } }
                    item { EditField("Messwert", editor.value) { onAction(UiAction.ChangeReadingValue(it)) } }
                    editor.previewDelta?.let { delta -> item { Text("Vorschau Differenz: ${formatNumber(delta)} ${meter?.units.orEmpty()}", color = ElectricBlue, fontWeight = FontWeight.SemiBold) } }
                    item { EditField("Datum (JJJJ-MM-TT)", editor.date) { onAction(UiAction.SetReadingEditor(editor.copy(date = it))) } }
                    item { EditField("Uhrzeit (HH:MM:SS)", editor.time) { onAction(UiAction.SetReadingEditor(editor.copy(time = it))) } }
                    item { EditField("Kommentar", editor.comment) { onAction(UiAction.SetReadingEditor(editor.copy(comment = it))) } }
                    item { Text("Farbe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { ReadingColorPicker(editor.color) { color -> onAction(UiAction.SetReadingEditor(editor.copy(color = color))) } }
                    item { Check("Erste Messung / Reset", editor.first) { onAction(UiAction.SetReadingEditor(editor.copy(first = it))) } }
                }
                HorizontalDivider()
                Button(
                    onClick = { onAction(UiAction.SaveReading) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                ) { Text("Messwert speichern") }
            }
        }
    }
}

@Composable private fun ReadingColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ReadingColors.forEachIndexed { index, (name, color) ->
            val isSelected = selected == index
            IconToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier.size(54.dp).semantics { contentDescription = "$name auswählen" },
            ) {
                Box(
                    Modifier.size(40.dp)
                        .background(color, CircleShape)
                        .border(if (isSelected) 4.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = .45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (index == 3) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable private fun TransferDialog(state: NotEcasUiState, onImportDatabase: () -> Unit, onExportDatabase: () -> Unit, onImportCsv: (Long) -> Unit, onExportCsv: (Long) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(close) { Text("Schließen") } }, title = { Text("Transfer") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Vollständige ECAS-Datenbank", fontWeight = FontWeight.Bold); Text("Warnung: Ein Datenbankimport ersetzt alle aktuellen Zähler und Messwerte. Bitte vorher exportieren.", color = MaterialTheme.colorScheme.error); Button(onImportDatabase, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("ECAS-Datenbank importieren") }; OutlinedButton(onExportDatabase, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Datenbank exportieren") }
        HorizontalDivider(); Text("Zählerbezogene CSV", fontWeight = FontWeight.Bold); val id = state.selectedMeterId; Text(state.meters.firstOrNull { it.id == id }?.name ?: "Bitte zuerst Zähler auswählen"); Button({ id?.let(onImportCsv) }, enabled = id != null, modifier = Modifier.fillMaxWidth()) { Text("CSV importieren") }; OutlinedButton({ id?.let(onExportCsv) }, enabled = id != null, modifier = Modifier.fillMaxWidth()) { Text("CSV exportieren") }; state.transferReport?.let { Text("Ergebnisbericht\n$it", color = MaterialTheme.colorScheme.primary) }
    } })
}

@Composable private fun DonationDialog(
    payPalAppAvailable: Boolean,
    onCopyAddress: () -> Unit,
    onOpenPayPalApp: () -> Unit,
    close: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Rounded.Favorite, null, tint = SensorPink) },
        title = { Text("NotECAS unterstützen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Wenn dir NotECAS hilft, kannst du Wolfgang freiwillig eine kleine Spende senden.")
                Surface(shape = RoundedCornerShape(16.dp), color = ElectricBlue.copy(alpha = .10f), border = BorderStroke(1.dp, ElectricBlue.copy(alpha = .20f))) {
                    Text("service@moh-computer.de", modifier = Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Bold, color = ElectricBlue)
                }
                Text("Die Adresse kopieren, in PayPal als Empfänger einfügen und dort – falls angeboten – „Geld an Freunde senden“ wählen.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCopyAddress, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Adresse kopieren") }
                if (payPalAppAvailable) {
                    Button(onClick = onOpenPayPalApp, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("PayPal-App öffnen") }
                } else {
                    Text("PayPal-App nicht installiert", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = { TextButton(close) { Text("Schließen") } },
    )
}

@Composable private fun AboutDialog(close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        icon = { Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(VividPurple, ElectricBlue)), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White) } },
        title = { Text("Über NotECAS") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("NotECAS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Moderne, unabhängige Verbrauchserfassung für Android.")
                HorizontalDivider()
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontWeight = FontWeight.SemiBold)
                Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (BuildConfig.DEBUG) "Debug-Testversion" else "Release-Version")
                HorizontalDivider()
                Text(stringResource(R.string.programmer_name), fontWeight = FontWeight.Bold)
                Text("Ziel: Android 17 · API 37")
                Text("Mindestversion: Android 8 · API 26")
                Text("ECAS-Datenbanken bis Version 17")
                Text("Unabhängige Neuimplementierung ohne Verbindung zum ursprünglichen ECAS-Entwickler.", style = MaterialTheme.typography.bodySmall)
                Text("Copyright © 2026 Wolfgang Lindner")
                Text("Freie Software unter GNU GPLv3 oder später · ohne Gewährleistung", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(close) { Text("Schließen") } },
    )
}
@Composable private fun EditField(label: String, value: String, change: (String) -> Unit) { OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = label != "Kommentar") }
@Composable private fun Check(label: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label, Modifier.weight(1f)) } }
private fun typeLabel(meter: LegacyMeterRecord) = when { !meter.vmsetup.isNullOrBlank() -> "Virtuell"; meter.type == 1 -> "Sensor"; meter.production -> "Produktion"; meter.invert -> "Prepaid"; else -> "Kumulativ" }
private fun formatNumber(value: Double) = String.format(java.util.Locale.GERMANY, "%,.2f", value)
private fun formatTimestamp(value: String) = runCatching { LocalDateTime.parse(value, de.mohcomputer.notecas.data.LEGACY_TIMESTAMP_FORMAT).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm")) }.getOrDefault(value)
