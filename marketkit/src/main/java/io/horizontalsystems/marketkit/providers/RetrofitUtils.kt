package io.horizontalsystems.marketkit.providers

import android.annotation.SuppressLint
import com.google.gson.GsonBuilder
import io.horizontalsystems.marketkit.HSCache
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitUtils {

    private fun buildClient(headers: Map<String, String>): OkHttpClient {
        val cache = HSCache.cacheDir?.let {
            Cache(it, HSCache.cacheQuotaBytes)
        }
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)

        val headersInterceptor = Interceptor { chain ->
            val request = chain.request()
            try {
                var response = chain.proceed(request)
                val requestBuilder = chain.request().newBuilder()
                headers.forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }
                response.close()
                response = chain.proceed(requestBuilder.build())
                return@Interceptor response
            } catch (e: Exception) {
                return@Interceptor chain.proceed(request)
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(headersInterceptor)

//            .proxy(Proxy( Proxy.Type.HTTP , InetSocketAddress("47.89.208.160", 58972) ))

            .cache(cache)
            .build()
    }

    fun build(baseUrl: String, headers: Map<String, String> = mapOf()): Retrofit {
        val client = buildClient(headers)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setLenient().create())
            )
            .build()
    }

    fun buildUnsafe(baseUrl: String, headers: Map<String, String> = mapOf()): Retrofit {
        val client = getUnsafeOkHttpClient(headers)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().setLenient().create())
            )
            .build()
    }

    @SuppressLint("TrustAllX509TrustManager", "BadHostnameVerifier")
    fun getUnsafeOkHttpClient(headers: Map<String, String>): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<X509Certificate>,
                                                    authType: String) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<X509Certificate>,
                                                    authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val cache = HSCache.cacheDir?.let {
                Cache(it, HSCache.cacheQuotaBytes)
            }
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)

            val headersInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                headers.forEach { (name, value) ->
                    requestBuilder.header(name, value)
                }
                chain.proceed(requestBuilder.build())
            }

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, (trustAllCerts[0] as X509TrustManager))
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
            builder.connectTimeout(5000, TimeUnit.MILLISECONDS)
            builder.readTimeout(60000, TimeUnit.MILLISECONDS)
            builder.addInterceptor(loggingInterceptor)
            builder.addInterceptor(headersInterceptor)
            builder.cache(cache)
            builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

}
