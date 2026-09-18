package com.infusory.modelviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.infusory.modelviewer.ui.canvas.CanvasScreen
import com.infusory.modelviewer.ui.canvas.CanvasViewModel
import com.infusory.modelviewer.ui.theme.ModelViewerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CanvasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ModelViewerTheme {
                CanvasScreen(viewModel = viewModel)
            }
        }
    }
}
