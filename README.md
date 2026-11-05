# POP SIPGN Android (WebView)

Aplikasi Android sederhana yang membuka `https://pop-sipgn.bgn.go.id/` melalui WebView.

## Fitur
- WebView dengan JavaScript dan DOM Storage aktif.
- Tombol unggah foto pada halaman web memunculkan pemilih foto Android.
- Pengguna dapat memilih foto dari Galeri/File Picker atau mengambil foto dengan Kamera.
- Menangani permintaan kamera WebRTC dari halaman web melalui izin kamera Android.
- Tombol Back kembali ke halaman sebelumnya.

## Cara membuka dan build
1. Ekstrak ZIP ini.
2. Buka folder `POP_SIPGN_Android` melalui Android Studio (Hedgehog atau lebih baru).
3. Tunggu Gradle Sync dan pasang Android SDK 35 bila diminta.
4. Hubungkan HP Android atau buat emulator.
5. Pilih **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

APK hasil build biasanya berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan penting
- Source ini tidak menyertakan APK karena APK perlu dibangun menggunakan Android SDK/Gradle di Android Studio.
- Website tujuan memerlukan koneksi internet dan akun SIPGN yang sah.
- Situs tujuan membatasi pengambilan halaman otomatis saat pemeriksaan, sehingga integrasi harus diuji langsung pada perangkat Android dengan akun dan alur unggah yang sebenarnya.
- Jika situs memaksa mode kamera khusus melalui JavaScript `getUserMedia` dan bukan input unggah file, izin kamera WebView juga sudah ditangani; tampilan pemilih Galeri/Kamera berlaku untuk kontrol unggah file (`input type=file`).
- Pengambilan foto dari kamera disimpan sementara di cache aplikasi dan diteruskan ke formulir web melalui URI FileProvider.
