# POP SIPGN BGN - Android WebView APK

Aplikasi Android WebView khusus untuk portal **https://pop-sipgn.bgn.go.id/** dengan fitur otomatis mengalihkan tombol kamera ke Galeri Foto HP.

---

## 🚀 Fitur Unggulan

1. **Pengalihan Kamera ke Galeri HP Otomatis:**
   - Menimpa implementasi `WebChromeClient.onShowFileChooser`.
   - Mengabaikan `MediaStore.ACTION_IMAGE_CAPTURE` (kamera hardware) dan langsung memanggil `Intent.ACTION_PICK` pada `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (Galeri HP).
   - Injeksi JavaScript secara real-time untuk menghapus atribut `capture="camera"` pada form web.
   - Dilengkapi `JavaScriptInterface` bernama `AndroidGallery.openGallery()`.

2. **Perizinan Lengkap:**
   - Mendukung Android 13/14/15 (`READ_MEDIA_IMAGES`).
   - Mendukung Android 12 ke bawah (`READ_EXTERNAL_STORAGE`).

3. **Optimasi WebView Pemerintah:**
   - `network_security_config.xml` untuk kompatibilitas SSL domain `*.bgn.go.id`.
   - CookieSync & DOM Storage aktif agar sesi login tersimpan.
   - Pull to refresh dan penanganan tombol Back HP.

---

## 🛠️ Cara Build Menjadi APK

### Cara 1: Menggunakan GitHub Actions (Otomatis & Tanpa Install Android Studio)
1. Buat repository baru di GitHub (misal: `pop-sipgn-apk`).
2. Upload seluruh isi folder project ini ke repository GitHub Anda.
3. Buka tab **Actions** di GitHub repository Anda.
4. Workflow `Build Android APK` akan berjalan secara otomatis dalam 2-3 menit.
5. Setelah selesai, download file `.apk` yang sudah jadi di bagian **Artifacts**.

### Cara 2: Menggunakan Android Studio (di Laptop/PC)
1. Buka Android Studio.
2. Pilih **File** > **Open**, lalu pilih folder project ini.
3. Tunggu hingga Gradle Sync selesai.
4. Klik menu **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. Setelah selesai, klik notifikasi **locate** di kanan bawah untuk mengambil file `app-debug.apk` atau `app-release.apk`.
6. Transfer file `.apk` tersebut ke HP Android Anda dan instal.

---

## ⚙️ Detail Konfigurasi
- **Nama Aplikasi:** POP SIPGN BGN
- **Package ID:** `id.go.bgn.popsipgn`
- **URL Target:** `https://pop-sipgn.bgn.go.id/`
- **Mode Pengalihan:** `gallery_only`
- **Versi:** 1.0.0 (Code: 1)


## Perbaikan pemilihan foto (versi revisi)

- Permintaan file dari HTML/WebView (`input type="file"`) membuka pemilih foto/dokumen Android.
- Tidak meminta izin membaca seluruh galeri; pengguna hanya memberikan akses ke foto yang dipilih.
- Mendukung pilihan satu foto atau beberapa foto jika pemilih perangkat mendukungnya.
- Membatalkan pilihan foto akan menyelesaikan callback WebView dengan nilai kosong.
- Atribut `capture` dihapus dari input file, termasuk input yang ditambahkan secara dinamis.

Catatan: ini menangani alur unggah file HTML/WebView. Jika website membuka kamera melalui API kamera langsung (`getUserMedia`) atau tombol JavaScript khusus yang tidak memicu `input type="file"`, perubahan pada aplikasi saja tidak dapat mengubah alur itu menjadi unggah galeri secara universal. Bagian website tersebut perlu disesuaikan agar menggunakan input file/unggah foto.


## Revisi kamera + galeri (1.1)

- Untuk unggah melalui `input type=file`, dialog Android menawarkan Galeri dan Kamera.
- Kamera yang berjalan langsung di halaman melalui `getUserMedia()` tetap ditangani WebView; izin kamera hanya diberikan kepada origin `pop-sipgn.bgn.go.id`.
- Foto kamera eksternal disimpan sementara melalui `FileProvider`.
- Tidak ada skrip yang menghapus atribut `capture` dari halaman.

**Batasan penting:** screenshot DevTools sebelumnya menunjukkan `AppPhotoInput.vue: galleryEnabled false`. Jika tombol "Ambil Foto" pada situs menggunakan komponen Vue dengan kamera JavaScript dan tidak menyediakan input file, aplikasi Android tidak dapat secara universal memasukkan foto galeri ke dalam state internal komponen tersebut. Untuk mengaktifkan galeri pada modal yang sama, pemilik situs perlu mengaktifkan fitur `galleryEnabled` atau menyediakan alur unggah file. Revisi Android ini menyediakan pilihan galeri/kamera pada permintaan file WebView dan mempertahankan kamera inline.

## Integrasi tombol Galeri pada modal kamera POP SIPGN

Aplikasi memuat `app/src/main/assets/sipgn-gallery.js` setelah halaman POP SIPGN selesai dimuat. Script mencari elemen video dan tombol **Ambil**, lalu menambahkan tombol **Pilih dari Galeri** pada modal kamera. Foto yang dipilih ditampilkan sebagai stream dari canvas agar tombol **Ambil** bawaan halaman dapat mencoba menangkap preview tersebut.

Catatan: teknik ini bergantung pada implementasi kamera di website. Jika komponen Vue menyimpan stream kamera asli secara terpisah atau mengambil foto dari track kamera asli, tombol galeri dapat muncul tetapi hasilnya belum tentu masuk ke formulir. Uji pada akun/perangkat uji terlebih dahulu. Perubahan pada aplikasi Android tidak mengubah kode website POP SIPGN.
