package app.pwhs.universalinstaller.privileged

import android.content.ComponentName
import android.content.Intent
import android.content.pm.IPackageManager
import android.os.IBinder
import android.os.ServiceManager
import app.pwhs.universalinstaller.util.DefaultInstallerLogic
import com.topjohnwu.superuser.ipc.RootService

/**
 * libsu-spawned process running as UID 0. We hand back a Binder implementing
 * [IPrivilegedService]. Persistent preferred activities require system UID 1000,
 * not root UID 0, so each role operation temporarily changes effective UID.
 */
class PrivilegedRootService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/IPackageManager",
                "Landroid/os/ServiceManager",
                "Landroid/content/pm/ParceledListSlice",
                "Landroid/content/pm/BaseParceledListSlice"
            )
        }
        return ServiceBinder() as IBinder
    }
    
    private class ServiceBinder : IPrivilegedService.Stub() {
        private inline fun asSystem(block: () -> Unit) {
            val uid = android.system.Os.geteuid()
            try {
                if (uid == 0) android.system.Os.seteuid(1000)
                block()
            } finally {
                if (uid == 0) android.system.Os.seteuid(uid)
            }
        }

        override fun setDefaultInstaller(component: ComponentName, lock: Boolean) {
            // Resolve in the isolated privileged process; role calls use system euid below.
            val pmBinder = ServiceManager.getService("package")
                ?: error("system_service 'package' returned null in root process")
            val iPm = IPackageManager.Stub.asInterface(pmBinder)
            asSystem { DefaultInstallerLogic.setDefaultInstaller(
                iPackageManager = iPm,
                component = component,
                lock = lock,
                hasSystemLevelPermission = true,
            ) }
        }

        override fun setDefaultUninstaller(component: ComponentName, lock: Boolean) {
            val pmBinder = ServiceManager.getService("package")
                ?: error("system_service 'package' returned null in root process")
            val iPm = IPackageManager.Stub.asInterface(pmBinder)
            asSystem { app.pwhs.universalinstaller.util.DefaultUninstallerLogic.setDefaultUninstaller(
                iPackageManager = iPm,
                component = component,
                lock = lock,
                hasSystemLevelPermission = true,
            ) }
        }
    }
}
