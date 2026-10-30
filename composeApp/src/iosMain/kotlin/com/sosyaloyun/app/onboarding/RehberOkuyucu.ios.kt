package com.sosyaloyun.app.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Contacts.CNContact
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber

@Composable
actual fun rehberOkuyucu(): RehberOkuyucu = remember {
    RehberOkuyucu {
        val depo = CNContactStore()
        val izin = CompletableDeferred<Boolean>()
        depo.requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { verildi, _ -> izin.complete(verildi) }
        if (izin.await()) numaralariOku(depo) else null
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun numaralariOku(depo: CNContactStore): List<String> = withContext(Dispatchers.IO) {
    val numaralar = mutableListOf<String>()
    val istek = CNContactFetchRequest(keysToFetch = listOf(CNContactPhoneNumbersKey))
    depo.enumerateContactsWithFetchRequest(istek, error = null) { kisi: CNContact?, _ ->
        kisi?.phoneNumbers?.forEach { deger ->
            ((deger as? CNLabeledValue)?.value as? CNPhoneNumber)?.stringValue?.let(numaralar::add)
        }
    }
    numaralar
}
