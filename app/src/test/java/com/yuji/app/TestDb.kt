package com.yuji.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yuji.app.data.db.AccountEntity
import com.yuji.app.data.db.GroupEntity
import com.yuji.app.data.db.RateEntity
import com.yuji.app.data.db.YujiDatabase
import com.yuji.app.domain.PendingSnapshotFlag
import java.math.BigDecimal

val context: Context get() = ApplicationProvider.getApplicationContext()

fun memoryDb(): YujiDatabase = YujiDatabase.inMemory(context)

class FakeFlag : PendingSnapshotFlag {
    override var pending = false
}

suspend fun YujiDatabase.seed(vararg accounts: Triple<String, String, String>, rates: Map<String, String> = mapOf("CNY" to "1")) {
    groups().insert(GroupEntity(id = 1, name = "资金账户"))
    rates().upsert(rates.map { (c, r) -> RateEntity(c, BigDecimal(r), 0) })
    accounts.forEachIndexed { i, (name, cur, bal) ->
        accounts().insert(AccountEntity(id = i + 1L, groupId = 1, name = name, currency = cur, balance = BigDecimal(bal), createdAt = 0, updatedAt = 0))
    }
}
