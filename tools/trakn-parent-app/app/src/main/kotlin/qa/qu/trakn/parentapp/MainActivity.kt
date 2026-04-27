package qa.qu.trakn.parentapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.Coil
import coil.ImageLoader
import okhttp3.OkHttpClient
import qa.qu.trakn.parentapp.data.SettingsRepository
import qa.qu.trakn.parentapp.ui.MainScreen
import qa.qu.trakn.parentapp.ui.locate.LocateViewModel
import qa.qu.trakn.parentapp.ui.settings.SettingsViewModel
import qa.qu.trakn.parentapp.ui.theme.TRAKNParentTheme
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> setupCompose() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Trust self-signed cert for Coil image loading
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslCtx = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
                        .hostnameVerifier { _, _ -> true }
                        .build()
                }
                .build()
        )

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) setupCompose() else permissionLauncher.launch(requiredPermissions)
    }

    private fun setupCompose() {
        val settingsRepo = SettingsRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(LocateViewModel::class.java) ->
                    LocateViewModel(applicationContext, settingsRepo) as T
                modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                    SettingsViewModel(applicationContext, settingsRepo) as T
                else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
            }
        }

        val locateVM   = ViewModelProvider(this, factory)[LocateViewModel::class.java]
        val settingsVM = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        setContent {
            TRAKNParentTheme {
                MainScreen(
                    locateViewModel  = locateVM,
                    settingsViewModel = settingsVM,
                    settingsRepo     = settingsRepo,
                )
            }
        }
    }
}
