package io.github.tsuyosh.trustedwebactivitiesdemo

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.customtabs.CustomTabsCallback
import android.support.customtabs.CustomTabsClient
import android.support.customtabs.CustomTabsIntent
import android.support.customtabs.CustomTabsServiceConnection
import android.support.customtabs.CustomTabsSession
import android.support.customtabs.TrustedWebUtils
import android.support.v7.app.AppCompatActivity
import android.util.Log

class LauncherActivity : AppCompatActivity() {
    private lateinit var mServiceConnection: TwaCustomTabsServiceConnection
    private var mTwaWasLaunched: Boolean = false

    protected val launchingUrl: Uri?
        get() {
            var uri = this.intent.data
            if (uri != null) {
                Log.d(TAG, "Using URL from Intent ($uri).")
                return uri
            } else {
                try {
                    val info = this.packageManager.getActivityInfo(
                        ComponentName(this, this.javaClass),
                        128
                    )
                    if (info.metaData != null && info.metaData.containsKey(METADATA_DEFAULT_URL)) {
                        uri =
                            Uri.parse(info.metaData.getString(METADATA_DEFAULT_URL))
                        Log.d(TAG, "Using URL from Manifest ($uri).")
                        return uri
                    }
                } catch (var3: PackageManager.NameNotFoundException) {
                }

                return Uri.parse("https://www.example.com/")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chromePackage =
            CustomTabsClient.getPackageName(this, TrustedWebUtils.SUPPORTED_CHROME_PACKAGES, true)
        if (chromePackage == null) {
            TrustedWebUtils.showNoPackageToast(this)
            this.finish()
        } else {
            if (!sChromeVersionChecked) {
                TrustedWebUtils.promptForChromeUpdateIfNeeded(this, chromePackage)
                sChromeVersionChecked = true
            }

            if (savedInstanceState != null && savedInstanceState.getBoolean(TWA_WAS_LAUNCHED_KEY)) {
                this.finish()
            } else {
                mServiceConnection = TwaCustomTabsServiceConnection()
                CustomTabsClient.bindCustomTabsService(
                    this,
                    chromePackage,
                    mServiceConnection
                )
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (this.mTwaWasLaunched) {
            this.finish()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        if (mServiceConnection != null) {
            this.unbindService(this.mServiceConnection)
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(
            TWA_WAS_LAUNCHED_KEY,
            this.mTwaWasLaunched
        )
    }

    protected fun getSession(client: CustomTabsClient): CustomTabsSession? {
        return client.newSession(null as CustomTabsCallback?, SESSION_ID)
    }

    protected fun getCustomTabsIntent(session: CustomTabsSession?): CustomTabsIntent {
        return CustomTabsIntent.Builder(session).build()
    }

    private fun finishAndRemoveTaskCompat() {
        if (Build.VERSION.SDK_INT >= 21) {
            this.finishAndRemoveTask()
        } else {
            this.finish()
        }

    }

    private inner class TwaCustomTabsServiceConnection() : CustomTabsServiceConnection() {

        override fun onCustomTabsServiceConnected(
            componentName: ComponentName,
            client: CustomTabsClient
        ) {
            client.warmup(0L)
            val session = getSession(client)
            val intent = getCustomTabsIntent(session)
            val url = launchingUrl
            Log.d(TAG, "Launching Trusted Web Activity.")
            TrustedWebUtils.launchAsTrustedWebActivity(
                this@LauncherActivity,
                session!!,
                intent,
                url
            )
            mTwaWasLaunched = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {}
    }

    companion object {
        private val TAG = "LauncherActivity"
        private val METADATA_DEFAULT_URL = "android.support.customtabs.trusted.DEFAULT_URL"
        private val TWA_WAS_LAUNCHED_KEY = "android.support.customtabs.trusted.TWA_WAS_LAUNCHED_KEY"
        private val SESSION_ID = 96375
        private var sChromeVersionChecked: Boolean = false
    }
}
