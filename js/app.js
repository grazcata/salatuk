/**
 * Salatuk PWA - Main Application
 * Prayer times, Qibla compass, countdown, notifications
 */

const PRAYER_NAMES = {
  sobh:    { ar: 'الفجر',    fr: 'Fajr',    en: 'Fajr',    icon: '🌙' },
  shorook: { ar: 'الشروق',   fr: 'Lever',   en: 'Sunrise', icon: '🌅' },
  dohr:    { ar: 'الظهر',    fr: 'Dhuhr',   en: 'Dhuhr',   icon: '☀️' },
  asr:     { ar: 'العصر',    fr: 'Asr',     en: 'Asr',     icon: '🌤️' },
  maghreb: { ar: 'المغرب',   fr: 'Maghrib', en: 'Maghrib', icon: '🌇' },
  ichaa:   { ar: 'العشاء',   fr: 'Isha',    en: 'Isha',    icon: '🌃' },
};

const PRAYER_ORDER = ['sobh', 'shorook', 'dohr', 'asr', 'maghreb', 'ichaa'];

class SalatukApp {
  constructor() {
    this.settings = this.loadSettings();
    this.calculator = new PrayerTimesCalculator(
      this.settings.method,
      this.settings.asrJuristic
    );
    this.times = null;
    this.countdownInterval = null;
    this.compassWatchId = null;

    this.init();
  }

  loadSettings() {
    const defaults = {
      method: 'morocco',
      asrJuristic: 1,
      lat: null,
      lng: null,
      city: '',
      timezone: null,
      adjustments: { sobh: 0, shorook: 0, dohr: 0, asr: 0, maghreb: 0, ichaa: 0 },
      notifications: true,
      darkMode: window.matchMedia('(prefers-color-scheme: dark)').matches,
      lang: 'fr',
    };
    try {
      const saved = JSON.parse(localStorage.getItem('salatuk_settings'));
      return { ...defaults, ...saved };
    } catch {
      return defaults;
    }
  }

  saveSettings() {
    localStorage.setItem('salatuk_settings', JSON.stringify(this.settings));
  }

  async init() {
    this.setupTheme();
    this.setupNavigation();
    this.setupSettings();

    if (this.settings.lat && this.settings.lng) {
      this.computeAndDisplay();
    }
    await this.getLocation();
  }

  // ─── Location ────────────────────────────
  async getLocation() {
    const statusEl = document.getElementById('location-status');
    if (!navigator.geolocation) {
      if (statusEl) statusEl.textContent = 'Géolocalisation non supportée';
      return;
    }

    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          this.settings.lat = pos.coords.latitude;
          this.settings.lng = pos.coords.longitude;
          this.settings.timezone = -new Date().getTimezoneOffset() / 60;
          
          // Reverse geocode
          try {
            const resp = await fetch(
              `https://nominatim.openstreetmap.org/reverse?lat=${pos.coords.latitude}&lon=${pos.coords.longitude}&format=json&accept-language=fr`
            );
            const data = await resp.json();
            this.settings.city = data.address?.city || data.address?.town || data.address?.village || '';
            const country = data.address?.country_code?.toUpperCase();
            if (country && !localStorage.getItem('salatuk_settings')) {
              this.settings.method = PrayerTimesCalculator.methodForCountry(country);
              this.calculator = new PrayerTimesCalculator(this.settings.method, this.settings.asrJuristic);
              const methodSelect = document.getElementById('calc-method');
              if (methodSelect) methodSelect.value = this.settings.method;
            }
          } catch { /* offline, no problem */ }

          this.saveSettings();
          this.computeAndDisplay();
          resolve();
        },
        (err) => {
          if (statusEl) statusEl.textContent = `📍 ${err.message}`;
          // Use saved location if available
          if (this.settings.lat) this.computeAndDisplay();
          resolve();
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    });
  }

  // ─── Prayer Times Computation ────────────────────
  computeAndDisplay() {
    const now = new Date();
    this.times = this.calculator.getTimes(
      now,
      this.settings.lat,
      this.settings.lng,
      this.settings.timezone,
      0
    );

    this.displayTimes();
    this.startCountdown();
    this.updateQibla();
  }

  displayTimes() {
    const container = document.getElementById('prayer-list');
    if (!container) return;

    const now = new Date();
    const nowSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
    const next = this.getNextPrayer();
    const lang = this.settings.lang;

    // City display
    const cityEl = document.getElementById('city-name');
    if (cityEl) cityEl.textContent = this.settings.city || `${this.settings.lat?.toFixed(2)}°, ${this.settings.lng?.toFixed(2)}°`;
    
    const dateEl = document.getElementById('date-display');
    if (dateEl) {
      const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
      dateEl.textContent = now.toLocaleDateString('fr-FR', options);
    }

    container.innerHTML = '';
    for (const key of PRAYER_ORDER) {
      const info = PRAYER_NAMES[key];
      const time = this.times[key];
      const isNext = key === next;
      const isPassed = this.times._raw[key] !== undefined && 
                       (this.times._raw[key] * 3600 + (this.settings.timezone || 0)) < nowSec;

      const row = document.createElement('div');
      row.className = `prayer-row${isNext ? ' next' : ''}${key === 'shorook' ? ' sunrise' : ''}`;
      row.innerHTML = `
        <span class="prayer-icon">${info.icon}</span>
        <span class="prayer-name">${info[lang] || info.fr}</span>
        <span class="prayer-time">${time}</span>
      `;
      container.appendChild(row);
    }
  }

  getNextPrayer() {
    if (!this.times?._raw) return 'sobh';
    const now = new Date();
    const nowHours = now.getHours() + now.getMinutes() / 60 + now.getSeconds() / 3600;
    
    for (const key of PRAYER_ORDER) {
      if (key === 'shorook') continue; // Skip sunrise for "next prayer"
      const t = this.times._raw[key];
      if (t > nowHours) return key;
    }
    return 'sobh'; // Next day's fajr
  }

  startCountdown() {
    if (this.countdownInterval) clearInterval(this.countdownInterval);
    
    const update = () => {
      const next = this.getNextPrayer();
      const el = document.getElementById('countdown');
      const nextNameEl = document.getElementById('next-prayer-name');
      if (!el || !this.times?._raw) return;

      const now = new Date();
      const nowSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
      let targetSec = Math.floor(this.times._raw[next] * 3600);
      
      if (targetSec <= nowSec) {
        targetSec += 24 * 3600; // Tomorrow
      }

      let diff = targetSec - nowSec;
      const h = Math.floor(diff / 3600);
      const m = Math.floor((diff % 3600) / 60);
      const s = diff % 60;

      el.textContent = `-${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
      
      const lang = this.settings.lang;
      const info = PRAYER_NAMES[next];
      if (nextNameEl) nextNameEl.textContent = `${info.icon} ${info[lang] || info.fr}`;
    };

    update();
    this.countdownInterval = setInterval(update, 1000);
  }

  // ─── Qibla Compass ────────────────────
  updateQibla() {
    if (!this.settings.lat || !this.settings.lng) return;
    const qibla = PrayerTimesCalculator.qiblaDirection(this.settings.lat, this.settings.lng);
    
    const degEl = document.getElementById('qibla-degree');
    if (degEl) degEl.textContent = `${qibla.toFixed(1)}°`;

    this.startCompass(qibla);
  }

  startCompass(qiblaAngle) {
    const compassEl = document.getElementById('compass-rose');
    const needleEl = document.getElementById('qibla-needle');
    if (!compassEl || !needleEl) return;

    const handleOrientation = (event) => {
      let heading;
      if (event.webkitCompassHeading !== undefined) {
        heading = event.webkitCompassHeading; // iOS
      } else if (event.alpha !== null) {
        heading = 360 - event.alpha; // Android
      } else {
        return;
      }

      compassEl.style.transform = `rotate(${-heading}deg)`;
      needleEl.style.transform = `rotate(${qiblaAngle - heading}deg)`;
    };

    if (typeof DeviceOrientationEvent !== 'undefined' &&
        typeof DeviceOrientationEvent.requestPermission === 'function') {
      // iOS 13+
      const btn = document.getElementById('compass-permission');
      if (btn) {
        btn.style.display = 'block';
        btn.onclick = async () => {
          const perm = await DeviceOrientationEvent.requestPermission();
          if (perm === 'granted') {
            window.addEventListener('deviceorientation', handleOrientation, true);
            btn.style.display = 'none';
          }
        };
      }
    } else {
      window.addEventListener('deviceorientation', handleOrientation, true);
    }
  }

  // ─── Theme ────────────────────
  setupTheme() {
    document.documentElement.setAttribute('data-theme', this.settings.darkMode ? 'dark' : 'light');
    const toggle = document.getElementById('theme-toggle');
    if (toggle) {
      toggle.checked = this.settings.darkMode;
      toggle.addEventListener('change', () => {
        this.settings.darkMode = toggle.checked;
        document.documentElement.setAttribute('data-theme', toggle.checked ? 'dark' : 'light');
        this.saveSettings();
      });
    }
  }

  // ─── Navigation ────────────────────
  setupNavigation() {
    const navBtns = document.querySelectorAll('.nav-btn');
    navBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const page = btn.dataset.page;
        document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
        document.getElementById(`page-${page}`)?.classList.add('active');
        navBtns.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
      });
    });
  }

  // ─── Settings ────────────────────
  setupSettings() {
    const methodSelect = document.getElementById('calc-method');
    if (methodSelect) {
      methodSelect.value = this.settings.method;
      methodSelect.addEventListener('change', () => {
        this.settings.method = methodSelect.value;
        this.calculator = new PrayerTimesCalculator(this.settings.method, this.settings.asrJuristic);
        this.saveSettings();
        this.computeAndDisplay();
      });
    }

    const asrSelect = document.getElementById('asr-method');
    if (asrSelect) {
      asrSelect.value = this.settings.asrJuristic;
      asrSelect.addEventListener('change', () => {
        this.settings.asrJuristic = parseInt(asrSelect.value);
        this.calculator = new PrayerTimesCalculator(this.settings.method, this.settings.asrJuristic);
        this.saveSettings();
        this.computeAndDisplay();
      });
    }

    // Adjustment sliders
    for (const prayer of PRAYER_ORDER) {
      const slider = document.getElementById(`adj-${prayer}`);
      const label = document.getElementById(`adj-${prayer}-val`);
      if (slider) {
        slider.value = this.settings.adjustments[prayer] || 0;
        if (label) label.textContent = `${slider.value} min`;
        slider.addEventListener('input', () => {
          this.settings.adjustments[prayer] = parseInt(slider.value);
          if (label) label.textContent = `${slider.value} min`;
          this.saveSettings();
          this.computeAndDisplay();
        });
      }
    }

    // Notifications toggle
    const notifToggle = document.getElementById('notif-toggle');
    if (notifToggle) {
      notifToggle.checked = this.settings.notifications;
      notifToggle.addEventListener('change', async () => {
        if (notifToggle.checked && 'Notification' in window) {
          const perm = await Notification.requestPermission();
          notifToggle.checked = perm === 'granted';
        }
        this.settings.notifications = notifToggle.checked;
        this.saveSettings();
      });
    }
  }
}

// Boot
document.addEventListener('DOMContentLoaded', () => {
  window.app = new SalatukApp();
});
