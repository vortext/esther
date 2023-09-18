function handleClick(button) {
  button.classList.add('submitting');
  document.querySelector('.sign-in-text').style.display = 'none';
}

document.addEventListener('DOMContentLoaded', function() {
  document.getElementById('login-form').addEventListener('htmx:afterOnLoad', (event) => {
    const button = document.getElementById('submit');
    if (event.detail.xhr.response.includes('login-failed')) {
      button.classList.remove('submitting');
      document.querySelector('.sign-in-text').style.display = 'inline';
    }
  });
});
