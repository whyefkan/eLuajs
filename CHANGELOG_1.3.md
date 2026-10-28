# eLuajs 1.3

- VS Code tarzı yeni arayüz: activity bar, explorer kenar çubuğu, editör sekmesi,
  çıktı paneli ve alt durum çubuğu.
- Hub ekranı yenilendi; hızlı işlemler ve çift tıklayarak açılan proje listesi.
- Hub ortası artık bağlama duyarlı: proje seçilince detay + hızlı işlemler,
  seçim yokken başlangıç ekranı, ortam durumu ve ipuçları gösteriliyor.
- Üst başlıklar ve kenar çubukları ferahlatıldı; editör yan çubuğundaki işlemler
  sıkışık tek satır yerine 2x2 düzene alındı.
- Başlık çubuklarındaki marka, proje adı/paket ve butonlar artık dikeyde
  ortalanıyor; üst alandaki "dip dibe" görünüm giderildi ve satır aralıkları açıldı.
- Editörde `//` yorumundan sonra tüm dosyanın yeşile boyanmasına yol açan
  syntax highlighting hatası düzeltildi.
- String içindeki `http://` gibi ifadeler artık yorum rengine dönmüyor.
- Java `package` algılama regex'i düzeltildi.
- Daha ince kaydırma çubukları, kompakt düğmeler ve yumuşak kartlar.
- Masaüstü için tek tıkla başlatan `eLuajs` kısayolu ve `eLuajs-icon.png`.
- Sürüm numarası 1.3 olarak güncellendi.
- v0.17 performans çekirdeği, TLS-FIX ve Play Store AAB akışı korunuyor.
- Ayarlar ekranı eklendi. Varsayılan arayüz dili İngilizce; Ayarlar'dan Türkçe seçilebiliyor.
- Yapay zeka kod asistanı eklendi. Sağdaki docked panelden model seçip prompt gönderilebiliyor;
  açık dosya bağlam olarak gönderiliyor, gelen kod editöre eklenebiliyor veya seçim değiştirilebiliyor.
- Sağlayıcılar: OpenAI, Claude (Anthropic), Google Gemini, Groq, DeepSeek, OpenRouter, River AI ve
  OpenAI uyumlu özel sunucu. API anahtarları `~/.eluajs/ai.properties` içinde 600 izinle saklanıyor.
- Güncel model presetleri: GPT-5 ailesi, Claude Sonnet/Opus/Haiku 4.x, Gemini 2.5, Groq GPT-OSS,
  DeepSeek V4 Flash/Pro, OpenRouter modelleri ve River AI (DeepSeek V4 + Astra Sonnet 5.5).
- Model kutusu düzenlenebilir; istenen herhangi bir model adı elle yazılabiliyor.
- Proje ikonları artık uygulama adına göre renk/baş harf üretiyor; dosya ağacında tür ikonları var.
- Activity bar için bağımlılıksız, çizim tabanlı modern ikon seti eklendi.
- Proje listesindeki paket adları sola hizalandı.
- AI paneli sohbet (chat bubble) arayüzüne geçirildi; kod blokları ayrı renkte gösteriliyor.
- Ayarlar ekranı yeniden tasarlandı: solda kategori rayı, sağda kart tabanlı içerik; IDE temasıyla uyumlu.
- "Yeni sohbet", proje dosyası okuma izni ve yanıtı editöre ekleme/seçimi değiştirme akışı iyileştirildi.
- AI artık projeyi baştan tamamen göndermiyor: dosya ağacını görüp ihtiyaç duyduğu dosyaları
  `READ: yol` protokolüyle parça parça istiyor. Böylece "çok uzun" hataları ve gereksiz token
  kullanımı azalıyor.
- Ana pencereler ve Ayarlar artık undecorated; VS Code tarzı özel pencere kontrolleri
  (küçült / büyüt-geri yükle / kapat), sürükleme ve kenarlardan boyutlandırma eklendi.
- Üstteki Kaydet / Doğrula / Derle / AI düğmeleri ikonlu ve yeni tasarımla uyumlu hale getirildi.
