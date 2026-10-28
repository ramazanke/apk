# Alpica TV · Android TV

Android TV / Google TV / Android TV Box için site arayüzü + yerel Media3 oynatıcı. Kaynak sayfa: https://ramazanke.github.io/Tvalp/

## APK almak (Android Studio olmadan)

1. ZIP içindeki android-tv klasörünü ve .github/workflows/android-tv-apk.yml dosyasını GitHub deponuzun ana klasörüne, klasör yapısını koruyarak yükleyin. Mevcut web dosyalarının yerine koymayın.
2. GitHub → Actions → Android TV APK → Run workflow seçin. Deponuzda Actions kapalıysa etkinleştirin. Workflow push işleminde de çalışır.
3. Başarılı çalışma sayfasının altındaki Artifacts bölümünden AlpicaTV-AndroidTV-APK dosyasını indirin.
4. İndirilen ZIP içindeki app-debug.apk dosyasını Android TV'ye aktarın ve dosya yöneticisiyle yükleyin. İlgili yükleyici için bilinmeyen uygulama yükleme izni istenebilir.

Bu oturumda Android SDK/JDK yoktur; APK henüz derlenmemiştir. Actions yapılandırması hazırlanmıştır ama GitHub'a yüklenmemiş veya çalıştırılmamıştır. Debug APK test kurulumu içindir. Üretim güncellemelerinde aynı imza anahtarı kullanılmalıdır; CI debug imzası kalıcı yayın anahtarı değildir.

## Android Studio

android-tv klasörünü açın. JDK 17, Android SDK 35 ve Gradle 8.9 gerekir. Projede Gradle wrapper JAR bulunmaz. Gradle kuruluysa terminalde:

    gradle wrapper --gradle-version 8.9
    gradlew.bat :app:assembleDebug :app:lintDebug

Çıktı: app/build/outputs/apk/debug/app-debug.apk

## Tamamen kumanda

| Tuş | İşlev |
| --- | --- |
| Sağ / Sol, CH+ / CH- | Seçili kategoride sonraki / önceki kanal |
| OK | Kategorileri aç; menüde seçimi onayla |
| Yukarı / Menü | Kategorileri aç |
| Aşağı | Kanal şeridini aç |
| Geri | Önce menüyü kapat; menü kapalıysa çıkış onayı |
| 0–9 | Seçili kategoride kanal sırası; 1,3 saniye sonra veya OK ile aç |
| Oynat / Duraklat | Yayını durdur veya devam ettir |
| Ses + / - / Sessiz | Android / TV ses kontrolü |

Tam ekran yerel pencere açılışta aktiftir. Site kanal adı ve 3 saniye gizleme davranışını, 8 saniyelik menü zaman aşımını sağlar. Sitedeki yönetim düğmeleri TV'de gizlidir. Kanal yönetimini bilgisayarda yapın ve channels.json dosyasını GitHub'a yükleyin. APK'nın WebView depolaması bilgisayardaki localStorage ile paylaşılmaz; yayımlanmış channels.json okunur.

## Bağlantı ve oynatma

Arayüz canlı GitHub Pages sayfasıdır; uygulama açılırken internet gerekir. Ayrı bir kanal kopyası APK'ya gömülmez. Sayfa/kategori/listede yapılan yayınlanmış değişiklikler site yenilendiğinde gelir. Android uygulaması tarayıcı videosu yerine yerel ExoPlayer kullanır; HTTP ve HTTPS adresleri desteklenir ve web CORS kuralı yerel oynatıcıyı etkilemez. HLS, DASH ve cihazın desteklediği doğrudan yayın biçimleri kullanılır. URL'ler değiştirilmez, uzantı eklenmez.

Güvenli mesaj köprüsü yalnızca ramazanke.github.io HTTPS kaynağının Tvalp ana sayfasına izin verir. Diğer sayfalara gezinme TV içinde engellenir. SSL sertifika hataları atlanmaz. Eski Android System WebView sürümleri için güncelleme uyarısı gösterilir. Web arayüzündeki channels, play, wake ve menü işlevleri değiştirilirse native-player.js uyarlaması gerekebilir.

Kapalı/bozuk/DRM korumalı veya özel sunucu başlığı isteyen yayınlar ve cihazın desteklemediği ses/video codec'leri otomatik düzeltilmez. Media3 decoder fallback cihazdaki alternatif decoder'ları dener; yazılımsal FFmpeg ses decoder'ı içermez. Samsung Tizen ve LG webOS televizyonlar Android APK yükleyemez; Android TV/Google TV cihazı gerekir.

## Değişiklik noktaları

- MainActivity.java: site adresi, yerel oynatma, yaşam döngüsü ve kumanda eşlemesi.
- app/src/main/assets/native-player.js: site arayüzünü yerel videoya bağlayan köprü.
- app/build.gradle: Android ve Media3 sürümleri.

Derleme/cihaz doğrulaması yapılmamıştır. JavaScript sözdizimi ve köprüdeki 16 davranış kontrolü yerel olarak doğrulandı; bunlar Android derlemesi veya cihaz testi yerine geçmez.
