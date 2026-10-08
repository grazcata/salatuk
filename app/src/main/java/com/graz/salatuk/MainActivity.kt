package com.graz.salatuk

import android.Manifest
import android.content.Context
import android.hardware.*
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.graz.salatuk.ui.theme.SalatukTheme
import com.graz.salatuk.util.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SalatukTheme {
                SalatukApp()
            }
        }
    }
}

// ─── ViewModel-like state holder ───
data class AppState(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val city: String = "Chargement...",
    val times: PrayerTimes? = null,
    val method: CalculationMethod = CalculationMethod.MOROCCO,
    val asrJuristic: AsrJuristic = AsrJuristic.SHAFI,
    val qiblaAngle: Double = 0.0,
    val heading: Float = 0f,
    val locationReady: Boolean = false,
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SalatukApp() {
    val navController = rememberNavController()
    var state by remember { mutableStateOf(AppState()) }
    val context = LocalContext.current

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    // Request location on launch
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val tz = TimeZone.getDefault().rawOffset / 3600000.0
                        val cal = Calendar.getInstance()
                        val calc = PrayerTimesCalculator(state.method, state.asrJuristic)
                        val times = calc.calculate(
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                            loc.latitude, loc.longitude, tz
                        )
                        var cityName = "%.2f°, %.2f°".format(loc.latitude, loc.longitude)
                        try {
                            @Suppress("DEPRECATION")
                            val addresses = Geocoder(context, Locale.FRENCH).getFromLocation(loc.latitude, loc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                cityName = addresses[0].locality ?: addresses[0].subAdminArea ?: cityName
                            }
                        } catch (_: Exception) {}

                        state = state.copy(
                            lat = loc.latitude, lng = loc.longitude,
                            city = cityName, times = times, locationReady = true,
                            qiblaAngle = PrayerTimesCalculator.qiblaDirection(loc.latitude, loc.longitude)
                        )
                    }
                }
            } catch (_: SecurityException) {}
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Recalc when method changes
    LaunchedEffect(state.method, state.asrJuristic) {
        if (state.locationReady) {
            val cal = Calendar.getInstance()
            val tz = TimeZone.getDefault().rawOffset / 3600000.0
            val calc = PrayerTimesCalculator(state.method, state.asrJuristic)
            state = state.copy(times = calc.calculate(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                state.lat, state.lng, tz
            ))
        }
    }

    Scaffold(
        bottomBar = {
            var selectedTab by remember { mutableIntStateOf(0) }
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Schedule, "Horaires") },
                    label = { Text("Horaires") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; navController.navigate("prayers") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, "Qibla") },
                    label = { Text("Qibla") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; navController.navigate("qibla") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Réglages") },
                    label = { Text("Réglages") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; navController.navigate("settings") { popUpTo(0) } }
                )
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "prayers", Modifier.padding(padding)) {
            composable("prayers") { PrayersScreen(state) }
            composable("qibla") { QiblaScreen(state) }
            composable("settings") {
                SettingsScreen(state) { newState -> state = newState }
            }
        }
    }
}

// ─── Prayers Screen ───
@Composable
fun PrayersScreen(state: AppState) {
    val prayers = listOf(
        "🌙" to "Fajr" to (state.times?.fajr to state.times?.fajrHours),
        "🌅" to "Lever" to (state.times?.sunrise to state.times?.sunriseHours),
        "☀️" to "Dhuhr" to (state.times?.dhuhr to state.times?.dhuhrHours),
        "🌤️" to "Asr" to (state.times?.asr to state.times?.asrHours),
        "🌇" to "Maghrib" to (state.times?.maghrib to state.times?.maghribHours),
        "🌃" to "Isha" to (state.times?.isha to state.times?.ishaHours),
    )

    // Find next prayer
    val now = Calendar.getInstance()
    val nowHours = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
    val nextIdx = prayers.indexOfFirst { (_, timeP) ->
        val h = timeP.second ?: 0.0
        h > nowHours
    }.let { if (it == -1 || it == 1) 0 else it } // skip sunrise, fallback to fajr

    // Countdown
    var countdown by remember { mutableStateOf("--:--:--") }
    LaunchedEffect(state.times) {
        while (true) {
            val c = Calendar.getInstance()
            val nowSec = c.get(Calendar.HOUR_OF_DAY) * 3600 + c.get(Calendar.MINUTE) * 60 + c.get(Calendar.SECOND)
            val targetH = prayers.getOrNull(nextIdx)?.second?.second ?: 0.0
            var diff = (targetH * 3600).toInt() - nowSec
            if (diff < 0) diff += 86400
            val h = diff / 3600; val m = (diff % 3600) / 60; val s = diff % 60
            countdown = "-%02d:%02d:%02d".format(h, m, s)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Header
        Text("صلاتك", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(state.city, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
        Text(
            SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date()),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
        )

        Spacer(Modifier.height(16.dp))

        // Countdown card
        val nextName = prayers.getOrNull(nextIdx)?.first?.second ?: "Fajr"
        val nextIcon = prayers.getOrNull(nextIdx)?.first?.first ?: "🌙"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1B7A4A), Color(0xFF2D9A5E))
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Prochaine prière", color = Color.White.copy(0.85f), fontSize = 14.sp)
                    Text("$nextIcon $nextName", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(countdown, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Prayer rows
        prayers.forEachIndexed { idx, (iconName, timeP) ->
            val (icon, name) = iconName
            val (time, _) = timeP
            val isNext = idx == nextIdx
            val isSunrise = idx == 1

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNext)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = if (isNext) CardDefaults.outlinedCardBorder() else null,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .then(if (isSunrise) Modifier else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
                    Text(
                        name, fontSize = 16.sp, fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        color = if (isSunrise) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        time ?: "--:--", fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isSunrise) MaterialTheme.colorScheme.primary.copy(0.5f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Qibla Screen ───
@Composable
fun QiblaScreen(state: AppState) {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }

    // Compass sensor
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, 3)
                    Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomag, 0, 3)
                }
                val r = FloatArray(9); val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, i, gravity, geomag)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (heading < 0) heading += 360f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Direction de la Qibla", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
        Spacer(Modifier.height(8.dp))
        Text("%.1f°".format(state.qiblaAngle), fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))

        // Compass
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rose
            Card(
                modifier = Modifier
                    .size(260.dp)
                    .rotate(-heading),
                shape = RoundedCornerShape(130.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text("N", modifier = Modifier.padding(top = 12.dp),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            // Qibla needle
            Text(
                "🕋",
                fontSize = 32.sp,
                modifier = Modifier
                    .rotate(state.qiblaAngle.toFloat() - heading)
                    .offset(y = (-100).dp)
            )
            // Center dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.3f))
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Tenez votre téléphone à plat.\nLa Kaaba 🕋 indique la direction.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Settings Screen ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, onUpdate: (AppState) -> Unit) {
    var expandedMethod by remember { mutableStateOf(false) }
    var expandedAsr by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("⚙️ Réglages", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Method
        Text("MÉTHODE DE CALCUL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f), letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            ExposedDropdownMenuBox(expanded = expandedMethod, onExpandedChange = { expandedMethod = it }) {
                OutlinedTextField(
                    value = state.method.label, onValueChange = {},
                    readOnly = true, label = { Text("Convention") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedMethod) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedMethod, onDismissRequest = { expandedMethod = false }) {
                    CalculationMethod.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            onClick = { onUpdate(state.copy(method = m)); expandedMethod = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            ExposedDropdownMenuBox(expanded = expandedAsr, onExpandedChange = { expandedAsr = it }) {
                OutlinedTextField(
                    value = state.asrJuristic.label, onValueChange = {},
                    readOnly = true, label = { Text("Asr (école)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAsr) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedAsr, onDismissRequest = { expandedAsr = false }) {
                    AsrJuristic.entries.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.label) },
                            onClick = { onUpdate(state.copy(asrJuristic = a)); expandedAsr = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("INFORMATIONS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f), letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("📍 ${state.city}", fontSize = 14.sp)
                Text("Lat: %.4f  Lng: %.4f".format(state.lat, state.lng),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Spacer(Modifier.height(8.dp))
                Text("🕋 Qibla: %.1f°".format(state.qiblaAngle), fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Salatuk v1.0.0 — Usage personnel\nCalculs astronomiques locaux\nAnalysé avec REA",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}
