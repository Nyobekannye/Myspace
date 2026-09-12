const form = document.querySelector('#post-form');
const textarea = document.querySelector('#post-content');
const count = document.querySelector('#character-count');
const submit = document.querySelector('.post-button');
const posts = document.querySelector('#posts');
const toast = document.querySelector('#toast');

textarea.addEventListener('input', () => {
  const length = textarea.value.trim().length;
  count.value = `${length} / 280`;
  submit.disabled = length === 0;
});

form.addEventListener('submit', (event) => {
  event.preventDefault();
  const message = textarea.value.trim();
  if (!message) return;

  const post = document.createElement('article');
  post.className = 'post new-post';
  post.innerHTML = `<div class="avatar large-avatar">SN</div><div class="post-body"><header><div><h3>Sari Nufti</h3><p>Just now · <span aria-label="Visible to everyone">◉</span></p></div><button class="more-button" aria-label="More options for your post">•••</button></header><p class="post-copy"></p><footer><button type="button">♡ <span>0</span></button><button type="button">▢ <span>Comment</span></button><button type="button">↗ <span>Share</span></button></footer></div>`;
  post.querySelector('.post-copy').textContent = message;
  posts.prepend(post);
  textarea.value = '';
  textarea.dispatchEvent(new Event('input'));
  toast.textContent = 'Your post is live in your feed.';
  toast.classList.add('visible');
  setTimeout(() => toast.classList.remove('visible'), 3000);
  post.scrollIntoView({ behavior: 'smooth', block: 'center' });
});
