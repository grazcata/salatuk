/**
 * Prayer Times Calculator
 * Based on astronomical calculations (same approach as Salatuk)
 * Supports multiple calculation methods including Moroccan (Habous)
 * 
 * Prayer names from Salatuk decompilation:
 *   sobh (Fajr), shorook (Sunrise), dohr (Dhuhr), asr (Asr), maghreb (Maghrib), ichaa (Isha)
 */

const PrayerMethods = {
  makkah:     { name: 'Umm al-Qura',           fajrAngle: 18.5, ishaAngle: 0,   ishaMinutes: 90 },
  mwl:        { name: 'Muslim World League',    fajrAngle: 18,   ishaAngle: 17,  ishaMinutes: 0 },
  isna:       { name: 'ISNA',                   fajrAngle: 15,   ishaAngle: 15,  ishaMinutes: 0 },
  egypt:      { name: 'Egyptian Authority',     fajrAngle: 19.5, ishaAngle: 17.5,ishaMinutes: 0 },
  karachi:    { name: 'Karachi',                fajrAngle: 18,   ishaAngle: 18,  ishaMinutes: 0 },
  tehran:     { name: 'Tehran',                 fajrAngle: 17.7, ishaAngle: 14,  ishaMinutes: 0 },
  jafari:     { name: 'Shia Ithna-Ashari',      fajrAngle: 16,   ishaAngle: 14,  ishaMinutes: 0 },
  morocco:    { name: 'Ministère Habous (Maroc)', fajrAngle: 19,  ishaAngle: 17, ishaMinutes: 0 },
  turkey:     { name: 'Diyanet (Turquie)',      fajrAngle: 18,   ishaAngle: 17,  ishaMinutes: 0 },
  singapore:  { name: 'MUIS (Singapour)',       fajrAngle: 20,   ishaAngle: 18,  ishaMinutes: 0 },
  france:     { name: 'UOIF (France)',          fajrAngle: 12,   ishaAngle: 12,  ishaMinutes: 0 },
  gulf:       { name: 'Gulf Region',            fajrAngle: 19.5, ishaAngle: 0,   ishaMinutes: 90 },
};

// Juristic method for Asr: 1 = Shafi/Hanbali/Maliki, 2 = Hanafi
const AsrJuristic = { STANDARD: 1, HANAFI: 2 };

class PrayerTimesCalculator {
  constructor(method = 'morocco', asrJuristic = AsrJuristic.STANDARD) {
    this.method = PrayerMethods[method] || PrayerMethods.morocco;
    this.methodKey = method;
    this.asrFactor = asrJuristic;
  }

  // Radians ↔ Degrees
  dtr(d) { return (d * Math.PI) / 180; }
  rtd(r) { return (r * 180) / Math.PI; }

  sin(d)  { return Math.sin(this.dtr(d)); }
  cos(d)  { return Math.cos(this.dtr(d)); }
  tan(d)  { return Math.tan(this.dtr(d)); }
  asin(x) { return this.rtd(Math.asin(x)); }
  acos(x) { return this.rtd(Math.acos(x)); }
  atan2(y, x) { return this.rtd(Math.atan2(y, x)); }

  // Julian Date from Gregorian
  julianDate(year, month, day) {
    if (month <= 2) { year -= 1; month += 12; }
    const A = Math.floor(year / 100);
    const B = 2 - A + Math.floor(A / 4);
    return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + B - 1524.5;
  }

  // Sun position calculations
  sunPosition(jd) {
    const D = jd - 2451545.0;
    const g = this.fixAngle(357.529 + 0.98560028 * D);
    const q = this.fixAngle(280.459 + 0.98564736 * D);
    const L = this.fixAngle(q + 1.915 * this.sin(g) + 0.020 * this.sin(2 * g));
    const e = 23.439 - 0.00000036 * D;
    const RA = this.atan2(this.cos(e) * this.sin(L), this.cos(L)) / 15;
    const eqt = q / 15 - this.fixHour(RA);
    const decl = this.asin(this.sin(e) * this.sin(L));
    return { declination: decl, equation: eqt };
  }

  fixAngle(a) { return this.fix(a, 360); }
  fixHour(a)  { return this.fix(a, 24); }
  fix(a, b) {
    a = a - b * Math.floor(a / b);
    return a < 0 ? a + b : a;
  }

  // Mid-day (Dhuhr) time
  midDay(t, jd, lng, timezone) {
    const sun = this.sunPosition(jd + t);
    const Z = this.fixHour(12 - sun.equation);
    return Z;
  }

  // Time for a given angle below horizon
  sunAngleTime(angle, t, jd, lat, direction) {
    const sun = this.sunPosition(jd + t);
    const decl = sun.declination;
    const noon = this.midDay(t, jd);
    const cosHA = (this.sin(angle) - this.sin(decl) * this.sin(lat)) /
                  (this.cos(decl) * this.cos(lat));
    
    if (cosHA > 1 || cosHA < -1) return NaN; // No valid time (polar)
    
    const HA = this.acos(cosHA) / 15;
    return noon + (direction === 'ccw' ? -HA : HA);
  }

  // Asr time based on shadow factor
  asrTime(factor, t, jd, lat) {
    const sun = this.sunPosition(jd + t);
    const decl = sun.declination;
    const noon = this.midDay(t, jd);
    // Shadow length = factor + tan(|lat - decl|), Asr angle = acot(shadow)
    const shadowLen = factor + Math.tan(this.dtr(Math.abs(lat - decl)));
    const angle = this.rtd(Math.atan(1 / shadowLen));
    // Use hour-angle approach directly for Asr
    const cosHA = (Math.sin(this.dtr(angle)) - Math.sin(this.dtr(decl)) * Math.sin(this.dtr(lat))) /
                  (Math.cos(this.dtr(decl)) * Math.cos(this.dtr(lat)));
    if (cosHA > 1 || cosHA < -1) return NaN;
    const HA = this.rtd(Math.acos(cosHA)) / 15;
    return noon + HA;
  }

  /**
   * Compute prayer times for a given date and location
   * Returns { sobh, shorook, dohr, asr, maghreb, ichaa } in "HH:MM" format
   * Matching Salatuk's internal naming: sobh, shorook, dohr, asr, maghreb, ichaa
   */
  getTimes(date, lat, lng, timezone, altitude = 0) {
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const jd = this.julianDate(year, month, day) - lng / (15 * 24);

    // Altitude correction for sunrise/sunset
    const altCorr = 0.0347 * Math.sqrt(altitude);

    // Iterative computation
    let t = 0;
    const noon = this.midDay(t, jd);
    
    // Fajr
    const fajr = this.sunAngleTime(-this.method.fajrAngle, t, jd, lat, 'ccw');
    
    // Sunrise
    const sunrise = this.sunAngleTime(-(0.833 + altCorr), t, jd, lat, 'ccw');
    
    // Dhuhr (+ 1-2 min safety margin as in Salatuk)
    const dhuhr = noon;
    
    // Asr
    const asr = this.asrTime(this.asrFactor, t, jd, lat);
    
    // Maghrib (sunset)
    const sunset = this.sunAngleTime(-(0.833 + altCorr), t, jd, lat, 'cw');
    
    // Isha
    let isha;
    if (this.method.ishaMinutes > 0) {
      isha = sunset + this.method.ishaMinutes / 60;
    } else {
      isha = this.sunAngleTime(-this.method.ishaAngle, t, jd, lat, 'cw');
    }

    // Apply timezone offset
    const diff = timezone - lng / 15;

    const times = {
      sobh:    fajr + diff,
      shorook: sunrise + diff,
      dohr:    dhuhr + diff,
      asr:     asr + diff,
      maghreb: sunset + diff,
      ichaa:   isha + diff,
    };

    // Format
    const result = {};
    for (const [key, val] of Object.entries(times)) {
      result[key] = this.formatTime(val);
    }
    result._raw = times; // Keep raw for countdown
    return result;
  }

  formatTime(hours) {
    if (isNaN(hours)) return '--:--';
    hours = this.fixHour(hours + 0.5 / 60); // round to nearest minute
    const h = Math.floor(hours);
    const m = Math.floor((hours - h) * 60);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  /**
   * Get Qibla direction from a given location
   * Same spherical trigonometry as Salatuk's QiblaActivity
   */
  static qiblaDirection(lat, lng) {
    const kaabaLat = 21.4225;
    const kaabaLng = 39.8262;
    const dLng = (kaabaLng - lng) * Math.PI / 180;
    const latR = lat * Math.PI / 180;
    const kLatR = kaabaLat * Math.PI / 180;
    const y = Math.sin(dLng);
    const x = Math.cos(latR) * Math.tan(kLatR) - Math.sin(latR) * Math.cos(dLng);
    let qibla = Math.atan2(y, x) * 180 / Math.PI;
    return (qibla + 360) % 360;
  }

  /**
   * Auto-detect method by country code (Salatuk logic from decompiled p.f())
   */
  static methodForCountry(countryCode) {
    const map = {
      MA: 'morocco', DZ: 'morocco', TN: 'morocco', LY: 'egypt',
      EG: 'egypt', SD: 'egypt', IQ: 'egypt', JO: 'egypt', PS: 'egypt', SY: 'egypt', LB: 'egypt',
      SA: 'makkah', AE: 'gulf', QA: 'gulf', BH: 'gulf', KW: 'gulf', OM: 'gulf', YE: 'makkah',
      TR: 'turkey',
      IR: 'tehran',
      PK: 'karachi', AF: 'karachi', BD: 'karachi',
      IN: 'karachi',
      MY: 'singapore', SG: 'singapore', ID: 'singapore', BN: 'singapore',
      US: 'isna', CA: 'isna',
      FR: 'france', BE: 'france',
      GB: 'mwl', DE: 'mwl', NL: 'mwl', IT: 'mwl', ES: 'mwl',
    };
    return map[countryCode?.toUpperCase()] || 'mwl';
  }
}

// Export for module use
if (typeof module !== 'undefined') module.exports = { PrayerTimesCalculator, PrayerMethods, AsrJuristic };
