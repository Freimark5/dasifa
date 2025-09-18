package com.dasifa

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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.documentfile.provider.DocumentFile
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import java.text.DecimalFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion

class MainActivity : AppCompatActivity() {

	private var usbStickUri: Uri? = null
	private lateinit var usbManager: UsbManager
	private lateinit var storageManager: StorageManager
	private var usbReceiver: BroadcastReceiver? = null
	private var dasifaFolder: DocumentFile? = null
	private lateinit var searchButton: Button
	private lateinit var ejectButton: Button
	private val tag = "DEBUG_DASIFA"
	private lateinit var internalLegend: TextView
	private lateinit var usbLegend: TextView
	private lateinit var darkModeButton: Button

	override fun onCreate(savedInstanceState: Bundle?) {
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
		internalLegend = findViewById(R.id.internal_legend)
		usbLegend = findViewById(R.id.usb_legend)
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

	private fun setupDarkModeToggle() {
		darkModeButton.text =
			if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
				getString(R.string.sun_icon)
			} else {
				getString(R.string.moon_icon)
			}
		darkModeButton.setOnClickListener {
			if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
			} else {
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
			}
			recreate()
		}
	}

	private fun checkUsbConnected() {
		Log.d(tag, "checkUsbConnected aufgerufen")
		val attachedDevices = usbManager.deviceList
		if (attachedDevices.isNotEmpty() && usbStickUri != null) {
			updateUsbStorageChart()
			checkAndCreateDaSifAFolder()
		} else {
			findViewById<PieChart>(R.id.usb_storage_chart).visibility = View.GONE
			findViewById<TextView>(R.id.usb_label).visibility = View.GONE
			usbLegend.visibility = View.GONE
			ejectButton.visibility = View.GONE
			findViewById<TextView>(R.id.no_usb_message).visibility = View.VISIBLE
		}
	}

	private fun registerUsbReceiver() {
		usbReceiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context, intent: Intent) {
				Log.d(tag, "USB-Receiver getriggert: Action = ${intent.action}")
				when (intent.action) {
					UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
						Toast.makeText(context, R.string.usb_connected_toast, Toast.LENGTH_SHORT)
							.show()
						checkUsbConnected()
					}

					UsbManager.ACTION_USB_DEVICE_DETACHED -> {
						Toast.makeText(context, R.string.usb_disconnected_toast, Toast.LENGTH_SHORT)
							.show()
						unmountUsb()
					}

					"com.dasifa.USB_PERMISSION" -> {
						if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
							updateUsbStorageChart()
						}
					}
				}
			}
		}
		val filter = IntentFilter().apply {
			addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
			addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
			addAction("com.dasifa.USB_PERMISSION")
		}

// Registriert den USB-Broadcast-Receiver mit Sicherheitsflag
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			registerReceiver(usbReceiver, IntentFilter("com.dasifa.USB_PERMISSION"), RECEIVER_NOT_EXPORTED)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		if (usbReceiver != null) {
			unregisterReceiver(usbReceiver)
			Log.d(tag, "USB-Receiver unregistriert")
		}
	}

	private fun unmountUsb() {
		Log.d(tag, "unmountUsb aufgerufen")
		try {
			usbStickUri?.let { uri ->
				contentResolver.releasePersistableUriPermission(
					uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				)
				storageManager.storageVolumes.firstOrNull { it.isRemovable }?.let { volume ->
					try {
						val uuid = volume.uuid
						if (uuid != null) {
							storageManager.javaClass.getMethod("unmount", String::class.java)
								.invoke(storageManager, uuid)
							Log.d(tag, "Volume $uuid erfolgreich ausgehängt")
						}
					} catch (e: Exception) {
						Log.e(tag, "Fehler beim Auswerfen des Volumes: ${e.message}")
					}
				}
			}
			usbStickUri = null
			dasifaFolder = null
			findViewById<PieChart>(R.id.usb_storage_chart).visibility = View.GONE
			findViewById<TextView>(R.id.usb_label).visibility = View.GONE
			usbLegend.visibility = View.GONE
			ejectButton.visibility = View.GONE
			findViewById<TextView>(R.id.no_usb_message).visibility = View.VISIBLE
			Toast.makeText(this, R.string.usb_eject_wait_toast, Toast.LENGTH_LONG).show()
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Auswerfen: ${e.message}")
			Toast.makeText(this, R.string.usb_eject_error, Toast.LENGTH_LONG).show()
		}
	}

	private fun updateInternalStorageChart() {
		Log.d(tag, "updateInternalStorageChart aufgerufen")
		val chart = findViewById<PieChart>(R.id.internal_storage_chart)
		val totalSpace = getTotalInternalStorage()
		val usedSpace = getUsedInternalStorage()

		if (totalSpace > 0) {
			val usedPercent = (usedSpace.toFloat() / totalSpace.toFloat()) * 100f
			val freePercent = 100f - usedPercent
			val df = DecimalFormat("#.##")

			val entries = mutableListOf<PieEntry>()
			entries.add(PieEntry(usedPercent)) // Belegt zuerst (Gelb, 12 Uhr)
			entries.add(PieEntry(freePercent)) // Frei danach (Grün)

			val dataSet = PieDataSet(entries, "")
			dataSet.colors = listOf("#FFC107".toColorInt(), "#4CAF50".toColorInt()) // Gelb, Grün
			dataSet.setDrawValues(false)
			val pieData = PieData(dataSet)
			chart.data = pieData
			chart.setRotationAngle(0f) // Start bei 12 Uhr
			chart.description.isEnabled = false
			chart.isRotationEnabled = false
			chart.isDrawHoleEnabled = false
			chart.holeRadius = 0f
			chart.setTransparentCircleAlpha(0)
			chart.legend.isEnabled = false
			chart.invalidate()

			val legendText = "Frei ${df.format(freePercent)}% Belegt ${df.format(usedPercent)}%"
			val spannable = android.text.SpannableString(legendText)
			val belegtIndex = legendText.indexOf("Belegt")
			spannable.setSpan(
				android.text.style.ForegroundColorSpan("#4CAF50".toColorInt()),
				0,
				belegtIndex,
				android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			spannable.setSpan(
				android.text.style.ForegroundColorSpan("#FFC107".toColorInt()),
				belegtIndex,
				legendText.length,
				android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			internalLegend.text = spannable
			internalLegend.visibility = View.VISIBLE
		} else {
			internalLegend.text = getString(R.string.storage_error)
		}
	}

	private fun updateUsbStorageChart() {
		Log.d(tag, "updateUsbStorageChart aufgerufen")
		val chart = findViewById<PieChart>(R.id.usb_storage_chart)
		val totalSpace = getTotalUsbStorage()
		val freeSpace = getFreeUsbStorage()

		if (totalSpace > 0) {
			val usedSpace = totalSpace - freeSpace
			val usedPercent = (usedSpace.toFloat() / totalSpace.toFloat()) * 100f
			val freePercent = 100f - usedPercent
			val df = DecimalFormat("#.##")

			val entries = mutableListOf<PieEntry>()
			entries.add(PieEntry(usedPercent)) // Belegt zuerst (Gelb, 12 Uhr)
			entries.add(PieEntry(freePercent)) // Frei danach (Grün)

			val dataSet = PieDataSet(entries, "")
			dataSet.colors = listOf("#FFC107".toColorInt(), "#4CAF50".toColorInt()) // Gelb, Grün
			dataSet.setDrawValues(false)
			val pieData = PieData(dataSet)
			chart.data = pieData
			chart.setRotationAngle(0f) // Start bei 12 Uhr
			chart.description.isEnabled = false
			chart.isRotationEnabled = false
			chart.isDrawHoleEnabled = false
			chart.holeRadius = 0f
			chart.setTransparentCircleAlpha(0)
			chart.legend.isEnabled = false
			chart.visibility = View.VISIBLE
			chart.invalidate()

			val legendText = "Frei ${df.format(freePercent)}% Belegt ${df.format(usedPercent)}%"
			val spannable = android.text.SpannableString(legendText)
			val belegtIndex = legendText.indexOf("Belegt")
			spannable.setSpan(
				android.text.style.ForegroundColorSpan("#4CAF50".toColorInt()),
				0,
				belegtIndex,
				android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			spannable.setSpan(
				android.text.style.ForegroundColorSpan("#FFC107".toColorInt()),
				belegtIndex,
				legendText.length,
				android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			usbLegend.text = spannable
			usbLegend.visibility = View.VISIBLE

			findViewById<TextView>(R.id.usb_label).visibility = View.VISIBLE
			findViewById<TextView>(R.id.no_usb_message).visibility = View.GONE
			searchButton.visibility = View.VISIBLE
			ejectButton.visibility = View.VISIBLE
		} else {
			Log.e(tag, "Kein USB-Speicher verfügbar oder Zugriff fehlgeschlagen")
			Toast.makeText(this, R.string.usb_storage_error, Toast.LENGTH_LONG).show()
			usbLegend.text = getString(R.string.storage_error)
		}
	}

	private fun getTotalInternalStorage(): Long {
		return try {
			val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
			stat.blockCountLong * stat.blockSizeLong
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des internen Speichers: ${e.message}")
			0L
		}
	}

	private fun getUsedInternalStorage(): Long {
		return try {
			val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
			(stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des belegten Speichers: ${e.message}")
			0L
		}
	}

	private fun getTotalUsbStorage(): Long {
		return try {
			usbStickUri?.let { uri ->
				val root = DocumentFile.fromTreeUri(this, uri)
				if (root != null && root.isDirectory) {
					val stat = android.os.StatFs(root.uri.path ?: return 0L)
					stat.blockCountLong * stat.blockSizeLong
				} else {
					0L
				}
			} ?: 0L
		} catch (e: Exception) {
			Log.e(tag, "Fehler beim Abrufen des USB-Speichers: ${e.message}")
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
				Toast.makeText(this, R.string.dasifa_folder_creation_failed, Toast.LENGTH_LONG)
					.show()
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
				updateUsbStorageChart()
				checkAndCreateDaSifAFolder()
			} else {
				Toast.makeText(this, R.string.usb_selection_failed, Toast.LENGTH_LONG).show()
			}
		}
}