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

function getTimeOfDay(latitude, longitude) {
  const date = new Date();

  const times = SunCalc.getTimes(date, latitude, longitude);

  if (date < times.nauticalDawn) return 'night';
  if (date < times.dawn) return 'nautical-twilight';
  if (date < times.sunrise) return 'civil-twilight';
  if (date < times.sunriseEnd) return 'sunrise';
  if (date < times.goldenHourEnd) return 'morning';
  if (date < times.solarNoon) return 'daytime';
  if (date < times.goldenHour) return 'afternoon';
  if (date < times.sunsetStart) return 'evening';
  if (date < times.sunset) return 'sunset';
  if (date < times.dusk) return 'civil-twilight';
  if (date < times.nauticalDusk) return 'nautical-twilight';

  return 'night';
}

function getLocalContext() {
  return {
    "time-of-day": getTimeOfDay(window.appConfig.latitude,
                                window.appConfig.longitude)
  };
}



function handleTextareaInput(e) {
  const textarea = e.target;
  var userInput = document.getElementById("user-input").value;

  // Resize the textarea
  textarea.style.height = 'auto'; // Reset height
  textarea.style.height = (textarea.scrollHeight) + 'px';

  // If the Enter key is pressed with the Shift key
  if (e.key === 'Enter' && e.shiftKey) {
    const value = textarea.value;
    const startPos = textarea.selectionStart;
    const endPos = textarea.selectionEnd;
    const newCaretPos = startPos + 1;
    textarea.value = value.substring(0, startPos) + '\n' + value.substring(endPos);
    e.preventDefault();

    // Apply the new selection asynchronously
    setTimeout(() => {
      textarea.focus();
      textarea.selectionStart = newCaretPos;
      textarea.selectionEnd = newCaretPos;

      // Simulate a "right arrow" key press
      const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
      textarea.dispatchEvent(event);
    }, 0);

    return; // Return early to prevent further execution
  }

  // If the Enter key is pressed without the Shift key
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault(); // Prevent the newline

    // Check if the input contains only whitespace
    if (userInput.trim() === "") {
      // If only whitespace, prevent the default behavior (submission)
      return;
    }

    // Trigger the submit event for HTMX
    const form = document.getElementById('message-form');
    const event = new Event('submit', {
      'bubbles': true,
      'cancelable': true
    });
    form.dispatchEvent(event);
    textarea.style.height = 'auto'; // Reset height
  }
}

function beforeConverseRequest() {
  setSentiment(getSentimentEnergy());
  let msg = document.querySelector('#user-input').value;
  let localContext = JSON.stringify(getLocalContext());
  document.getElementById("user-context").value = localContext;
  document.getElementById('user-value').innerHTML = marked.parse(msg);
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
  window.appConfig.latitude = lat;
  window.appConfig.longitude = lon;
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


document.addEventListener('DOMContentLoaded', function() {
  var sidElements = document.querySelectorAll('.session-sid');
  sidElements.forEach(function(element) {
    element.value = window.appConfig.sid;
  });

  // Focus input on window focus
  document.addEventListener('focus', function() {
    setTimeout(() => {
      document.getElementById("user-input").focus();
    }, 100);
  });

});
