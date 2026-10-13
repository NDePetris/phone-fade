package app.ndplabs.phonefade

data class MonitoringCheckpoint(
    val config: MonitoringConfig = MonitoringConfig(),
    val session: SessionState = SessionState(),
    val bootCount: Int? = null,
    val pending: DisplayEffect? = null,
    val attention: String? = null,
    val interrupted: Boolean = false,
    val pendingPurpose: OperationPurpose? = null,
    val pendingTarget: SessionState? = null,
    val expectedBaseline: ColorSettings? = null,
)

interface MonitoringStore {
    fun load(): MonitoringCheckpoint
    /** Must be durable on return, or throw. */
    fun save(checkpoint: MonitoringCheckpoint)
}
