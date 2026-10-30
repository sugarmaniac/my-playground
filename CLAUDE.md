# Sosyal Oyun Platformu

Oyun günlüğü ve sosyal ağı: Kotlin Multiplatform + Compose Multiplatform mobil uygulama, Ktor backend, statik HTML+JS web sitesi. Alan dili `CONTEXT.md`'de, mimari kararlar `docs/adr/`'de.

Issue'lar, ticket'lar ve commit mesajları Türkçe yazılır ve `CONTEXT.md`'deki terimleri kullanır.

Kod, PR'lar ve CI public `sugarmaniac/my-playground` reposunda; issue'lar (spec ve ticket'lar) private `sugarmaniac/Sosyal-Oyun-Platformu` reposunda. Ayrıntı `docs/agents/otomasyon.md` → Repolar.

Ticket'lar zamanlanmış cloud oturumlarınca insan müdahalesi olmadan tek tek uygulanır ve merge edilir; döngünün kuralları `docs/agents/otomasyon.md`'de. Karar gerektiğinde insana sorulmaz, önerilen seçenek seçilip ticket'a yazılır.

## Agent skills

### Issue tracker

Issues and specs live in the GitHub Issues of the private repo sugarmaniac/Sosyal-Oyun-Platformu, not in this code repo. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage labels, unchanged (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
