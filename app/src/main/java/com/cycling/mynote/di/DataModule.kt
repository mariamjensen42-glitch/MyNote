package com.cycling.mynote.di

import android.content.Context
import androidx.room.Room
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.data.index.NoteIndexDao
import com.cycling.mynote.data.index.NoteIndexDatabase
import com.cycling.mynote.data.repository.NoteRepositoryImpl
import com.cycling.mynote.data.repository.RepoRepositoryImpl
import com.cycling.mynote.data.repository.SearchRepositoryImpl
import com.cycling.mynote.data.repository.SettingsRepositoryImpl
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.domain.repository.RepoRepository
import com.cycling.mynote.domain.repository.SearchRepository
import com.cycling.mynote.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The search index database.
 *
 * `fallbackToDestructiveMigration` is the right policy here and not a shortcut: the table is a
 * rebuildable cache of the Markdown folder, so the correct response to a schema change is to drop
 * it and re-scan, which the app does anyway on first launch after an upgrade. Asking the user to
 * wait for a migration of data that is derivable from their own files would be pure cost.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNoteIndexDatabase(
        @ApplicationContext context: Context,
    ): NoteIndexDatabase = Room
        .databaseBuilder(context, NoteIndexDatabase::class.java, "mynote-index.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideNoteIndexDao(database: NoteIndexDatabase): NoteIndexDao = database.noteIndexDao()

    /**
     * One formatter for the whole app.
     *
     * A singleton with the device's default zone and locale so every screen agrees on when "昨天"
     * starts; a per-screen instance would be equally correct today and a divergence the moment
     * someone adds a custom zone to one of them.
     */
    @Provides
    @Singleton
    fun provideRelativeTimeFormatter(): RelativeTimeFormatter = RelativeTimeFormatter()
}

/**
 * Binds each repository interface to its implementation.
 *
 * `@Binds` rather than `@Provides`: the implementations are constructor-injected, so binding them
 * generates a direct cast instead of a factory method body, and Hilt can keep them out of the
 * component's explicit provision list.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRepoRepository(impl: RepoRepositoryImpl): RepoRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
