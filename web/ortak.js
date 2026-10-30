// Sayfaların ortak yardımcıları: API çağrısı, güvenli metin ve uygulama indirme düğmeleri.
const ayar = window.SOSYALOYUN || {};

async function apiGetir(yol) {
  const yanit = await fetch((ayar.apiAdresi || "") + yol, { headers: { Accept: "application/json" } });
  if (yanit.status === 404) return null;
  if (!yanit.ok) throw new Error("API " + yanit.status);
  return yanit.json();
}

/** Metinler her zaman textContent ile yazılır; kullanıcı içeriği HTML olarak yorumlanmaz. */
function oge(etiket, ozellikler = {}, ...cocuklar) {
  const e = document.createElement(etiket);
  for (const [ad, deger] of Object.entries(ozellikler)) {
    if (ad === "metin") e.textContent = deger;
    else if (ad === "sinif") e.className = deger;
    else e.setAttribute(ad, deger);
  }
  for (const c of cocuklar) if (c) e.append(c);
  return e;
}

function magazaDugmeleri() {
  const kutu = oge("div", { sinif: "dugmeler" });
  for (const [ad, link] of [["App Store", ayar.appStore], ["Google Play", ayar.googlePlay]]) {
    kutu.append(link
      ? oge("a", { sinif: "dugme", href: link, metin: ad })
      : oge("span", { sinif: "dugme pasif", metin: ad + " · Yakında" }));
  }
  return kutu;
}

function kisiKarti(kisi) {
  return oge("div", { sinif: "kisi" },
    oge("div", { sinif: "avatar", metin: (kisi.gorunenAd || kisi.kullaniciAdi).charAt(0).toUpperCase() }),
    oge("div", {},
      oge("a", { href: "profil.html?ad=" + encodeURIComponent(kisi.kullaniciAdi) }, oge("strong", { metin: kisi.gorunenAd })),
      oge("div", { sinif: "soluk", metin: "@" + kisi.kullaniciAdi })));
}

function oyunVeKayit(oyun, kayit) {
  const bilgi = oge("div", {},
    oge("strong", { metin: oyun.ad + (oyun.cikisYili ? " (" + oyun.cikisYili + ")" : "") }));
  if (kayit.puan != null) bilgi.append(oge("div", { sinif: "puan", metin: (kayit.puan / 2).toLocaleString("tr-TR") + " / 5" }));
  const tarih = kayit.bitis || kayit.baslama;
  if (tarih) bilgi.append(oge("div", { sinif: "soluk", metin: new Date(tarih).toLocaleDateString("tr-TR") }));
  if (kayit.inceleme) {
    const metin = oge("p", { sinif: "inceleme" + (kayit.spoiler ? " spoiler" : ""), metin: kayit.inceleme });
    if (kayit.spoiler) {
      metin.title = "Spoiler içerir; görmek için dokun";
      // Kart bir linkin içindeyse (profildeki son kayıtlar) ilk dokunuş sayfayı açmaz, yalnızca spoiler'ı gösterir.
      metin.addEventListener("click", (olay) => {
        if (metin.classList.contains("acik")) return;
        olay.preventDefault();
        metin.classList.add("acik");
      });
    }
    bilgi.append(metin);
  }
  if (kayit.begeniSayisi) bilgi.append(oge("div", { sinif: "soluk", metin: kayit.begeniSayisi + " beğeni" }));
  return oge("div", { sinif: "oyun" },
    oyun.kapakUrl ? oge("img", { sinif: "kapak", src: oyun.kapakUrl, alt: "" }) : null,
    bilgi);
}

function bulunamadi(kutu, mesaj) {
  kutu.replaceChildren(oge("p", { metin: mesaj }), oge("p", { sinif: "soluk", metin: "Sosyal Oyun'u indirip kendi oyun günlüğünü tutmaya başla:" }), magazaDugmeleri());
}
