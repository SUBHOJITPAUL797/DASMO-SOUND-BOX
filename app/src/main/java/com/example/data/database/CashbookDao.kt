package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entity.CustomerBorrowEntity
import com.example.data.database.entity.WalletTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashbookDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun getAllWalletTransactions(): Flow<List<WalletTransactionEntity>>

    @Query("SELECT SUM(amount) FROM wallet_transactions WHERE walletType = :walletType")
    fun getWalletBalance(walletType: String): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalletTransaction(transaction: WalletTransactionEntity)

    @Query("SELECT * FROM customer_borrows ORDER BY timestamp DESC")
    fun getAllCustomerBorrows(): Flow<List<CustomerBorrowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerBorrow(borrow: CustomerBorrowEntity)

    @Update
    suspend fun updateCustomerBorrow(borrow: CustomerBorrowEntity)
    
    @Query("SELECT SUM(amount) FROM customer_borrows WHERE isCleared = 0")
    fun getTotalPendingBorrows(): Flow<Double?>
}
