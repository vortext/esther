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

function getTimeOfDay() {
  const date = new Date();

  // FIXME Assuming a generic latitude and longitude
  const latitude = 51.509865; // Example: London's latitude
  const longitude = -0.118092; // Example: London's longitude

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
