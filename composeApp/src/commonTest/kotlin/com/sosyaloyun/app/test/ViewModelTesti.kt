package com.sosyaloyun.app.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** ViewModel'leri `Dispatchers.Main` yerine sanal zamanlı test dağıtıcısıyla çalıştırır. */
@OptIn(ExperimentalCoroutinesApi::class)
fun viewModelTesti(blok: suspend TestScope.() -> Unit) {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        runTest(dispatcher) { blok() }
    } finally {
        Dispatchers.resetMain()
    }
}
