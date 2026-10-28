package eluajs.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Tiny dependency-free localization layer.
 *
 * Existing UI literals are Turkish keys with an EN translation map; new UI
 * (settings, AI assistant) is written in English with a TR translation map.
 * This keeps the migration incremental without pulling in a resource framework.
 */
public final class I18n {
    public enum Lang { EN, TR }

    private static final Map<String, String> EN = new HashMap<>(); // Turkish key -> English
    private static final Map<String, String> TR = new HashMap<>(); // English key -> Turkish
    private static Lang lang = Lang.EN;

    private I18n() {}

    public static Lang language() { return lang; }
    public static String code() { return lang == Lang.TR ? "tr" : "en"; }

    public static String t(String text, Object... args) {
        String out = switch (lang) {
            case EN -> EN.getOrDefault(text, text);
            case TR -> TR.getOrDefault(text, text);
        };
        return args.length == 0 ? out : String.format(out, args);
    }

    public static synchronized void load() {
        Path file = settingsFile();
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            String value = p.getProperty("language", "en").trim().toLowerCase();
            lang = "tr".equals(value) ? Lang.TR : Lang.EN;
        } catch (IOException ignored) { }
    }

    public static synchronized void setLanguage(Lang next) {
        lang = next == null ? Lang.EN : next;
        save();
    }

    public static synchronized void save() {
        Path file = settingsFile();
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); } catch (IOException ignored) { }
        }
        p.setProperty("language", code());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "eLuajs settings");
            }
        } catch (IOException ignored) { }
    }

    private static Path settingsFile() {
        return AppPaths.config().resolve("settings.properties");
    }

    private static void en(String tr, String english) { EN.put(tr, english); }
    private static void tr(String english, String turkish) { TR.put(english, turkish); }

    static {
        // Hub / shell
        en("Hafif Android Java IDE", "Lightweight Android Java IDE");
        en("PROJELER", "PROJECTS");
        en("Projeler", "Projects");
        en("Yeni proje", "New project");
        en("Başlangıç ekranı", "Welcome screen");
        en("Projeler klasörünü aç", "Open projects folder");
        en("Hakkında", "About");
        en("Yenile", "Refresh");
        en("Seç: detay  •  Çift tık: aç", "Select: details  •  Double-click: open");
        en("Henüz proje yok", "No projects yet");
        en("%d proje", "%d projects");
        en("Hafif Android Java IDE  •  Linux x86_64", "Lightweight Android Java IDE  •  Linux x86_64");
        en("Eski bilgisayarlarda bile hızlı çalışır. Kodla, doğrula, debug APK veya imzalı Play Store AAB al.",
                "Runs fast even on old computers. Code, validate, and get a debug APK or signed Play Store AAB.");
        en("Yeni Proje", "New Project");
        en("Projeler Klasörü", "Projects Folder");
        en("BAŞLARKEN", "GET STARTED");
        en("Proje oluştur", "Create a project");
        en("Uygulama adı, paket adı ve sürümü belirle; gerçek Gradle projesi oluşsun.",
                "Set the app name, package and version; a real Gradle project is created.");
        en("Kodla ve doğrula", "Code and validate");
        en("Java + XML dosyalarını düzenle. Ctrl+Space tamamlama, Ctrl+Shift+F arama.",
                "Edit Java + XML files. Ctrl+Space completion, Ctrl+Shift+F search.");
        en("Çıktı al", "Get output");
        en("Doğrula; debug APK veya imzalı Play Store AAB üret.",
                "Validate; produce a debug APK or a signed Play Store AAB.");
        en("ORTAM DURUMU", "ENVIRONMENT");
        en("Hazır", "Ready");
        en("Gerekli", "Required");
        en("İPUÇLARI", "TIPS");
        en("Son değişiklik  %s", "Last modified  %s");
        en("PROJE BİLGİLERİ", "PROJECT DETAILS");
        en("Sürüm", "Version");
        en("Paket adı", "Package name");
        en("Proje konumu", "Project location");
        en("Projeyi Aç  →", "Open Project  →");
        en("Klasörü Aç", "Open Folder");
        en("Sil", "Delete");
        en("APK / AAB almak için projeyi aç ve üstteki Derle düğmesini kullan.",
                "Open the project and use the Build button above to get an APK / AAB.");
        en("Projeyi Aç", "Open Project");
        en("Proje klasörü", "Project folder");
        en("Klasör açılamadı", "Could not open folder");
        en("Silme hatası", "Delete error");
        en("Projeyi Sil", "Delete Project");
        en("'%s' projesi kalıcı olarak silinsin mi?\n\n%s", "Permanently delete project '%s'?\n\n%s");
        en("eLuajs %s — Hafif Android Java IDE", "eLuajs %s — Lightweight Android Java IDE");
        en("eLuajs Hakkında", "About eLuajs");
        en("eLuajs %s\nHafif Android Java IDE\n\nGerçek Android Gradle projeleri oluşturur, doğrular,\ndebug APK ve imzalı Play Store AAB üretir.\n\nEfkwn Mobile",
                "eLuajs %s\nLightweight Android Java IDE\n\nCreates and validates real Android Gradle projects,\nand produces debug APKs and signed Play Store AABs.\n\nEfkwn Mobile");
        en("● JDK hazır", "● JDK ready");
        en("○ JDK gerekli", "○ JDK required");
        en("● Android SDK hazır", "● Android SDK ready");
        en("○ Android SDK gerekli", "○ Android SDK required");

        // Editor shell
        en("Dosya seçin", "Select a file");
        en("Satır %d, Sütun %d", "Line %d, Column %d");
        en("Kaydet", "Save");
        en("Doğrula", "Validate");
        en("Derle / APK + AAB", "Build / APK + AAB");
        en("Kaydet (Ctrl+S)", "Save (Ctrl+S)");
        en("Dosyalar", "Files");
        en("Sayfalar", "Pages");
        en("Projede ara (Ctrl+Shift+F)", "Search in project (Ctrl+Shift+F)");
        en("Assets / görsel ve ses ekle", "Assets / add images and audio");
        en("Gradle kütüphaneleri", "Gradle libraries");
        en("Android izinleri", "Android permissions");
        en("Projeler ekranına dön", "Back to projects");
        en("DOSYALAR", "FILES");
        en("SAYFALAR", "PAGES");
        en("Dosya ağacını yenile", "Refresh file tree");
        en("＋ Sayfa", "+ Page");
        en("− Sayfa", "- Page");
        en("＋ Sınıf", "+ Class");
        en("⟳ Yenile", "⟳ Refresh");
        en("Yeni Android Activity / ekran oluştur", "Create a new Android Activity / screen");
        en("Ek Android Activity / sayfayı sil", "Delete an extra Android Activity / screen");
        en("Activity olmayan normal Java sınıfı oluştur (Ctrl+Shift+N)", "Create a normal Java class without an Activity (Ctrl+Shift+N)");
        en("Bir sayfaya tıklayarak Java dosyasını aç.", "Click a page to open its Java file.");
        en("Java / Android", "Java / Android");
        en("Kaydedildi", "Saved");
        en("Değiştirildi", "Modified");
        en("Dosya açılamadı: %s", "Could not open file: %s");
        en("Kaydetme hatası", "Save error");
        en("Kaydedilmemiş değişiklik", "Unsaved changes");
        en("%s dosyasında kaydedilmemiş değişiklikler var.", "%s has unsaved changes.");
        en("Değişiklikleri At", "Discard changes");
        en("İptal", "Cancel");
        en("Yeni Java sınıfı oluşturuldu: %s", "New Java class created: %s");
        en("--- Yeni Sayfa ---", "--- New Page ---");
        en("%s.java oluşturuldu ve AndroidManifest.xml'e kaydedildi.", "%s.java was created and registered in AndroidManifest.xml.");
        en("Bu sayfayı açmak için:", "To open this page:");
        en("Yeni sayfa hazır", "New page ready");
        en("%s oluşturuldu.\nManifest kaydı otomatik eklendi.\n\nSayfalar arası geçiş örneği alt konsola yazıldı.",
                "%s was created.\nIts manifest entry was added automatically.\n\nA sample navigation snippet was written to the output panel.");
        en("--- Sayfa Silindi ---", "--- Page Deleted ---");
        en("%s.java ve Manifest kaydı kaldırıldı.", "%s.java and its manifest entry were removed.");
        en("Not: Bu sayfaya giden Intent kodları varsa onları koddan siz kaldırmalısınız.", "Note: Remove any Intent references to this page from your code.");
        en("--- Doğrulama ---", "--- Validation ---");
        en("Doğrulama başarılı", "Validation passed");
        en("Doğrulama sorunları", "Validation issues");
        en("eLuajs %s hazır — hafif VS Code tarzı çalışma alanı. Kaydet, doğrula, debug APK veya imzalı Play Store AAB al.",
                "eLuajs %s ready — lightweight VS Code style workspace. Save, validate, and get a debug APK or signed Play Store AAB.");
        en("Konuma gidildi: %s:%d", "Jumped to: %s:%d");
        en("Dosya konumu açılamadı: %s", "Could not open file location: %s");
        en("Proje ağacı yenilenemedi: %s", "Could not refresh project tree: %s");

        en("Projede ara", "Search in project");
        en("Yeni Java sınıfı", "New Java class");
        en("Kod tamamlama", "Code completion");
        en("Sayfa", "Page");
        en("Sınıf", "Class");
        en("ÇIKTI", "OUTPUT");
        en("Temizle", "Clear");
        en("Çıktıyı temizle", "Clear output");
        en("Asset ağacı yenilendi.", "Asset tree refreshed.");
        en("Gradle kütüphaneleri güncellendi.", "Gradle libraries updated.");
        en("Android izinleri güncellendi.", "Android permissions updated.");
        en("Proje ağacı yenileniyor...", "Refreshing project tree...");
        en("Kaydedildi: %s", "Saved: %s");
        en("eLuajs hazır. Dosya seçin, kodlayın, doğrulayın ve çıktı alın.", "eLuajs ready. Select a file, write code, validate and build.");

        // Common buttons / dialogs
        en("Vazgeç", "Cancel");
        en("Kapat", "Close");
        en("Ekle", "Add");
        en("Seçileni Kaldır", "Remove Selected");
        en("Seç", "Choose");
        en("＋  Dosya Seç", "+  Choose Files");
        en("Hedef", "Destination");
        en("Asset yöneticisi", "Asset manager");
        en("Görsel, ses, font, JSON veya başka dosyaları Android projenize ekleyin.", "Add images, audio, fonts, JSON or other files to your Android project.");
        en("Dosya seçin ve projeye ekleyin.", "Choose files and add them to the project.");
        en("Assets'e Ekle", "Add to Assets");
        en("Projeye eklenecek dosyaları seçin", "Choose files to add to the project");
        en("%d dosya seçildi.", "%d file(s) selected.");
        en("Önce en az bir dosya seçin.", "Choose at least one file first.");
        en("%d asset projeye eklendi.\n\n", "%d asset(s) added to the project.\n\n");
        en("Assets eklendi", "Assets added");
        en("Assetler eklendi. Yeni dosyalar seçebilirsiniz.", "Assets added. You can choose more files.");
        en("Asset ekleme hatası", "Asset import error");
        en("Görsel dosyaları (PNG/JPG)", "Image files (PNG/JPG)");

        // New project
        en("Yeni eLuajs Projesi", "New eLuajs Project");
        en("Yeni Android projesi", "New Android project");
        en("Gerçek Java + Android Gradle proje yapısı oluşturulur.", "Creates a real Java + Android Gradle project structure.");
        en("Benim Uygulamam", "My Application");
        en("Uygulama adı", "App name");
        en("Sürüm kodu", "Version code");
        en("Logo Seç", "Choose Logo");
        en("Uygulama logosu (isteğe bağlı)", "App logo (optional)");
        en("Projeyi Oluştur", "Create Project");
        en("Uygulama logosu seç", "Choose app logo");
        en("Proje oluşturulamadı", "Could not create project");

        // Activity / class dialogs
        en("Yeni Sayfa / Activity", "New Page / Activity");
        en("Yeni Android sayfası", "New Android page");
        en("eLuajs Java Activity dosyasını oluşturur ve AndroidManifest.xml'e ekler.", "Creates the Java Activity file and adds it to AndroidManifest.xml.");
        en("IkinciSayfa", "SecondPage");
        en("İkinci Sayfa", "Second Page");
        en("Sayfa / Activity adı", "Page / Activity name");
        en("Ekranda görünecek başlık", "Screen title");
        en("Örnek: IkinciSayfa → IkinciSayfaActivity.java", "Example: SecondPage → SecondPageActivity.java");
        en("＋  Sayfayı Oluştur", "+  Create Page");
        en("Sayfa oluşturulamadı", "Could not create page");
        en("Yeni Java Sınıfı", "New Java Class");
        en("Yeni Java sınıfı", "New Java class");
        en("Activity olmayan normal Java sınıfı oluşturur. Manifest değişmez.", "Creates a normal Java class without an Activity. The manifest is not changed.");
        en("Örnek: %s.data", "Example: %s.data");
        en("Sınıf adı", "Class name");
        en("Paket", "Package");
        en("＋  Sınıfı Oluştur", "+  Create Class");
        en("Sınıf oluşturulamadı", "Could not create class");
        en("Sayfa Sil", "Delete Page");
        en("Android sayfasını sil", "Delete Android page");
        en("Java dosyası ve AndroidManifest kaydı birlikte kaldırılır.", "The Java file and AndroidManifest entry are removed together.");
        en("Silinecek sayfa", "Page to delete");
        en("MainActivity güvenlik için silinemez. Bu sayfaya yazdığınız Intent referansları otomatik silinmez.", "MainActivity cannot be deleted for safety. Intent references to this page are not removed automatically.");
        en("−  Sayfayı Sil", "-  Delete Page");
        en("Sayfalar okunamadı", "Could not read pages");
        en("%s kalıcı olarak silinsin mi?\n\nJava dosyası ve Manifest kaydı kaldırılacak.", "Permanently delete %s?\n\nThe Java file and manifest entry will be removed.");
        en("Sayfayı Sil", "Delete Page");
        en("Sayfa silinemedi", "Could not delete page");

        // Libraries / permissions / search
        en("Kütüphaneler — Gradle", "Libraries — Gradle");
        en("Gradle bağımlılıkları app/build.gradle dosyasına eklenir.", "Gradle dependencies are added to app/build.gradle.");
        en("Kütüphane yöneticisi", "Library manager");
        en("Maven/Google koordinatını ekle; eLuajs kütüphanenin kodunu çevirmeden standart Gradle kullanır.", "Add a Maven/Google coordinate; eLuajs uses standard Gradle without modifying library code.");
        en("Tür", "Type");
        en("Koordinat", "Coordinate");
        en("＋ Ekle", "+ Add");
        en("Ctrl+Space: editör tamamlama", "Ctrl+Space: editor completion");
        en("build.gradle Aç", "Open build.gradle");
        en("Not: Bazı kütüphaneler ayrıca plugin, JSON dosyası veya özel Gradle ayarı isteyebilir.", "Note: Some libraries may also need a plugin, JSON file or custom Gradle configuration.");
        en("%d Gradle bağımlılığı bulundu.", "%d Gradle dependencies found.");
        en("Kütüphaneler okunamadı: %s", "Could not read libraries: %s");
        en("Eklendi: %s", "Added: %s");
        en("Kütüphane eklenemedi", "Could not add library");
        en("%s kaldırılsın mı?", "Remove %s?");
        en("Kütüphaneyi Kaldır", "Remove Library");
        en("Kütüphane kaldırılamadı", "Could not remove library");
        en("Android İzinleri", "Android Permissions");
        en("Manifest izinlerini seçin.", "Select manifest permissions.");
        en("Android izinleri", "Android permissions");
        en("Sık kullanılan izinları kutucuklardan yönet; eLuajs AndroidManifest.xml dosyasını düzenler.", "Manage common permissions with checkboxes; eLuajs edits AndroidManifest.xml.");
        en("Özel izin", "Custom permission");
        en("Listede olmayan manifest iznini tam adıyla ekleyebilirsin.", "Add a manifest permission that is not in the list by its full name.");
        en("Manifest'i Aç", "Open Manifest");
        en("İzinleri Uygula", "Apply Permissions");
        en("%d manifest izni var", "%d manifest permissions");
        en(" • %d özel", " • %d custom");
        en("İzinler okunamadı", "Could not read permissions");
        en("İzinler AndroidManifest.xml dosyasına uygulandı.", "Permissions applied to AndroidManifest.xml.");
        en("İzinler uygulanamadı", "Could not apply permissions");
        en("İzin eklenemedi", "Could not add permission");
        en("Proje İçinde Ara", "Search in Project");
        en("Aranacak metni yazın.", "Type the text to search.");
        en("⌕  Ara", "⌕  Search");
        en("Proje içinde ara", "Search in project");
        en("Java, XML, Gradle ve diğer metin dosyalarında arar. Ctrl+Shift+F", "Searches Java, XML, Gradle and other text files. Ctrl+Shift+F");
        en("Aranıyor...", "Searching...");
        en("%d sonuç", "%d results");
        en(" (ilk 500)", " (first 500)");
        en(". Çift tıklayarak açın.", ". Double-click to open.");
        en("Arama başarısız.", "Search failed.");
        en("Arama hatası", "Search error");

        // Build
        en("eLuajs — Derle / APK / AAB", "eLuajs — Build / APK / AAB");
        en("Araçları Hazırla", "Prepare Tools");
        en("▶  Debug APK", "▶  Debug APK");
        en("◆  Play Store AAB", "◆  Play Store AAB");
        en("Çıktı Klasörünü Aç", "Open Output Folder");
        en("↗  Hata Satırına Git", "↗  Go to Error Line");
        en("Android SDK lisans koşullarını kabul ediyorum", "I accept the Android SDK license terms");
        en("Android çıktısı al", "Build Android output");
        en("ANDROID BUILD", "ANDROID BUILD");
        en("TOOLCHAIN", "TOOLCHAIN");
        en("BUILD OUTPUT", "BUILD OUTPUT");
        en("Debug APK test içindir. Play Store AAB ise upload keystore ile imzalı release çıktısı üretir.", "Debug APK is for testing. Play Store AAB produces a signed release output with your upload keystore.");
        en("Derleme günlüğü burada görünecek.\nHata konumu olan bir satıra çift tıklayarak editörde açabilirsiniz.\n", "The build log will appear here.\nDouble-click a line with an error location to open it in the editor.\n");
        en("Kontrol...", "Checking...");
        en("● Hazır", "● Ready");
        en("○ Eksik", "○ Missing");
        en("Lisans onayı gerekli", "License acceptance required");
        en("JDK eksik", "JDK missing");
        en("İlk kurulum", "First setup");
        en("signing.properties açılamadı: %s", "Could not open signing.properties: %s");
        en("Hata", "Error");
        en("Play Store AAB hazır", "Play Store AAB ready");
        en("Derleme başarılı", "Build successful");
        en("İşlem başarısız", "Operation failed");
        en("Klasör açılamadı: %s", "Could not open folder: %s");

        // Release signing
        en("eLuajs — Play Store AAB", "eLuajs — Play Store AAB");
        en("Upload key bilgilerini girin veya yeni keystore oluşturun.", "Enter upload key details or create a new keystore.");
        en("Play Store release AAB", "Play Store release AAB");
        en("AAB, seçtiğiniz upload keystore ile imzalanır. Debug imzası kullanılmaz.", "The AAB is signed with your upload keystore. The debug key is not used.");
        en("Önemli: Bu keystore'un güvenli bir yedeğini alın. Play App Signing kullanırken bu anahtar upload key olarak kullanılır.", "Important: Back up this keystore securely. With Play App Signing this key acts as the upload key.");
        en("＋ Yeni Keystore Oluştur", "+ Create New Keystore");
        en("</> signing.properties Aç", "</> Open signing.properties");
        en("▶  İmzalı AAB Oluştur", "▶  Create Signed AAB");
        en("Kaydedilmiş signing bilgileri yüklendi.", "Saved signing details loaded.");
        en("Keystore yolu ve alias yüklendi; şifreleri girin.", "Keystore path and alias loaded; enter the passwords.");
        en("Güvenlik için mevcut keystore'un üzerine yazılmıyor. Başka bir dosya adı seçin.", "Existing keystores are not overwritten for safety. Choose another file name.");
        en("Dosya zaten var", "File already exists");
        en("Yeni keystore için iki şifre de en az 6 karakter olmalıdır.", "Both passwords must be at least 6 characters for a new keystore.");
        en("Şifre gerekli", "Password required");
        en("Sertifika adı (CN):", "Certificate name (CN):");
        en("Keystore oluşturuluyor...", "Creating keystore...");
        en("Keystore hazır. Güvenli bir yedeğini almayı unutmayın.", "Keystore ready. Remember to keep a secure backup.");
        en("Keystore hazır", "Keystore ready");
        en("Keystore oluşturulamadı.", "Could not create keystore.");
        en("Keystore hatası", "Keystore error");
        en("Ayar dosyası: %s", "Settings file: %s");
        en("Dosya açılamadı", "Could not open file");
        en("İmzalama bilgileri eksik", "Signing details are incomplete");

        // Syntax editor
        en("Java dosyalarında Ctrl+Space ile kod tamamlama ve temel otomatik import", "Ctrl+Space code completion and basic auto-import for Java files");
        en("Büyük dosya: performans için syntax renklendirme geçici olarak sınırlandı.", "Large file: syntax coloring is temporarily limited for performance.");

        en("Ara", "Search");
        en("Debug APK", "Debug APK");
        en("Play Store AAB", "Play Store AAB");
        en("Hata Satırına Git", "Go to Error Line");
        en("Dosya Seç", "Choose Files");
        en("Projeye Dosya Ekle", "Add Files to Project");
        en("Sayfayı Oluştur", "Create Page");
        en("Sayfayı Sil", "Delete Page");
        en("Sınıfı Oluştur", "Create Class");
        en("Silinebilecek ek sayfa yok", "No extra pages to delete");
        en("Örnek: PdfManager, UserRepository, StepCounter", "Example: PdfManager, UserRepository, StepCounter");
        en("Kaynak dosya:Satır içeren hata satırına çift tıklayın", "Double-click an error line containing SourceFile:Line");
        en("Key alias", "Key alias");
        en("Keystore / upload key dosyası", "Keystore / upload key file");
        en("Keystore şifresi", "Keystore password");
        en("Anahtar şifresi", "Key password");
        en("Keystore yolu + alias’ı bu projede hatırla", "Remember keystore path + alias for this project");
        en("Şifreleri de kaydet (önerilmez)", "Save passwords too (not recommended)");
        en("Yeni Keystore Oluştur", "Create New Keystore");
        en("signing.properties Aç", "Open signing.properties");
        en("İmzalı AAB Oluştur", "Create Signed AAB");
        en("Upload keystore seç", "Choose upload keystore");
        en("Yeni upload keystore kaydet", "Save new upload keystore");
        en("İsterseniz aynı bilgileri proje içindeki ayar dosyasına elle yazabilirsiniz.", "You can also write the same details manually into the project settings file.");
        en("Upload keystore oluşturuldu:\n%s\n\nBu dosyanın güvenli bir yedeğini alın.", "Upload keystore created:\n%s\n\nKeep a secure backup of this file.");
        en("İmzalı release AAB başarıyla oluşturuldu:\n%s\n\nBu dosya Google Play Console'a yüklenmek için hazırlanmıştır.", "Signed release AAB created successfully:\n%s\n\nThis file is ready to upload to Google Play Console.");
        en("Debug APK başarıyla oluşturuldu:\n%s", "Debug APK created successfully:\n%s");
        en("İmzalı release AAB başarıyla oluşturuldu:\n%s\n\nBu dosya Google Play Console'a yüklenmek için hazırlanmıştır.", "Signed release AAB created successfully:\n%s\n\nThis file is ready to upload to Google Play Console.");
        en("JDK içindeki jlink aracı kullanılamadı. eLuajs paketini yeniden çıkarın veya güncel paketi kullanın.\n\n%s", "The jlink tool inside the JDK could not be used. Extract the eLuajs package again or use a newer package.\n\n%s");
        en("\n\nKaynak konumu bulundu. 'Hata Satırına Git' düğmesini kullanabilirsiniz.", "\n\nSource location found. You can use the 'Go to Error Line' button.");
        en("Android SDK eksik. SDK bileşenlerini indirebilmek için lisans kutusunu işaretlemeniz gerekir.", "Android SDK is missing. You must accept the license to download the SDK components.");
        en("eLuajs JDK araçları eksik. java, javac ve jlink birlikte bulunmalıdır.\nBu paket bozuk veya eksik çıkarılmış olabilir.", "eLuajs JDK tools are missing. java, javac and jlink must be present.\nThis package may be corrupt or incomplete.");
        en("İlk APK derlemesi için Android SDK kurulacak. Önce lisans kutusunu işaretleyin; ardından APK Derle'ye tekrar basın.", "Android SDK will be installed for the first APK build. Accept the license first, then press Build APK again.");
        en("eLuajs JDK araçları eksik. Release AAB için java, javac, jlink ve keytool gereklidir.", "eLuajs JDK tools are missing. java, javac, jlink and keytool are required for release AAB.");
        en("İlk AAB derlemesi için Android SDK kurulacak. Önce lisans kutusunu işaretleyin.", "Android SDK will be installed for the first AAB build. Accept the license first.");
        en("Bağlantı başarılı: %s", "Connection OK: %s");
        en("Bağlantı başarısız: %s", "Connection failed: %s");
        en("İstek başarısız: %s", "Request failed: %s");
        en("Yanıt burada görünecek.", "The response will appear here.");
        en("Düşünüyor...", "Thinking...");
        en("Yanıtta kod bloğu bulunamadı.", "No code block found in the response.");
        en("Kod imlecin bulunduğu yere eklendi.", "Code inserted at the cursor.");
        en("Seçim üretilen kodla değiştirildi.", "Selection replaced with the generated code.");
        en("Modelden kod yazmasını veya değiştirmesini isteyin. Açık dosya bağlam olarak gönderilir.", "Ask the model to write or change code. The open file is sent as context.");
        en("API anahtarı ayarlı değil. Ayarlar'dan ekleyin.", "No API key configured. Add one in Settings.");
        en("Yapay Zeka Asistanı", "AI Assistant");
        en("Yapay Zeka Asistanı (Ctrl+Alt+A)", "AI Assistant (Ctrl+Alt+A)");
        en("Açık dosyayı bağlam olarak gönder", "Include current file as context");
        en("Yerel anahtarlar, çağırdığınız sağlayıcı dışında hiçbir yere yüklenmez.", "Local keys are never uploaded anywhere except to the provider you call.");
        en("Dili uygulamak için eLuajs yeniden başlatılıyor...", "Restarting eLuajs to apply the language...");
        en("Ayarlar açılamadı.", "Could not open Settings.");
        en("eLuajs Ayarları", "eLuajs Settings");
        en("Ayarlar", "Settings");
        en("Genel", "General");
        en("Yapay Zeka Sağlayıcıları", "AI Providers");
        en("Dil", "Language");
        en("Arayüz dilini seçin.", "Choose the interface language.");
        en("İngilizce (varsayılan)", "English (default)");
        en("Türkçe", "Turkish");
        en("Dil değişikliği eLuajs yeniden başlatıldığında uygulanır.", "Language changes are applied when you restart eLuajs.");
        en("Sağlayıcı", "Provider");
        en("Model", "Model");
        en("API anahtarı", "API key");
        en("Sunucu adresi (isteğe bağlı)", "Base URL (optional)");
        en("Göster", "Show");
        en("Bağlantıyı test et", "Test connection");
        en("Kaydet", "Save");
        en("Vazgeç", "Cancel");
        en("Kayıtlı sağlayıcılar", "Saved providers");
        en("Ayarlar kaydedildi.", "Settings saved.");
        en("Test mesajı", "Test message");
        en("API anahtarını sağlayıcının sitesinden alın. Anahtarlar yerel olarak ~/.eluajs/ai.properties içinde saklanır.", "Get an API key from the provider's website. Keys are stored locally in ~/.eluajs/ai.properties.");
        en("Bu sağlayıcı için kayıtlı API anahtarı yok.", "No API key saved for this provider.");
        en("İstek", "Prompt");
        en("Gönder", "Send");
        en("Durdur", "Stop");
        en("Editöre ekle", "Insert into editor");
        en("Seçimi değiştir", "Replace selection");
        en("Kopyala", "Copy");
        en("Temizle", "Clear");
        en("Sistem istemi", "System prompt");
        en("Sıfırla", "Reset");
        en("Varsayılan sağlayıcı", "Default provider");
        en("Yapay Zeka", "AI");
        en("Uygula", "Apply");
        en("Yapay zeka ayarları", "AI settings");
        en("Hazır", "Ready");
        en("Bu sağlayıcı için model adı girin.", "Enter a model name for this provider.");

        en("Büyük/küçük harf duyarlı", "Case sensitive");
        en("Araçlar hazırlanıyor", "Preparing tools");
        en("APK derleniyor", "Building APK");
        en("Play Store release AAB derleniyor", "Building Play Store release AAB");
        en("HATA: ", "ERROR: ");
        en("AI deleted the open file: %s", "AI deleted the open file: %s");
        en("AI updated project files. The open file has unsaved changes, so it was not reloaded automatically.", "AI project files were updated. The open file has unsaved changes, so it was not reloaded automatically.");
        en("AI updated: %s", "AI updated: %s");
        en("Could not reload the file after AI edits: %s", "Could not reload the file after AI edits: %s");
        tr("Replace file", "Dosyayı değiştir");
        tr("Replace the whole open file with the generated code.", "Açık dosyanın tamamını üretilen kodla değiştir.");
        tr("Replace only the selected text with the generated code.", "Sadece seçili metni üretilen kodla değiştir.");
        tr("Code applied to the file. Save to keep it.", "Kod dosyaya uygulandı. Kalıcı olması için kaydet.");
        tr("Let AI read and edit project files", "AI proje dosyalarını okuyup düzenleyebilsin");
        tr("AI can read and write project files directly. Reads happen on demand to save tokens.", "AI proje dosyalarını doğrudan okuyup yazabilir. Token tasarrufu için okumalar ihtiyaç anında yapılır.");
        tr("Ask me to inspect or change the project. I can read and edit files directly, and token use stays economical.", "Projeyi incelememi veya değiştirmemi iste. Dosyaları doğrudan okuyup düzenleyebilirim; token kullanımı ekonomik kalır.");
        tr("Ask me to change the project. I will edit the files automatically and tell you what changed.", "Projeyi değiştirmemi iste. Dosyaları otomatik düzenlerim ve neyi değiştirdiğimi söylerim.");
        tr("Inspect project", "Projeyi incele");
        tr("Fix a problem", "Bir hatayı düzelt");
        tr("Add a new screen", "Yeni ekran ekle");
        tr("Inspect the project and summarize its structure.", "Projeyi incele ve yapısını özetle.");
        tr("Find and fix any obvious errors in the project.", "Projedeki belirgin hataları bul ve düzelt.");
        tr("Create a new Android activity screen for this project.", "Bu proje için yeni bir Android activity ekranı oluştur.");
        tr("Send (Enter)", "Gönder (Enter)");
        tr("Enter: send  •  Shift+Enter: new line", "Enter: gönder  •  Shift+Enter: yeni satır");
        tr("Updated %s.", "%s güncellendi.");
        tr("Writing: %s", "Yazılıyor: %s");
        tr("Updated: %s", "Güncellendi: %s");
        tr("Deleting: %s", "Siliniyor: %s");
        tr("Deleted: %s", "Silindi: %s");
        tr("Undo AI edits", "AI değişikliklerini geri al");
        tr("Restore the files changed by the last AI edit.", "Son AI düzenlemesinin değiştirdiği dosyaları geri yükle.");
        tr("AI edits undone.", "AI değişiklikleri geri alındı.");
        tr("AI could not apply changes: %s", "AI değişiklikleri uygulayamadı: %s");
        tr("No files were changed.", "Hiçbir dosya değiştirilmedi.");
        tr("Updated %d files.", "%d dosya güncellendi.");
        tr("Skipped: %s", "Atlandı: %s");
        tr("Undo failed: %s", "Geri alma başarısız: %s");
        // Settings (English keys)
        tr("API key saved.", "API anahtarı kayıtlı.");
        tr("Base URL is required for this provider.", "Bu sağlayıcı için sunucu adresi gerekli.");
        tr("Copied to clipboard.", "Panoya kopyalandı.");
        tr("Empty response from the provider.", "Sağlayıcıdan boş yanıt geldi.");
        tr("Ready.", "Hazır.");
        tr("Response", "Yanıt");
        tr("Enter a model name for this provider.", "Bu sağlayıcı için model adı girin.");
        tr("No provider", "Sağlayıcı yok");
        tr("Language", "Dil");
        tr("AI Providers", "Yapay Zeka Sağlayıcıları");
        tr("English (default)", "İngilizce (varsayılan)");
        tr("Turkish", "Türkçe");
        tr("Language changes are applied when you restart eLuajs.", "Dil değişikliği eLuajs yeniden başlatıldığında uygulanır.");
        tr("OpenRouter / Groq / OpenAI / Claude / Gemini / River AI", "OpenRouter / Groq / OpenAI / Claude / Gemini / River AI");
        tr("Local keys are never uploaded anywhere except to the provider you call.", "Yerel anahtarlar, çağırdığınız sağlayıcı dışında hiçbir yere yüklenmez.");
        tr("Provider", "Sağlayıcı");
        tr("Model", "Model");
        tr("Base URL (optional)", "Sunucu adresi (isteğe bağlı)");
        tr("API key", "API anahtarı");
        tr("Show", "Göster");
        tr("Test connection", "Bağlantıyı test et");
        tr("Thinking...", "Düşünüyor...");
        tr("Connection OK: %s", "Bağlantı başarılı: %s");
        tr("Connection failed: %s", "Bağlantı başarısız: %s");
        tr("No API key saved for this provider.", "Bu sağlayıcı için kayıtlı API anahtarı yok.");
        tr("Saved providers", "Kayıtlı sağlayıcılar");
        tr("Get an API key from the provider's website. Keys are stored locally in ~/.eluajs/ai.properties.", "API anahtarını sağlayıcının sitesinden alın. Anahtarlar yerel olarak ~/.eluajs/ai.properties içinde saklanır.");
        tr("Save", "Kaydet");
        tr("Cancel", "Vazgeç");
        tr("General", "Genel");
        tr("Provider / Model", "Sağlayıcı / Model");
        tr("API keys are in Settings.", "API anahtarları Ayarlar'da.");
        tr("Chat history", "Sohbet geçmişi");
        tr("No saved chats yet.", "Henüz kayıtlı sohbet yok.");
        tr("Restored chat: %s", "Sohbet geri yüklendi: %s");
        tr("AI Assistant", "Yapay Zeka Asistanı");
        tr("Include current file as context", "Açık dosyayı bağlam olarak gönder");
        tr("Prompt", "İstek");
        tr("Send", "Gönder");
        tr("Clear", "Temizle");
        tr("Insert into editor", "Editöre ekle");
        tr("Replace selection", "Seçimi değiştir");
        tr("Copy", "Kopyala");
        tr("Close", "Kapat");
        tr("No API key configured. Open Settings and add one.", "API anahtarı ayarlı değil. Ayarlar'dan ekleyin.");
        tr("Request failed: %s", "İstek başarısız: %s");
        tr("Code inserted at the cursor.", "Kod imlecin bulunduğu yere eklendi.");
        tr("Selection replaced with the generated code.", "Seçim üretilen kodla değiştirildi.");
        tr("AI", "Yapay Zeka");
        tr("You can type any model supported by the provider.", "Sağlayıcının desteklediği herhangi bir model adını yazabilirsiniz.");
        tr("Configure language and AI provider settings.", "Dil ve yapay zeka sağlayıcı ayarlarını yapılandırın.");
        tr("Let AI read project files", "AI proje dosyalarını okuyabilsin");
        tr("AI sees the file tree and reads files on demand, keeping token use low.", "AI dosya ağacını görür ve dosyaları ihtiyaç oldukça okur; token kullanımı düşük kalır.");
        tr("Ask me to inspect the project, write code, or fix an error. I can read project files on demand, so token use stays economical.", "Projeyi incelememi, kod yazmamı veya bir hatayı düzeltmemi iste. Proje dosyalarını ihtiyaç oldukça okuyabilirim; böylece token kullanımı ekonomik kalır.");
        tr("Session: no usage yet", "Oturum: henüz kullanım yok");
        tr("Session: %s tokens • ~%s", "Oturum: %s token • ~%s");
        tr("%s in / %s out • ~%s", "%s giriş / %s çıkış • ~%s");
        tr("Approximate cost based on public list prices.", "Herkese açık liste fiyatlarına göre yaklaşık maliyet.");
        tr("Reading: %s", "Okunuyor: %s");
        tr("Include whole project", "Tüm projeyi dahil et");
        tr("Sends the file tree and text files of the whole project.", "Tüm projenin dosya ağacını ve metin dosyalarını gönderir.");
        tr("New chat", "Yeni sohbet");
        tr("Start a new conversation", "Yeni bir sohbet başlat");
        tr("Ask me to inspect the whole project, write code, or fix an error. I receive a snapshot of all text files in your project.", "Tüm projeyi incelememi, kod yazmamı veya bir hatayı düzeltmemi iste. Projedeki tüm metin dosyalarının bir anlık görüntüsünü alıyorum.");
        tr("You", "Sen");
        tr("eLuajs AI", "eLuajs AI");
        tr("Reading project...", "Proje okunuyor...");
        tr("Thinking...", "Düşünüyor...");
        tr("Language and basic preferences.", "Dil ve temel tercihler.");
        tr("Appearance", "Görünüm");
        tr("Lightweight VS Code style dark theme. Built for old computers.", "Eski bilgisayarlar için hafif, VS Code tarzı koyu tema.");
        tr("Active provider", "Etkin sağlayıcı");
        tr("Connect a provider and choose a model.", "Bir sağlayıcı bağlayın ve model seçin.");
        tr("Keys are stored locally in ~/.eluajs/ai.properties and are never uploaded except to the provider you call.", "Anahtarlar yerel olarak ~/.eluajs/ai.properties içinde saklanır ve çağırdığınız sağlayıcı dışında hiçbir yere yüklenmez.");
        tr("Official repository:", "Resmi depo:");
        tr("Official repository", "Resmi depo");
        tr("Updates always come from the official eLuajs repository.", "Güncellemeler her zaman resmi eLuajs deposundan gelir.");
        tr("No release has been published yet.", "Henüz yayınlanmış bir sürüm yok.");
        tr("GitHub API rate limit reached. Try again later.", "GitHub API sınırına ulaşıldı. Daha sonra tekrar deneyin.");
        tr("GitHub returned HTTP %d", "GitHub HTTP %d döndürdü");
        tr("Latest release has no tag", "En son sürümün etiketi yok");
        tr("No eLuajs.jar asset in the release", "Sürümde eLuajs.jar dosyası yok");
        tr("Download failed: HTTP %d", "İndirme başarısız: HTTP %d");
        tr("Downloaded file is not a valid eLuajs jar", "İndirilen dosya geçerli bir eLuajs jar'ı değil");
        tr("Launcher and bundled runtime not found", "Başlatıcı ve paket içi çalışma ortamı bulunamadı");
        tr("This update mode needs a packaged eLuajs.jar", "Bu güncelleme modu paketlenmiş bir eLuajs.jar gerektiriyor");
        tr("Official repository is invalid", "Resmi depo geçersiz");
        tr("Updates", "Güncellemeler");
        tr("Check GitHub releases and update eLuajs.", "GitHub sürümlerini kontrol edin ve eLuajs'ı güncelleyin.");
        tr("GitHub repository", "GitHub deposu");
        tr("Use owner/repo, for example whyefkan/eLuajs.", "owner/repo biçiminde yazın; örnek: whyefkan/eLuajs.");
        tr("Check for updates automatically on startup", "Açılışta güncellemeleri otomatik kontrol et");
        tr("Current version: %s", "Mevcut sürüm: %s");
        tr("Check now", "Şimdi kontrol et");
        tr("Checking for updates...", "Güncellemeler kontrol ediliyor...");
        tr("You are up to date. (v%s)", "Güncel sürümü kullanıyorsunuz. (v%s)");
        tr("Update available: v%s", "Güncelleme mevcut: v%s");
        tr("Update check failed: %s", "Güncelleme kontrolü başarısız: %s");
        tr("How updates work", "Güncellemeler nasıl çalışır?");
        tr("Releases must include an eLuajs.jar asset. The app downloads it, replaces app/eLuajs.jar, and restarts.", "Sürümlerde eLuajs.jar dosyası olmalıdır. Uygulama dosyayı indirir, app/eLuajs.jar dosyasını değiştirir ve yeniden başlar.");
        tr("Update available", "Güncelleme mevcut");
        tr("A new eLuajs version is available.", "Yeni bir eLuajs sürümü mevcut.");
        tr("Current version: %s    →    Latest version: %s", "Mevcut sürüm: %s    →    En son sürüm: %s");
        tr("This release has no eLuajs.jar asset. Open the release page to update manually.", "Bu sürümde eLuajs.jar dosyası yok. Elle güncellemek için sürüm sayfasını açın.");
        tr("Release notes", "Sürüm notları");
        tr("Download & Install", "İndir ve Kur");
        tr("Open release page", "Sürüm sayfasını aç");
        tr("Later", "Daha sonra");
        tr("Could not open release page.", "Sürüm sayfası açılamadı.");
        tr("This will update eLuajs and restart it. Save any unsaved work first.", "Bu işlem eLuajs'ı güncelleyip yeniden başlatacak. Önce kaydedilmemiş çalışmalarınızı kaydedin.");
        tr("Install update", "Güncellemeyi kur");
        tr("Installing update", "Güncelleme kuruluyor");
        tr("Downloading and installing the update...", "Güncelleme indiriliyor ve kuruluyor...");
        tr("Update failed: %s", "Güncelleme başarısız: %s");
        tr("Update", "Güncelleme");
        tr("Settings", "Ayarlar");
        tr("eLuajs Settings", "eLuajs Ayarları");
        tr("AI Assistant (Ctrl+Alt+A)", "Yapay Zeka Asistanı (Ctrl+Alt+A)");
        tr("Restarting eLuajs to apply the language...", "Dili uygulamak için eLuajs yeniden başlatılıyor...");
        tr("Could not open Settings.", "Ayarlar açılamadı.");
        tr("You can type any model supported by the provider.", "Sağlayıcının desteklediği herhangi bir model adını yazabilirsiniz.");
        tr("Configure language and AI provider settings.", "Dil ve yapay zeka sağlayıcı ayarlarını yapılandırın.");
        tr("Let AI read project files", "AI proje dosyalarını okuyabilsin");
        tr("AI sees the file tree and reads files on demand, keeping token use low.", "AI dosya ağacını görür ve dosyaları ihtiyaç oldukça okur; token kullanımı düşük kalır.");
        tr("Ask me to inspect the project, write code, or fix an error. I can read project files on demand, so token use stays economical.", "Projeyi incelememi, kod yazmamı veya bir hatayı düzeltmemi iste. Proje dosyalarını ihtiyaç oldukça okuyabilirim; böylece token kullanımı ekonomik kalır.");
        tr("Session: no usage yet", "Oturum: henüz kullanım yok");
        tr("Session: %s tokens • ~%s", "Oturum: %s token • ~%s");
        tr("%s in / %s out • ~%s", "%s giriş / %s çıkış • ~%s");
        tr("Approximate cost based on public list prices.", "Herkese açık liste fiyatlarına göre yaklaşık maliyet.");
        tr("Reading: %s", "Okunuyor: %s");
        tr("Include whole project", "Tüm projeyi dahil et");
        tr("Sends the file tree and text files of the whole project.", "Tüm projenin dosya ağacını ve metin dosyalarını gönderir.");
        tr("New chat", "Yeni sohbet");
        tr("Start a new conversation", "Yeni bir sohbet başlat");
        tr("Ask me to inspect the whole project, write code, or fix an error. I receive a snapshot of all text files in your project.", "Tüm projeyi incelememi, kod yazmamı veya bir hatayı düzeltmemi iste. Projedeki tüm metin dosyalarının bir anlık görüntüsünü alıyorum.");
        tr("You", "Sen");
        tr("eLuajs AI", "eLuajs AI");
        tr("Reading project...", "Proje okunuyor...");
        tr("Thinking...", "Düşünüyor...");
        tr("Language and basic preferences.", "Dil ve temel tercihler.");
        tr("Appearance", "Görünüm");
        tr("Lightweight VS Code style dark theme. Built for old computers.", "Eski bilgisayarlar için hafif, VS Code tarzı koyu tema.");
        tr("Active provider", "Etkin sağlayıcı");
        tr("Connect a provider and choose a model.", "Bir sağlayıcı bağlayın ve model seçin.");
        tr("Keys are stored locally in ~/.eluajs/ai.properties and are never uploaded except to the provider you call.", "Anahtarlar yerel olarak ~/.eluajs/ai.properties içinde saklanır ve çağırdığınız sağlayıcı dışında hiçbir yere yüklenmez.");
        tr("Official repository:", "Resmi depo:");
        tr("Official repository", "Resmi depo");
        tr("Updates always come from the official eLuajs repository.", "Güncellemeler her zaman resmi eLuajs deposundan gelir.");
        tr("No release has been published yet.", "Henüz yayınlanmış bir sürüm yok.");
        tr("GitHub API rate limit reached. Try again later.", "GitHub API sınırına ulaşıldı. Daha sonra tekrar deneyin.");
        tr("GitHub returned HTTP %d", "GitHub HTTP %d döndürdü");
        tr("Latest release has no tag", "En son sürümün etiketi yok");
        tr("No eLuajs.jar asset in the release", "Sürümde eLuajs.jar dosyası yok");
        tr("Download failed: HTTP %d", "İndirme başarısız: HTTP %d");
        tr("Downloaded file is not a valid eLuajs jar", "İndirilen dosya geçerli bir eLuajs jar'ı değil");
        tr("Launcher and bundled runtime not found", "Başlatıcı ve paket içi çalışma ortamı bulunamadı");
        tr("This update mode needs a packaged eLuajs.jar", "Bu güncelleme modu paketlenmiş bir eLuajs.jar gerektiriyor");
        tr("Official repository is invalid", "Resmi depo geçersiz");
        tr("Updates", "Güncellemeler");
        tr("Check GitHub releases and update eLuajs.", "GitHub sürümlerini kontrol edin ve eLuajs'ı güncelleyin.");
        tr("GitHub repository", "GitHub deposu");
        tr("Use owner/repo, for example whyefkan/eLuajs.", "owner/repo biçiminde yazın; örnek: whyefkan/eLuajs.");
        tr("Check for updates automatically on startup", "Açılışta güncellemeleri otomatik kontrol et");
        tr("Current version: %s", "Mevcut sürüm: %s");
        tr("Check now", "Şimdi kontrol et");
        tr("Checking for updates...", "Güncellemeler kontrol ediliyor...");
        tr("You are up to date. (v%s)", "Güncel sürümü kullanıyorsunuz. (v%s)");
        tr("Update available: v%s", "Güncelleme mevcut: v%s");
        tr("Update check failed: %s", "Güncelleme kontrolü başarısız: %s");
        tr("How updates work", "Güncellemeler nasıl çalışır?");
        tr("Releases must include an eLuajs.jar asset. The app downloads it, replaces app/eLuajs.jar, and restarts.", "Sürümlerde eLuajs.jar dosyası olmalıdır. Uygulama dosyayı indirir, app/eLuajs.jar dosyasını değiştirir ve yeniden başlar.");
        tr("Update available", "Güncelleme mevcut");
        tr("A new eLuajs version is available.", "Yeni bir eLuajs sürümü mevcut.");
        tr("Current version: %s    →    Latest version: %s", "Mevcut sürüm: %s    →    En son sürüm: %s");
        tr("This release has no eLuajs.jar asset. Open the release page to update manually.", "Bu sürümde eLuajs.jar dosyası yok. Elle güncellemek için sürüm sayfasını açın.");
        tr("Release notes", "Sürüm notları");
        tr("Download & Install", "İndir ve Kur");
        tr("Open release page", "Sürüm sayfasını aç");
        tr("Later", "Daha sonra");
        tr("Could not open release page.", "Sürüm sayfası açılamadı.");
        tr("This will update eLuajs and restart it. Save any unsaved work first.", "Bu işlem eLuajs'ı güncelleyip yeniden başlatacak. Önce kaydedilmemiş çalışmalarınızı kaydedin.");
        tr("Install update", "Güncellemeyi kur");
        tr("Installing update", "Güncelleme kuruluyor");
        tr("Downloading and installing the update...", "Güncelleme indiriliyor ve kuruluyor...");
        tr("Update failed: %s", "Güncelleme başarısız: %s");
        tr("Update", "Güncelleme");
        tr("Settings", "Ayarlar");
        tr("General", "Genel");
        tr("AI Providers", "Yapay Zeka Sağlayıcıları");
        tr("Language", "Dil");
        tr("Choose the interface language.", "Arayüz dilini seçin.");
        tr("English (default)", "İngilizce (varsayılan)");
        tr("Turkish", "Türkçe");
        tr("Language changes are applied when you restart eLuajs.", "Dil değişikliği eLuajs yeniden başlatıldığında uygulanır.");
        tr("Provider", "Sağlayıcı");
        tr("Model", "Model");
        tr("API key", "API anahtarı");
        tr("Base URL (optional)", "Sunucu adresi (isteğe bağlı)");
        tr("Show", "Göster");
        tr("Test connection", "Bağlantıyı test et");
        tr("Save", "Kaydet");
        tr("Cancel", "Vazgeç");
        tr("Saved providers", "Kayıtlı sağlayıcılar");
        tr("Settings saved.", "Ayarlar kaydedildi.");
        tr("Test message", "Test mesajı");
        tr("Connection OK: %s", "Bağlantı başarılı: %s");
        tr("Connection failed: %s", "Bağlantı başarısız: %s");
        tr("Get an API key from the provider's website. Keys are stored locally in ~/.eluajs/ai.properties.", "API anahtarını sağlayıcının sitesinden alın. Anahtarlar yerel olarak ~/.eluajs/ai.properties içinde saklanır.");
        tr("No API key saved for this provider.", "Bu sağlayıcı için kayıtlı API anahtarı yok.");
        tr("Provider / Model", "Sağlayıcı / Model");
        tr("API keys are in Settings.", "API anahtarları Ayarlar'da.");
        tr("Chat history", "Sohbet geçmişi");
        tr("No saved chats yet.", "Henüz kayıtlı sohbet yok.");
        tr("Restored chat: %s", "Sohbet geri yüklendi: %s");
        tr("AI Assistant", "Yapay Zeka Asistanı");
        tr("Ask the model to write or change code. The current file is sent as context.", "Modelden kod yazmasını veya değiştirmesini isteyin. Açık dosya bağlam olarak gönderilir.");
        tr("Prompt", "İstek");
        tr("Send", "Gönder");
        tr("Stop", "Durdur");
        tr("Insert into editor", "Editöre ekle");
        tr("Replace selection", "Seçimi değiştir");
        tr("Copy", "Kopyala");
        tr("Clear", "Temizle");
        tr("The response will appear here.", "Yanıt burada görünecek.");
        tr("No API key configured. Open Settings and add one.", "API anahtarı ayarlı değil. Ayarlar'dan ekleyin.");
        tr("Thinking...", "Düşünüyor...");
        tr("Request failed: %s", "İstek başarısız: %s");
        tr("No code block found in the response.", "Yanıtta kod bloğu bulunamadı.");
        tr("Code inserted at the cursor.", "Kod imlecin bulunduğu yere eklendi.");
        tr("Selection replaced with the generated code.", "Seçim üretilen kodla değiştirildi.");
        tr("AI Assistant (Ctrl+Alt+A)", "Yapay Zeka Asistanı (Ctrl+Alt+A)");
        tr("Include current file as context", "Açık dosyayı bağlam olarak gönder");
        tr("System prompt", "Sistem istemi");
        tr("Reset", "Sıfırla");
        tr("OpenRouter / Groq / OpenAI / Claude / Gemini / River AI", "OpenRouter / Groq / OpenAI / Claude / Gemini / River AI");
        tr("Default provider for the AI assistant.", "Yapay zeka asistanı için varsayılan sağlayıcı.");
        tr("Select the model name. You can type any model supported by the provider.", "Model adını seçin. Sağlayıcının desteklediği herhangi bir modeli yazabilirsiniz.");
        tr("Apply", "Uygula");
        tr("AI settings", "Yapay zeka ayarları");
        tr("Local keys are never uploaded anywhere except to the provider you call.", "Yerel anahtarlar, çağırdığınız sağlayıcı dışında hiçbir yere yüklenmez.");
        tr("Restarting eLuajs to apply the language...", "Dili uygulamak için eLuajs yeniden başlatılıyor...");
        tr("Could not open Settings.", "Ayarlar açılamadı.");
        tr("eLuajs Settings", "eLuajs Ayarları");
    }
}
