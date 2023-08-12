function setSentiment(sentimentValue) {
  sentimentValue = Math.max(0, Math.min(1, sentimentValue));
  const duration = 1.2 - sentimentValue * 0.4;
  const ease = `cubic-bezier(${0.2 + sentimentValue * 0.3}, 0.5, 0.5, 1)`;
  const balls = document.querySelectorAll('.loading div');

  balls.forEach((ball) => {
    ball.style.animationDuration = `${duration}s`;
    ball.style.animationTimingFunction = ease;
  });
}

function getSentimentEnergy() {
  const lastMemory = document.querySelector("#history .memory:last-child");
  return lastMemory ? parseFloat(lastMemory.dataset.energy || 0.5) : 0.5;
}

function getTimeOfDay() {
  const date = new Date();

  const latitude = window.latitude;
  const longitude = window.longitude ;

  const times = SunCalc.getTimes(date, latitude, longitude);

  if (date < times.nauticalDawn) return 'night';
  if (date < times.dawn) return 'nautical twilight';
  if (date < times.sunrise) return 'civil twilight';
  if (date < times.sunriseEnd) return 'sunrise';
  if (date < times.goldenHourEnd) return 'morning';
  if (date < times.solarNoon) return 'daytime';
  if (date < times.goldenHour) return 'afternoon';
  if (date < times.sunsetStart) return 'evening';
  if (date < times.sunset) return 'sunset';
  if (date < times.dusk) return 'civil twilight';
  if (date < times.nauticalDusk) return 'nautical twilight';

  return 'night';
}

function getLocalContext() {
  return {
    "time-of-day": getTimeOfDay()
  };
}

function handleTextareaInput(e) {
  const textarea = e.target;

  // If the Enter key is pressed without the Ctrl key
  if (e.key === 'Enter' && !e.ctrlKey) {
    e.preventDefault(); // Prevent the newline

    // Trigger the submit event for HTMX
    const form = document.getElementById('message-form');
    const event = new Event('submit', {
      'bubbles': true,
      'cancelable': true
    });
    form.dispatchEvent(event);
    return;
  }

  // If the Enter key is pressed with the Ctrl key
  if (e.key === 'Enter' && e.ctrlKey) {
    const value = textarea.value;
    const startPos = textarea.selectionStart;
    const endPos = textarea.selectionEnd;
    textarea.value = value.substring(0, startPos) + '\n' + value.substring(endPos);
    textarea.selectionStart = textarea.selectionEnd = startPos + 1;
    e.preventDefault();
  }

  // Resize the textarea
  textarea.style.height = 'auto'; // Reset height
  textarea.style.height = (textarea.scrollHeight) + 'px';
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

function setPosition(lat, lon) {
  window.latitude = lat;
  window.longitude = lon;
}

// Geolocation Handling
navigator.geolocation.getCurrentPosition(
  (position) => {
    setPosition(position.coords.latitude, position.coords.longitude);
  },
  (error) => {
    setPosition(51.509865, -0.118092); // London
    console.warn('Geolocation error:', error);
  }
);
