package com.sosyaloyun.app.gunluk

import com.sosyaloyun.shared.GunlukKaydi

/** Beğeninin iyimser hali: beğenilmişse geri alınır, değilse beğenilir; sayaç buna göre değişir. */
fun GunlukKaydi.begenisiCevrilmis(): GunlukKaydi = copy(begendim = !begendim, begeniSayisi = begeniSayisi + if (begendim) -1 else 1)
