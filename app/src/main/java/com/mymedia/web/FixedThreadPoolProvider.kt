package com.mymedia.web

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FixedThreadPoolProvider {
    @Singleton
    @Provides
    fun providerPool(): ExecutorService {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    }
}