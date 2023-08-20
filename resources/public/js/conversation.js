var emoji = new EmojiConvertor();
emoji.replace_mode = "unified";

function getCurrentSeason(lat) {
  const now = new Date();
  const year = now.getFullYear();
  const marchEquinox = new Date(year, 2, 21);
  const juneSolstice = new Date(year, 5, 21);
  const septemberEquinox = new Date(year, 8, 23);
  const decemberSolstice = new Date(year, 11, 21);

  if (lat > 0) {
    // Northern Hemisphere
    if (now >= marchEquinox && now < juneSolstice) return 'spring';
    if (now >= juneSolstice && now < septemberEquinox) return 'summer';
    if (now >= septemberEquinox && now < decemberSolstice) return 'autumn';
    return 'winter';
  } else {
    // Southern Hemisphere
    if (now >= marchEquinox && now < juneSolstice) return 'autumn';
    if (now >= juneSolstice && now < septemberEquinox) return 'winter';
    if (now >= septemberEquinox && now < decemberSolstice) return 'spring';
    return 'summer';
  }
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

function getLocalContext(latitude, longitude) {
  return {
    "current-season": getCurrentSeason(latitude),
    "time-of-day": getTimeOfDay(latitude, longitude)
  };
}

function resizeTextarea(e) {
  const textarea = e.target;
  textarea.style.height = 'auto'; // Reset height
  textarea.style.height = (textarea.scrollHeight) + 'px';
}

function handleTextareaInput(e) {
  const textarea = e.target;

  // Resize the textarea
  resizeTextarea(e);

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
      textarea.selectionStart = newCaretPos;
      textarea.selectionEnd = newCaretPos;

      textarea.focus();
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
    if (textarea.value.trim() === "") {
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

function setContext() {
  let context = getLocalContext(window.appConfig.latitude, window.appConfig.longitude);
  let userContext = document.getElementById("user-context");
  userContext.value = JSON.stringify(context);
}

function beforeConverseRequest() {
  setSentiment(getSentimentEnergy());
  // Get the form and input elements
  let textarea = document.getElementById('user-input');
  let userValue = document.getElementById("user-value");
  let userSid = document.getElementById("user-sid");

  // Store the values in the hidden input fields
  userSid.value = window.appConfig.sid;
  setContext();

  // Update the UI
  let msg = emoji.replace_colons(textarea.value);
  userValue.innerHTML = marked.parse(msg);
  textarea.classList.add('hidden');
  textarea.placeholder = '';
  textarea.value = '';
}

function focusWithoutScrolling(element) {
  const x = window.scrollX;
  const y = window.scrollY;
  element.focus();
  window.scrollTo(x, y);
}

function afterConverseRequest() {
  let textarea = document.getElementById('user-input');
  let messagesContainer = document.getElementById('conversation');
  let bottomElement = document.getElementById('bottom');

  textarea.classList.remove('hidden');
  bottomElement.scrollIntoView({ behavior: 'smooth' });

  // Simulate a "right arrow" key press
  setTimeout(() => {
    focusWithoutScrolling(textarea);
    const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
    textarea.dispatchEvent(event);
  }, 250);
}


function setUserLocation(latitude, longitude) {
  window.appConfig.latitude = latitude;
  window.appConfig.longitude = longitude;
}


document.addEventListener('DOMContentLoaded', function() {
  document.getElementById("bottom").scrollIntoView({behavior: 'smooth'});
  var sidElements = document.querySelectorAll('.session-sid');

  // Geolocation Handling
  setUserLocation(51.509865, -0.118092); // London
  navigator.geolocation.getCurrentPosition(
    (position) => {
      setUserLocation(position.coords.latitude, position.coords.longitude);
      setContext();
    },
    (error) => {
      console.warn('Geolocation error:', error);
      setContext();
    }
  );

  sidElements.forEach(function(element) {
    element.value = window.appConfig.sid;
  });
});
