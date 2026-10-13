package app.ndplabs.phonefade

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenEventMapperTest {
    @Test fun userPresentIsUnlockEvenWhenKeyguardSnapshotLags() {
        for (observed in listOf(DeviceActivity.LOCKED, DeviceActivity.UNLOCKED)) {
            assertEquals(SessionEvent.Observe(DeviceActivity.UNLOCKED),
                ScreenEventMapper.event(ScreenSignal.USER_PRESENT, observed))
        }
    }

    @Test fun userPresentDeliveredAfterScreenOffDoesNotFabricateUse() {
        assertEquals(SessionEvent.Observe(DeviceActivity.OFF),
            ScreenEventMapper.event(ScreenSignal.USER_PRESENT, DeviceActivity.OFF))
    }

    @Test fun screenOnUsesSnapshotWithoutInventingUnlock() {
        for (observed in listOf(DeviceActivity.LOCKED, DeviceActivity.UNLOCKED)) {
            assertEquals(SessionEvent.Observe(observed),
                ScreenEventMapper.event(ScreenSignal.SCREEN_ON, observed))
        }
    }

    @Test fun staleScreenBoundariesStillInvalidateUnknownGaps() {
        assertEquals(SessionEvent.Recover(DeviceActivity.OFF),
            ScreenEventMapper.event(ScreenSignal.SCREEN_ON, DeviceActivity.OFF))
        for (observed in listOf(DeviceActivity.LOCKED, DeviceActivity.UNLOCKED)) {
            assertEquals(SessionEvent.Recover(observed),
                ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, observed))
        }
    }

    @Test fun screenOffStartsObservedBreak() {
        assertEquals(SessionEvent.Observe(DeviceActivity.OFF),
            ScreenEventMapper.event(ScreenSignal.SCREEN_OFF, DeviceActivity.OFF))
    }
}
