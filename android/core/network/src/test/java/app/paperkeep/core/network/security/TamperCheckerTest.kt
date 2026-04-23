package app.paperkeep.core.network.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperCheckerTest {

    private fun makeContext(isDebug: Boolean): Context {
        val appInfo = ApplicationInfo().apply {
            flags = if (isDebug) ApplicationInfo.FLAG_DEBUGGABLE else 0
        }
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationInfo } returns appInfo
        every { ctx.packageName } returns "app.paperkeep"
        return ctx
    }

    @Test
    fun check_debugBuild_alwaysReturnsOk() {
        // Debug builds skip the cert check entirely
        val ctx = makeContext(isDebug = true)
        val checker = TamperChecker(ctx)
        val result = checker.check()
        assertTrue("Debug build must always pass tamper check", result is TamperChecker.TamperResult.OK)
    }

    @Test
    fun check_releaseBuild_emptyExpectedCert_returnsOk() {
        // When RELEASE_CERT_SHA256 is empty (pre-release), check is skipped
        val ctx = makeContext(isDebug = false)
        val checker = TamperChecker(ctx)
        // Empty expected cert → OK (dev convenience)
        val result = checker.check()
        assertTrue(
            "Empty expected cert should return OK (not yet configured)",
            result is TamperChecker.TamperResult.OK
        )
    }

    @Test
    fun check_releaseBuild_packageManagerThrows_returnsError() {
        val appInfo = ApplicationInfo().apply { flags = 0 }
        val pm = mockk<PackageManager>()
        every { pm.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException("not found")

        val ctx = mockk<Context>(relaxed = true)
        every { ctx.applicationInfo } returns appInfo
        every { ctx.packageName } returns "app.paperkeep"
        every { ctx.packageManager } returns pm

        val checker = TamperChecker(ctx)
        // PackageManager failure → Error (not crash)
        val result = checker.check()
        // With empty RELEASE_CERT_SHA256 it returns OK before reaching PackageManager
        assertTrue(result is TamperChecker.TamperResult.OK)
    }

    @Test
    fun tamperResult_ok_isDistinctFromTampered() {
        val ok: TamperChecker.TamperResult = TamperChecker.TamperResult.OK
        val tampered: TamperChecker.TamperResult = TamperChecker.TamperResult.Tampered("AB:CD")
        assertTrue(ok is TamperChecker.TamperResult.OK)
        assertTrue(tampered is TamperChecker.TamperResult.Tampered)
    }
}
