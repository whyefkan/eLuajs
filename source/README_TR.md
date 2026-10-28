# eLuajs v1.3 — Kaynak Kod

Efkwn Mobile tarafından geliştirilen hafif Android Java IDE.

## Derleme

```bash
cd source
./build.sh
```

JDK 17+ gerekir (sistemde yoksa paket içindeki runtime/bin/javac kullanılır). Derlenen JAR `source/out/eLuajs.jar` konumuna yazılır.

## v1.3 teknik değişiklikleri

- VS Code tarzı `HubFrame` ve `EditorFrame` düzeni: activity bar, explorer, editör sekmesi, çıktı paneli, durum çubuğu.
- `SyntaxEditorPane`: yorumların dosyanın kalanını yeşile boyamasına neden olan `(?s)` + `//.*$` hatası düzeltildi. String/yorum tek geçişte ayrıştırılıyor.
- `Theme`: VS Code benzeri yüzey renkleri, ince `FlatScrollBarUI`, kompakt düğmeler.
- `Ui`: `ActivityButton` ve ortak durum çubuğu yardımcıları.
- `ReleaseSigningConfig`: release imzalama bilgilerini parola sızdırmadan environment üzerinden taşır.
- `ProjectReleaseManager`: `.eluajs/release-signing.properties`, `.gitignore` koruması ve secrets içermeyen Gradle init script üretir.
- `ReleaseSigningDialog`: keystore seçme/oluşturma, alias ve parola girişi, opsiyonel yerel hatırlama.
- `ToolchainManager`: `keytool` kontrolü, upload keystore üretme/doğrulama, `:app:bundleRelease`, AAB imza girdisi kontrolü ve kolay `release/` çıktısı.
- `BuildDialog`: Debug APK ve imzalı Play Store AAB akışlarını aynı derleme merkezinde sunar.
- Self-test: `keytool`, JKS oluşturma, alias doğrulama ve signing ayarlarında secret sızıntısı kontrolü.

### Neden build.gradle içine şifre yazılmıyor?

Release AAB derlenirken eLuajs bir Gradle init script kullanır. Keystore yolu/şifre/alias yalnız build process environment'ına verilir. Böylece normal kullanımda `app/build.gradle` içine parola gömülmez.

Kullanıcı isterse `.eluajs/release-signing.properties` dosyasını editörden düzenleyebilir. Şifreleri düz metin kaydetmek isteğe bağlıdır.

## Taşınabilir runtime

`runtime/bin/keytool` release imzalama için zorunludur. TLS için `jdk.crypto.ec` modülünü runtime oluştururken çıkarmayın.

## v1.3 yapay zeka + ayarlar

- `eluajs.ai.AiProvider`: OpenAI, Claude, Gemini, Groq, DeepSeek, OpenRouter, River AI ve Custom.
- `eluajs.ai.AiClient`: OpenAI uyumlu, Anthropic ve Gemini API çağrıları; harici JSON kütüphanesi yok.
- `eluajs.ai.AiConfig`: `~/.eluajs/ai.properties`, sağlayıcı bazlı anahtar/model/base URL, 600 izin.
- `eluajs.ui.AiPanel`: editörün sağında docked panel; kod ortada kalır.
- `eluajs.ui.SettingsDialog`: dil (EN varsayılan / TR) ve AI sağlayıcı ayarları.
- `eluajs.util.I18n`: bağımlılıksız EN/TR sözlüğü ve `settings.properties` kalıcılığı.
- `eluajs.ui.Icons`: activity bar ve dosya ağacı için çizim tabanlı ikon seti.
