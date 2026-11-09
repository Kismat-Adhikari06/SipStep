package com.kismat.path

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProfileInk = Color(0xFF182533)
private val ProfileMuted = Color(0xFF667585)

@Composable
internal fun ProfileScreen() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val profile = ProfileStore.load(context)
        name = profile.name
        age = profile.age
        height = profile.heightCm
        weight = profile.weightKg
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Your details", color = ProfileInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Enter any details you want to keep with your profile.", color = ProfileMuted, fontSize = 14.sp)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80); saved = false },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(3); saved = false },
                    label = { Text("Age (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6); saved = false },
                    label = { Text("Height in cm (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6); saved = false },
                    label = { Text("Weight in kg (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Stored locally on this phone. These details aren’t sent to a server.", color = ProfileMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Button(onClick = {
                    ProfileStore.save(context, UserProfile(name, age, height, weight))
                    saved = true
                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (saved) "Saved" else "Save profile", fontWeight = FontWeight.SemiBold)
                }
                if (saved) Text("Your profile is saved on this phone.", color = Color(0xFF287547), fontSize = 13.sp)
            }
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear profile", color = ProfileMuted)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear your profile?") },
            text = { Text("This removes the details saved on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    name = ""; age = ""; height = ""; weight = ""; saved = false
                    ProfileStore.save(context, UserProfile())
                    confirmClear = false
                }) { Text("Clear profile") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
