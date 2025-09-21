package com.dasifa

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.documentfile.provider.DocumentFile
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {
	private var usbStickUri: Uri? = null
	private lateinit var usbManager: UsbManager
	private lateinit var storageManager: StorageManager
	private lateinit var usbReceiver: BroadcastReceiver
	private var dasifaFolder: DocumentFile? = null
	private lateinit var searchButton: Button
	private lateinit var ejectButton: Button
	private lateinit var internalChart: PieChart
	private lateinit var usbChart: PieChart
	private lateinit var internalLegend: TextView
	private lateinit var usbLegend: TextView
	private lateinit var internalSize: TextView
	private lateinit var usbSize: TextView
	private lateinit var internalLabel: TextView
	private lateinit var usbLabel: TextView
	private lateinit var noUsbMessage: TextView
	private lateinit var darkModeButton: Button
	private val tag = "DEBUG_DASIFA"

	override fun onCreate(savedInstanceState: Bundle?) {
		window.setFlags(
			WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
			WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
		)
		if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
			setTheme(R.style.Base_Theme_DaSifA_Dark)
		} else {
			setTheme(R.style.Base_Theme_DaSifA)
		}
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		usbManager = getSystemService(USB_SERVICE) as UsbManager
		storageManager = getSystemService(STORAGE_SERVICE) as StorageManager
		searchButton = findViewById(R.id.search_button)
		ejectButton = findViewById(R.id.eject_button)
		internalChart = findViewById(R.id.internal_chart)
		usbChart = findViewById(R.id.usb_chart)
		internalLegend = findViewById(R.id.internal_legend)
		usbLegend = findViewById(R.id.usb_legend)
		internalSize = findViewById(R.id.internal_size)
		usbSize = findViewById(R.id.usb_size)
		internalLabel = findViewById(R.id.internal_label)
		usbLabel = findViewById(R.id.usb_label)
		noUsbMessage = findViewById(R.id.no_usb_message)
		darkModeButton = findViewById(R.id.dark_mode_button)

		registerUsbReceiver()
		setupDarkModeToggle()
		updateInternalStorageChart()
		checkUsbConnected()

		searchButton.setOnClickListener {
			storageAccessLauncher.launch(null)
		}

		ejectButton.setOnClickListener {
			unmountUsb()
		}
	}

	private fun registerUsbReceiver() {
		usbReceiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context, intent: Intent) {
				Log.d(tag, "USB-Receiver getriggert: Action = ${intent.action}")
				when (intent.action) {
					UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
						Toast.makeText(context, R.string.usb_connected_toast, Toast.LENGTH_SHORT).show()
						checkUsbConnected()
					}
					UsbManager.ACTION_USB_DEVICE_DETACHED -> {
						Toast.makeText(context, R.string.usb_disconnected_toast, Toast.LENGTH_SHORT).show()
						unmountUsb()
					}
					"com.dasifa.USB_PERMISSION" -> {
						if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
							Log.d(tag, "USB-Berechtigung erteilt, scanne USB")
							updateUsbStorageChart()
							checkAndCreateDaSifAFolder()
						} else {
							Log.d(tag, "USB-Berechtigung verweigert")
							Toast.makeText(context, R.string.usb_selection_failed, Toast.LENGTH_LONG).show()
						}
					}
				}
			}
		}
		val intentFilter = IntentFilter().apply {
			addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
			addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
			addAction("com.dasifa.USB_PERMISSION")
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(usbReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
		} else {
			@Suppress("DEPRECATION")
			ContextCompat.registerReceiver(
				this@MainActivity,
				usbReceiver,
				intentFilter,
				ContextCompat.RECEIVER_NOT_EXPORTED
			)
		}
	}

	private fun setupDarkModeToggle() {
		darkModeButton.setOnClickListener {
			val currentMode = AppCompatDelegate.getDefaultNightMode()
			if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
			} else {
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
			}
			darkModeButton.text = getString(R.string.moon_icon)
			recreate()
		}
	}

	private fun updateInternalStorageChart() {
		val used = getUsedInternalStorage()
		val free = getFreeInternalStorage()
		val total = used + free
		val usedPercent = if (total > 0) (used * 100f / total) else 0f
		val freePercent = if (total > 0) (free * 100f / total) else 0f
		val df = DecimalFormat("#.##")

		val entries = listOf(
			PieEntry(usedPercent, ""),
			PieEntry(freePercent, "")
		)
		val dataSet = PieDataSet(entries, "").apply {
			colors = listOf("#FF5722".toColorInt(), "#4CAF50".toColorInt())
			setDrawValues(false)
		}
		val data = PieData(dataSet)
		internalChart.apply {
			this.data = data
			rotationAngle = 0f
			setDrawCenterText(false)
			setRotationEnabled(false)
			setHighlightPerTapEnabled(true)
			setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
				override fun onValueSelected(e: Entry?, h: Highlight?) {
					Toast.makeText(this@MainActivity, "Ausgewählt: ${(e as PieEntry).label}", Toast.LENGTH_SHORT).show()
				}
				override fun onNothingSelected() {}
			})
			description.isEnabled = false
			legend.isEnabled = false
			setNoDataText("")
			invalidate()
		}
		internalLegend.text = getString(R.string.internal_storage_label, df.format(usedPercent), df.format(freePercent))
		internalSize.text = getString(R.string.storage_size, df.format(used / 1_000_000_000.0), df.format(free / 1_000_000_000.0))
		internalLabel.text = getString(R.string.schmarty_label)
	}

	private fun updateUsbStorageChart() {
		val used = getUsedUsbStorage()
		val free = getFreeUsbStorage()
		val total = used + free
		val usedPercent = if (total > 0) (used * 100f / total) else 0f
		val freePercent = if (total > 0) (free * 100f / total) else 0f
		val df = DecimalFormat("#.##")

		val entries = listOf(
			PieEntry(usedPercent, ""),
			PieEntry(freePercent, "")
		)
		val dataSet = PieDataSet(entries, "").apply {
			colors = listOf("#FF5722".toColorInt(), "#4CAF50".toColorInt())
			setDrawValues(false)
		}
		val data = PieData(dataSet)
		usbChart.apply {
			this.data = data
			rotationAngle = 0f
			setDrawCenterText(false)
			setRotationEnabled(false)
			setHighlightPerTapEnabled(true)
			setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
				override fun onValueSelected(e: Entry?, h: Highlight?) {
					Toast.makeText(this@MainActivity, "Ausgewählt: ${(e as PieEntry).label}", Toast.LENGTH_SHORT).show()
				}
				override fun onNothingSelected() {}
			})
			description.isEnabled = false
			legend.isEnabled = false
			setNoDataText("")
			invalidate()
		}
		usbLegend.text = getString(R.string.usb_storage_label, df.format(usedPercent), df.format(freePercent))
		usbSize.text = getString(R.string.storage_size, df.format(used / 1_000_000_000.0), df.format(free / 1_000_000_000.0))
		usbLabel.text = getString(R.string.usb_label)
		ejectButton.visibility = if (total > 0) View.VISIBLE else View.GONE
		noUsbMessage.visibility = if (total > 0) View.GONE else View.VISIBLE
	}

	private fun checkUsbConnected() {
		val devices = usbManager.deviceList
		Log.d(tag, "checkUsbConnected: ${devices.size} Geräte gefunden")
		if (devices.isNotEmpty()) {
			val device = devices.values.first()
			if (!usbManager.hasPermission(device)) {
				Log.d(tag, "Keine Berechtigung für USB, fordere an")
				usbManager.requestPermission(device, PendingIntent.getBroadcast(this, 0, Intent("com.dasifa.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE))
			} else {
				Log.d(tag, "Berechtigung vorhanden, scanne USB")
				updateUsbStorageChart()
				checkAndCreateDaSifAFolder()
			}
		} else {
			Log.d(tag, "Kein USB-Gerät angeschlossen")
			ejectButton.visibility = View.GONE
			noUsbMessage.visibility = View.VISIBLE
		}
	}

	private fun unmountUsb() {
		usbStickUri = null
		dasifaFolder = null
		updateUsbStorageChart()
	}

	private fun getUsedInternalStorage(): Long {
		return try {
			val stat = android.os.StatFs(Environment.getDataDirectory().path)
			(stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des belegten internen Speichers: ${e.message}")
			0L
		}
	}

	private fun getFreeInternalStorage(): Long {
		return try {
			val stat = android.os.StatFs(Environment.getDataDirectory().path)
			stat.availableBlocksLong * stat.blockSizeLong
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des freien internen Speichers: ${e.message}")
			0L
		}
	}

	private fun getUsedUsbStorage(): Long {
		return try {
			usbStickUri?.let { uri ->
				val root = DocumentFile.fromTreeUri(this, uri)
				if (root != null && root.isDirectory) {
					val stat = android.os.StatFs(root.uri.path ?: return 0L)
					(stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
				} else {
					0L
				}
			} ?: 0L
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des belegten USB-Speichers: ${e.message}")
			0L
		}
	}

	private fun getFreeUsbStorage(): Long {
		return try {
			usbStickUri?.let { uri ->
				val root = DocumentFile.fromTreeUri(this, uri)
				if (root != null && root.isDirectory) {
					val stat = android.os.StatFs(root.uri.path ?: return 0L)
					stat.availableBlocksLong * stat.blockSizeLong
				} else {
					0L
				}
			} ?: 0L
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des freien USB-Speichers: ${e.message}")
			0L
		}
	}

	private fun formatSize(size: Long): String {
		val df = DecimalFormat("#.##")
		return when {
			size >= 1_000_000_000 -> "${df.format(size / 1_000_000_000.0)} GB"
			size >= 1_000_000 -> "${df.format(size / 1_000_000.0)} MB"
			size >= 1_000 -> "${df.format(size / 1_000.0)} KB"
			else -> "$size B"
		}
	}

	private fun checkAndCreateDaSifAFolder() {
		Log.d(tag, "checkAndCreateDaSifAFolder aufgerufen")
		usbStickUri?.let { uri ->
			val root = DocumentFile.fromTreeUri(this, uri)
			if (root != null && root.isDirectory) {
				dasifaFolder = root.findFile("DaSifA")
				if (dasifaFolder == null) {
					dasifaFolder = root.createDirectory("DaSifA")
					dasifaFolder?.createDirectory("Images")
					dasifaFolder?.createDirectory("Videos")
					dasifaFolder?.createDirectory("Downloads")
					Toast.makeText(this, R.string.dasifa_folder_created, Toast.LENGTH_SHORT).show()
				} else {
					Toast.makeText(this, R.string.dasifa_folder_exists, Toast.LENGTH_SHORT).show()
				}
			} else {
				Toast.makeText(this, R.string.dasifa_folder_creation_failed, Toast.LENGTH_LONG).show()
			}
		}
	}

	private val storageAccessLauncher =
		registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
			if (uri != null) {
				usbStickUri = uri
				contentResolver.takePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				)
				Toast.makeText(this, R.string.usb_selected_toast, Toast.LENGTH_LONG).show()
				Log.d(tag, "USB ausgewählt, scanne USB")
				updateUsbStorageChart()
				checkAndCreateDaSifAFolder()
			} else {
				Log.d(tag, "USB-Auswahl abgebrochen")
				Toast.makeText(this, R.string.usb_selection_failed, Toast.LENGTH_LONG).show()
			}
		}

	override fun onDestroy() {
		super.onDestroy()
		if (::usbReceiver.isInitialized) {
			unregisterReceiver(usbReceiver)
		}
	}
}