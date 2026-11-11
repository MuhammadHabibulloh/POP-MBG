// Integrasi tombol galeri untuk modal kamera POP SIPGN.
(() => {
  if (window.__sipgnGalleryV11) return;
  window.__sipgnGalleryV11 = true;

  let activeVideo = null;
  let galleryStream = null;
  let galleryCanvas = null;

  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.style.display = 'none';
  document.body.appendChild(input);

  function findCamera() {
    const videos = [...document.querySelectorAll('video')];
    const video = videos.find(v => v.isConnected && v.getClientRects().length > 0);
    const ambil = [...document.querySelectorAll('button')].find(b =>
      b.textContent.trim().toLowerCase() === 'ambil' && b.getClientRects().length > 0
    );
    if (!video || !ambil) return;
    activeVideo = video;
    if (document.getElementById('sipgn-gallery-button')) return;

    const btn = document.createElement('button');
    btn.id = 'sipgn-gallery-button';
    btn.type = 'button';
    btn.textContent = 'Pilih dari Galeri';
    Object.assign(btn.style, {
      padding: '11px', margin: '5px', borderRadius: '10px',
      background: '#087443', color: '#fff', fontWeight: 'bold',
      border: 'none', display: 'block', width: '100%'
    });
    btn.addEventListener('click', () => {
      input.value = '';
      if (input.showPicker) input.showPicker(); else input.click();
    });
    ambil.parentElement.appendChild(btn);
  }

  function stopGallery() {
    if (galleryStream) galleryStream.getTracks().forEach(t => t.stop());
    galleryStream = null;
  }

  input.addEventListener('change', async () => {
    const file = input.files && input.files[0];
    if (!file || !activeVideo || !activeVideo.isConnected) return;
    try {
      if (!file.type.startsWith('image/')) throw new Error('File yang dipilih bukan gambar.');
      const bitmap = await createImageBitmap(file);
      const canvas = document.createElement('canvas');
      canvas.width = bitmap.width;
      canvas.height = bitmap.height;
      const ctx = canvas.getContext('2d');
      if (!ctx) throw new Error('Tidak dapat memproses gambar.');
      ctx.drawImage(bitmap, 0, 0);
      bitmap.close();
      stopGallery();
      galleryCanvas = canvas;
      galleryStream = galleryCanvas.captureStream(1);
      activeVideo.srcObject = galleryStream;
      await activeVideo.play();
      alert('Foto sudah dimasukkan ke preview. Periksa fotonya, lalu tekan Ambil bawaan SIPGN.');
    } catch (e) {
      alert('Kesalahan memilih foto: ' + (e && e.message ? e.message : e));
    }
  });

  setInterval(findCamera, 500);
  const observer = new MutationObserver(findCamera);
  observer.observe(document.body, { childList: true, subtree: true });
  window.addEventListener('pagehide', stopGallery, { once: true });
})();
