# eLuajs v1.3 — Hafif Android Java IDE — Linux x86_64

Efkwn Mobile tarafından geliştirilen, eski bilgisayarlarda bile rahat çalışan
hafif Android Java IDE.

ZIP'i çıkarın ve `eLuajs` dosyasına çift tıklayın. Paket kendi Java çalışma
ortamını içerir. Kaynak kodun tamamı `source/` klasöründedir.

## v1.3 — VS Code tarzı arayüz + düzeltmeler

- Yeni VS Code benzeri çalışma alanı: ince activity bar, explorer kenar çubuğu,
  sekmeli editör başlığı, çıktı paneli ve alt durum çubuğu.
- Hub ekranı yenilendi: solda kompakt proje listesi; ortada seçime göre
  başlangıç ekranı veya proje detayı, hızlı işlemler, ortam durumu ve ipuçları.
- Başlık alanları ve kenar çubukları ferahlatıldı; editör işlemleri 2x2 düzene alındı.
- **Editör renklendirme düzeltmesi:** `//` yorumundan sonra dosyanın tamamının
  yeşile boyanmasına neden olan regex hatası giderildi. Artık yorum, string ve
  kod renkleri doğru ayrılıyor (`http://...` string içinde string renginde kalır).
- `package` algılama regex'i düzeltildi.
- Yuvarlak köşeli, hafif düğmeler ve ince kaydırma çubukları.
- Ana pencereler ve Ayarlar için VS Code tarzı özel pencere kontrolleri:
  küçült, büyüt/geri yükle, kapat; başlık çubuğundan sürükleme ve kenarlardan boyutlandırma.
- Üst araç çubuğundaki Kaydet / Doğrula / Derle / AI düğmeleri ikonlu ve tutarlı.
- Masaüstü için tek tıkla açılan `eLuajs` kısayolu ve uygulama ikonu.

## Ayarlar ve dil

- Varsayılan arayüz dili **İngilizce**dir.
- Hub veya editördeki dişli (Ayarlar) düğmesinden **Türkçe** seçilebilir.
- Dil değişikliği uygulanırken eLuajs kendini yeniden başlatır.

## Yapay zeka asistanı

Editörde **✦ AI** düğmesine veya sağdaki AI simgesine basınca editörün sağında
docked bir panel açılır; kod alanı ortada kalır.

- Sağlayıcılar: OpenAI, Claude (Anthropic), Google Gemini, Groq, DeepSeek,
  OpenRouter, River AI ve OpenAI uyumlu özel sunucu.
- API anahtarları Ayarlar > AI Providers bölümünden girilir ve yerel olarak
  `~/.eluajs/ai.properties` dosyasında `600` izinle saklanır.
- Model listesi güncel presetleri içerir; kutu düzenlenebilir olduğu için
  sağlayıcının desteklediği herhangi bir model adı elle yazılabilir.
- AI paneli sohbet baloncuklarıyla çalışır; kod blokları ayrı gösterilir.
- AI projeyi ekonomik okur: dosya ağacını alır, ihtiyaç duyduğu dosyaları
  `READ: yol` protokolüyle parça parça ister. Böylece gereksiz token harcamaz
  ve "çok uzun" hataları oluşmaz. İstersen "AI proje dosyalarını okuyabilsin"
  işaretini kaldırıp sadece açık dosyayı gönderebilirsin.
- Gelen yanıttaki kod bloğu **Editöre ekle** veya **Seçimi değiştir** ile koda aktarılır.
- Ctrl+Alt+A paneli açar/kapatır; Ctrl+Enter promptu gönderir.

## GitHub ve otomatik güncelleme

- Ayarlar > Güncellemeler bölümünde `owner/repo` yazılır (varsayılan: `whyefkan/eLuajs`).
- "Açılışta güncellemeleri otomatik kontrol et" varsayılan olarak açıktır.
- Uygulama GitHub Releases üzerinden en son sürümü kontrol eder.
- Yeni sürüm varsa **İndir ve Kur** ile `eLuajs.jar` indirilir, `app/eLuajs.jar` değiştirilir
  ve eLuajs yeniden başlatılır. Eski jar `eLuajs.jar.bak` olarak yedeklenir.
- Release'e mutlaka `eLuajs.jar` adlı asset eklenmelidir. `.github/workflows/release.yml`
  `v*` etiketi push edildiğinde bunu otomatik üretir.
- Sürüm numarası hem `source/build.sh` hem `source/src/main/java/eluajs/util/AppInfo.java`
  içinde güncellenmelidir. Workflow jar manifest sürümünü etiketten alır; uygulama
  sürümü manifestten okur.

Yayın adımları:

```bash
git tag v1.4.0
git push origin v1.4.0
```

Workflow jar'ı derler ve GitHub Release'e `eLuajs.jar` olarak yükler.

## Derleme

```bash
cd source
./build.sh
```

JDK 17+ gerekir. Sistemde `javac` yoksa paket içindeki `runtime/bin/javac`
otomatik kullanılır. Derlenen JAR `source/out/eLuajs.jar` konumuna yazılır.

## Play Store AAB

`Derle / APK + AAB` ekranında iki ayrı çıktı vardır:

- **Debug APK:** Telefonda hızlı test / kurulum için mevcut akış.
- **Play Store AAB:** `release` varyantını `bundleRelease` ile üretir ve sizin
  upload keystore'unuzla imzalar.

### Keystore / upload key

Play Store AAB düğmesine bastığınızda eLuajs içinden:

- Var olan `.jks` / keystore dosyasını seçebilirsiniz.
- Key alias, keystore şifresi ve key şifresi girebilirsiniz.
- **Yeni Keystore Oluştur** ile eLuajs'ın paket içindeki `keytool` aracıyla yeni
  upload key oluşturabilirsiniz.
- `signing.properties Aç` ile aynı bilgileri editörde metin olarak düzenleyebilirsiniz.

Ayar dosyası:

```properties
storeFile=/home/kullanici/.eluajs/keystores/uygulama-upload-key.jks
storePassword=
keyAlias=upload
keyPassword=
```

Şifreleri arayüzden her derlemede girmek daha güvenlidir. `Şifreleri de kaydet`
seçeneği özellikle açılmazsa eLuajs şifreleri projeye yazmaz.
`release-signing.properties` otomatik olarak `.gitignore` içine eklenir.

### Çıktı

Başarılı AAB sonrasında kolay bulunan kopya şuraya yazılır:

```text
<proje>/release/<proje>-release.aab
```

Gradle'ın orijinal çıktısı da `app/build/outputs/bundle/release/app-release.aab`
altında kalır.

> Upload keystore dosyanızı güvenli şekilde yedekleyin. Google Play App Signing
> kullanılan projelerde bu anahtar upload key rolündedir. Güncelleme yayınlarken
> `versionCode` değerini artırmanız gerekir.

## Performans

- Debounce edilen syntax renklendirme ve büyük dosya koruması.
- Arka planda proje ağacı / Hub proje keşfi.
- Streaming proje araması.
- Toplu ve sınırlandırılmış Gradle günlüğü.
- Zaman aşımında process-tree temizliği.
- TLS-FIX + `jdk.crypto.ec` self-test koruması.

## Kontrol

```bash
./eLuajs --self-test
./eLuajs --diagnostics
```

Self-test `keytool`, keystore üretme/doğrulama ve release signing
yapılandırmasını da test eder.

Geliştirici: Efkwn Mobile
