package tech.progma.parcelpath.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import tech.progma.parcelpath.domain.ShipmentStatus
import java.time.Instant
import java.util.UUID

interface ShipmentRepository : JpaRepository<ShipmentEntity, UUID> {
    fun findByTrackingNumber(trackingNumber: String): ShipmentEntity?

    fun findAllByStatus(
        status: ShipmentStatus,
        pageable: Pageable,
    ): Page<ShipmentEntity>

    /** Active shipments with no scan since [before]: the "where is my parcel?" list for operations. */
    @Query(
        """
        select s from ShipmentEntity s
        where s.status in :statuses
          and coalesce(s.lastEventAt, s.createdAt) < :before
        order by coalesce(s.lastEventAt, s.createdAt) asc
        """,
    )
    fun findStale(
        @Param("statuses") statuses: Collection<ShipmentStatus>,
        @Param("before") before: Instant,
        pageable: Pageable,
    ): Page<ShipmentEntity>
}

interface TrackingEventRepository : JpaRepository<TrackingEventEntity, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): TrackingEventEntity?

    fun findAllByShipmentIdOrderByOccurredAtAscRecordedAtAsc(shipmentId: UUID): List<TrackingEventEntity>

    fun findAllByShipmentId(
        shipmentId: UUID,
        pageable: Pageable,
    ): Page<TrackingEventEntity>

    fun countByShipmentId(shipmentId: UUID): Long
}
