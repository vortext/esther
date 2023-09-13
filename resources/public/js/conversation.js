var emoji = new EmojiConvertor();
emoji.replace_mode = "unified";

let setClientContext = function () {
  const clientContext = document.getElementById("client-context");
  const date = new Date();
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const timezones = window.clientConfig.timezones;

  let location = null;

  let clientLocation = window.clientConfig.location;
  if (clientLocation) {
    location =  {"latitude": window.clientConfig.location.latitude,
                 "longitude": window.clientConfig.location.longitude};
  }

  const context =  {
    "timezone": timezone,
    "location": location,
    "iso8601": date.toISOString()};

  clientContext.value = JSON.stringify(context);
};

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
  const inputContent = document.getElementById("input-content");

  // Resize the textarea
  resizeTextarea(e);
  scrollToView(textarea);
  setClientContext();

  inputContent.value = emoji.replace_colons(textarea.value);

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
  const textarea = document.getElementById('user-input');
  const inputContent = document.getElementById("input-content");
  const userValue = document.getElementById("user-value");

  userValue.innerHTML = marked.parse(inputContent.value, {"gfm": true, "breaks": true});

  textarea.classList.add('hidden');
  textarea.placeholder = '';
  textarea.value = '';
  inputContent.value = "";
}

function focusWithoutScrolling(element) {
  const x = window.scrollX;
  const y = window.scrollY;
  element.focus();
  window.scrollTo(x, y);
}

function afterConverseRequest() {
  const textarea = document.getElementById('user-input');
  const messagesContainer = document.getElementById('conversation');
  const bottomElement = document.getElementById('bottom');


  textarea.classList.remove('hidden');
  bottomElement.scrollIntoView({ behavior: 'smooth' });

  // Simulate a "right arrow" key press
  setTimeout(() => {
    focusWithoutScrolling(textarea);
    const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
    textarea.dispatchEvent(event);
  }, 250);
}

function today(date = new Date()) {
  if (!(date instanceof Date)) {
    return 'Invalid input. Please provide a Date object.';
  }

  const day = date.getDate();
  const daySuffix = [null, 'st', 'nd', 'rd', ...Array(17).fill('th'), 'st', 'nd', 'rd', ...Array(7).fill('th')][day];
  const month = date.toLocaleString('default', { month: 'long' });
  const weekday = date.toLocaleString('default', { weekday: 'long' });

  return `${weekday} the ${day}${daySuffix} of ${month}, ${date.getFullYear()}.`;
}

document.addEventListener('DOMContentLoaded', function() {
  document.getElementById("today").innerHTML = today();
  setClientContext();

  navigator.geolocation.getCurrentPosition(
    (position) => {
      window.clientConfig.location = position.coords;
      setClientContext();
    },
    (error) => {
      console.warn('Geolocation error:', error);
    }
  );

  setTimeout(() => {
    document.getElementById("bottom").scrollIntoView({behavior: 'smooth'});
  },0);
});
