package de.mohcomputer.notecas

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import de.mohcomputer.notecas.data.CsvReading
import de.mohcomputer.notecas.data.EcasDatabaseHelper
import de.mohcomputer.notecas.data.EcasRepository
import de.mohcomputer.notecas.data.LegacyCsvCodec
import de.mohcomputer.notecas.data.LegacyDatabaseExporter
import de.mohcomputer.notecas.data.LegacyDatabaseImporter
import de.mohcomputer.notecas.data.LegacyReadingRecord
import de.mohcomputer.notecas.data.LegacyCsvPrecision
import de.mohcomputer.notecas.ui.NotEcasApp
import de.mohcomputer.notecas.ui.NotEcasReducer
import de.mohcomputer.notecas.ui.NotEcasUiState
import de.mohcomputer.notecas.ui.UiAction
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private lateinit var repository: EcasRepository
    private var state = NotEcasUiState()
    private var render: ((NotEcasUiState) -> Unit)? = null
    private var pendingCsvMeter: Long? = null
    private lateinit var torch: TorchController

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dispatch(UiAction.SetTransferReport("Taschenlampe bereit")) else dispatch(UiAction.SetTransferReport("Kamerazugriff wurde nicht erlaubt"))
    }
    private val importDatabase = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val report = LegacyDatabaseImporter(this).import(uri, repository)
        state = loadState().copy(overlay = state.overlay)
        dispatch(UiAction.SetTransferReport(if (report.success) "Import erfolgreich: ${report.metersImported} Zähler, ${report.readingsImported} Messwerte" else "Import fehlgeschlagen: ${report.issues.joinToString { it.detail }}"))
    }
    private val exportDatabase = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching { LegacyDatabaseExporter(this, repository = repository).export(uri) }
            .onSuccess { repository = EcasRepository(EcasDatabaseHelper(this)); dispatch(UiAction.SetTransferReport("Datenbank vollständig exportiert")) }
            .onFailure { dispatch(UiAction.SetTransferReport("Export fehlgeschlagen: ${it.message}")) }
    }
    private val importCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val meterId = pendingCsvMeter ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        runCatching {
            val decoded = contentResolver.openInputStream(uri)!!.use(LegacyCsvCodec::decode)
            var nextId = state.readings.values.flatten().maxOfOrNull { it.id } ?: 0L
            decoded.rows.forEach { row ->
                repository.upsertReading(LegacyReadingRecord(++nextId, meterId, row.timestamp.format(de.mohcomputer.notecas.data.LEGACY_TIMESTAMP_FORMAT), row.value, row.comment))
            }
            state = loadState().copy(overlay = state.overlay, selectedMeterId = meterId)
            dispatch(UiAction.SetTransferReport("CSV-Import: ${decoded.report.imported} importiert, ${decoded.report.discarded} verworfen"))
        }.onFailure { dispatch(UiAction.SetTransferReport("CSV-Import fehlgeschlagen: ${it.message}")) }
    }
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val meterId = pendingCsvMeter ?: return@registerForActivityResult
        uri ?: return@registerForActivityResult
        runCatching {
            val rows = state.readings[meterId].orEmpty().map { CsvReading(LocalDateTime.parse(it.date, de.mohcomputer.notecas.data.LEGACY_TIMESTAMP_FORMAT), it.value, it.comment.orEmpty()) }
            contentResolver.openOutputStream(uri, "w")!!.use { it.write(LegacyCsvCodec.encode(rows, LegacyCsvPrecision.DOUBLE)) }
            dispatch(UiAction.SetTransferReport("CSV-Export: ${rows.size} Messwerte"))
        }.onFailure { dispatch(UiAction.SetTransferReport("CSV-Export fehlgeschlagen: ${it.message}")) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = EcasRepository(EcasDatabaseHelper(this))
        torch = TorchController(this)
        state = loadState()
        setContent {
            NotEcasApp(
                initialState = state,
                onStateConsumer = { render = it },
                onAction = ::dispatch,
                onImportDatabase = { importDatabase.launch(arrayOf("application/vnd.sqlite3", "application/octet-stream", "*/*")) },
                onExportDatabase = { exportDatabase.launch("notecas-ecas.db") },
                onImportCsv = { meterId -> pendingCsvMeter = meterId; importCsv.launch(arrayOf("text/csv", "text/plain")) },
                onExportCsv = { meterId -> pendingCsvMeter = meterId; exportCsv.launch("notecas-$meterId.csv") },
                torchAvailable = torch.available,
                onTorch = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) cameraPermission.launch(Manifest.permission.CAMERA)
                    else runCatching { torch.toggle() }.onFailure { dispatch(UiAction.SetTransferReport("Taschenlampe nicht verfügbar")) }
                },
                onCopyDonationAddress = ::copyPayPalAddress,
                onOpenPayPalApp = ::openPayPalApp,
                payPalAppAvailable = payPalLaunchIntent() != null,
            )
        }
    }

    private fun copyPayPalAddress() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("PayPal-Adresse", "service@moh-computer.de"),
        )
        dispatch(UiAction.SetTransferReport("PayPal-Adresse service@moh-computer.de wurde kopiert"))
    }

    private fun payPalLaunchIntent() = packageManager.getLaunchIntentForPackage(PAYPAL_PACKAGE)

    private fun openPayPalApp() {
        val intent = payPalLaunchIntent()
        if (intent == null) dispatch(UiAction.SetTransferReport("PayPal-App ist nicht installiert"))
        else runCatching { startActivity(intent) }
            .onFailure { dispatch(UiAction.SetTransferReport("PayPal-App konnte nicht geöffnet werden")) }
    }

    private fun loadState(): NotEcasUiState {
        val meters = repository.meters()
        return NotEcasUiState(meters = meters, readings = meters.associate { it.id to repository.readings(it.id) }, selectedMeterId = meters.firstOrNull()?.id)
    }

    private fun dispatch(action: UiAction) {
        val before = state
        state = NotEcasReducer.reduce(state, action)
        runCatching {
            when (action) {
                UiAction.SaveMeter -> state.meters.firstOrNull { meter -> before.meterEditor?.id?.let { it == meter.id } ?: (meter.id !in before.meters.map { it.id }) }?.let(repository::upsertMeter)
                UiAction.SaveReading -> state.readings.values.flatten().firstOrNull { reading -> before.readingEditor?.id?.let { it == reading.id } ?: (reading.id !in before.readings.values.flatten().map { it.id }) }?.let(repository::upsertReading)
                is UiAction.DeleteMeter -> repository.deleteMeter(action.id)
                is UiAction.DeleteReading -> repository.deleteReading(action.id)
                else -> Unit
            }
        }.onFailure { state = before.copy(snackbar = "Speichern fehlgeschlagen: ${it.message}") }
        render?.invoke(state)
    }

    override fun onDestroy() {
        torch.off()
        super.onDestroy()
    }
}

private const val PAYPAL_PACKAGE = "com.paypal.android.p2pmobile"

class TorchController(activity: Activity) {
    private val manager = activity.getSystemService(CameraManager::class.java)
    private val cameraId = manager.cameraIdList.firstOrNull { id ->
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    val available: Boolean = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) && cameraId != null
    private var enabled = false
    fun toggle() { val id = requireNotNull(cameraId); enabled = !enabled; manager.setTorchMode(id, enabled) }
    fun off() { cameraId?.let { runCatching { manager.setTorchMode(it, false) } }; enabled = false }
}
