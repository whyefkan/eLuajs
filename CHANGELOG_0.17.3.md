# eLuajs 0.17.3

- Play Store için signed release AAB (`bundleRelease`) desteği.
- Upload keystore seçme ve IDE içinden oluşturma.
- Keystore alias/store password/key password alanları.
- İsteğe bağlı yerel signing ayarı saklama; signing secrets `.gitignore` korumasında.
- `signing.properties` dosyasını editörde elle düzenleme desteği.
- Gradle init-script signing enjeksiyonu: kullanıcı build.gradle dosyasına parola yazmak zorunda değil.
- Üretilen AAB içinde imza girdisi kontrolü.
- AAB kolay çıktısı: `<project>/release/<project>-release.aab`.
- `keytool` artık JDK readiness ve self-test parçası.
- v0.17 Performance Core ve TLS-FIX korunur.
