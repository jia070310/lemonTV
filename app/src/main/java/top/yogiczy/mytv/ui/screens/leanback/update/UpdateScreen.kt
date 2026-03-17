package top.yogiczy.mytv.ui.screens.leanback.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.screens.leanback.update.components.LeanbackUpdateDialog
import top.yogiczy.mytv.utils.ApkInstaller
import top.yogiczy.mytv.utils.Logger
import java.io.File

@Composable
fun LeanbackUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {
    val log = remember { Logger.create("LeanbackUpdateScreen") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val packageInfo = rememberPackageInfo()
    val latestFile = remember { File(AppGlobal.cacheDir, "latest.apk") }

    LaunchedEffect(Unit) {
        delay(3000)
        updateViewModel.checkUpdate(packageInfo.versionName)

        val latestRelease = updateViewModel.latestRelease
        if (
            updateViewModel.isUpdateAvailable &&
            latestRelease.version != settingsViewModel.appLastLatestVersion
        ) {
            settingsViewModel.appLastLatestVersion = latestRelease.version

            if (settingsViewModel.updateForceRemind) {
                updateViewModel.showDialog = true
            } else {
                LeanbackToastState.I.showToast("新版本: v${latestRelease.version}")
            }
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    ApkInstaller.installApk(context, latestFile.path)
                } else {
                    LeanbackToastState.I.showToast("未授予安装权限")
                }
            }
        }

    LaunchedEffect(updateViewModel.updateDownloaded) {
        if (!updateViewModel.updateDownloaded) return@LaunchedEffect

        log.i("下载完成，开始安装流程")
        log.i("APK 文件路径: ${latestFile.absolutePath}")
        log.i("APK 文件存在: ${latestFile.exists()}")
        log.i("APK 文件大小: ${latestFile.length()} 字节")

        if (!latestFile.exists()) {
            log.e("APK 文件不存在！")
            LeanbackToastState.I.showToast("安装失败：APK 文件不存在")
            return@LaunchedEffect
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Android 8.0 以下，直接安装
                log.i("Android 8.0 以下，直接安装")
                ApkInstaller.installApk(context, latestFile.path)
            } else {
                // Android 8.0+，尝试直接安装（电视盒子可能默认允许）
                log.i("Android 8.0+，检查安装权限")
                val canInstall = try {
                    context.packageManager.canRequestPackageInstalls()
                } catch (e: Exception) {
                    log.w("无法检查安装权限，尝试直接安装", e)
                    true // 默认假设可以安装
                }
                
                if (canInstall) {
                    log.i("已授权安装权限，开始安装")
                    ApkInstaller.installApk(context, latestFile.path)
                } else {
                    log.w("未授权安装权限，尝试直接安装（电视盒子可能支持）")
                    // 即使未授权，也尝试安装（电视盒子通常会自动弹权限框）
                    try {
                        ApkInstaller.installApk(context, latestFile.path)
                    } catch (installError: Exception) {
                        log.e("直接安装失败，尝试打开设置页面", installError)
                        // 尝试跳转到设置页面
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            launcher.launch(intent)
                        } catch (settingsError: Exception) {
                            log.e("设置页面不存在，无法打开", settingsError)
                            LeanbackToastState.I.showToast("安装失败：系统不支持此操作")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.e("安装流程异常", e)
            LeanbackToastState.I.showToast("安装失败: ${e.message}")
        }
    }

    LeanbackUpdateDialog(
        modifier = modifier,
        showDialogProvider = { updateViewModel.showDialog },
        onDismissRequest = { updateViewModel.showDialog = false },
        releaseProvider = { updateViewModel.latestRelease },
        onUpdateAndInstall = {
            updateViewModel.showDialog = false
            coroutineScope.launch(Dispatchers.IO) {
                updateViewModel.downloadAndUpdate(latestFile)
            }
        },
    )
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)
