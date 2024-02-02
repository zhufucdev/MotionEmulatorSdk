@file:Suppress("DEPRECATION")

package com.zhufucdev.me.xposed

import android.app.Activity
import android.content.ContentResolver
import android.location.GnssStatus
import android.location.GpsSatellite
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.location.OnNmeaMessageListener
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.android.ActivityClass
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.DoubleType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.android
import com.zhufucdev.me.stub.estimateSpeed
import com.zhufucdev.me.stub.offsetFixed
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.concurrent.timer
import kotlin.random.Random
import kotlin.reflect.jvm.isAccessible

class LocationHooker(private val scheduler: XposedScheduler) : YukiBaseHooker() {
    companion object {
        private const val TAG = "LocationHook"
    }

    private var lastLocation = scheduler.location to System.currentTimeMillis()
    private var estimatedSpeed = 0F

    private val listeners = mutableMapOf<Any, (Point) -> Unit>()
    override fun onHook() {
        if (scheduler.hookingMethod.directHook) {
            invalidateOthers()
            hookGPS()
            val appClassLoaderSucceeded = appClassLoader?.let { hookAMap(it) } == true
            if (!appClassLoaderSucceeded) {
                ActivityClass
                    .method {
                        name = "onCreate"
                        param(BundleClass)
                    }
                    .hook {
                        before {
                            hookAMap((instance as Activity).classLoader, log = true)
                        }
                    }
            }
            hookLocation()
        } else if (scheduler.hookingMethod.testProviderTrick) {
            testProviderTrick()
        }
    }

    fun raise(point: Point) {
        listeners.forEach { (_, p) ->
            estimatedSpeed =
                runCatching {
                    estimateSpeed(
                        point to System.currentTimeMillis(),
                        lastLocation
                    ).toFloat()
                }.getOrDefault(0f)
            p.invoke(point)
            lastLocation = point to System.currentTimeMillis()
        }
    }

    private fun redirectListener(original: Any, l: (Point) -> Unit) {
        listeners[original] = l
    }

    private fun cancelRedirection(listener: Any) {
        listeners.remove(listener)
    }

    /**
     * Make network and cell providers invalid
     */
    private fun invalidateOthers() {
        YLog.info(tag = TAG, msg = "-- block other location methods --")

        classOf<WifiManager>().apply {
            method {
                name = "getScanResults"
                emptyParam()
                returnType = classOf<List<ScanResult>>()
            }
                .hook {
                    replaceTo(emptyList<ScanResult>())
                }

            method {
                name = "getWifiState"
                emptyParam()
                returnType = IntType
            }
                .hook {
                    replaceTo(WifiManager.WIFI_STATE_ENABLED)
                }

            method {
                name = "isWifiEnabled"
                emptyParam()
                returnType = BooleanType
            }
                .hook {
                    replaceTo(true)
                }

            method {
                name = "getConnectionInfo"
                emptyParam()
                returnType = classOf<WifiInfo>()
            }
                .hook {
                    replaceTo(null)
                }
        }

        classOf<NetworkInfo>().apply {
            method {
                name = "isConnectedOrConnecting"
                emptyParam()
                returnType = BooleanType
            }
                .hook {
                    replaceTo(true)
                }

            method {
                name = "isConnected"
                emptyParam()
                returnType = BooleanType
            }
                .hook {
                    replaceTo(true)
                }

            method {
                name = "isAvailable"
                emptyParam()
                returnType = BooleanType
            }
                .hook {
                    replaceTo(true)
                }
        }

        classOf<WifiInfo>().apply {
            method {
                name = "getSSID"
                emptyParam()
                returnType = StringClass
            }
                .hook {
                    replaceTo("null")
                }

            method {
                name = "getBSSID"
                emptyParam()
                returnType = StringClass
            }
                .hook {
                    replaceTo("00-00-00-00-00-00-00-00")
                }

            method {
                name = "getMacAddress"
                emptyParam()
                returnType = StringClass
            }
                .hook {
                    replaceTo("00-00-00-00-00-00-00-00")
                }
        }
    }

    private fun hookGPS() {
        YLog.info(tag = TAG, msg = "-- hook GPS --")

        val classOfLM = classOf<LocationManager>()
        classOfLM.apply {
            method {
                name = "getLastKnownLocation"
                param(StringClass, "android.location.LastLocationRequest".toClass())
                returnType = classOf<Location>()
            }
                .hook {
                    replaceAny {
                        scheduler.location.android(args(0).string(), estimatedSpeed)
                    }
                }

            method {
                name = "getLastLocation"
                emptyParam()
                returnType = classOf<Location>()
            }
                .hook {
                    replaceAny {
                        scheduler.location.android(speed = estimatedSpeed)
                    }
                }

            method {
                name {
                    it == "requestLocationUpdates" || it == "requestSingleUpdate"
                }
            }
                .hookAll {
                    replaceAny {
                        val listener =
                            args.firstOrNull { it is LocationListener } as LocationListener?
                                ?: return@replaceAny callOriginal()
                        val provider =
                            args.firstOrNull { it is String } as String?
                                ?: LocationManager.GPS_PROVIDER
                        val handler = Handler(Looper.getMainLooper())
                        redirectListener(listener) {
                            val location = it.android(provider, estimatedSpeed)
                            handler.post {
                                listener.onLocationChanged(location)
                            }
                        }
                        listener.onLocationChanged(
                            scheduler.location.android(
                                provider,
                                estimatedSpeed
                            )
                        )
                    }
                }

            method {
                name = "removeUpdates"
                param(classOf<LocationListener>())
            }
                .hook {
                    replaceAny {
                        val listener = args(0)
                        if (listeners.contains(listener)) {
                            cancelRedirection(listener)
                        } else {
                            callOriginal()
                        }
                    }
                }

            // make the app believe gps works
            method {
                name = "getGpsStatus"
                param(classOf<GpsStatus>())
                returnType = classOf<GpsStatus>()
            }.hook {
                after {
                    if (scheduler.satellites <= 0)
                        return@after

                    val info = args(0).cast<GpsStatus>() ?: result as GpsStatus
                    val method7 =
                        GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 8 }

                    if (method7 != null) {
                        method7.isAccessible = true

                        val prns = IntArray(scheduler.satellites) { it }
                        val ones = FloatArray(scheduler.satellites) { 1f }
                        val zeros = FloatArray(scheduler.satellites) { 0f }
                        val ephemerisMask = 0x1f
                        val almanacMask = 0x1f

                        //5 Scheduler.satellites are fixed
                        val usedInFixMask = 0x1f

                        method7.call(
                            info,
                            scheduler.satellites,
                            prns,
                            ones,
                            zeros,
                            zeros,
                            ephemerisMask,
                            almanacMask,
                            usedInFixMask
                        )
                    } else {
                        val method =
                            GpsStatus::class.members.firstOrNull { it.name == "setStatus" && it.parameters.size == 3 }
                        if (method == null) {
                            YLog.error(
                                tag = TAG,
                                msg = "method GpsStatus::setStatus is not provided"
                            )
                            return@after
                        }
                        method.isAccessible = true
                        val fake = fakeGnssStatus
                        method.call(info, fake, 1000 + Random.nextInt(-500, 500))
                    }
                    result = info
                }
            }

            method {
                name = "addGpsStatusListener"
                param(classOf<GpsStatus.Listener>())
                returnType = BooleanType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) {
                            return@replaceAny callOriginal()
                        }
                        val listener = args(0).cast<GpsStatus.Listener>()
                        listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED)
                        listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                        timer(name = "satellite heartbeat", period = 1000) {
                            listener?.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                        }
                        true
                    }
                }

            method {
                name = "addNmeaListener"
                param(classOf<GpsStatus.NmeaListener>())
                returnType = BooleanType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) {
                            return@replaceAny callOriginal()
                        }
                        false
                    }
                }

            method {
                name = "addNmeaListener"
                param(classOf<Executor>(), classOf<OnNmeaMessageListener>())
                returnType = BooleanType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) {
                            return@replaceAny callOriginal()
                        }
                        false
                    }
                }

            method {
                name = "registerGnssStatusCallback"
                param(classOf<Executor>(), classOf<GnssStatus.Callback>())
                returnType = BooleanType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) {
                            return@replaceAny callOriginal()
                        }

                        val callback = args(1).cast<GnssStatus.Callback>()
                        callback?.onStarted()
                        callback?.onFirstFix(1000 + Random.nextInt(-500, 500))
                        timer(name = "satellite heartbeat", period = 1000) {
                            callback?.onSatelliteStatusChanged(fakeGnssStatus ?: return@timer)
                        }
                        true
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                method {
                    name = "getCurrentLocation"
                    param(
                        StringClass,
                        classOf<LocationRequest>(),
                        classOf<CancellationSignal>(),
                        classOf<Executor>(),
                        classOf<Consumer<Location>>()
                    )
                    returnType = UnitType
                }
                    .hook {
                        replaceAny {
                            args(4).cast<Consumer<Location>>()
                                ?.accept(
                                    scheduler.location.android(args(0).string(), estimatedSpeed)
                                )
                        }
                    }
            }
        }

        classOf<GpsStatus>().apply {
            method {
                name = "getSatellites"
                emptyParam()
                returnType = classOf<Iterable<GpsSatellite>>()
            }
                .hook {
                    after {
                        if (scheduler.satellites <= 0)
                            return@after

                        result = fakeSatellites.also {
                            YLog.debug(tag = TAG, msg = "${it.count()} satellites are fixed")
                        }
                    }
                }

            method {
                name = "getMaxSatellites"
                emptyParam()
                returnType = IntType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) callOriginal()
                        else scheduler.satellites
                    }
                }
        }

        classOf<GnssStatus>().apply {
            method {
                name = "usedInFix"
                param(IntType)
                returnType = BooleanType
            }
                .hook {
                    replaceAny {
                        if (scheduler.satellites <= 0) callOriginal()
                        else true
                    }
                }
        }
    }

    /**
     * AMap uses network location, which
     * is a troublemaker for this project
     *
     * Specially designed for it
     */
    private fun hookAMap(classLoader: ClassLoader, log: Boolean = false): Boolean {
        YLog.info(tag = TAG, msg = "-- hook Amap --")
        var succeeded = true

        try {
            classLoader.loadAMapLocation().locationHook()

            classLoader.loadAMapLocation().apply {
                method {
                    name = "getSatellites"
                    emptyParam()
                }
                    .hook {
                        replaceAny {
                            scheduler.satellites
                        }
                    }

                method {
                    name = "getAccuracy"
                    emptyParam()
                }
                    .hook {
                        replaceTo(5F)
                    }
            }
        } catch (e: ClassNotFoundException) {
            succeeded = false
            if (log) {
                YLog.error(tag = TAG, msg = "Failed to hook AMap location", e = e)
            }
        }

        try {
            val listenerOf = mutableMapOf<Any, Any>()
            val stateOf = mutableMapOf<Any, Boolean>()

            classLoader.loadAMapLocationClient().apply {
                method {
                    name = "setLocationListener"
                    param(classLoader.loadAMapListener())
                }
                    .hook {
                        replaceAny {
                            val listener = args[0]
                                ?: return@replaceAny callOriginal()

                            listenerOf[instance] = listener
                            YLog.debug(tag = TAG, msg = "AMap location registered")
                        }
                    }

                method {
                    name = "startLocation"
                    emptyParam()
                }
                    .hook {
                        replaceUnit {
                            YLog.info(tag = TAG, msg = "AMap location started")
                            stateOf[instance] = true

                            val listener = listenerOf[instance] ?: return@replaceUnit
                            val listenerClass = classLoader.loadAMapListener()
                            val locationClass = classLoader.loadAMapLocation()
                            val method = listenerClass.getMethod(
                                "onLocationChanged",
                                locationClass
                            )
                            val handler = Handler(Looper.getMainLooper())

                            redirectListener(listener) {
                                val android = it.android(speed = estimatedSpeed)
                                // create an AMapLocation instance via reflection
                                val amap =
                                    locationClass.getConstructor(classOf<Location>())
                                        .newInstance(android)
                                // Location type 1 is GPS located
                                locationClass.getMethod("setLocationType", IntType)
                                    .invoke(amap, 1)
                                handler.post {
                                    method.invoke(listener, amap)
                                }
                                YLog.debug(tag = TAG, msg = "AMap location redirected")
                            }
                        }
                    }

                method {
                    name = "stopLocation"
                    emptyParam()
                }
                    .hook {
                        replaceUnit {
                            stateOf[instance] = false
                            YLog.info(tag = TAG, msg = "AMap location stopped")

                            val listener = listenerOf[instance] ?: return@replaceUnit
                            cancelRedirection(listener)
                        }
                    }

                method {
                    name = "isStarted"
                    emptyParam()
                    returnType(BooleanType)
                }
                    .hook {
                        replaceAny {
                            stateOf[instance] == true
                        }
                    }
            }
        } catch (e: ClassNotFoundException) {
            succeeded = false
            if (log) {
                YLog.error(tag = TAG, msg = "Failed to hook AMap Location Client", e = e)
            }
        }

        return succeeded
    }

    private fun Class<*>.locationHook() {
        method {
            name = "getLatitude"
            emptyParam()
            returnType = DoubleType
        }
            .hook {
                after {
                    result = scheduler.location.offsetFixed().latitude
                }
            }

        method {
            name = "getLongitude"
            emptyParam()
            returnType = DoubleType
        }
            .hook {
                after {
                    result = scheduler.location.offsetFixed().longitude
                }
            }
    }

    private fun hookLocation() {
        YLog.info(tag = TAG, msg = "-- hook location --")

        classOf<Location>().locationHook()
    }

    private fun testProviderTrick() {
        YLog.info(tag = TAG, msg = "-- make test provider undetectable --")

        classOf<Location>().apply {
            method {
                name = "isMock"
                emptyParam()
            }
                .hook {
                    replaceToFalse()
                }

            method {
                name = "isFromMockProvider"
                emptyParam()
            }
                .hook {
                    replaceToFalse()
                }
        }

        classOf<Settings.Secure>()
            .method {
                name = "getString"
                param(classOf<ContentResolver>(), StringClass)
                modifiers { isStatic }
            }
            .hook {
                after {
                    val item = args(1).string()
                    if (item == Settings.Secure.ALLOW_MOCK_LOCATION) {
                        YLog.info(tag = TAG, msg = "Spoof mock location developer options")
                        result = "0"
                    }
                }
            }

        classOf<LocationManager>().method {
            name = "removeTestProvider"
            param(StringClass)
        }
            .hook {
                replaceUnit {
                    YLog.info(tag = TAG, msg = "Block test provider removal")
                }
            }
    }

    private val fakeGnssStatus: GnssStatus?
        get() {
            val svid = IntArray(scheduler.satellites) { it }
            val zeros = FloatArray(scheduler.satellites) { 0f }
            val ones = FloatArray(scheduler.satellites) { 1f }

            val constructor = GnssStatus::class.constructors.firstOrNull { it.parameters.size >= 6 }
            if (constructor == null) {
                YLog.error(tag = TAG, msg = "GnssStatus constructor not available")
                return null
            }

            val constructorArgs = Array(constructor.parameters.size) { index ->
                when (index) {
                    0 -> scheduler.satellites
                    1 -> svid
                    2 -> ones
                    else -> zeros
                }
            }
            return constructor.call(*constructorArgs)
        }

    private val fakeSatellites: Iterable<GpsSatellite> =
        buildList {
            val clz = classOf<GpsSatellite>()
            for (i in 1..scheduler.satellites) {
                val instance =
                    clz.constructor {
                        param(IntType)
                    }
                        .get()
                        .newInstance<GpsSatellite>(i) ?: return@buildList
                listOf("mValid", "mHasEphemeris", "mHasAlmanac", "mUsedInFix").forEach {
                    clz.field { name = it }.get(instance).setTrue()
                }
                listOf("mSnr", "mElevation", "mAzimuth").forEach {
                    clz.field { name = it }.get(instance).set(1F)
                }
                add(instance)
            }
        }
}