# eLuajs 0.17 — Performance Core

- TLS-FIX tabanı korunarak 0.17'ye yükseltildi.
- Hub proje taraması asenkron hale getirildi.
- Editör proje ağacı asenkron hale getirildi ve eski refresh sonuçları generation kontrolüyle engellendi.
- Syntax highlighting 120 ms debounce kullanıyor.
- 1.5M karakter üzerindeki dosyalarda ağır syntax pass otomatik sınırlandırılıyor.
- Ctrl+Space identifier taraması imleç çevresindeki 120K karakterlik pencereye indirildi.
- Proje araması streaming hale getirildi; `readAllLines` ve tüm dosya listesini `toList` kullanımı kaldırıldı.
- Build logu ConcurrentLinkedQueue + Swing Timer ile batch ediliyor.
- Build logu 2M karakter civarında sınırlandırılıyor.
- Process timeout/interruption durumunda child process tree temizleniyor.
- HTTP istemcisi tekrar kullanılıyor; eski `.part` dosyası indirmeden önce temizleniyor.
- Launcher Xms 128 MB -> 64 MB.
- `jdk.crypto.ec` self-test ve `javaReady()` kontrolüne eklendi.
