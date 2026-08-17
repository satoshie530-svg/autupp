package com.privatestore.tvmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.privatestore.tvmanager.ui.AppManagerViewModel
import com.privatestore.tvmanager.ui.TvAppManagerApp

class MainActivity : ComponentActivity() {

    private val viewModel: AppManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvAppManagerApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresca permiso + estado de instalación al volver de Ajustes,
        // del instalador o del desinstalador del sistema.
        viewModel.onActivityResumed()
    }
}
