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

function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
};


let setLocalContext = debounce(function () {
  let timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  let timezoneLocation;
  const timezones = window.appConfig.timezones;

  if (timezones && timezones[timezone] && timezones[timezone].coordinates_decimal) {
    timezoneLocation = timezones[timezone].coordinates_decimal;
  } else {
    timezoneLocation = {"latitude": 51.509865, "longitude": -0.118092} // London
  }

  let locationAllowed = window.appConfig.isLocationAllowed;
  let location = locationAllowed ? window.appConfig.location : timezoneLocation;

  let context =  {
    "location-allowed": locationAllowed,
    "season": getCurrentSeason(location.latitude),
    "time-of-day": getTimeOfDay(location.latitude, location.longitude),
    "timezone": timezone,
    "location": {"latitude": location.latitude,
                 "longitude": location.longitude},
    "lunar-phase": lunarphase.Moon.lunarPhase()};

  let localContext = document.getElementById("local-context");
  localContext.value = JSON.stringify(context);
}, 500);

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
  setLocalContext();

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

  // Get the form and input elements
  let textarea = document.getElementById('user-input');
  let userValue = document.getElementById("user-value");

  // Update the UI
  let msg = emoji.replace_colons(textarea.value);
  userValue.innerHTML = marked.parse(msg, {"gfm": true, "breaks": true});
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

async function fetchTimezones() {
  try {
    const response = await fetch('/resources/public/misc/timezones.min.json');
    if (!response.ok) {
      throw new Error('Network response was not ok ' + response.statusText);
    }
    const timezones = await response.json();
    window.appConfig.timezones = timezones;
  } catch (error) {
    console.error('There has been a problem with your fetch operation:', error);
  }
}

fetchTimezones();

document.addEventListener('DOMContentLoaded', function() {
  navigator.geolocation.getCurrentPosition(
    (position) => {
      window.appConfig.isLocationAllowed = true;
      window.appConfig.location = position.coords;
    },
    (error) => {
      console.warn('Geolocation error:', error);
      // London as default set by render
      window.appConfig.isLocationAllowed = false;
    }
  );

  setTimeout(() => {
    document.getElementById("bottom").scrollIntoView({behavior: 'smooth'});
  },0);
});
