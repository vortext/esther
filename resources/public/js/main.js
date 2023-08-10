function setSentiment(sentimentValue) {
  console.log(sentimentValue);
  sentimentValue = Math.max(0, Math.min(1, sentimentValue));

  // Use the sentiment to create smooth and natural durations
  const duration = 0.8 + (1 - sentimentValue) * 0.5; // seconds
  const ease = `cubic-bezier(${0.2 + sentimentValue * 0.5},0.5,0.5,1)`;

  const balls = document.querySelectorAll('.loading div');

  balls.forEach(ball => {
    ball.style.animationDuration = `${duration}s`;
    ball.style.animationTimingFunction = ease;
  });
}

function beforeConverseRequest() {
  setSentiment(Math.random());
  let msg = document.querySelector('#user-input').value;
  document.querySelector('#user-value').textContent = msg;
  document.querySelector('#user-input').disabled = true;
  document.querySelector('#user-input').placeholder = '';
  document.querySelector('#user-input').value = '';
}

function afterConverseRequest() {
  document.querySelector('#user-input').disabled = false;
  document.getElementById('user-input').focus();
  document.getElementById('user-input').scrollIntoView({behavior: 'smooth'});
}
