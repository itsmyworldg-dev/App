package com.example.ui.dialogs

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LocationEnableDialog(
    isOpen: Boolean,
    onTurnOnLocation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("location_enable_dialog"),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1E212B),
        icon = {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = "Location Off",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Turn On Device Location",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Location services are turned off on your phone. To navigate with spoken turn-by-turn voice directions and see your live location on the map, please turn on location.",
                color = Color(0xFFD1D5DB),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onTurnOnLocation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38A169),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_turn_on_location_btn")
            ) {
                Text(
                    text = "Turn On",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_location_dialog_btn")
            ) {
                Text(
                    text = "Not Now",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp
                )
            }
        }
    )
}
