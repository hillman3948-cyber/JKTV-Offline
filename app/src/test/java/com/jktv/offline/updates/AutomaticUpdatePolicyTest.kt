package com.jktv.offline.updates

import org.junit.Assert.*
import org.junit.Test

class AutomaticUpdatePolicyTest {
    @Test fun cooldownAndRetryUseMonotonicTime() {
        val policy = AutomaticUpdatePolicy()
        assertTrue(policy.canCheck(0))
        policy.attempted(100)
        assertFalse(policy.canCheck(101))
        assertFalse(policy.canCheck(AutomaticUpdatePolicy.CHECK_INTERVAL_MS))
        assertTrue(policy.canCheck(100 + AutomaticUpdatePolicy.CHECK_INTERVAL_MS))
        policy.failed(200)
        assertTrue(policy.canCheck(200 + AutomaticUpdatePolicy.RETRY_INTERVAL_MS))
        policy.interrupted()
        assertTrue(policy.canCheck(201))
    }

    @Test fun laterSnoozesOnlyThatBuildForOneDayAndSurvivesClockRollback() {
        val p = AutomaticUpdatePolicy()
        assertFalse(p.shouldNotify("build20", "build20", 100, 101))
        assertTrue(p.shouldNotify("build21", "build20", 100, 101))
        assertTrue(p.shouldNotify("build20", "build20", 100, 100 + AutomaticUpdatePolicy.SNOOZE_MS))
        assertTrue(p.shouldNotify("build20", "build20", 100, 99))
        assertTrue(p.shouldNotify("build20", null, 0, 0))
    }
}
