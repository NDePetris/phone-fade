package app.ndplabs.phonefade

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/** Synthetic, static states only. Previews cannot access the coordinator or system settings. */
class QuietRoadPreviewStates : PreviewParameterProvider<MonitoringState> {
    override val values: Sequence<MonitoringState> get() {
        val display = ControllerStatus(permissionGranted = true, readable = true, current = ColorSettings("0", "12"))
        val session = SessionState(enabled = true, hasSession = true, consumedMs = 180_000, activity = DeviceActivity.UNLOCKED)
        val active = MonitoringState(MonitoringCheckpoint(session = session), display, running = true, ready = true)
        val grayscale = display.copy(current = ColorSettings("1", "0"), original = display.current)
        val automatic = active.copy(checkpoint = active.checkpoint.copy(session = session.copy(exhausted = true)), display = grayscale)
        return sequenceOf(
            active,
            automatic,
            MonitoringState(display = grayscale, ready = true),
            active.copy(checkpoint = active.checkpoint.copy(session = session.copy(exhausted = true,
                temporary = TemporaryColorState(durationMs = 300_000, consumedMs = 268_000)))),
            automatic.copy(checkpoint = automatic.checkpoint.copy(attention = "Permission missing. Grant it again before retrying restoration."),
                display = grayscale.copy(permissionGranted = false)),
            active.copy(checkpoint = active.checkpoint.copy(session = session.copy(activity = DeviceActivity.LOCKED))),
            active.copy(checkpoint = active.checkpoint.copy(session = session.copy(activity = DeviceActivity.OFF))),
            active.copy(running = false, checkpoint = active.checkpoint.copy(interrupted = true)),
            active.copy(checkpoint = active.checkpoint.copy(interrupted = true)),
            MonitoringState(display = display, ready = true),
        )
    }
}

@Preview(name = "Quiet Road • light", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Quiet Road • dark", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Quiet Road • large text", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 2f)
@Preview(name = "Quiet Road • landscape", showBackground = true, widthDp = 800, heightDp = 400)
@Composable
private fun QuietRoadScreenPreview(@PreviewParameter(QuietRoadPreviewStates::class) state: MonitoringState) {
    FadeTheme { FadeScreen(state, true, restore = {}, manualEnable = {}, temporaryColor = {}, configure = {}, start = {}) }
}
