// Webde giriş ve günlük (Faz 3, #58). Yalnızca Faz 3 açıkken ve sunucu web istemci kimliklerini verdiğinde çalışır.
// Oturum token'ı yalnızca bu kökenin localStorage'ında tutulur; çıkışta ve reddedilen istekte silinir. Kullanıcı
// içeriği her zaman ortak.js'teki oge() ile textContent olarak yazılır.
const OTURUM_ANAHTARI = "sosyaloyun.oturum";
const kutu = document.getElementById("icerik");
const menu = document.getElementById("menu");

const DURUMLAR = [["", "Durum yok"], ["oynuyorum", "Oynuyorum"], ["bitirdim", "Bitirdim"], ["biraktim", "Bıraktım"], ["beklemede", "Beklemede"]];
const GORUNURLUKLER = [["herkes", "Herkes"], ["takipciler", "Takipçiler"], ["arkadaslar", "Arkadaşlar"], ["ben", "Yalnızca ben"]];

function oturum() {
  try { return localStorage.getItem(OTURUM_ANAHTARI); } catch (e) { return null; }
}

function oturumuYaz(token) {
  try {
    if (token) localStorage.setItem(OTURUM_ANAHTARI, token); else localStorage.removeItem(OTURUM_ANAHTARI);
  } catch (e) { /* depolama kapalıysa oturum yalnızca bu sayfada sürer */ }
}

class ApiHatasi extends Error {
  constructor(durum, kod) { super(kod || "API " + durum); this.durum = durum; this.kod = kod; }
}

/** Oturumlu API isteği; 401'de oturum silinir ve giriş ekranına dönülür. */
async function api(yontem, yol, govde) {
  const basliklar = { Accept: "application/json" };
  const token = oturum();
  if (token) basliklar.Authorization = "Bearer " + token;
  if (govde !== undefined) basliklar["Content-Type"] = "application/json";
  const yanit = await fetch((ayar.apiAdresi || "") + yol, { method: yontem, headers: basliklar, body: govde === undefined ? undefined : JSON.stringify(govde) });
  if (yanit.status === 401 && token) {
    oturumuYaz(null);
    baslat();
    throw new ApiHatasi(401);
  }
  const metin = await yanit.text();
  const veri = metin ? JSON.parse(metin) : null;
  if (!yanit.ok) throw new ApiHatasi(yanit.status, veri && veri.kod);
  return veri;
}

function goster(...cocuklar) { kutu.replaceChildren(...cocuklar.filter(Boolean)); }

function hataMetni(mesaj) { return oge("p", { sinif: "hata", metin: mesaj }); }

function secim(ad, secenekler, secili) {
  const s = oge("select", { name: ad, "aria-label": ad });
  for (const [deger, etiket] of secenekler) {
    const o = oge("option", { value: deger, metin: etiket });
    if (String(deger) === String(secili ?? "")) o.selected = true;
    s.append(o);
  }
  return s;
}

const yuklenenScriptler = new Map();

/** Sağlayıcı kütüphanesini bir kez yükler; aynı adres için aynı söz döner. */
function scriptYukle(adres) {
  if (!yuklenenScriptler.has(adres)) {
    yuklenenScriptler.set(adres, new Promise((tamam, hata) => {
      const s = document.createElement("script");
      s.src = adres; s.async = true; s.onload = tamam;
      s.onerror = (e) => { yuklenenScriptler.delete(adres); hata(e); };
      document.head.append(s);
    }));
  }
  return yuklenenScriptler.get(adres);
}

// ── Giriş ──────────────────────────────────────────────────────────────────

async function girisYap(saglayici, kimlikTokeni) {
  try {
    const yanit = await api("POST", "/oturum", { saglayici, kimlikTokeni });
    oturumuYaz(yanit.oturumTokeni);
    baslat();
  } catch (e) {
    const mesaj = e.kod === "hesap_askida" ? "Hesabın askıya alınmış." : e.kod === "hesap_banli" ? "Hesabın kapatılmış." : "Giriş yapılamadı. Tekrar dene.";
    kutu.append(hataMetni(mesaj));
  }
}

async function girisGorunumu(webGiris) {
  menu.hidden = true;
  const dugmeler = oge("div", { sinif: "dugmeler" });
  goster(oge("h1", { metin: "Giriş yap" }), oge("p", { sinif: "soluk", metin: "Uygulamadaki hesabınla giriş yap; günlüğünü webde de tut." }), dugmeler);
  if (webGiris.googleIstemciKimligi) {
    const yer = oge("div");
    dugmeler.append(yer);
    try {
      await scriptYukle("https://accounts.google.com/gsi/client");
      google.accounts.id.initialize({ client_id: webGiris.googleIstemciKimligi, callback: (r) => girisYap("google", r.credential) });
      google.accounts.id.renderButton(yer, { theme: "outline", size: "large", text: "signin_with", locale: "tr" });
    } catch (e) { yer.append(hataMetni("Google girişi yüklenemedi.")); }
  }
  if (webGiris.appleServisKimligi) {
    const dugme = oge("button", { sinif: "dugme", type: "button", metin: "Apple ile giriş yap" });
    dugmeler.append(dugme);
    let hazir = false;
    dugme.addEventListener("click", async () => {
      try {
        await scriptYukle("https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/tr_TR/appleid.auth.js");
        if (!hazir) {
          AppleID.auth.init({ clientId: webGiris.appleServisKimligi, scope: "", redirectURI: location.origin + location.pathname, usePopup: true });
          hazir = true;
        }
        const sonuc = await AppleID.auth.signIn();
        await girisYap("apple", sonuc.authorization.id_token);
      } catch (e) { kutu.append(hataMetni("Apple ile giriş tamamlanmadı.")); }
    });
  }
}

// ── Davet kodu ve kullanıcı adı ────────────────────────────────────────────

function davetGorunumu() {
  const kod = oge("input", { name: "kod", placeholder: "Davet kodu", "aria-label": "Davet kodu", autocomplete: "off", required: "" });
  const form = oge("form", {}, kod, oge("button", { sinif: "dugme", type: "submit", metin: "Devam" }));
  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    try {
      await api("POST", "/davet/kullan", { kod: kod.value.trim().toUpperCase() });
      baslat();
    } catch (e) { form.append(hataMetni(e.kod === "davet_kodu_kullanilmis" ? "Bu kod kullanılmış." : "Kod geçersiz.")); }
  });
  goster(oge("h1", { metin: "Davet kodu" }), oge("p", { sinif: "soluk", metin: "Sosyal Oyun kapalı betada; davet koduyla katılınır." }), form);
}

function kullaniciAdiGorunumu() {
  const ad = oge("input", { name: "kullaniciAdi", placeholder: "kullanici_adi", "aria-label": "Kullanıcı adı", autocomplete: "off", required: "" });
  const gorunen = oge("input", { name: "gorunenAd", placeholder: "Görünen ad", "aria-label": "Görünen ad", required: "" });
  const form = oge("form", {}, ad, gorunen, oge("button", { sinif: "dugme", type: "submit", metin: "Kaydet" }));
  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    try {
      await api("PUT", "/profil", { kullaniciAdi: ad.value.trim(), gorunenAd: gorunen.value.trim(), avatar: "kumanda" });
      baslat();
    } catch (e) {
      form.append(hataMetni(e.kod === "kullanici_adi_alinmis" ? "Bu kullanıcı adı alınmış." : "Kullanıcı adı 3–20 harf, rakam ya da alt çizgi olmalı."));
    }
  });
  goster(oge("h1", { metin: "Kullanıcı adını seç" }), form);
}

// ── Akış, arama, profil, oyun ──────────────────────────────────────────────

function menuyuGoster() {
  menu.hidden = false;
  const cikis = oge("a", { href: "#", metin: "Çıkış" });
  cikis.addEventListener("click", async (olay) => {
    olay.preventDefault();
    try { await api("DELETE", "/oturum"); } catch (e) { /* sunucuya ulaşılamasa da oturum tarayıcıdan silinir */ }
    oturumuYaz(null);
    location.hash = "";
    baslat();
  });
  menu.replaceChildren(
    oge("a", { href: "#akis", metin: "Akış" }), " · ",
    oge("a", { href: "#ara", metin: "Ara" }), " · ",
    oge("a", { href: "#profil", metin: "Profilim" }), " · ", cikis);
}

function oyunSatiri(oyun) {
  return oge("li", {}, oge("a", { sinif: "oyun", href: "#oyun/" + encodeURIComponent(oyun.id) },
    oyun.kapakUrl ? oge("img", { sinif: "kapak", src: oyun.kapakUrl, alt: "" }) : null,
    oge("strong", { metin: oyun.ad + (oyun.cikisYili ? " (" + oyun.cikisYili + ")" : "") })));
}

async function akisGorunumu() {
  const sayfa = await api("GET", "/akis");
  const ogeler = sayfa.ogeler.map((o) => {
    const ne = o.tur === "gunluk_kaydi" ? "günlüğüne yazdı" : o.tur === "oynamaya_basladi" ? "oynamaya başladı" : "ile arkadaş oldu";
    return oge("li", {}, oge("p", {},
      oge("strong", { metin: o.kisi.gorunenAd }), " " + ne + " ",
      o.oyun ? oge("a", { href: "#oyun/" + encodeURIComponent(o.oyun.id), metin: o.oyun.ad }) : null,
      o.arkadas ? oge("strong", { metin: o.arkadas.gorunenAd }) : null),
      o.gunlukKaydi && o.gunlukKaydi.inceleme && !o.gunlukKaydi.spoiler ? oge("p", { sinif: "inceleme", metin: o.gunlukKaydi.inceleme }) : null);
  });
  goster(oge("h1", { metin: "Akış" }), ogeler.length ? oge("ul", { sinif: "liste" }, ...ogeler) : oge("p", { sinif: "soluk", metin: "Akışın boş. Arkadaşlarını takip et." }));
}

function aramaGorunumu() {
  const q = oge("input", { name: "q", placeholder: "Oyun ara", "aria-label": "Oyun ara", autocomplete: "off" });
  const sonuclar = oge("ul", { sinif: "liste" });
  const form = oge("form", {}, q, oge("button", { sinif: "dugme", type: "submit", metin: "Ara" }));
  form.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    if (!q.value.trim()) return;
    try {
      const yanit = await api("GET", "/oyunlar/ara?q=" + encodeURIComponent(q.value.trim()));
      sonuclar.replaceChildren(...yanit.oyunlar.map(oyunSatiri));
      if (!yanit.oyunlar.length) sonuclar.append(oge("li", { sinif: "soluk", metin: "Sonuç yok." }));
    } catch (e) {
      if (e.durum !== 401) sonuclar.replaceChildren(oge("li", {}, hataMetni("Arama yapılamadı. Tekrar dene.")));
    }
  });
  goster(oge("h1", { metin: "Oyun ara" }), form, sonuclar);
  q.focus();
}

async function profilGorunumu() {
  const [profil, gunluk] = await Promise.all([api("GET", "/profilim"), api("GET", "/profilim/gunluk")]);
  goster(
    kisiKarti(profil.kullanici),
    profil.biyografi ? oge("p", { metin: profil.biyografi }) : null,
    oge("p", { sinif: "soluk", metin: "Bu yıl " + profil.buYilBitirilen + " oyun bitirdin." }),
    oge("h2", { metin: "Günlüğün" }),
    ...gunluk.ogeler.map((o) => oyunVeKayit(o.oyun, o.kayit)));
}

const PUANLAR = [["", "Puan yok"], ...Array.from({ length: 10 }, (_, i) => [i + 1, "★ " + ((i + 1) / 2).toLocaleString("tr-TR")])];

async function oyunGorunumu(id) {
  const detay = await api("GET", "/oyunlar/" + encodeURIComponent(id));
  const giris = detay.kutuphaneGirisi || {};
  const durum = secim("durum", DURUMLAR, giris.durum);
  const puan = secim("puan", PUANLAR, giris.puan);
  const kutuphaneSonucu = oge("p", { sinif: "soluk" });
  const kutuphaneFormu = oge("form", {}, durum, puan, oge("button", { sinif: "dugme", type: "submit", metin: "Kaydet" }), kutuphaneSonucu);
  kutuphaneFormu.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    try {
      // Diğer işaretler (beğeni, istek listesi, %100) olduğu gibi gönderilir; %100 yalnızca Bitirdim'de kalır.
      await api("PUT", "/oyunlar/" + encodeURIComponent(id) + "/kutuphane", {
        durum: durum.value || null,
        puan: puan.value ? Number(puan.value) : null,
        begendim: !!giris.begendim,
        istekListesinde: !!giris.istekListesinde,
        yuzdeYuz: !!giris.yuzdeYuz && durum.value === "bitirdim",
      });
      kutuphaneSonucu.textContent = "Kaydedildi.";
    } catch (e) { kutuphaneSonucu.textContent = "Kaydedilemedi. Tekrar dene."; }
  });

  const inceleme = oge("textarea", { name: "inceleme", rows: "4", maxlength: "5000", placeholder: "Ne düşündün? (isteğe bağlı)", "aria-label": "İnceleme" });
  const spoiler = oge("input", { type: "checkbox", name: "spoiler" });
  const gorunurluk = secim("gorunurluk", GORUNURLUKLER, "herkes");
  const kayitSonucu = oge("p", { sinif: "soluk" });
  const kayitFormu = oge("form", {}, inceleme, oge("label", {}, spoiler, " Spoiler içeriyor"), gorunurluk,
    oge("button", { sinif: "dugme", type: "submit", metin: "Günlüğe yaz" }), kayitSonucu);
  kayitFormu.addEventListener("submit", async (olay) => {
    olay.preventDefault();
    try {
      await api("POST", "/oyunlar/" + encodeURIComponent(id) + "/gunluk", {
        inceleme: inceleme.value.trim() || null, spoiler: spoiler.checked, gorunurluk: gorunurluk.value,
      });
      inceleme.value = "";
      kayitSonucu.textContent = "Günlüğüne yazıldı.";
    } catch (e) { kayitSonucu.textContent = "Yazılamadı. Tekrar dene."; }
  });

  goster(
    oge("div", { sinif: "oyun" },
      detay.oyun.kapakUrl ? oge("img", { sinif: "kapak", src: detay.oyun.kapakUrl, alt: "" }) : null,
      oge("h1", { metin: detay.oyun.ad })),
    detay.aciklama ? oge("p", { sinif: "soluk", metin: detay.aciklama }) : null,
    oge("h2", { metin: "Kütüphanen" }), kutuphaneFormu,
    oge("h2", { metin: "Günlük kaydı" }), kayitFormu);
}

async function yonlendir() {
  const [yer, deger] = location.hash.replace(/^#/, "").split("/");
  try {
    if (yer === "ara") aramaGorunumu();
    else if (yer === "profil") await profilGorunumu();
    else if (yer === "oyun" && deger) await oyunGorunumu(decodeURIComponent(deger));
    else await akisGorunumu();
  } catch (e) {
    if (e.durum !== 401) goster(hataMetni("Şu an yüklenemedi. Biraz sonra tekrar dene."));
  }
}

// ── Başlangıç ──────────────────────────────────────────────────────────────

async function baslat() {
  try {
    const ozellikler = await apiGetir("/ozellikler");
    // Faz 1 ve 2'de ya da web istemci kimlikleri yokken webde giriş yoktur.
    if (!ozellikler || !ozellikler.webGiris) {
      oturumuYaz(null);
      return goster(oge("p", { metin: "Webde giriş henüz açık değil. Uygulamayı indir." }), magazaDugmeleri());
    }
    if (!oturum()) return girisGorunumu(ozellikler.webGiris);
    const ben = await api("GET", "/ben");
    if (!ben.davetKabulEdildi) return davetGorunumu();
    if (!ben.profil) return kullaniciAdiGorunumu();
    menuyuGoster();
    await yonlendir();
  } catch (e) {
    if (e.durum !== 401) goster(hataMetni("Şu an yüklenemedi. Biraz sonra tekrar dene."));
  }
}

window.addEventListener("hashchange", () => { if (oturum()) yonlendir(); });
baslat();
