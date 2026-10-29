package com.example.plain

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.plain.data.local.PlainDatabase
import com.example.plain.data.repository.RoomCalendarRepository
import com.example.plain.domain.repository.CalendarRepository

/**
 * 애플리케이션 수명 동안 데이터베이스와 저장소 인스턴스를 소유합니다.
 *
 * 현재 MVP에는 별도 DI 프레임워크를 도입하지 않고 단순한 애플리케이션 컨테이너를
 * 사용합니다. 프로세스가 종료되면 Room이 데이터베이스 자원을 정리합니다.
 */
class PlainApplication : Application() {
    val calendarRepository: CalendarRepository by lazy {
        val database = Room.databaseBuilder(
            applicationContext,
            PlainDatabase::class.java,
            DATABASE_NAME,
        ).addCallback(InitialTagCallback()).build()

        RoomCalendarRepository(
            eventDao = database.calendarEventDao(),
            tagDao = database.scheduleTagDao(),
        )
    }

    private class InitialTagCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 최초 설치에만 기본 태그를 넣습니다. 이후 사용자가 이름이나 색상을 바꿔도 덮어쓰지 않습니다.
            db.execSQL(
                "INSERT INTO schedule_tags(name, colorArgb) VALUES(?, ?)",
                arrayOf<Any>("업무", 0xFF3B82F6L),
            )
            db.execSQL(
                "INSERT INTO schedule_tags(name, colorArgb) VALUES(?, ?)",
                arrayOf<Any>("일상", 0xFF10B981L),
            )
        }
    }

    private companion object {
        const val DATABASE_NAME = "plain.db"
    }
}
