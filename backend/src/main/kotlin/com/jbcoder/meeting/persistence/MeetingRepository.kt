package com.jbcoder.meeting.persistence

import com.jbcoder.meeting.domain.MeetingStatus
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object MeetingRepository {
    
    fun createMeeting(entity: MeetingEntity): MeetingEntity = transaction {
        MeetingsTable.insert {
            it[id] = entity.id
            it[publicMeetingCode] = entity.publicMeetingCode
            it[livekitRoomName] = entity.livekitRoomName
            it[title] = entity.title
            it[passcodeHash] = entity.passcodeHash
            it[hostSecretHash] = entity.hostSecretHash
            it[status] = entity.status.name
            it[waitingRoomEnabled] = entity.waitingRoomEnabled
            it[joinBeforeHostEnabled] = entity.joinBeforeHostEnabled
            it[isLocked] = entity.isLocked
            it[maximumParticipants] = entity.maximumParticipants
            it[scheduledStart] = entity.scheduledStart
            it[scheduledEnd] = entity.scheduledEnd
            it[actualStart] = entity.actualStart
            it[actualEnd] = entity.actualEnd
            it[expiresAt] = entity.expiresAt
            it[createdAt] = entity.createdAt
            it[updatedAt] = entity.updatedAt
            it[optimisticLockVersion] = entity.optimisticLockVersion
        }
        entity
    }

    fun findByPublicCode(code: String): MeetingEntity? = transaction {
        MeetingsTable.selectAll().where { MeetingsTable.publicMeetingCode eq code }.singleOrNull()?.let {
            mapRowToEntity(it)
        }
    }
    
    fun findById(id: UUID): MeetingEntity? = transaction {
        MeetingsTable.selectAll().where { MeetingsTable.id eq id }.singleOrNull()?.let {
            mapRowToEntity(it)
        }
    }

    fun findByIdForUpdate(id: UUID): MeetingEntity? = transaction {
        MeetingsTable.selectAll().where { MeetingsTable.id eq id }.forUpdate().singleOrNull()?.let {
            mapRowToEntity(it)
        }
    }

    fun updateStatus(id: UUID, newStatus: MeetingStatus): Boolean = transaction {
        MeetingsTable.update({ MeetingsTable.id eq id }) {
            it[status] = newStatus.name
            it[updatedAt] = Instant.now()
        } > 0
    }

    fun updateStatusWithOptimisticLock(id: UUID, currentVersion: Int, newStatus: MeetingStatus): Boolean = transaction {
        val updatedRows = MeetingsTable.update({ (MeetingsTable.id eq id) and (MeetingsTable.optimisticLockVersion eq currentVersion) }) {
            it[status] = newStatus.name
            if (newStatus == MeetingStatus.LIVE) {
                it[actualStart] = Instant.now()
            }
            if (newStatus == MeetingStatus.ENDED) {
                it[actualEnd] = Instant.now()
            }
            it[updatedAt] = Instant.now()
            it[optimisticLockVersion] = currentVersion + 1
        }
        updatedRows > 0
    }

    fun updateLockStateWithOptimisticLock(id: UUID, currentVersion: Int, isLocked: Boolean): Boolean = transaction {
        val updatedRows = MeetingsTable.update({ (MeetingsTable.id eq id) and (MeetingsTable.optimisticLockVersion eq currentVersion) }) {
            it[MeetingsTable.isLocked] = isLocked
            it[updatedAt] = Instant.now()
            it[optimisticLockVersion] = currentVersion + 1
        }
        updatedRows > 0
    }

    private fun mapRowToEntity(row: ResultRow): MeetingEntity {
        return MeetingEntity(
            id = row[MeetingsTable.id],
            publicMeetingCode = row[MeetingsTable.publicMeetingCode],
            livekitRoomName = row[MeetingsTable.livekitRoomName],
            title = row[MeetingsTable.title],
            passcodeHash = row[MeetingsTable.passcodeHash],
            hostSecretHash = row[MeetingsTable.hostSecretHash],
            status = MeetingStatus.valueOf(row[MeetingsTable.status]),
            waitingRoomEnabled = row[MeetingsTable.waitingRoomEnabled],
            joinBeforeHostEnabled = row[MeetingsTable.joinBeforeHostEnabled],
            isLocked = row[MeetingsTable.isLocked],
            maximumParticipants = row[MeetingsTable.maximumParticipants],
            scheduledStart = row[MeetingsTable.scheduledStart],
            scheduledEnd = row[MeetingsTable.scheduledEnd],
            actualStart = row[MeetingsTable.actualStart],
            actualEnd = row[MeetingsTable.actualEnd],
            expiresAt = row[MeetingsTable.expiresAt],
            createdAt = row[MeetingsTable.createdAt],
            updatedAt = row[MeetingsTable.updatedAt],
            optimisticLockVersion = row[MeetingsTable.optimisticLockVersion]
        )
    }
}
