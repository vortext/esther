function setSentiment(sentimentValue) {
  sentimentValue = Math.max(0, Math.min(1, sentimentValue));

  // Use the sentiment to create smooth and natural durations
  const duration = 1.2 - sentimentValue * 0.4; // seconds
  const ease = `cubic-bezier(${0.2 + sentimentValue * 0.3}, 0.5, 0.5, 1)`;

  const balls = document.querySelectorAll('.loading div');

  balls.forEach(ball => {
    ball.style.animationDuration = `${duration}s`;
    ball.style.animationTimingFunction = ease;
  });
}

function getSentimentEnergy() {
  const lastMemory = document.querySelectorAll("#history .memory:last-child");
  if (lastMemory.length) {
    return parseFloat(lastMemory[0].dataset.energy || 0.5);
  } else {
    return 0.5;
  }
}

function getLocalContext() {
  return {
    "local-time": Date()
  };
}

function beforeConverseRequest() {
  setSentiment(getSentimentEnergy());
  let msg = document.querySelector('#user-input').value;
  let localContext = JSON.stringify(getLocalContext());
  document.getElementById("user-context").value = localContext;
  document.getElementById('user-value').textContent = msg;
  document.getElementById('user-input').disabled = true;
  document.getElementById('user-input').placeholder = '';
  document.getElementById('user-input').value = '';
}

function afterConverseRequest() {
  document.querySelector('#user-input').disabled = false;
  document.getElementById('user-input').focus();
  document.getElementById('user-input').scrollIntoView({behavior: 'smooth'});
}
