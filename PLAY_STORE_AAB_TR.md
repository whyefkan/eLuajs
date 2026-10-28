# eLuajs v1.3 — Play Store AAB Kullanımı

1. Projeyi açın.
2. `Derle / APK + AAB` düğmesine basın.
3. Android SDK hazır değilse lisans kutusunu işaretleyip araçları hazırlayın.
4. `Play Store AAB` düğmesine basın.
5. Mevcut upload keystore'unuzu seçin veya `Yeni Keystore Oluştur` ile yeni bir `.jks` oluşturun.
6. Alias, keystore şifresi ve key şifresini girin.
7. `İmzalı AAB Oluştur` düğmesine basın.
8. Başarılı çıktı `<proje>/release/<proje>-release.aab` konumuna gelir.

## Çok önemli

- Keystore dosyanızın en az iki güvenli yedeğini alın.
- Keystore'u ve şifreleri GitHub/Git gibi açık depolara yüklemeyin.
- eLuajs şifreleri kaydetmeyi varsayılan olarak zorunlu tutmaz.
- `Şifreleri de kaydet` seçeneği açılırsa bilgiler `.eluajs/release-signing.properties` içinde düz metin tutulur; dosya `.gitignore` içindedir ama yine de dikkatli olun.
- Play Store'da uygulama güncellerken `versionCode` her yeni sürümde yükseltilmelidir.
- Play App Signing kullanıldığında eLuajs'ta seçtiğiniz keystore normalde Play Console'a bundle yüklemek için kullanılan upload key'dir.

## Elle ayar girmek

Derleme ekranında `signing.properties Aç` düğmesiyle bu dosyayı IDE editöründe açabilirsiniz:

```properties
storeFile=/home/kullanici/.eluajs/keystores/my-app-upload-key.jks
storePassword=
keyAlias=upload
keyPassword=
```

Şifre alanlarını boş bırakırsanız şifreleri AAB ekranında girebilirsiniz.
