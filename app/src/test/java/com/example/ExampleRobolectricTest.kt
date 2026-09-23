package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.CompatibilityChecker
import com.example.core.LinuxEnvironment
import com.example.data.SecStationRepository
import com.example.model.CompatibilityStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var linuxEnv: LinuxEnvironment
    private lateinit var repository: SecStationRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        linuxEnv = LinuxEnvironment(context)
        repository = SecStationRepository(context)
    }

    @Test
    fun `read string from context verifies app name`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Security Workstation", appName)
    }

    @Test
    fun `linux environment bootstrap creates directories and config files`() = runBlocking {
        val result = linuxEnv.bootstrap()
        assertTrue(result.isSuccess)
        assertTrue(linuxEnv.isEnvironmentReady())

        // Check essential directories
        assertTrue(linuxEnv.usrDir.exists())
        assertTrue(linuxEnv.homeDir.exists())
        assertTrue(linuxEnv.binDir.exists())
        assertTrue(linuxEnv.etcDir.exists())
        assertTrue(linuxEnv.bugbountyDir.exists())

        // Check essential files
        val bashrc = File(linuxEnv.homeDir, ".bashrc")
        assertTrue(bashrc.exists())
        val bashrcContent = bashrc.readText()
        assertTrue(bashrcContent.contains("PREFIX="))
        assertTrue(bashrcContent.contains("KALI_ROOTLESS"))

        val motd = File(linuxEnv.etcDir, "motd")
        assertTrue(motd.exists())
        assertTrue(motd.readText().contains("KALI LINUX ROOTLESS"))
    }

    @Test
    fun `compatibility checker correctly identifies rootless capabilities`() {
        val nmapCompat = CompatibilityChecker.checkTool("nmap")
        assertEquals(CompatibilityStatus.LIMITED, nmapCompat.status)
        assertNotNull(nmapCompat.rootlessAlternative)

        val curlCompat = CompatibilityChecker.checkTool("curl")
        assertEquals(CompatibilityStatus.COMPATIBLE, curlCompat.status)

        val tcpdumpCompat = CompatibilityChecker.checkTool("tcpdump")
        assertEquals(CompatibilityStatus.UNSUPPORTED, tcpdumpCompat.status)
        assertTrue(tcpdumpCompat.technicalReason.contains("CAP_NET_RAW"))
    }

    @Test
    fun `package manager toggle and cli installation works`() {
        val initialStatus = repository.packages.value.first { it.id == "curl" }.isInstalled
        assertTrue(initialStatus)

        // Install an uninstalled package
        val installMsg = repository.installPackageByName("sqlmap")
        assertTrue(installMsg.contains("Successfully installed sqlmap"))
        val sqlmapPkg = repository.packages.value.first { it.id == "sqlmap" }
        assertTrue(sqlmapPkg.isInstalled)
    }

    @Test
    fun `system resource metrics are populated correctly`() {
        val metrics = repository.getSystemMetrics()
        assertNotNull(metrics.architecture)
        assertTrue(metrics.totalRamMb > 0)
        assertTrue(metrics.storageTotalMb > 0)
    }
}
