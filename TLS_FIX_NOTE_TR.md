# eLuajs 0.16.1 TLS düzeltmesi

Bu paket, Gradle/Maven/Google Maven indirmelerinde görülen `handshake_failure` hatasını düzeltmek için gömülü Java 21 çalışma zamanına `jdk.crypto.ec` modülü eklenerek yeniden paketlenmiştir.

Eksik modül nedeniyle Java tarafında ECDHE TLS şifre takımları kullanılamıyordu. Google Maven ve Maven Central gibi modern HTTPS sunucularıyla TLS el sıkışması bu nedenle başarısız olabiliyordu.

Uygulama kodunda veya Gradle projesinde bu düzeltme için değişiklik gerekmez.
