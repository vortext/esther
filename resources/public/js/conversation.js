var emoji = new EmojiConvertor();
emoji.replace_mode = "unified";

function getCurrentSeason(latitude) {
  const now = new Date();
  const year = now.getFullYear();
  const marchEquinox = new Date(year, 2, 21);
  const juneSolstice = new Date(year, 5, 21);
  const septemberEquinox = new Date(year, 8, 23);
  const decemberSolstice = new Date(year, 11, 21);

  if (latitude > 0) {
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

function getLocalContext() {
  let latitude = window.appConfig.latitude;
  let longitude = window.appConfig.longitude;
  return {
    "season": getCurrentSeason(latitude),
    "time-of-day": getTimeOfDay(latitude, longitude),
    "lunar-phase": lunarphase.Moon.lunarPhaseEmoji(),
    "remote-addr": window.appConfig.remoteAddr // For weather geo-ip ...
  };
}

function setLocalContext() {
  let userContext = document.getElementById("user-context");
  userContext.value = JSON.stringify(getLocalContext());
}

function scrollToView(element) {
  const rect = element.getBoundingClientRect();
  const padding = 140;
  if (rect.bottom > window.innerHeight) {
    window.scrollBy(0, rect.bottom - window.innerHeight + padding);
  }
}

function resizeTextarea(e) {
  const textarea = e.target;
  textarea.style.height = 'auto'; // Reset height
  textarea.style.height = (textarea.scrollHeight) + 'px';
  scrollToView(textarea);
}

function handleTextareaInput(e) {
  const textarea = e.target;

  // Resize the textarea
  resizeTextarea(e);
  scrollToView(textarea);

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

function setEnergy(energyValue) {
  energyValue = Math.max(0, Math.min(1, energyValue));
  const duration = 1.2 - energyValue * 0.4;
  const ease = `cubic-bezier(${0.2 + energyValue * 0.3}, 0.5, 0.5, 1)`;
  const balls = document.querySelectorAll('.loading div');

  balls.forEach((ball) => {
    ball.style.animationDuration = `${duration}s`;
    ball.style.animationTimingFunction = ease;
  });
}

function getEnergy() {
  const lastMemory = document.querySelector("#history .memory:last-child");
  return lastMemory ? parseFloat(lastMemory.dataset.energy || 0.5) : 0.5;
}

function beforeConverseRequest() {
  setEnergy(getEnergy());
  setLocalContext();

  // Get the form and input elements
  let textarea = document.getElementById('user-input');
  let userValue = document.getElementById("user-value");

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

document.addEventListener('DOMContentLoaded', function() {
  // For geoip weather data
  fetch('https://api.ipify.org?format=json')
    .then(response => response.json())
    .then(data => {
      window.appConfig.remoteAddr = data;
      setLocalContext();
    })
    .catch(error => {
      console.error('Error fetching remoteAddr:', error);
      setLocalContext();
    });

  navigator.geolocation.getCurrentPosition(
    (position) => {
      window.appConfig.latitude = position.coords.latitude;
      window.appConfig.longitude = position.coords.longitude;
      setLocalContext();
    },
    (error) => {
      console.warn('Geolocation error:', error);
      // London as default
      setLocalContext();
    }
  );

  setTimeout(() => {
    document.getElementById("bottom").scrollIntoView({behavior: 'smooth'});
  },0);
});
