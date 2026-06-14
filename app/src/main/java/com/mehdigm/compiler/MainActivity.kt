package com.mehdigm.compiler

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mehdigm.compiler.storage.FileManager
import com.mehdigm.compiler.ui.console.CompilerViewModel
import com.mehdigm.compiler.ui.console.ConsoleView
import com.mehdigm.compiler.ui.editor.CodeEditor
import com.mehdigm.compiler.ui.editor.SoraEditorHandle
import com.mehdigm.compiler.ui.theme.GSColors
import com.mehdigm.compiler.ui.theme.GSCompilerTheme
import com.mehdigm.compiler.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
    val activity = context as? Activity
    val viewModel: CompilerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val editorHandle = remember { SoraEditorHandle() }

    var showStorageDialog by remember { mutableStateOf(false) }
    val openFileTrigger = remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadFromUri(context, uri)
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        viewModel.clearRequestSaveAs()
        if (uri != null) {
            viewModel.saveToUri(context, uri)
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && Environment.isExternalStorageManager()
        ) {
            AppLogger.start(context)
            AppLogger.i("GSCompiler", "All files access granted")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && !Environment.isExternalStorageManager()
        ) {
            showStorageDialog = true
        } else {
            AppLogger.start(context)
        }
    }

    LaunchedEffect(openFileTrigger.value) {
        if (openFileTrigger.value) {
            filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
            openFileTrigger.value = false
        }
    }

    LaunchedEffect(uiState.showFileSavedToast) {
        if (uiState.showFileSavedToast) {
            val msg = if (uiState.fileSavedSuccess) "File Saved" else "Save failed"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearSavedToast()
        }
    }

    BackHandler(enabled = uiState.isDirty) {
        viewModel.requestExit()
    }

    /* ===== Unsaved Changes Dialog ===== */
    if (uiState.showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.handleUnsavedCancel() },
            containerColor = GSColors.DarkSurface,
            titleContentColor = GSColors.White,
            textContentColor = GSColors.TextGray,
            title = {
                Text(
                    "Unsaved Changes",
                    fontWeight = FontWeight.Bold,
                    color = GSColors.White
                )
            },
            text = {
                Text(
                    "You have unsaved changes. What would you like to do?",
                    color = GSColors.TextGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val isExit = uiState.unsavedDialogTabIndex == null
                        val idx = uiState.unsavedDialogTabIndex ?: uiState.activeTabIndex
                        val tab = uiState.tabs.getOrNull(idx)
                        if (tab != null && tab.file != null) {
                            runBlocking(Dispatchers.IO) {
                                FileManager.writeFileContent(tab.file, tab.content)
                            }
                        }
                        viewModel.handleUnsavedSave()
                        if (isExit) {
                            activity?.finish()
                        }
                    }
                ) {
                    Text("Save", color = GSColors.AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val isExit = uiState.unsavedDialogTabIndex == null
                            viewModel.handleUnsavedDismiss()
                            if (isExit) {
                                activity?.finish()
                            }
                        }
                    ) {
                        Text("Discard", color = GSColors.ErrorRed)
                    }
                    TextButton(
                        onClick = { viewModel.handleUnsavedCancel() }
                    ) {
                        Text("Cancel", color = GSColors.TextGray)
                    }
                }
            }
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            containerColor = GSColors.DarkSurface,
            titleContentColor = GSColors.White,
            textContentColor = GSColors.TextGray,
            title = { Text("Storage Access Required", fontWeight = FontWeight.Bold) },
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
                        storageLauncher.launch(
                            FileManager.requestManageStorageIntent()
                        )
                    }
                }) {
                    Text("Grant Access", color = GSColors.AccentGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Later", color = GSColors.TextGray)
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
            ToolbarRow(
                isCompiling = uiState.isCompiling,
                isDirty = uiState.isDirty,
                editorHandle = editorHandle,
                onSave = { viewModel.saveCurrentTab() },
                onCompile = { viewModel.compile() },
                onOpenFile = { openFileTrigger.value = true }
            )

            /* ===== Tab Bar ===== */
            TabBar(
                tabs = uiState.tabs,
                activeIndex = uiState.activeTabIndex,
                onTabClick = { viewModel.switchTab(it) },
                onTabClose = { viewModel.requestCloseTab(it) },
                onNewFile = { viewModel.newFile() }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CodeEditor(
                    text = uiState.editorText,
                    onTextChange = { viewModel.setEditorText(it) },
                    editorHandle = editorHandle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )

                if (uiState.isReadingFile || uiState.isCompiling) {
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
                                if (uiState.isReadingFile) "Opening file..." else "Compiling...",
                                color = GSColors.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = GSColors.ErrorRed.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = GSColors.ErrorRed,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = GSColors.ErrorRed, fontSize = 12.sp)
                        }
                    }
                }
            }

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
fun TabBar(
    tabs: List<com.mehdigm.compiler.ui.console.EditorTab>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onNewFile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GSColors.DarkBackground,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeIndex
                val bgColor = if (isActive) GSColors.DarkSurface else Color.Transparent
                val textColor = if (isActive) GSColors.White else GSColors.TextGray

                Surface(
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onTabClick(index) },
                    color = bgColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tab.isDirty) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GSColors.AccentGold, RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = tab.displayName,
                            color = textColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(
                            onClick = { onTabClose(index) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close ${tab.displayName}",
                                tint = GSColors.TextGray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onNewFile,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New File",
                    tint = GSColors.TextGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ToolbarRow(
    isCompiling: Boolean,
    isDirty: Boolean,
    editorHandle: SoraEditorHandle,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { editorHandle.undo() },
                    enabled = editorHandle.canUndo && !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = GSColors.White
                    )
                }
                IconButton(
                    onClick = { editorHandle.redo() },
                    enabled = editorHandle.canRedo && !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = GSColors.White
                    )
                }
            }

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

                val saveTint = if (isDirty) GSColors.White else GSColors.White.copy(alpha = 0.4f)
                IconButton(
                    onClick = onSave,
                    enabled = !isCompiling,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = saveTint
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
