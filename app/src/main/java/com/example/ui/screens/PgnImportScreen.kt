package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chess.core.ParsedPgnGame
import com.example.ui.theme.ClassBlunder
import com.example.ui.theme.ClassInaccurate
import com.example.ui.theme.CreamText
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MutedStoneText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.SubtleStoneText
import com.example.ui.theme.TournamentEmerald
import com.example.ui.viewmodel.PgnImportUiState
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PgnImportScreen(
    state: PgnImportUiState,
    onBack: () -> Unit,
    onTextChange: (String, String?) -> Unit,
    onParseClick: () -> Unit,
    onSelectGame: (ParsedPgnGame) -> Unit,
    onLoadClassicGame: () -> Unit,
    onClear: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    // Zero-permission Android Storage Access Framework document picker
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = resolveDisplayName(context, uri) ?: "Imported PGN"
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }.orEmpty()
                onTextChange(text, fileName)
                onParseClick()
            } catch (e: Exception) {
                onTextChange(state.rawText, null)
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = { Text("Import PGN", color = CreamText) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pgn_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = CreamText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File picker + Classic Master Game row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TournamentEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pgn_open_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Open .PGN File", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onLoadClassicGame,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("pgn_classic_game_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TournamentEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Opera Game", color = CreamText)
                        }
                    }
                }

                // PGN Text Input
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.selectedFileName ?: "Paste PGN notation (single or multi-game)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MutedStoneText
                            )
                            if (state.rawText.isNotBlank()) {
                                IconButton(onClick = onClear) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear PGN input",
                                        tint = SubtleStoneText
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.rawText,
                            onValueChange = { onTextChange(it, null) },
                            placeholder = {
                                Text(
                                    "[Event \"Casual Game\"]\n1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 ...",
                                    fontFamily = JetBrainsMonoFont,
                                    fontSize = 12.sp,
                                    color = SubtleStoneText
                                )
                            },
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 12.5.sp,
                                color = CreamText,
                                lineHeight = 18.sp
                            ),
                            minLines = 6,
                            maxLines = 12,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ObsidianSurface,
                                unfocusedContainerColor = ObsidianSurface,
                                focusedBorderColor = TournamentEmerald,
                                unfocusedBorderColor = ObsidianBorder,
                                cursorColor = TournamentEmerald
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pgn_text_input")
                        )

                        Button(
                            onClick = onParseClick,
                            enabled = state.rawText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = TournamentEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("pgn_parse_analyze_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Parse & Analyze PGN", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Error Banner
                if (state.errorMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClassBlunder.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ClassBlunder.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ClassBlunder)
                                Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = CreamText)
                            }
                        }
                    }
                }

                // Partial / Malformed PGN Warnings
                if (state.warnings.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClassInaccurate.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ClassInaccurate.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Parsed with warnings",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ClassInaccurate
                                )
                                state.warnings.forEach { w ->
                                    Text(w, style = MaterialTheme.typography.bodySmall, color = CreamText)
                                }
                            }
                        }
                    }
                }

                // Multi-game Selection List
                if (state.parsedGames.isNotEmpty()) {
                    item {
                        Text(
                            text = "Found ${state.parsedGames.size} ${if (state.parsedGames.size == 1) "game" else "games"} — Select to analyze",
                            style = MaterialTheme.typography.titleMedium,
                            color = CreamText
                        )
                    }
                    itemsIndexed(state.parsedGames, key = { _, g -> g.id }) { idx, game ->
                        Surface(
                            onClick = { onSelectGame(game) },
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pgn_game_item_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${game.whitePlayer} vs ${game.blackPlayer}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CreamText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${game.moves.size} plies • ${game.result}")
                                            if (game.openingName != null) append(" • ${game.openingName}")
                                            if (game.date.isNotBlank()) append(" • ${game.date}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedStoneText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TournamentEmerald.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "Analyze",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TournamentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}
