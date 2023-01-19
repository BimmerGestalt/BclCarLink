package io.bimmergestalt.bclclient.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.bimmergestalt.bclclient.helpers.LogBufferWriter

class LogViewModel: ViewModel() {
    val logMessages = LogBufferWriter.messages.asLiveData(viewModelScope.coroutineContext)
}