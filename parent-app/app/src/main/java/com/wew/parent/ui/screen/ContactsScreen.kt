package com.wew.parent.ui.screen

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.parent.data.model.Contact
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.EmergencyRed
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import com.wew.parent.ui.viewmodel.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(deviceId: String) {
    val vm: ContactsViewModel = viewModel(factory = ContactsViewModel.factory(deviceId))
    val state by vm.uiState.collectAsState()

    if (state.showDetail && state.selectedContact != null) {
        ContactDetailScreen(
            contact = state.selectedContact!!,
            isSaving = state.isSaving,
            onSave = vm::saveContact,
            onDelete = { vm.deleteContact(it); vm.closeDetail() },
            onBack = vm::closeDetail
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A1A2E)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = vm::openNew,
                containerColor = BrandViolet,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add contact")
            }
        },
        containerColor = ParentBackground
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BrandViolet) }
            return@Scaffold
        }

        if (state.contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No contacts yet. Tap + to add one.",
                    color = Color(0xFF9999AA),
                    fontSize = 15.sp
                )
            }
            return@Scaffold
        }

        // Group by status
        val pending  = state.contacts.filter { it.status == "requested" }
        val approved = state.contacts.filter { it.status == "approved" }
        val blocked  = state.contacts.filter { it.status == "blocked" }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (pending.isNotEmpty()) {
                item {
                    SectionHeader(
                        label = "Pending Approval",
                        count = pending.size,
                        accentColor = Color(0xFFFF9800)
                    )
                }
                items(pending, key = { it.id ?: it.phone.orEmpty() }) { c ->
                    ContactRow(c, onClick = { vm.openContact(c) },
                        onApprove = { vm.setStatus(c, "approved") },
                        onBlock   = { vm.setStatus(c, "blocked") })
                }
            }

            if (approved.isNotEmpty()) {
                item { SectionHeader("Approved", approved.size, SafetyGreen) }
                items(approved, key = { it.id ?: it.phone.orEmpty() }) { c ->
                    ContactRow(c, onClick = { vm.openContact(c) })
                }
            }

            if (blocked.isNotEmpty()) {
                item { SectionHeader("Blocked", blocked.size, EmergencyRed) }
                items(blocked, key = { it.id ?: it.phone.orEmpty() }) { c ->
                    ContactRow(c, onClick = { vm.openContact(c) })
                }
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String, count: Int, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label  ($count)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF6B6B8A),
            letterSpacing = 0.6.sp
        )
    }
}

// ── Contact row ───────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    onApprove: (() -> Unit)? = null,
    onBlock: (() -> Unit)? = null
) {
    val statusColor = when (contact.status) {
        "approved"  -> SafetyGreen
        "blocked"   -> EmergencyRed
        else        -> Color(0xFFFF9800)
    }
    val statusIcon = when (contact.status) {
        "approved" -> Icons.Default.Check
        "blocked"  -> Icons.Default.Block
        else       -> Icons.Default.HourglassEmpty
    }
    val initial = contact.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandViolet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = BrandViolet)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E)
                )
                if (!contact.phone.isNullOrBlank()) {
                    Text(
                        text = contact.phone,
                        fontSize = 13.sp,
                        color = Color(0xFF9999AA)
                    )
                }
                if (!contact.relationship.isNullOrBlank()) {
                    Text(
                        text = contact.relationship,
                        fontSize = 12.sp,
                        color = Color(0xFF9999AA)
                    )
                }
            }

            // Quick-action buttons for pending
            if (contact.status == "requested" && onApprove != null && onBlock != null) {
                IconButton(onClick = onApprove) {
                    Icon(Icons.Default.Check, "Approve", tint = SafetyGreen)
                }
                IconButton(onClick = onBlock) {
                    Icon(Icons.Default.Block, "Block", tint = EmergencyRed)
                }
            } else {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = Color(0xFFF0F0F5), thickness = 1.dp)
    }
}

// ── Contact detail / edit screen ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: Contact,
    isSaving: Boolean,
    onSave: (Contact) -> Unit,
    onDelete: (Contact) -> Unit,
    onBack: () -> Unit
) {
    var firstName   by remember { mutableStateOf(contact.firstName ?: "") }
    var lastName    by remember { mutableStateOf(contact.lastName ?: "") }
    var nickname    by remember { mutableStateOf(contact.nickname ?: "") }
    var phone       by remember { mutableStateOf(contact.phone ?: "") }
    var relationship by remember { mutableStateOf(contact.relationship ?: "") }
    var birthday    by remember { mutableStateOf(contact.birthday ?: "") }
    var status      by remember { mutableStateOf(contact.status) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker   by remember { mutableStateOf(false) }

    val isNew = contact.id == null
    val canSave = firstName.isNotBlank() && phone.isNotBlank() && relationship.isNotBlank()

    // Date picker dialog
    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        birthday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete contact?") },
            text = { Text("This will remove ${contact.displayName} from the approved contacts list.") },
            confirmButton = {
                TextButton(onClick = { onDelete(contact) },
                    colors = ButtonDefaults.textButtonColors(contentColor = EmergencyRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "New Contact" else "Edit Contact",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = EmergencyRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = ParentBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar placeholder
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(BrandViolet.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = firstName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        if (firstName.isNotBlank()) {
                            Text(initial, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = BrandViolet)
                        } else {
                            Icon(Icons.Default.Person, null, tint = BrandViolet, modifier = Modifier.size(40.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Required fields card
            item {
                FieldCard {
                    ContactField("First name *", firstName, { firstName = it }, KeyboardCapitalization.Words)
                    HorizontalDivider(color = Color(0xFFF0F0F5))
                    ContactField("Last name", lastName, { lastName = it }, KeyboardCapitalization.Words)
                    HorizontalDivider(color = Color(0xFFF0F0F5))
                    ContactField("Phone number *", phone, { phone = it },
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Phone
                    )
                    HorizontalDivider(color = Color(0xFFF0F0F5))
                    ContactField("Relationship *", relationship, { relationship = it },
                        placeholder = "e.g. Mom, Friend, Coach"
                    )
                }
            }

            // Optional fields card
            item {
                FieldCard {
                    ContactField("Nickname", nickname, { nickname = it }, KeyboardCapitalization.Words,
                        placeholder = "Optional")
                    HorizontalDivider(color = Color(0xFFF0F0F5))
                    // Birthday — taps open date picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Birthday", fontSize = 15.sp, color = Color(0xFF1A1A2E), modifier = Modifier.weight(1f))
                        Text(
                            text = birthday.ifBlank { "Select date" },
                            fontSize = 15.sp,
                            color = if (birthday.isBlank()) Color(0xFF9999AA) else Color(0xFF1A1A2E)
                        )
                    }
                }
            }

            // Status selector
            item {
                FieldCard {
                    Text(
                        "Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B6B8A),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("approved", "requested", "blocked").forEach { s ->
                            val selected = status == s
                            val color = when (s) {
                                "approved"  -> SafetyGreen
                                "blocked"   -> EmergencyRed
                                else        -> Color(0xFFFF9800)
                            }
                            Button(
                                onClick = { status = s },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) color else color.copy(alpha = 0.1f),
                                    contentColor = if (selected) Color.White else color
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(s.replaceFirstChar { it.uppercase() }, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = {
                        onSave(
                            contact.copy(
                                firstName = firstName.trim().ifBlank { null },
                                lastName  = lastName.trim().ifBlank { null },
                                nickname  = nickname.trim().ifBlank { null },
                                phone     = phone.trim().ifBlank { null },
                                relationship = relationship.trim().ifBlank { null },
                                birthday  = birthday.ifBlank { null },
                                status    = status,
                                isAuthorized = status == "approved"
                            )
                        )
                    },
                    enabled = canSave && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isNew) "Add Contact" else "Save Changes", fontSize = 15.sp)
                    }
                }
                if (!canSave) {
                    Text(
                        "First name, phone number, and relationship are required.",
                        fontSize = 12.sp,
                        color = Color(0xFF9999AA),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun FieldCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    ) { content() }
}

@Composable
private fun ContactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, fontSize = 14.sp) }) else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}
