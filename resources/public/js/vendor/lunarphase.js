(function (root, factory) {
  if (typeof exports === 'object' && typeof module !== 'undefined') {
    module.exports = factory();
  } else if (typeof define === 'function' && define.amd) {
    define(factory);
  } else {
    root.lunarPhase = factory();
  }
}(this, function () {
  'use strict';

  var t = ((e2) => (e2.NORTHERN = "Northern", e2.SOUTHERN = "Southern", e2))(t || {}), n = ((e2) => (e2.NEW = "🌑", e2.WAXING_CRESCENT = "🌒", e2.FIRST_QUARTER = "🌓", e2.WAXING_GIBBOUS = "🌔", e2.FULL = "🌕", e2.WANING_GIBBOUS = "🌖", e2.LAST_QUARTER = "🌗", e2.WANING_CRESCENT = "🌘", e2))(n || {}), r = ((e2) => (e2.NEW = "🌑", e2.WAXING_CRESCENT = "🌘", e2.FIRST_QUARTER = "🌗", e2.WAXING_GIBBOUS = "🌖", e2.FULL = "🌕", e2.WANING_GIBBOUS = "🌔", e2.LAST_QUARTER = "🌓", e2.WANING_CRESCENT = "🌒", e2))(r || {}), a = ((e2) => (e2.ANOMALISTIC = "Anomalistic", e2.DRACONIC = "Draconic", e2.SIDEREAL = "Sidereal", e2.SYNODIC = "Synodic", e2.TROPICAL = "Tropical", e2))(a || {}), s = ((e2) => (e2.NEW = "New", e2.WAXING_CRESCENT = "Waxing Crescent", e2.FIRST_QUARTER = "First Quarter", e2.WAXING_GIBBOUS = "Waxing Gibbous", e2.FULL = "Full", e2.WANING_GIBBOUS = "Waning Gibbous", e2.LAST_QUARTER = "Last Quarter", e2.WANING_CRESCENT = "Waning Crescent", e2))(s || {});
  const N = 24405875e-1, i = 29.53058770576;
  class o {
    static fromDate(e2) {
      return e2.getTime() / 864e5 - e2.getTimezoneOffset() / 1440 + N;
    }
    static toDate(e2) {
      const t2 = new Date();
      return t2.setTime(864e5 * (e2 - N + t2.getTimezoneOffset() / 1440)), t2;
    }
  }
  const u = { hemisphere: t.NORTHERN }, A = (e2) => ((e2 -= Math.floor(e2)) < 0 && (e2 += 1), e2);
  class E {
    static lunarAge(e2) {
      return E.lunarAgePercent(e2) * i;
    }
    static lunarAgePercent(e2) {
      return A((o.fromDate(e2) - 24515501e-1) / i);
    }
    static lunationNumber(e2) {
      return Math.round((o.fromDate(e2) - 2.4234366115277777e6) / i) + 1;
    }
    static lunarDistance(e2) {
      const t2 = o.fromDate(e2), n2 = 2 * E.lunarAgePercent(e2) * Math.PI, r2 = 2 * Math.PI * A((t2 - 24515622e-1) / 27.55454988);
      return 60.4 - 3.3 * Math.cos(r2) - 0.6 * Math.cos(2 * n2 - r2) - 0.5 * Math.cos(2 * n2);
    }
    static lunarPhase(date, t2) {
      const e2 = (date instanceof Date) ? date : new Date(date);
      t2 = { ...u, ...t2 };
      const n2 = E.lunarAge(e2);
      return n2 < 1.84566173161 ? s.NEW : n2 < 5.53698519483 ? s.WAXING_CRESCENT : n2 < 9.22830865805 ? s.FIRST_QUARTER : n2 < 12.91963212127 ? s.WAXING_GIBBOUS : n2 < 16.61095558449 ? s.FULL : n2 < 20.30227904771 ? s.WANING_GIBBOUS : n2 < 23.99360251093 ? s.LAST_QUARTER : n2 < 27.68492597415 ? s.WANING_CRESCENT : s.NEW;
    }
    static lunarPhaseEmoji(date, t2) {
      const e2 = (date instanceof Date) ? date : new Date(date);

      t2 = { ...u, ...t2 };
      const n2 = E.lunarPhase(e2);
      return E.emojiForLunarPhase(n2, t2);
    }
    static emojiForLunarPhase(e2, a2) {
      const { hemisphere: N2 } = { ...u, ...a2 };
      let i2;
      switch (i2 = N2 === t.SOUTHERN ? r : n, e2) {
      case s.WANING_CRESCENT:
        return i2.WANING_CRESCENT;
      case s.LAST_QUARTER:
        return i2.LAST_QUARTER;
      case s.WANING_GIBBOUS:
        return i2.WANING_GIBBOUS;
      case s.FULL:
        return i2.FULL;
      case s.WAXING_GIBBOUS:
        return i2.WAXING_GIBBOUS;
      case s.FIRST_QUARTER:
        return i2.FIRST_QUARTER;
      case s.WAXING_CRESCENT:
        return i2.WAXING_CRESCENT;
      default:
      case s.NEW:
        return i2.NEW;
      }
    }
    static isWaxing(e2) {
      return E.lunarAge(e2) <= 14.765;
    }
    static isWaning(e2) {
      return E.lunarAge(e2) > 14.765;
    }
  }
  var R = ((e2) => (e2.EARTH_RADII = "Earth Radii", e2.KILOMETERS = "km", e2.MILES = "m", e2))(R || {});
  return E;
}));
