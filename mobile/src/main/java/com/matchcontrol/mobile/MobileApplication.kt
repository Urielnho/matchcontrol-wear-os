package com.matchcontrol.mobile
import android.app.Application
import com.matchcontrol.core.sync.AppGraph
class MobileApplication: Application() {
 override fun onCreate() {super.onCreate();AppGraph.configureBackend(this,BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY)}
}
