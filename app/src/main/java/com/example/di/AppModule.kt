package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.database.DedupDao
import com.example.data.database.TransactionDao
import com.example.data.repository.SettingsRepositoryImpl
import com.example.data.repository.TransactionRepositoryImpl
import com.example.domain.repository.SettingsRepository
import com.example.domain.repository.TransactionRepository
import com.example.domain.repository.CashbookRepository
import com.example.domain.usecase.SaveTransactionUseCase
import com.example.service.PaymentEventBus
import com.example.util.DedupEngine
import com.example.util.TtsEngine
import com.example.util.update.AppUpdateManager

object AppModule {
    @Volatile private var database: AppDatabase? = null
    @Volatile var transactionRepository: TransactionRepository? = null
    @Volatile var cashbookRepository: CashbookRepository? = null
    @Volatile var settingsRepository: SettingsRepository? = null
    @Volatile var saveTransactionUseCase: SaveTransactionUseCase? = null
    @Volatile var paymentEventBus: PaymentEventBus? = null
    @Volatile var dedupEngine: DedupEngine? = null
    @Volatile var ttsEngine: TtsEngine? = null
    @Volatile var updateManager: AppUpdateManager? = null

    @Synchronized
    fun getOrInit(context: Context): AppModule {
        if (database == null) {
            init(context)
        }
        return this
    }

    @Synchronized
    fun init(context: Context) {
        if (database != null) return
        val appContext = context.applicationContext
        val db = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "soundbox_db"
        ).fallbackToDestructiveMigration().build()
        
        database = db
        val transRepo = TransactionRepositoryImpl(db.transactionDao())
        val cashRepo = CashbookRepository(db.cashbookDao())
        val setRepo = SettingsRepositoryImpl(appContext)
        
        transactionRepository = transRepo
        cashbookRepository = cashRepo
        settingsRepository = setRepo
        saveTransactionUseCase = SaveTransactionUseCase(transRepo)
        
        paymentEventBus = PaymentEventBus()
        dedupEngine = DedupEngine(db.dedupDao(), setRepo)
        ttsEngine = TtsEngine(appContext)
        updateManager = AppUpdateManager.getInstance()
    }
}

