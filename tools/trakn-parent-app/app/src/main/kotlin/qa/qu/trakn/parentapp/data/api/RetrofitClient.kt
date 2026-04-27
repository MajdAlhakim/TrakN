package qa.qu.trakn.parentapp.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {

    private var baseUrl: String = ""
    private var instance: ApiService? = null

    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })

    private val sslCtx = SSLContext.getInstance("TLS").apply {
        init(null, trustAll, SecureRandom())
    }

    private val okHttp = OkHttpClient.Builder()
        .sslSocketFactory(sslCtx.socketFactory, trustAll[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    fun get(url: String): ApiService {
        if (url != baseUrl || instance == null) {
            baseUrl = url
            instance = Retrofit.Builder()
                .baseUrl(url.trimEnd('/') + "/")
                .client(okHttp)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
        return instance!!
    }
}
