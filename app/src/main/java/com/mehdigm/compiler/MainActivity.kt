package com.mehdigm.compiler

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mehdigm.compiler.storage.FileManager
import com.mehdigm.compiler.ui.console.CompilerViewModel
import com.mehdigm.compiler.ui.console.ConsoleView
import com.mehdigm.compiler.ui.editor.PawnEditor
import com.mehdigm.compiler.ui.theme.GSColors
import com.mehdigm.compiler.ui.theme.GSCompilerTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GSCompilerTheme {
                GSCompilerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSCompilerApp() {
    val context = LocalContext.current
    val viewModel: CompilerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var showStorageDialog by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }

    /* File picker for .pwn files */
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = FileManager.readFromDocument(context, it)
            if (content != null) {
                viewModel.setEditorValue(
                    androidx.compose.ui.text.input.TextFieldValue(content)
                )
            }
        }
    }

    /* Storage permission request */
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (!FileManager.hasManageStoragePermission(context)) {
            Toast.makeText(context, "Storage permission is required", Toast.LENGTH_LONG).show()
        }
    }

    /* Check storage permission on launch */
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStorageDialog = true
            }
        }
    }

    LaunchedEffect(showFilePicker) {
        if (showFilePicker) {
            filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
            showFilePicker = false
        }
    }

    /* Storage permission dialog */
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = {
                Text("Storage Access Required")
            },
            text = {
                Text(
                    "GS Compiler needs access to all files to read .pwn scripts " +
                    "and write compiled .amx binaries. Please grant the permission."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStorageDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = FileManager.requestManageStorageIntent()
                        storageLauncher.launch(intent)
                    }
                }) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GS",
                            fontWeight = FontWeight.Bold,
                            color = GSColors.AccentGold
                        )
                        Text(
                            text = " Compiler",
                            color = GSColors.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GSColors.ToolbarBackground
                ),
                actions = {
                    /* File info badge */
                    if (uiState.currentFile != null) {
                        Text(
                            text = uiState.currentFile!!.name,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(GSColors.DarkBackground)
        ) {
            /* Toolbar */
            ToolbarRow(
                canUndo = uiState.editorValue.text.isNotEmpty(),
                canRedo = false,
                isCompiling = uiState.isCompiling,
                onUndo = { /* undo */ },
                onRedo = { /* redo */ },
                onSave = { viewModel.saveFile() },
                onCompile = { viewModel.compile() },
                onOpenFile = { showFilePicker = true }
            )

            /* Editor area */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                PawnEditor(
                    textFieldValue = uiState.editorValue,
                    onValueChange = { viewModel.setEditorValue(it) }
                )

                /* Compilation overlay */
                if (uiState.isCompiling) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GSColors.AccentGold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Compiling...",
                                color = GSColors.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            /* Console */
            ConsoleView(
                entries = uiState.consoleEntries,
                expanded = uiState.consoleExpanded,
                onToggleExpanded = { viewModel.toggleConsole() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (uiState.consoleExpanded) 300.dp else 48.dp)
            )
        }
    }
}

@Composable
fun ToolbarRow(
    canUndo: Boolean,
    canRedo: Boolean,
    isCompiling: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onCompile: () -> Unit,
    onOpenFile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GSColors.DarkSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* Left side: Undo / Redo */
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo && !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = GSColors.White
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo && !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "Redo",
                        tint = GSColors.White
                    )
                }
            }

            /* Right side: Open, Save, Compile */
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onOpenFile,
                    enabled = !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Open File",
                        tint = GSColors.AccentBlue
                    )
                }

                IconButton(
                    onClick = onSave,
                    enabled = !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = GSColors.White
                    )
                }

                Button(
                    onClick = onCompile,
                    enabled = !isCompiling,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GSColors.AccentGold,
                        contentColor = GSColors.DarkBackground
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isCompiling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GSColors.DarkBackground
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Compile",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Compile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
