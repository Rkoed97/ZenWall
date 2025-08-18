package com.example.zenwall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.zenwall.data.AppRulesRepo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRulesRepo(app)

    val whitelistMode: StateFlow<Boolean> =
        repo.whitelistModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch { repo.setWhitelistMode(enabled) }
    }
}