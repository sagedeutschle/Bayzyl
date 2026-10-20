// Progressive enhancement for the two Build Big image collections.
// Each image remains a normal full-size link if dialog support or JavaScript is unavailable.
(() => {
  const collections = [...document.querySelectorAll('[data-gallery]')];
  const viewer = document.createElement('dialog');
  if (!collections.length || typeof viewer.showModal !== 'function') return;

  viewer.id = 'build-viewer';
  viewer.className = 'build-viewer';
  viewer.setAttribute('aria-labelledby', 'build-viewer-title');
  viewer.innerHTML = `
    <div class="viewer-shell">
      <div class="viewer-header">
        <h2 id="build-viewer-title">Build gallery</h2>
        <button class="viewer-button" type="button" data-viewer-close aria-label="Close image viewer" autofocus>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg>
        </button>
      </div>
      <div class="viewer-stage" data-viewer-stage aria-busy="false"></div>
      <div class="viewer-footer">
        <a class="viewer-full" data-viewer-full>Open original</a>
        <div class="viewer-controls">
          <button class="viewer-button" type="button" data-viewer-prev aria-label="Previous image">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m14 5-7 7 7 7"/></svg>
          </button>
          <p class="viewer-counter" data-viewer-counter aria-live="polite" aria-atomic="true"></p>
          <button class="viewer-button" type="button" data-viewer-next aria-label="Next image">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m10 5 7 7-7 7"/></svg>
          </button>
        </div>
      </div>
    </div>`;
  document.body.append(viewer);

  const title = viewer.querySelector('#build-viewer-title');
  const stage = viewer.querySelector('[data-viewer-stage]');
  const previous = viewer.querySelector('[data-viewer-prev]');
  const next = viewer.querySelector('[data-viewer-next]');
  const counter = viewer.querySelector('[data-viewer-counter]');
  const original = viewer.querySelector('[data-viewer-full]');
  let links = [], current = 0, origin = null, request = 0;
  let scrollPosition = null;

  function showImage(index) {
    if (index < 0 || index >= links.length) return;
    current = index;
    const link = links[current];
    const thumbnail = link.querySelector('img');
    const thisRequest = ++request;
    const image = new Image();
    const status = document.createElement('p');
    image.dataset.viewerImage = '';
    image.alt = thumbnail.alt;
    image.width = Number(thumbnail.getAttribute('width'));
    image.height = Number(thumbnail.getAttribute('height'));
    image.decoding = 'async';
    status.className = 'viewer-status';
    status.dataset.viewerStatus = '';
    status.setAttribute('role', 'status');
    status.textContent = 'Loading image…';
    stage.setAttribute('aria-busy', 'true');
    stage.replaceChildren(image, status);
    counter.textContent = `${current + 1} / ${links.length}`;
    previous.disabled = current === 0;
    next.disabled = current === links.length - 1;
    original.href = link.href;

    image.addEventListener('load', () => {
      if (thisRequest !== request) return;
      stage.setAttribute('aria-busy', 'false');
      status.textContent = '';
    }, { once: true });
    image.addEventListener('error', () => {
      if (thisRequest !== request) return;
      stage.setAttribute('aria-busy', 'false');
      image.hidden = true;
      status.textContent = 'This image could not be loaded. Try opening the original.';
    }, { once: true });
    image.src = link.href;
  }

  viewer.querySelector('[data-viewer-close]').addEventListener('click', () => viewer.close());
  previous.addEventListener('click', () => showImage(current - 1));
  next.addEventListener('click', () => showImage(current + 1));
  viewer.addEventListener('keydown', (event) => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      showImage(current + (event.key === 'ArrowLeft' ? -1 : 1));
    }
  });
  viewer.addEventListener('close', () => {
    request++;
    stage.replaceChildren();
    document.documentElement.classList.remove('bayzyl-viewer-open');
    document.body.classList.remove('bayzyl-viewer-open');
    document.body.style.removeProperty('--viewer-scroll-top');
    if (scrollPosition) {
      window.scrollTo(scrollPosition.x, scrollPosition.y);
      scrollPosition = null;
    }
    origin?.focus({ preventScroll: true });
  });

  for (const collection of collections) {
    const collectionLinks = [...collection.querySelectorAll('.image-link')];
    for (const [index, link] of collectionLinks.entries()) {
      link.setAttribute('aria-haspopup', 'dialog');
      link.addEventListener('click', (event) => {
        if (event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        event.preventDefault();
        links = collectionLinks;
        origin = link;
        title.textContent = collection.dataset.galleryTitle;
        showImage(index);
        scrollPosition = { x: window.scrollX, y: window.scrollY };
        document.body.style.setProperty('--viewer-scroll-top', `-${scrollPosition.y}px`);
        document.documentElement.classList.add('bayzyl-viewer-open');
        document.body.classList.add('bayzyl-viewer-open');
        viewer.showModal();
      });
    }
  }
})();
