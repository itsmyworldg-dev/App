package com.example.storyeditor.guide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.InstagramStoryGradient
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPink

@Composable
fun IntegrationGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val claudePrompt = """
I want to merge this Story & Post Editor module into my existing Android application.
Here is what to do:
1. Copy the package `com.example.storyeditor` into my existing app source directory (e.g. `feature/storyeditor`).
2. Add CameraX, Coil, and Accompanist dependencies to my app's `build.gradle.kts`:
   - `androidx.camera:camera-camera2:1.5.0`
   - `androidx.camera:camera-lifecycle:1.5.0`
   - `androidx.camera:camera-view:1.5.0`
   - `io.coil-kt:coil-compose:2.7.0`
3. Add permissions to `AndroidManifest.xml`:
   - `<uses-permission android:name="android.permission.CAMERA" />`
   - `<uses-permission android:name="android.permission.INTERNET" />`
4. Use `StoryEditorContract` to launch the editor from my feed/profile screen:
   val launcher = rememberLauncherForActivityResult(StoryEditorContract()) { uri ->
       uri?.let { /* handle edited story uri */ }
   }
   launcher.launch(StoryEditorInput())
5. To link my existing app's music library to the Story Editor:
   StoryEditorMusicBridge.availableTracks = myExistingAppMusicList
   // Or open my app's music picker:
   StoryEditorMusicBridge.onOpenMusicPicker = { onTrackChosen ->
       myMusicPicker.show { track -> onTrackChosen(track) }
   }
""".trimIndent()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .testTag("integration_guide_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.IntegrationInstructions,
                            contentDescription = "Integrate",
                            tint = StudioCyan
                        )
                        Text(
                            text = "Integrate to Existing App",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            text = "You can export this project as a ZIP and prompt Claude or merge it directly with 3 simple steps:",
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0C0),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Step 1
                        StepCard(
                            stepNumber = "1",
                            title = "Export from AI Studio",
                            description = "Click the top-right settings/export menu and choose 'Export ZIP' or 'Push to GitHub'."
                        )

                        // Step 2
                        StepCard(
                            stepNumber = "2",
                            title = "Self-Contained Package",
                            description = "Everything is isolated inside `com.example.storyeditor` with zero circular dependencies to existing apps."
                        )

                        // Step 3
                        StepCard(
                            stepNumber = "3",
                            title = "Contract Launch in 2 lines",
                            description = "Launch with `StoryEditorContract()` and receive the resulting High-Res content:// URI back in your parent app."
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Claude Prompt Copy Box
                        Text(
                            text = "PROMPT TO PASTE INTO CLAUDE:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioPink
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F0F16))
                                .border(1.dp, Color(0xFF28283C), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = claudePrompt,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFD0D0E5),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(InstagramStoryGradient)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Claude Integration Prompt", claudePrompt)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Copy Claude Integration Prompt", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E2C))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(StudioPink),
            contentAlignment = Alignment.Center
        ) {
            Text(stepNumber, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFFA0A0B0), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
