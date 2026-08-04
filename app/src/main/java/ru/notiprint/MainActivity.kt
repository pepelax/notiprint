package ru.notiprint

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.notiprint.data.AppDatabase
import ru.notiprint.data.BlockedSender
import ru.notiprint.data.NotificationKind
import ru.notiprint.data.PrintJob
import ru.notiprint.data.PrintStatus
import ru.notiprint.data.SenderIdentifier
import ru.notiprint.notifications.NotificationListenerController
import ru.notiprint.printer.BluetoothPermissions
import ru.notiprint.printer.BluetoothPrinterClient
import ru.notiprint.printer.NotificationBitmapRenderer
import ru.notiprint.printer.PrinterConnectionGate
import ru.notiprint.printer.PrinterDiagnosticBitmapRenderer
import ru.notiprint.settings.AppPreferences
import ru.notiprint.settings.AppSettings
import ru.notiprint.settings.NightMode
import ru.notiprint.service.NotiPrintForegroundService
import ru.notiprint.work.PrintScheduler

class MainActivity : ComponentActivity() {
    private lateinit var preferences: AppPreferences
    private var permissionRevision by mutableIntStateOf(0)

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRevision++
        if (granted) PrintScheduler.enqueueNow(applicationContext)
        if (granted) startForegroundServiceIfPossible()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRevision++
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRevision++
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRevision++
    }

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionRevision++
    }

    private val blockedContactPicker = registerForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { contactUri ->
        contactUri?.let(::addBlockedContact)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(applicationContext)
        PrintScheduler.enqueueNow(applicationContext)
        startForegroundServiceIfPossible()
        requestNotificationPermissionIfNeeded()
        requestSmsPermissionIfNeeded()
        requestContactsPermissionIfNeeded()
        requestCallLogPermissionIfNeeded()
        NotificationListenerController.requestRebindIfEnabled(applicationContext)

        setContent {
            MaterialTheme {
                NotiPrintScreen(
                    preferences = preferences,
                    permissionRevision = permissionRevision,
                    requestBluetoothPermission = ::requestBluetoothPermission,
                    openNotificationAccess = ::openNotificationAccess,
                    openAutoStartSettings = ::openAutoStartSettings,
                    pickBlockedContact = { blockedContactPicker.launch(null) },
                    addBlockedSender = ::addBlockedSender,
                    removeBlockedSender = ::removeBlockedSender,
                    showTimePicker = ::showTimePicker,
                    printTest = ::printTest,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRevision++
        NotificationListenerController.requestRebindIfEnabled(applicationContext)
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun startForegroundServiceIfPossible() {
        if (BluetoothPermissions.hasConnectPermission(this)) {
            NotiPrintForegroundService.start(applicationContext)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        if (
            preferences.snapshot().smsEnabled &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun requestContactsPermissionIfNeeded() {
        if (
            preferences.snapshot().smsEnabled &&
            checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun requestCallLogPermissionIfNeeded() {
        if (
            preferences.snapshot().missedCallsEnabled &&
            checkSelfPermission(Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun openNotificationAccess() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAutoStartSettings() {
        val huaweiAppLaunch = Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
        )
        val applicationDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(
            if (huaweiAppLaunch.resolveActivity(packageManager) != null) huaweiAppLaunch else applicationDetails,
        )
    }

    private fun addBlockedSender(rawValue: String) {
        val label = rawValue.trim()
        val normalized = SenderIdentifier.normalize(label)
        if (normalized == null) {
            toast("Введите номер или имя отправителя")
            return
        }

        lifecycleScope.launch {
            val added = withContext(Dispatchers.IO) {
                AppDatabase.get(applicationContext).blockedSenderDao().insertAll(
                    listOf(BlockedSender(normalized = normalized, label = label)),
                ).single() != -1L
            }
            toast(if (added) "Добавлено в чёрный список" else "Такая запись уже есть в списке")
        }
    }

    private fun addBlockedContact(contactUri: Uri) {
        lifecycleScope.launch {
            val contact = withContext(Dispatchers.IO) {
                readContactWithPhoneNumbers(contactUri)
            }
            if (contact == null) {
                toast("У выбранного контакта нет доступных телефонных номеров")
                return@launch
            }

            val entries = contact.phoneNumbers.mapNotNull { phoneNumber ->
                SenderIdentifier.normalize(phoneNumber)?.let { normalized ->
                    BlockedSender(
                        normalized = normalized,
                        label = "${contact.name} · $phoneNumber",
                    )
                }
            }.distinctBy(BlockedSender::normalized)
            val addedCount = withContext(Dispatchers.IO) {
                AppDatabase.get(applicationContext).blockedSenderDao().insertAll(entries).count { it != -1L }
            }
            toast(
                when {
                    addedCount == 0 -> "Все номера контакта уже есть в чёрном списке"
                    addedCount == 1 -> "Один номер добавлен в чёрный список"
                    else -> "$addedCount номера добавлены в чёрный список"
                },
            )
        }
    }

    private fun removeBlockedSender(entry: BlockedSender) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.get(applicationContext).blockedSenderDao().delete(entry)
            }
        }
    }

    private fun readContactWithPhoneNumbers(contactUri: Uri): ContactWithPhoneNumbers? = runCatching {
        val resolver = contentResolver
        val contactId = ContentUris.parseId(contactUri)
        val name = resolver.query(
            contactUri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.trim().orEmpty() else ""
        }.orEmpty().ifBlank { "Контакт" }
        val numbers = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.trim()?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty()
        ContactWithPhoneNumbers(name, numbers)
    }.getOrNull()?.takeIf { it.phoneNumbers.isNotEmpty() }

    private fun showTimePicker(currentMinutes: Int, onTimeSelected: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onTimeSelected(hour * 60 + minute) },
            currentMinutes / 60,
            currentMinutes % 60,
            true,
        ).show()
    }

    private fun printTest() {
        val settings = preferences.snapshot()
        if (!BluetoothPermissions.hasConnectPermission(this)) {
            requestBluetoothPermission()
            return
        }
        val address = settings.printerAddress
        if (address.isNullOrBlank()) {
            toast("Сначала выберите принтер")
            return
        }

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val job = PrintJob(
                        notificationKey = "test-${System.currentTimeMillis()}",
                        kind = NotificationKind.CALENDAR,
                        title = "Проверка печати",
                        message = "Кириллица, крупный текст и растровая печать работают.",
                        packageName = packageName,
                        postedAt = System.currentTimeMillis(),
                    )
                    val bitmap = NotificationBitmapRenderer.render(job)
                    val diagnosticBitmap = PrinterDiagnosticBitmapRenderer.render()
                    try {
                        PrinterConnectionGate.withLock {
                            BluetoothPrinterClient(applicationContext).use { printer ->
                                printer.connect(address)
                                // Keep both parts on one receipt, but use a fresh
                                // GS v 0 command at the boundary. Some compact
                                // printers recover from this more reliably than
                                // from one very tall raster image.
                                printer.printRaster(bitmap)
                                printer.print(diagnosticBitmap, feedLines = 8)
                                // Let the printer consume its long test-page
                                // buffer before its SPP connection is released.
                                Thread.sleep(TEST_PRINTER_DRAIN_DELAY_MS)
                            }
                        }
                    } finally {
                        bitmap.recycle()
                        diagnosticBitmap.recycle()
                    }
                }
            }
            toast(
                if (result.isSuccess) "Тест отправлен на принтер"
                else "Не удалось напечатать: ${result.exceptionOrNull()?.message ?: "неизвестная ошибка"}",
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }


    private companion object {
        const val TEST_PRINTER_DRAIN_DELAY_MS = 3_000L
    }
}

private data class PairedPrinter(val name: String, val address: String)

private data class ContactWithPhoneNumbers(
    val name: String,
    val phoneNumbers: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotiPrintScreen(
    preferences: AppPreferences,
    permissionRevision: Int,
    requestBluetoothPermission: () -> Unit,
    openNotificationAccess: () -> Unit,
    openAutoStartSettings: () -> Unit,
    pickBlockedContact: () -> Unit,
    addBlockedSender: (String) -> Unit,
    removeBlockedSender: (BlockedSender) -> Unit,
    showTimePicker: (Int, (Int) -> Unit) -> Unit,
    printTest: () -> Unit,
) {
    val context = LocalContext.current
    val database = AppDatabase.get(context)
    val jobs by database.printJobDao().observeRecent(20)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val blockedSenders by database.blockedSenderDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var settings by remember { mutableStateOf(preferences.snapshot()) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showManualBlockedSenderDialog by remember { mutableStateOf(false) }
    var manualBlockedSender by remember { mutableStateOf("") }
    var pairedPrinters by remember { mutableStateOf(emptyList<PairedPrinter>()) }
    val bluetoothAllowed = remember(permissionRevision) {
        BluetoothPermissions.hasConnectPermission(context)
    }
    val notificationAccessAllowed = remember(permissionRevision) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun refreshSettings() {
        settings = preferences.snapshot()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NotiPrint") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(1.dp))
            StatusCard(
                settings = settings,
                waitingCount = jobs.count { it.status != PrintStatus.PRINTED },
                notificationAccessAllowed = notificationAccessAllowed,
                bluetoothAllowed = bluetoothAllowed,
            )

            SectionTitle("Подключение")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val printerName = settings.printerName ?: "Принтер не выбран"
                    Text(printerName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.printerAddress == null) {
                            "Сначала сопрягите принтер в настройках Bluetooth телефона."
                        } else {
                            settings.printerAddress.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                            if (!bluetoothAllowed) {
                                requestBluetoothPermission()
                            } else {
                                pairedPrinters = getPairedPrinters(context)
                                showPrinterDialog = true
                            }
                            },
                        ) {
                            Text("Выбрать принтер")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !settings.printerAddress.isNullOrBlank(),
                            onClick = printTest,
                        ) {
                            Text("Тестовая печать")
                        }
                    }
                }
            }

            SectionTitle("Доступ")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (notificationAccessAllowed) "Доступ к уведомлениям выдан"
                        else "Нужно разрешить чтение уведомлений",
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = openNotificationAccess,
                    ) {
                        Text(if (notificationAccessAllowed) "Открыть настройки доступа" else "Разрешить доступ")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = openAutoStartSettings,
                    ) {
                        Text("Настроить автозапуск")
                    }
                    Text(
                        "Разрешите автозапуск и работу в фоновом режиме в настройках телефона.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionTitle("Что печатать")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingSwitch("СМС", settings.smsEnabled) {
                        preferences.setSmsEnabled(it)
                        refreshSettings()
                    }
                    if (settings.smsEnabled) {
                        HorizontalDivider()
                        SettingSwitch("Игнорировать СМС с незнакомых номеров", settings.ignoreSmsFromUnknownNumbers) {
                            preferences.setIgnoreSmsFromUnknownNumbers(it)
                            refreshSettings()
                        }
                    }
                    HorizontalDivider()
                    SettingSwitch("Пропущенные звонки", settings.missedCallsEnabled) {
                        preferences.setMissedCallsEnabled(it)
                        refreshSettings()
                    }
                    HorizontalDivider()
                    SettingSwitch("Уведомления календаря", settings.calendarEnabled) {
                        preferences.setCalendarEnabled(it)
                        refreshSettings()
                    }
                }
            }

            SectionTitle("Чёрный список")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Не печатать СМС и пропущенные звонки от указанных номеров.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = pickBlockedContact,
                    ) {
                        Text("Выбрать контакт")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showManualBlockedSenderDialog = true },
                    ) {
                        Text("Добавить номер вручную")
                    }
                    if (blockedSenders.isEmpty()) {
                        Text(
                            "Список пуст.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        HorizontalDivider()
                        blockedSenders.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val contactName = entry.label.substringBefore(" · ", missingDelimiterValue = "")
                                    if (contactName.isNotEmpty()) {
                                        Text(contactName, style = MaterialTheme.typography.bodyMedium)
                                        Text(entry.normalized, style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                TextButton(onClick = { removeBlockedSender(entry) }) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("Ночной режим")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingSwitch("Не печатать ночью", settings.nightModeEnabled) {
                        preferences.setNightModeEnabled(it)
                        refreshSettings()
                    }
                    if (settings.nightModeEnabled) {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Копить уведомления")
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showTimePicker(settings.nightStartMinutes) {
                                        preferences.setNightStartMinutes(it)
                                        refreshSettings()
                                    }
                                },
                            ) {
                                Text("Начало: ${NightMode.format(settings.nightStartMinutes)}")
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showTimePicker(settings.nightEndMinutes) {
                                        preferences.setNightEndMinutes(it)
                                        refreshSettings()
                                    }
                                },
                            ) {
                                Text("Окончание: ${NightMode.format(settings.nightEndMinutes)}")
                            }
                        }
                    }
                }
            }

            SectionTitle("Последние уведомления")
            Card(modifier = Modifier.fillMaxWidth()) {
                if (jobs.isEmpty()) {
                    Text("Пока нет сохранённых уведомлений.", Modifier.padding(16.dp))
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        jobs.forEachIndexed { index, job ->
                            if (index > 0) HorizontalDivider()
                            Column(Modifier.padding(vertical = 10.dp)) {
                                Text("${job.kind.title} · ${jobStatusTitle(job.status)}", style = MaterialTheme.typography.labelMedium)
                                Text(job.title.ifBlank { "Без заголовка" }, fontWeight = FontWeight.SemiBold)
                                if (job.message.isNotBlank()) {
                                    Text(job.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                if (job.lastError != null) {
                                    Text(job.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showManualBlockedSenderDialog) {
        AlertDialog(
            onDismissRequest = { showManualBlockedSenderDialog = false },
            title = { Text("Добавить в чёрный список") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = manualBlockedSender,
                    onValueChange = { manualBlockedSender = it },
                    label = { Text("Номер или имя отправителя") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = manualBlockedSender.isNotBlank(),
                    onClick = {
                        addBlockedSender(manualBlockedSender)
                        manualBlockedSender = ""
                        showManualBlockedSenderDialog = false
                    },
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualBlockedSenderDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = { Text("Сопряжённые устройства") },
            text = {
                if (pairedPrinters.isEmpty()) {
                    Text("Сопряжённых Bluetooth-устройств нет. Сначала добавьте принтер в настройках телефона.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pairedPrinters.forEach { printer ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    preferences.setPrinter(printer.address, printer.name)
                                    refreshSettings()
                                    showPrinterDialog = false
                                    PrintScheduler.enqueueNow(context)
                                },
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(printer.name)
                                    Text(printer.address, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterDialog = false }) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    settings: AppSettings,
    waitingCount: Int,
    notificationAccessAllowed: Boolean,
    bluetoothAllowed: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Состояние", style = MaterialTheme.typography.titleMedium)
            StatusLine("Принтер", if (settings.printerAddress == null) "не выбран" else "настроен")
            StatusLine("Bluetooth", if (bluetoothAllowed) "разрешён" else "нужно разрешение")
            StatusLine("Уведомления", if (notificationAccessAllowed) "разрешены" else "нужен доступ")
            StatusLine("Очередь", if (waitingCount == 0) "пуста" else "$waitingCount ожидают печати")
            StatusLine("Версия", BuildConfig.VERSION_NAME)
            StatusLine("Сборка", BuildConfig.BUILD_DATE)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private fun jobStatusTitle(status: PrintStatus): String = when (status) {
    PrintStatus.PENDING -> "ожидает"
    PrintStatus.PRINTING -> "печатается"
    PrintStatus.PRINTED -> "напечатано"
    PrintStatus.RETRY -> "ожидает принтер"
}

@SuppressLint("MissingPermission")
private fun getPairedPrinters(context: android.content.Context): List<PairedPrinter> {
    if (!BluetoothPermissions.hasConnectPermission(context)) return emptyList()
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
    return adapter.bondedDevices
        .map { device -> PairedPrinter(device.name ?: "Без имени", device.address) }
        .sortedBy { it.name.lowercase() }
}

@Preview(showBackground = true)
@Composable
private fun StatusCardPreview() {
    MaterialTheme {
        StatusCard(
            settings = AppSettings(null, null, true, true, true, true, 22 * 60, 8 * 60),
            waitingCount = 2,
            notificationAccessAllowed = true,
            bluetoothAllowed = true,
        )
    }
}
