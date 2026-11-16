package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorWorkspaceScreen
import com.example.ui.screens.ImportHubScreen
import com.example.ui.theme.FrameCraftTheme
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameCraftTheme {
                val vm: StudioViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                val htmlPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { vm.loadHtmlFromUri(it) }
                }

                if (uiState.project == null) {
                    ImportHubScreen(
                        onPickHtmlFile = {
                            htmlPicker.launch(arrayOf("text/html", "application/xhtml+xml", "*/*"))
                        },
                        onLoadSampleStory = {
                            vm.loadSampleStoryHtml()
                        },
                        statusBanner = uiState.statusBanner,
                        onDismissBanner = { vm.clearBanner() }
                    )
                } else {
                    EditorWorkspaceScreen(
                        uiState = uiState,
                        viewModel = vm
                    )
                }
            }
        }
    }
}
