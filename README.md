# Motion Emulator Software Development Kit

[Motion Emulator](https://github.com/zhufucdev/MotionEmulator) is a Xposed-enabled 
location & sensor mock platform. This is where you get started when developing
a Motion Emulator plug-in.

## Dependencies

The SDK is on maven central. It is available in three forms.

```kotlin
dependencies {
    implementation("com.zhufucdev.me:stub:1.0.0") // Stub
    implementation("com.zhufucdev.me:plugin:1.0.0") // Common plugin
    implementation("com.zhufucdev.me:xposed:1.0.0") // Xposed plugin
}
```

When writing an Xposed plugin, you would probably enable YukiHook support.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

dependencies {
    val yukiVersion = "1.2.0"
    ksp("com.highcapable.yukihookapi:ksp-xposed:${yukiVersion}")
    implementation("com.highcapable.yukihookapi:api:${yukiVersion}")
    compileOnly("de.robv.android.xposed:api:82")
}
```

For more details, go to
[Quick Start | YukiHook API](https://highcapable.github.io/YukiHookAPI/en/guide/quick-start.html)

## Modifying manifest

Motion Emulator and Xposed frameworks use <meta-data> tags make it clear for user
what the plug-in is designed for. If you were writing a plug-in which doesn't involve
Xposed, you would only have me_description in the application tag.

Also, a broadcast receiver (.ControllerReceiver) is required. We will come back to this later.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".Application"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:supportsRtl="true">

        <receiver
            android:name=".ControllerReceiver"
            android:exported="true" />

        <meta-data
            android:name="me_description"
            android:value="@string/text_description" />

        <meta-data
            android:name="xposedmodule"
            android:value="true" />
        <meta-data
            android:name="xposeddescription"
            android:value="@string/text_description_xposed" />
        <meta-data
            android:name="xposedminversion"
            android:value="93" />
        <meta-data
            android:name="xposedsharedprefs"
            android:value="true" />
    </application>
</manifest>
```

## Getting connected

Motion Emulator uses a custom protocol to transit emulation data (trace & motion) and keep
track of active agents. Connector of the protocol is here. You just use it.

```kotlin
import com.zhufucdev.me.plugin.WsServer
import com.zhufucdev.me.plugin.connect
import com.zhufucdev.me.stub.Emulation
import kotlin.time.Duration.Companion.seconds

suspend fun connect(server: WsServer) {
    while (true) {
        server.connect(id = "hi i am john cena") {
            if (emulation.isPresent) {
                startEmulation(emulation)
            }
        }
        delay(1.seconds)
    }
}

suspend fun startEmulation(emulation: Emulation) {
    // do something
}
```

To initialize a WsServer instance, you just query it.
```kotlin
import com.zhufucdev.me.plugin.MePlugin
import android.content.Context

val Context.meServer get() = MePlugin.queryServer(context)
val Context.meMethod get() = MePlugin.queryMethod(context) // You may also need this
```

If it's not possible to query and do stuff, just query it a head of time and save it somewhere. 
Remember that ControllerReceiver in the last section? You will need it namely, and it must
be placed in the package root.

This smells stinky, but what can you do to me? Probably nothing.

```kotlin
class ControllerReceiver : PluginBroadcastReceiver() {
    override fun onEmulationStart(context: Context) {
        // you have to override this one, because it sounds very imported
    }

    override fun onEmulationStop(context: Context) {
        // you don't have to override this one
        // it just happens to be here somehow
    }

    override fun onSettingsChanged(context: Context) {
        context.prefs().edit { // yuki hook api stuff, you do whatever
            val server = MePlugin.queryServer(context)
            val method = MePlugin.queryMethod(context)
            putBoolean("me_server_tls", server.useTls)
            putInt("me_server_port", server.port)
            putString("me_method", method.name)
        }
    }
}
```

## Schedule emulation

Your plug-in is responsible for following the order of Motion Emulator. I think it's probably fair
because it's already sent you the emulation data before making orders. 

To make this easier, there's something called an AbstractScheduler.

```kotlin
import com.zhufucdev.me.plugin.AbstractScheduler
import com.aventrix.jnanoid.jnanoid.NanoIdUtils

object YourScheduler : AbstractScheduler() {
    private lateinit var server: WsServer
    private val emulationId = NanoIdUtils.randomNanoId()
    
    fun init(server: WsServer) {
        server.connect(emulationId) {
            if (emulation.isPresent) {
                startEmulation(emulation) // implemented in AbstractScheduler
            }
        }
        delay(1.seconds)
    }

    override suspend fun ServerScope.startTraceEmulation(trace: Trace) {
        // do something with it
    }
    
    // feel free to override more
}

```

Notice that we declare `YourScheduler` as an object (single instance). It makes senses when you
think of how many positions a phone can be at at the same time.

## Getting Xposed

If you would write an Xposed plugin, you can write the mock location and sensor implementation
all yourself, in which case I would appreciate you and copy your code to my plug-in if it were better.
But you won't do that anyway, and here's why.

There's something called the XposedScheduler, which has implemented all the mock aspects of AbstractScheduler.

- startStepsEmulation
- startMotionSimulation
- startTraceEmulation
- startCellEmulation

Like its name, it implements the mocking logic in Xposed. You just extend it and implement
a few stuffs.

```kotlin
class YourScheduler : XposedScheduler() {
    private lateinit var server: WsServer
    
    override fun PackageParam.initialize() {
        val prefs = prefs() // read from XSharedPreference, priorly saved in ControllerReceiver
        server = WsServer(
            port = prefs.getInt("me_server_port", 20230),
            useTls = prefs.getBoolean("me_server_tls", true)
        )

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            startServer()
        }
    }

    override fun PackageParam.getHookingMethod(): Method =
        prefs(PREFERENCE_NAME_BRIDGE).getString("me_method", "xposed_only").let {
            Method.valueOf(it.uppercase())
        }

    private suspend fun startServer() {
        var warned = false

        while (true) {
            server.connect(id) {
                if (emulation.isPresent)
                    startEmulation(emulation.get()) // implemented in AbstractScheduler
            }

            if (!warned) {
                loggerI(
                    tag = TAG,
                    msg = "Provider offline. Waiting for data channel to become online"
                )
                warned = true
            }
            delay(1.seconds)
        }
    }
}
```

Congratulations!! You've just written the Motion Emulator Websocket Plugin!! Yay!!

## Next steps

If you have read carefully, or better, run these code yourself, the architecture of Motion Emulator
should be clear. You are supposed to come up with your own middleware protocol like the 
[Content Provider Plugin](https://github.com/Xposed-Modules-Repo/com.zhufucdev.cp_plugin),
or your own faking implementation like the 
[Mock Location Plugin](https://github.com/zhufucdev/com.zhufucdev.mock_location_plugin).
Wish you good luck. Either way, have fun.
