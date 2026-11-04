package com.customdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    private val vm: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { DialerTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Otras apps abren el marcador con un número (tel:...). */
    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_DIAL || i?.action == Intent.ACTION_VIEW) {
            i.data?.schemeSpecificPart?.let { vm.dial = it }
            vm.keypadOpen = true
        }
    }
}

private fun requiredPerms(): Array<String> {
    val l = mutableListOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    )
    if (Build.VERSION.SDK_INT >= 33) l += Manifest.permission.POST_NOTIFICATIONS
    return l.toTypedArray()
}

private fun hasCorePerms(ctx: Context) = listOf(
    Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE
).all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private fun isDefaultDialer(ctx: Context): Boolean =
    ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

@Composable
fun AppRoot(vm: DialerViewModel) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf("main") }
    var granted by remember { mutableStateOf(hasCorePerms(ctx)) }
    var isDefault by remember { mutableStateOf(isDefaultDialer(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasCorePerms(ctx); vm.refresh() }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDefault = isDefaultDialer(ctx) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasCorePerms(ctx)
        isDefault = isDefaultDialer(ctx)
        vm.refresh()
    }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(requiredPerms()) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        if (screen == "codes") {
            BackHandler { screen = "main" }
            CodesScreen(vm) { screen = "main" }
        } else {
            MainScreen(
                vm = vm,
                granted = granted,
                isDefault = isDefault,
                onAskPerms = { permLauncher.launch(requiredPerms()) },
                onAskRole = {
                    val rm = ctx.getSystemService(RoleManager::class.java)
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                },
                onOpenCodes = { screen = "codes" }
            )
        }
        vm.revealed?.let { RevealDialog(it) { vm.revealed = null } }
    }
}

@Composable
fun MainScreen(
    vm: DialerViewModel,
    granted: Boolean,
    isDefault: Boolean,
    onAskPerms: () -> Unit,
    onAskRole: () -> Unit,
    onOpenCodes: () -> Unit
) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val recents by vm.recents.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    var menu by remember { mutableStateOf(false) }
    val q = query.trim()
    val shownTab = if (q.isNotEmpty()) 1 else tab

    BackHandler(vm.keypadOpen) { vm.keypadOpen = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF5D9C3C)),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.profile_initial), color = Color.White, fontSize = 22.sp) }
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    placeholder = { Text("Buscar contactos o números", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { Icon(Icons.Default.Mic, null) },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Códigos secretos") },
                            onClick = { menu = false; onOpenCodes() }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar recientes") },
                            onClick = { menu = false; vm.clearRecents() }
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = shownTab, containerColor = Color.Transparent) {
                Tab(selected = shownTab == 0, onClick = { tab = 0; query = "" },
                    icon = { Icon(Icons.Default.History, "Recientes") })
                Tab(selected = shownTab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Contacts, "Contactos") })
            }
            if (!isDefault) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Úsala como tu app de teléfono para recibir y hacer llamadas.", Modifier.weight(1f), fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = onAskRole) { Text("Activar") }
                }
            }
            if (!granted) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Para mostrar tus contactos y llamadas necesito permiso.",
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.size(16.dp))
                    Button(onClick = onAskPerms) { Text("Dar permisos") }
                }
            } else if (shownTab == 1) {
                ContactsList(vm, contacts, q)
            } else {
                RecentsList(vm, recents, contacts)
            }
        }

        FloatingActionButton(
            onClick = { vm.keypadOpen = true },
            shape = CircleShape,
            containerColor = Color(0xFF35B8F0),
            contentColor = Color(0xFF0B2B3A),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(72.dp)
        ) { Icon(Icons.Default.Dialpad, "Abrir teclado", Modifier.size(34.dp)) }

        AnimatedVisibility(
            visible = vm.keypadOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val dd = vm.dial.filter { it.isDigit() }
            val hint = if (dd.length >= 3) {
                contacts.firstOrNull { it.number.filter { c -> c.isDigit() }.contains(dd) }
                    ?.let { "${it.name} · ${it.number}" }
            } else null
            Keypad(
                dial = vm.dial,
                hint = hint,
                onKey = vm::press,
                onBack = vm::backspace,
                onClear = { vm.dial = "" },
                onCall = vm::callFromKeypad,
                onHide = { vm.keypadOpen = false }
            )
        }
    }
}

@Composable
private fun ContactsList(vm: DialerViewModel, contacts: List<Contact>, q: String) {
    val ctx = LocalContext.current
    val qd = q.filter { it.isDigit() }
    val list = if (q.isEmpty()) contacts else contacts.filter {
        it.name.contains(q, ignoreCase = true) ||
            (qd.isNotEmpty() && it.number.filter { c -> c.isDigit() }.contains(qd))
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
        if (q.isEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(contacts.filter { it.starred }) { c ->
                        FavCard(c.name, c.photoUri) { vm.call(c.number) }
                    }
                    item {
                        Column(
                            Modifier.width(88.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    try {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                                        )
                                    } catch (_: ActivityNotFoundException) {
                                    }
                                }
                                .padding(vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                            Text("Añadir", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                    }
                }
            }
        } else if (Regex("^\\+?[\\d\\s]{3,}$").matches(q) && list.none { it.number.filter { c -> c.isDigit() } == qd }) {
            item {
                ContactRow("Llamar a $q", "Número nuevo", null, null) { vm.call(q.replace(" ", "")) }
            }
        }
        items(list) { c ->
            ContactRow(c.name, c.number, c.photoUri, c.name) { vm.call(c.number) }
        }
        if (q.isNotEmpty() && list.isEmpty()) {
            item {
                Text(
                    "Sin resultados. Revisa el nombre o el número.",
                    Modifier.fillMaxWidth().padding(40.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FavCard(name: String, photo: String?, onClick: () -> Unit) {
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(name, photo, 64.dp)
        Spacer(Modifier.size(8.dp))
        Text(name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentsList(vm: DialerViewModel, recents: List<CallEntry>, contacts: List<Contact>) {
    if (recents.isEmpty()) {
        Text(
            "Aún no hay llamadas. Abre el teclado para marcar un número.",
            Modifier.fillMaxWidth().padding(40.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val now = System.currentTimeMillis()
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
        items(recents) { e ->
            val contact = contacts.firstOrNull { Repo.sameNumber(it.number, e.number) }
            val name = contact?.name ?: e.name
            val kind = when (e.type) {
                CallLog.Calls.INCOMING_TYPE -> "Entrante"
                CallLog.Calls.OUTGOING_TYPE -> "Saliente"
                CallLog.Calls.MISSED_TYPE -> "Perdida"
                CallLog.Calls.REJECTED_TYPE -> "Rechazada"
                CallLog.Calls.BLOCKED_TYPE -> "Bloqueada"
                else -> "Llamada"
            }
            val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
            val whenText = DateUtils.getRelativeTimeSpanString(e.date, now, DateUtils.MINUTE_IN_MILLIS)
            ContactRow(
                title = name ?: e.number.ifBlank { "Número privado" },
                subtitle = "$kind · $whenText",
                photo = contact?.photoUri,
                avatarName = name,
                titleColor = if (missed) Color(0xFFFF453A) else Color.Unspecified
            ) { if (e.number.isNotBlank()) vm.call(e.number) }
        }
    }
}
